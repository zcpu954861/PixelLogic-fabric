package com.pixelmc.pixellogic.core.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Set;
import java.util.UUID;

public record EntityTargetRef(
        EntityTargetSource source,
        UUID playerUuid,
        String playerNameHint
) {
    private static final Set<String> KEYS = Set.of("source", "playerUuid", "playerNameHint");

    public EntityTargetRef {
        if (source == null) {
            throw new IllegalArgumentException("target.source is required");
        }
        if (source == EntityTargetSource.ONLINE_PLAYER) {
            if (playerUuid == null) {
                throw new IllegalArgumentException("ONLINE_PLAYER requires playerUuid");
            }
            validateNameHint(playerNameHint);
        } else if (playerUuid != null || playerNameHint != null) {
            throw new IllegalArgumentException(source + " must not include player identity");
        }
    }

    public static EntityTargetRef currentEntity() {
        return new EntityTargetRef(EntityTargetSource.CURRENT_ENTITY, null, null);
    }

    public static EntityTargetRef conditionSubject() {
        return new EntityTargetRef(EntityTargetSource.CONDITION_SUBJECT, null, null);
    }

    public static EntityTargetRef targetEntity() {
        return new EntityTargetRef(EntityTargetSource.TARGET_ENTITY, null, null);
    }

    public static EntityTargetRef onlinePlayer(UUID playerUuid, String playerNameHint) {
        return new EntityTargetRef(EntityTargetSource.ONLINE_PLAYER, playerUuid, playerNameHint);
    }

    public static EntityTargetRef parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("target is required");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("target must be a JSON object", exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("target must be a JSON object");
        }
        JsonObject object = parsed.getAsJsonObject();
        if (object.keySet().stream().anyMatch(key -> !KEYS.contains(key))) {
            throw new IllegalArgumentException("target contains unsupported fields");
        }
        EntityTargetSource source = parseSource(object.get("source"));
        boolean hasUuid = object.has("playerUuid");
        boolean hasName = object.has("playerNameHint");
        if (source != EntityTargetSource.ONLINE_PLAYER && (hasUuid || hasName)) {
            throw new IllegalArgumentException(source + " must not include player identity");
        }
        if (source != EntityTargetSource.ONLINE_PLAYER) {
            return new EntityTargetRef(source, null, null);
        }
        if (!hasUuid || object.get("playerUuid").isJsonNull()) {
            throw new IllegalArgumentException("ONLINE_PLAYER requires playerUuid");
        }
        String rawUuid = string(object.get("playerUuid"), "playerUuid");
        UUID uuid;
        try {
            uuid = UUID.fromString(rawUuid);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("playerUuid is invalid", exception);
        }
        if (!uuid.toString().equals(rawUuid)) {
            throw new IllegalArgumentException("playerUuid must be canonical");
        }
        if (hasName && object.get("playerNameHint").isJsonNull()) {
            throw new IllegalArgumentException("playerNameHint is invalid");
        }
        String nameHint = hasName ? string(object.get("playerNameHint"), "playerNameHint") : null;
        return onlinePlayer(uuid, nameHint);
    }

    public String toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("source", source.name());
        if (source == EntityTargetSource.ONLINE_PLAYER) {
            object.addProperty("playerUuid", playerUuid.toString());
            if (playerNameHint != null) {
                object.addProperty("playerNameHint", playerNameHint);
            }
        }
        return object.toString();
    }

    private static EntityTargetSource parseSource(JsonElement element) {
        String raw = string(element, "source");
        try {
            return EntityTargetSource.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("target.source is invalid", exception);
        }
    }

    private static String string(JsonElement element, String field) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("target." + field + " must be a string");
        }
        return element.getAsString();
    }

    private static void validateNameHint(String nameHint) {
        if (nameHint == null) {
            return;
        }
        if (nameHint.length() > 64 || nameHint.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("playerNameHint is invalid");
        }
    }
}
