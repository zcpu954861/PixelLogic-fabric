package com.pixelmc.pixellogic.server.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.core.simulation.context.SimulationBlockFact;
import com.pixelmc.pixellogic.core.simulation.context.SimulationEntity;
import com.pixelmc.pixellogic.core.simulation.context.SimulationPosition;
import com.pixelmc.pixellogic.core.simulation.context.SimulationRegionFact;
import com.pixelmc.pixellogic.core.simulation.context.SimulationWorld;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

final class SimulationTestContextParser {
    private static final int MAX_TEST_PLAYER_NAME_LENGTH = 64;
    private static final int MAX_TEST_TAGS = 32;
    private static final int MAX_TEST_TAG_LENGTH = 64;
    private static final int MAX_TEST_REGIONS = 8;
    private static final int MAX_TEST_REGION_NAME_LENGTH = 64;
    private static final int MAX_COORDINATE = 30_000_000;
    private static final int MIN_TEST_Y = -2048;
    private static final int MAX_TEST_Y = 4096;
    private static final Pattern NAMESPACED_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private final Gson gson;
    private final UUID actorId;
    private final String defaultActorName;

    SimulationTestContextParser(Gson gson, UUID actorId, String defaultActorName) {
        this.gson = gson;
        this.actorId = actorId;
        this.defaultActorName = defaultActorName;
    }

    Parsed parse(String body) {
        if (body == null || body.isBlank()) {
            return defaultContext();
        }
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("测试上下文请求格式无效。");
            }
            JsonObject root = element.getAsJsonObject();
            JsonElement contextElement = root.get("testContext");
            if (contextElement == null || contextElement.isJsonNull()) {
                return defaultContext();
            }
            if (!contextElement.isJsonObject()) {
                throw new IllegalArgumentException("测试上下文格式无效。");
            }
            JsonObject context = contextElement.getAsJsonObject();
            JsonElement worldElement = context.get("world");
            SimulationPosition position = parsePlayerPosition(worldElement);
            SimulationActor actor = parseActor(context.get("actor"), position);
            SimulationWorld world = parseWorld(worldElement, position.dimensionId());
            return new Parsed(actor, world);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("测试上下文请求无法解析。", exception);
        }
    }

    private SimulationActor parseActor(JsonElement actorElement, SimulationPosition position) {
        if (actorElement == null || actorElement.isJsonNull()) {
            return defaultActor(position);
        }
        try {
            if (!actorElement.isJsonObject()) {
                throw new IllegalArgumentException("测试玩家格式无效。");
            }
            TestActorRequest actor = gson.fromJson(actorElement, TestActorRequest.class);
            return new SimulationActor(
                    actorId,
                    displayName(actor.displayName()),
                    true,
                    Boolean.TRUE.equals(actor.operator()),
                    tags(actor.tags()),
                    position
            );
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("测试玩家请求无法解析。", exception);
        }
    }

    private Parsed defaultContext() {
        SimulationPosition position = SimulationPosition.overworldSpawn();
        return new Parsed(defaultActor(position), SimulationWorld.overworld());
    }

    private SimulationActor defaultActor(SimulationPosition position) {
        return new SimulationActor(actorId, defaultActorName, true, false, List.of(), position);
    }

    private SimulationPosition parsePlayerPosition(JsonElement worldElement) {
        if (worldElement == null || worldElement.isJsonNull()) {
            return SimulationPosition.overworldSpawn();
        }
        if (!worldElement.isJsonObject()) {
            throw new IllegalArgumentException("测试环境格式无效。");
        }
        JsonElement positionElement = worldElement.getAsJsonObject().get("playerPosition");
        if (positionElement == null || positionElement.isJsonNull()) {
            return SimulationPosition.overworldSpawn();
        }
        if (!positionElement.isJsonObject()) {
            throw new IllegalArgumentException("测试玩家位置格式无效。");
        }
        JsonObject position = positionElement.getAsJsonObject();
        return new SimulationPosition(
                namespacedId(position.get("dimensionId"), "测试玩家位置的维度 ID 不合法。", "minecraft:overworld"),
                coordinate(position.get("x"), "测试玩家位置 X", -MAX_COORDINATE, MAX_COORDINATE, 0),
                coordinate(position.get("y"), "测试玩家位置 Y", MIN_TEST_Y, MAX_TEST_Y, 64),
                coordinate(position.get("z"), "测试玩家位置 Z", -MAX_COORDINATE, MAX_COORDINATE, 0)
        );
    }

    private SimulationWorld parseWorld(JsonElement worldElement, String defaultDimensionId) {
        if (worldElement == null || worldElement.isJsonNull()) {
            return new SimulationWorld(defaultDimensionId);
        }
        JsonObject world = worldElement.getAsJsonObject();
        return new SimulationWorld(
                namespacedId(world.get("defaultDimensionId"), "测试环境默认维度 ID 不合法。", defaultDimensionId),
                parseTargetBlock(world.get("targetBlock")),
                parseRegions(world.get("regions")),
                parseTargetEntity(world.get("targetEntity"))
        );
    }

    private SimulationEntity parseTargetEntity(JsonElement targetElement) {
        if (targetElement == null || targetElement.isJsonNull()) {
            return null;
        }
        if (!targetElement.isJsonObject()) {
            throw new IllegalArgumentException("测试目标实体格式无效。");
        }
        JsonObject target = targetElement.getAsJsonObject();
        boolean enabled = target.has("enabled")
                && !target.get("enabled").isJsonNull()
                && target.get("enabled").getAsBoolean();
        String entityTypeId = namespacedId(
                target.get("entityTypeId"),
                "测试目标实体类型 ID 必须类似 minecraft:zombie。",
                "minecraft:zombie"
        );
        String displayName = target.has("displayName") && !target.get("displayName").isJsonNull()
                ? displayName(target.get("displayName").getAsString(), "测试僵尸", "测试目标实体")
                : "测试僵尸";
        String[] rawTags = target.has("tags") && !target.get("tags").isJsonNull()
                ? gson.fromJson(target.get("tags"), String[].class)
                : null;
        List<String> entityTags = tags(rawTags == null ? List.of() : Arrays.asList(rawTags));
        return enabled ? new SimulationEntity(UUID.randomUUID(), entityTypeId, displayName, entityTags) : null;
    }

    private SimulationBlockFact parseTargetBlock(JsonElement targetElement) {
        if (targetElement == null || targetElement.isJsonNull()) {
            return SimulationBlockFact.disabled();
        }
        if (!targetElement.isJsonObject()) {
            throw new IllegalArgumentException("目标方块格式无效。");
        }
        JsonObject target = targetElement.getAsJsonObject();
        boolean enabled = target.has("enabled") && !target.get("enabled").isJsonNull() && target.get("enabled").getAsBoolean();
        return new SimulationBlockFact(
                enabled,
                namespacedId(target.get("dimensionId"), "目标方块的维度 ID 不合法。", "minecraft:overworld"),
                coordinate(target.get("x"), "目标方块 X", -MAX_COORDINATE, MAX_COORDINATE, 0),
                coordinate(target.get("y"), "目标方块 Y", MIN_TEST_Y, MAX_TEST_Y, 64),
                coordinate(target.get("z"), "目标方块 Z", -MAX_COORDINATE, MAX_COORDINATE, 0),
                namespacedId(target.get("blockId"), "目标方块 ID 必须类似 minecraft:stone。", "minecraft:stone")
        );
    }

    private List<SimulationRegionFact> parseRegions(JsonElement regionsElement) {
        if (regionsElement == null || regionsElement.isJsonNull()) {
            return List.of();
        }
        if (!regionsElement.isJsonArray()) {
            throw new IllegalArgumentException("测试区域列表格式无效。");
        }
        if (regionsElement.getAsJsonArray().size() > MAX_TEST_REGIONS) {
            throw new IllegalArgumentException("测试区域数量不能超过 8 个。");
        }
        List<SimulationRegionFact> regions = new ArrayList<>();
        regionsElement.getAsJsonArray().forEach(regionElement -> {
            if (!regionElement.isJsonObject()) {
                throw new IllegalArgumentException("测试区域格式无效。");
            }
            JsonObject region = regionElement.getAsJsonObject();
            regions.add(new SimulationRegionFact(
                    regionName(region.get("name")),
                    namespacedId(region.get("dimensionId"), "测试区域的维度 ID 不合法。", "minecraft:overworld"),
                    coordinate(region.get("minX"), "测试区域 minX", -MAX_COORDINATE, MAX_COORDINATE, 0),
                    coordinate(region.get("minY"), "测试区域 minY", MIN_TEST_Y, MAX_TEST_Y, 64),
                    coordinate(region.get("minZ"), "测试区域 minZ", -MAX_COORDINATE, MAX_COORDINATE, 0),
                    coordinate(region.get("maxX"), "测试区域 maxX", -MAX_COORDINATE, MAX_COORDINATE, 0),
                    coordinate(region.get("maxY"), "测试区域 maxY", MIN_TEST_Y, MAX_TEST_Y, 64),
                    coordinate(region.get("maxZ"), "测试区域 maxZ", -MAX_COORDINATE, MAX_COORDINATE, 0)
            ));
        });
        return List.copyOf(regions);
    }

    private String displayName(String raw) {
        return displayName(raw, defaultActorName, "测试玩家");
    }

    private static String displayName(String raw, String defaultName, String label) {
        if (raw == null) {
            return defaultName;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + "名称不能为空。");
        }
        if (value.length() > MAX_TEST_PLAYER_NAME_LENGTH) {
            throw new IllegalArgumentException(label + "名称不能超过 64 个字符。");
        }
        if (hasControlCharacter(value)) {
            throw new IllegalArgumentException(label + "名称不能包含换行或控制字符。");
        }
        return value;
    }

    private static List<String> tags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String rawTag : rawTags) {
            if (rawTag == null) {
                continue;
            }
            String tag = rawTag.trim();
            if (tag.isEmpty()) {
                continue;
            }
            if (tag.length() > MAX_TEST_TAG_LENGTH) {
                throw new IllegalArgumentException("单个标签不能超过 64 个字符。");
            }
            if (hasControlCharacter(tag)) {
                throw new IllegalArgumentException("标签不能包含换行或控制字符。");
            }
            if (tag.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("标签不能包含空白字符。");
            }
            tags.add(tag);
            if (tags.size() > MAX_TEST_TAGS) {
                throw new IllegalArgumentException("标签数量不能超过 32 个。");
            }
        }
        return List.copyOf(tags);
    }

    private static String regionName(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("测试区域名称不能为空。");
        }
        String value = element.getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("测试区域名称不能为空。");
        }
        if (value.length() > MAX_TEST_REGION_NAME_LENGTH) {
            throw new IllegalArgumentException("测试区域名称不能超过 64 个字符。");
        }
        if (hasControlCharacter(value)) {
            throw new IllegalArgumentException("测试区域名称不能包含换行或控制字符。");
        }
        return value;
    }

    private static String namespacedId(JsonElement element, String message, String defaultValue) {
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        String value = element.getAsString().trim();
        if (!NAMESPACED_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static int coordinate(JsonElement element, String label, int min, int max, int defaultValue) {
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(label + " 必须是整数。");
        }
        double value = element.getAsNumber().doubleValue();
        if (!Double.isFinite(value) || value % 1 != 0) {
            throw new IllegalArgumentException(label + " 必须是整数。");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " 超出允许范围。");
        }
        return element.getAsInt();
    }

    private static boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    record Parsed(SimulationActor actor, SimulationWorld world) {
    }

    private record TestActorRequest(String displayName, List<String> tags, Boolean operator) {
    }

}
