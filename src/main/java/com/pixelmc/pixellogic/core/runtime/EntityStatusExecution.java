package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.model.EntityStatusEffect;
import com.pixelmc.pixellogic.core.model.EntityTargetRequirement;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.PlayerGameMode;
import com.pixelmc.pixellogic.core.model.StatusEffectUpdatePolicy;

import java.util.Optional;
import java.util.regex.Pattern;

final class EntityStatusExecution {
    private static final int MAX_DURATION_SECONDS = 1_000_000;
    private static final Pattern NAMESPACED_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private EntityStatusExecution() {
    }

    static Optional<RuntimeNodeExecutionResult> execute(
            NodeDefinition node,
            RuntimeExecutionContext context,
            RuntimeServices services
    ) {
        if (!supports(node.type())) {
            return Optional.empty();
        }
        ResolvedEntityTarget target = resolve(node, context, services);
        try {
            RuntimeActionOutcome outcome = switch (node.type()) {
                case ENTITY_ADD_STATUS_EFFECT_ACTION -> addStatusEffect(node, target, services.entityProvider());
                case ENTITY_REMOVE_STATUS_EFFECT_ACTION -> removeStatusEffect(node, target, services.entityProvider());
                case PLAYER_SET_GAME_MODE_ACTION -> setGameMode(node, target);
                default -> throw new IllegalStateException("unsupported entity status action: " + node.type());
            };
            return Optional.of(new RuntimeNodeExecutionResult("done", outcome.message(), null, outcome));
        } catch (EntityActionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(
                    EntityActionErrorCode.ENTITY_ACTION_EXECUTION_FAILED,
                    node,
                    "",
                    target.reference(),
                    "实体状态动作执行失败。"
            );
        }
    }

    static boolean supports(NodeType type) {
        return type == NodeType.ENTITY_ADD_STATUS_EFFECT_ACTION
                || type == NodeType.ENTITY_REMOVE_STATUS_EFFECT_ACTION
                || type == NodeType.PLAYER_SET_GAME_MODE_ACTION;
    }

    private static RuntimeActionOutcome addStatusEffect(
            NodeDefinition node,
            ResolvedEntityTarget target,
            RuntimeEntityProvider provider
    ) {
        String effectId = effectId(node, target.reference(), provider);
        int durationSeconds = integer(node, "durationSeconds", 1, MAX_DURATION_SECONDS, target.reference());
        int level = integer(node, "level", 1, 256, target.reference());
        StatusEffectUpdatePolicy policy = updatePolicy(node, target.reference());
        EntityStatusEffect requested = new EntityStatusEffect(
                effectId,
                Math.multiplyExact(durationSeconds, 20),
                level - 1,
                bool(node, "ambient", target.reference()),
                bool(node, "showParticles", target.reference()),
                bool(node, "showIcon", target.reference())
        );
        EntityStatusEffect before = target.entity().statusEffect(effectId).orElse(null);
        RuntimeEntityAccess.StatusEffectMutation mutation = target.entity().addStatusEffect(requested, policy);
        requireAccepted(node, target.reference(), mutation, "effectId");
        EntityStatusEffect after = target.entity().statusEffect(effectId).orElseThrow(() -> failure(
                EntityActionErrorCode.STATUS_EFFECT_REJECTED,
                node,
                "effectId",
                target.reference(),
                "实体未保留请求的状态效果。"
        ));
        boolean changed = mutation == RuntimeEntityAccess.StatusEffectMutation.CHANGED;
        String message = "给予实体 " + target.reference().displayName() + " 状态效果 " + effectId
                + " " + level + " 级，持续 " + durationSeconds + " 秒（"
                + (policy == StatusEffectUpdatePolicy.REPLACE ? "强制替换" : "原版更新")
                + "）；当前可见效果 " + effect(before) + " → " + effect(after)
                + (changed ? "。" : "，未发生变化。");
        return RuntimeActionOutcome.success(node.blockId(), message, target.reference(), changed);
    }

    private static RuntimeActionOutcome removeStatusEffect(
            NodeDefinition node,
            ResolvedEntityTarget target,
            RuntimeEntityProvider provider
    ) {
        String effectId = effectId(node, target.reference(), provider);
        EntityStatusEffect before = target.entity().statusEffect(effectId).orElse(null);
        RuntimeEntityAccess.StatusEffectMutation mutation = target.entity().removeStatusEffect(effectId);
        requireAccepted(node, target.reference(), mutation, "effectId");
        boolean changed = mutation == RuntimeEntityAccess.StatusEffectMutation.CHANGED;
        String message = "从实体 " + target.reference().displayName() + " 移除状态效果 " + effectId
                + "；当前可见效果 " + effect(before) + " → 无"
                + (changed ? "。" : "，未发生变化。");
        return RuntimeActionOutcome.success(node.blockId(), message, target.reference(), changed);
    }

    private static RuntimeActionOutcome setGameMode(NodeDefinition node, ResolvedEntityTarget target) {
        PlayerGameMode requested;
        try {
            requested = PlayerGameMode.valueOf(node.config().getOrDefault("gameMode", ""));
        } catch (IllegalArgumentException exception) {
            throw failure(
                    EntityActionErrorCode.PLAYER_GAME_MODE_REJECTED,
                    node,
                    "gameMode",
                    target.reference(),
                    "玩家游戏模式配置无效。"
            );
        }
        PlayerGameMode before = target.entity().gameMode().orElseThrow(() -> failure(
                EntityActionErrorCode.PLAYER_GAME_MODE_REJECTED,
                node,
                "gameMode",
                target.reference(),
                "目标不支持切换游戏模式。"
        ));
        if (before == requested) {
            return RuntimeActionOutcome.success(
                    node.blockId(),
                    "玩家 " + target.reference().displayName() + " 已经是" + gameMode(requested) + "，未发生变化。",
                    target.reference(),
                    false
            );
        }
        if (!target.entity().changeGameMode(requested)
                || target.entity().gameMode().orElse(null) != requested) {
            throw failure(
                    EntityActionErrorCode.PLAYER_GAME_MODE_REJECTED,
                    node,
                    "gameMode",
                    target.reference(),
                    "服务器拒绝切换玩家游戏模式。"
            );
        }
        return RuntimeActionOutcome.success(
                node.blockId(),
                "将玩家 " + target.reference().displayName() + " 从" + gameMode(before)
                        + "切换为" + gameMode(requested) + "。",
                target.reference(),
                true
        );
    }

    private static void requireAccepted(
            NodeDefinition node,
            RuntimeSubjectReference target,
            RuntimeEntityAccess.StatusEffectMutation mutation,
            String field
    ) {
        if (mutation == RuntimeEntityAccess.StatusEffectMutation.UNKNOWN) {
            throw failure(
                    EntityActionErrorCode.STATUS_EFFECT_UNKNOWN,
                    node,
                    field,
                    target,
                    "未知的状态效果 ID。"
            );
        }
        if (mutation == RuntimeEntityAccess.StatusEffectMutation.REJECTED) {
            throw failure(
                    EntityActionErrorCode.STATUS_EFFECT_REJECTED,
                    node,
                    field,
                    target,
                    "实体拒绝了状态效果操作。"
            );
        }
    }

    private static String effectId(
            NodeDefinition node,
            RuntimeSubjectReference target,
            RuntimeEntityProvider provider
    ) {
        String effectId = node.config().getOrDefault("effectId", "").trim();
        if (!NAMESPACED_ID.matcher(effectId).matches()
                || provider == null
                || !provider.statusEffectExists(effectId)) {
            throw failure(
                    EntityActionErrorCode.STATUS_EFFECT_UNKNOWN,
                    node,
                    "effectId",
                    target,
                    "状态效果 ID 无效或未知。"
            );
        }
        return effectId;
    }

    private static int integer(
            NodeDefinition node,
            String key,
            int min,
            int max,
            RuntimeSubjectReference target
    ) {
        try {
            int value = Integer.parseInt(node.config().getOrDefault(key, ""));
            if (value < min || value > max) {
                throw new NumberFormatException("out of range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw failure(
                    EntityActionErrorCode.ENTITY_ACTION_INVALID_NUMBER,
                    node,
                    key,
                    target,
                    "状态效果数值无效或超出允许范围。"
            );
        }
    }

    private static boolean bool(NodeDefinition node, String key, RuntimeSubjectReference target) {
        String raw = node.config().get(key);
        if ("true".equals(raw)) {
            return true;
        }
        if ("false".equals(raw)) {
            return false;
        }
        throw failure(
                EntityActionErrorCode.ENTITY_ACTION_EXECUTION_FAILED,
                node,
                key,
                target,
                "状态效果显示配置无效。"
        );
    }

    private static StatusEffectUpdatePolicy updatePolicy(NodeDefinition node, RuntimeSubjectReference target) {
        try {
            return StatusEffectUpdatePolicy.valueOf(node.config().getOrDefault("updatePolicy", ""));
        } catch (IllegalArgumentException exception) {
            throw failure(
                    EntityActionErrorCode.ENTITY_ACTION_EXECUTION_FAILED,
                    node,
                    "updatePolicy",
                    target,
                    "状态效果更新策略无效。"
            );
        }
    }

    private static ResolvedEntityTarget resolve(
            NodeDefinition node,
            RuntimeExecutionContext context,
            RuntimeServices services
    ) {
        EntityTargetRequirement requirement = BuiltInBlockCatalog.block(node.blockId())
                .map(block -> block.entityTargetRequirement())
                .orElseThrow(() -> new IllegalStateException("积木缺少实体目标类型约束。"));
        return EntityTargetResolver.resolve(
                node.id(),
                "target",
                node.config().get("target"),
                requirement,
                context,
                services.entityProvider()
        );
    }

    private static EntityActionException failure(
            EntityActionErrorCode code,
            NodeDefinition node,
            String field,
            RuntimeSubjectReference target,
            String message
    ) {
        String path = field.isBlank() ? node.id() : node.id() + "." + field;
        return new EntityActionException(new EntityActionError(code, node.id(), path, target, message));
    }

    private static String effect(EntityStatusEffect effect) {
        if (effect == null) {
            return "无";
        }
        return (effect.amplifier() + 1) + " 级、"
                + (effect.infinite() ? "无限时长" : effect.durationTicks() / 20 + " 秒")
                + "、环境效果" + (effect.ambient() ? "开" : "关")
                + "、粒子" + (effect.showParticles() ? "开" : "关")
                + "、图标" + (effect.showIcon() ? "开" : "关");
    }

    private static String gameMode(PlayerGameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> "生存模式";
            case CREATIVE -> "创造模式";
            case ADVENTURE -> "冒险模式";
            case SPECTATOR -> "旁观模式";
        };
    }
}
