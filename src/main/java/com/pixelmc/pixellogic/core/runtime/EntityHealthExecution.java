package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.model.EntityDamageKind;
import com.pixelmc.pixellogic.core.model.EntityTargetRequirement;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;

import java.math.BigDecimal;
import java.util.Optional;

final class EntityHealthExecution {
    private static final double MAX_CONFIGURED_HEALTH = 1_000_000;

    private EntityHealthExecution() {
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
                case ENTITY_DAMAGE_ACTION -> damage(node, target);
                case ENTITY_HEAL_ACTION -> heal(node, target);
                case ENTITY_SET_HEALTH_ACTION -> setHealth(node, target);
                case ENTITY_KILL_ACTION -> kill(node, target);
                case ENTITY_REMOVE_ACTION -> remove(node, target);
                default -> throw new IllegalStateException("unsupported entity health action: " + node.type());
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
                    "实体动作执行失败。"
            );
        }
    }

    static boolean supports(NodeType type) {
        return type == NodeType.ENTITY_DAMAGE_ACTION
                || type == NodeType.ENTITY_HEAL_ACTION
                || type == NodeType.ENTITY_SET_HEALTH_ACTION
                || type == NodeType.ENTITY_KILL_ACTION
                || type == NodeType.ENTITY_REMOVE_ACTION;
    }

    private static RuntimeActionOutcome damage(NodeDefinition node, ResolvedEntityTarget target) {
        double requestedAmount = number(node, "amount", true, target.reference());
        double amount = minecraftNumber(requestedAmount);
        EntityDamageKind kind;
        try {
            kind = EntityDamageKind.valueOf(node.config().getOrDefault("damageKind", ""));
        } catch (IllegalArgumentException exception) {
            throw failure(
                    EntityActionErrorCode.ENTITY_DAMAGE_KIND_UNSUPPORTED,
                    node,
                    "damageKind",
                    target.reference(),
                    "不支持的伤害类型。"
            );
        }
        double before = target.entity().health();
        if (!target.entity().damage(kind, amount)) {
            throw failure(
                    EntityActionErrorCode.ENTITY_DAMAGE_REJECTED,
                    node,
                    "amount",
                    target.reference(),
                    "实体拒绝了本次伤害。"
            );
        }
        double after = target.entity().health();
        String message = target.entity().damageIsApproximate()
                ? "模拟未减伤近似：请求对实体 " + target.reference().displayName() + " 造成 " + display(requestedAmount)
                + " 点" + kind.displayName() + "伤害，应用 " + observed(Math.max(0, before - after))
                + " 点（生命值 " + observed(before) + " → " + observed(after) + "）。"
                : "请求对实体 " + target.reference().displayName() + " 造成 " + display(requestedAmount)
                + " 点" + kind.displayName() + "伤害，服务器已接受（可观测生命值 "
                + observed(before) + " → " + observed(after) + "）。";
        return RuntimeActionOutcome.success(node.blockId(), message, target.reference(), true);
    }

    private static RuntimeActionOutcome heal(NodeDefinition node, ResolvedEntityTarget target) {
        double requestedAmount = number(node, "amount", true, target.reference());
        double amount = minecraftNumber(requestedAmount);
        double before = target.entity().health();
        boolean changed = target.entity().heal(amount);
        double after = target.entity().health();
        String message = "请求恢复实体 " + target.reference().displayName() + " " + display(requestedAmount)
                + " 点生命值，实际恢复 " + observed(Math.max(0, after - before))
                + " 点（生命值 " + observed(before) + " → " + observed(after) + "）"
                + (changed ? "。" : "，未发生变化。");
        return RuntimeActionOutcome.success(node.blockId(), message, target.reference(), changed);
    }

    private static RuntimeActionOutcome setHealth(NodeDefinition node, ResolvedEntityTarget target) {
        double requestedHealth = number(node, "health", false, target.reference());
        double maximum = target.entity().maxHealth();
        if (requestedHealth > maximum) {
            throw failure(
                    EntityActionErrorCode.ENTITY_HEALTH_ABOVE_MAXIMUM,
                    node,
                    "health",
                    target.reference(),
                    "生命值 " + display(requestedHealth) + " 超过目标最大生命值 " + observed(maximum) + "。"
            );
        }
        double health = minecraftNumber(requestedHealth);
        double before = target.entity().health();
        boolean changed = target.entity().setHealth(health);
        double after = target.entity().health();
        String message = "请求将实体 " + target.reference().displayName() + " 的生命值设为 "
                + display(requestedHealth) + "，按服务器精度应用 " + observed(after)
                + "（生命值 " + observed(before) + " → " + observed(after) + "）"
                + (changed ? "。" : "，未发生变化。");
        return RuntimeActionOutcome.success(node.blockId(), message, target.reference(), changed);
    }

    private static RuntimeActionOutcome kill(NodeDefinition node, ResolvedEntityTarget target) {
        if (!target.entity().kill()) {
            throw failure(
                    EntityActionErrorCode.ENTITY_KILL_REJECTED,
                    node,
                    "target",
                    target.reference(),
                    "实体未能进入正常死亡流程。"
            );
        }
        String message = "杀死实体 " + target.reference().displayName() + "，已进入正常死亡流程。";
        return RuntimeActionOutcome.success(node.blockId(), message, target.reference(), true);
    }

    private static RuntimeActionOutcome remove(NodeDefinition node, ResolvedEntityTarget target) {
        if (target.reference().kind() == RuntimeSubjectReference.Kind.PLAYER) {
            throw failure(
                    EntityActionErrorCode.ENTITY_REMOVE_PLAYER_FORBIDDEN,
                    node,
                    "target",
                    target.reference(),
                    "玩家不能被直接移除，请使用杀死实体动作。"
            );
        }
        if (!target.entity().remove()) {
            throw failure(
                    EntityActionErrorCode.ENTITY_ACTION_EXECUTION_FAILED,
                    node,
                    "target",
                    target.reference(),
                    "实体未能从世界中移除。"
            );
        }
        String message = "直接移除实体 " + target.reference().displayName() + "，未触发正常死亡流程。";
        return RuntimeActionOutcome.success(node.blockId(), message, target.reference(), true);
    }

    private static double number(
            NodeDefinition node,
            String key,
            boolean positive,
            RuntimeSubjectReference target
    ) {
        double value;
        try {
            value = Double.parseDouble(node.config().getOrDefault(key, ""));
        } catch (NumberFormatException exception) {
            throw invalidNumber(node, key, target);
        }
        if (!Double.isFinite(value)
                || value > MAX_CONFIGURED_HEALTH
                || (positive ? value < 0.001 : value < 0)) {
            throw invalidNumber(node, key, target);
        }
        return value;
    }

    private static EntityActionException invalidNumber(
            NodeDefinition node,
            String key,
            RuntimeSubjectReference target
    ) {
        return failure(
                EntityActionErrorCode.ENTITY_ACTION_INVALID_NUMBER,
                node,
                key,
                target,
                "生命数值无效或超出允许范围。"
        );
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

    private static String display(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static double minecraftNumber(double value) {
        return Double.parseDouble(Float.toString((float) value));
    }

    private static String observed(double value) {
        return display(minecraftNumber(value));
    }
}
