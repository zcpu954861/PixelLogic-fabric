package com.pixelmc.pixellogic.core.simulation.executor;

import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.runtime.RuntimeNodeExecutionResult;
import com.pixelmc.pixellogic.core.runtime.RuntimePredicateResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.runtime.RuntimeExecutionContext;
import com.pixelmc.pixellogic.core.runtime.RuntimeConditionResult;
import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.catalog.RichTextComponentValue;
import com.pixelmc.pixellogic.core.simulation.context.SimulationBlockFact;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;
import com.pixelmc.pixellogic.core.simulation.context.SimulationRegionFact;
import com.pixelmc.pixellogic.core.simulation.result.SimulationActionResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationMessageResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationStateChangeResult;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SimulationExecutionRegistry {
    private final Map<NodeType, SimulationBlockExecutor> executors;

    public SimulationExecutionRegistry(List<SimulationBlockExecutor> executors) {
        EnumMap<NodeType, SimulationBlockExecutor> mapped = new EnumMap<>(NodeType.class);
        for (SimulationBlockExecutor executor : executors) {
            mapped.put(executor.nodeType(), executor);
        }
        this.executors = Map.copyOf(mapped);
    }

    public static SimulationExecutionRegistry playerTags() {
        return new SimulationExecutionRegistry(List.of(
                new MessageExecutor(),
                new PlayerHasTagExecutor(),
                new PlayerIsAdminExecutor(),
                new PlayerDimensionExecutor(),
                new PlayerInRegionExecutor(),
                new PlayerYCompareExecutor(),
                new TargetBlockTypeExecutor(),
                new TargetBlockInRegionExecutor(),
                new TargetBlockYCompareExecutor(),
                new PlayerNearTargetBlockExecutor(),
                new PlayerAddTagExecutor(),
                new PlayerRemoveTagExecutor(),
                ContextEntityTagExecutors.hasTag(),
                ContextEntityTagExecutors.addTag(),
                ContextEntityTagExecutors.removeTag()
        ));
    }

    public Optional<RuntimeNodeExecutionResult> execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
        return Optional.ofNullable(executors.get(node.type()))
                .map(executor -> executor.execute(node, context, services));
    }

    public Optional<RuntimeNodeExecutionResult> execute(
            NodeDefinition node,
            SimulationContext context,
            RuntimeExecutionContext runtimeContext,
            RuntimeServices services
    ) {
        return Optional.ofNullable(executors.get(node.type()))
                .map(executor -> executor.execute(node, context, runtimeContext, services));
    }

    public Optional<RuntimePredicateResult> evaluatePredicate(
            NodeDefinition node,
            SimulationContext context,
            RuntimeServices services
    ) {
        SimulationBlockExecutor executor = executors.get(node.type());
        if (executor instanceof SimulationPredicateEvaluator predicate) {
            return Optional.of(predicate.evaluatePredicate(node, context, services));
        }
        return Optional.empty();
    }

    public Optional<RuntimePredicateResult> evaluatePredicate(
            NodeDefinition node,
            SimulationContext context,
            RuntimeExecutionContext runtimeContext,
            RuntimeServices services
    ) {
        SimulationBlockExecutor executor = executors.get(node.type());
        if (executor instanceof SimulationPredicateEvaluator predicate) {
            return Optional.of(predicate.evaluatePredicate(node, context, runtimeContext, services));
        }
        return Optional.empty();
    }

    private static final class MessageExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.MESSAGE_ACTION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String component = RichTextComponentValue.normalize(node.config().getOrDefault("message", ""));
            String message = RichTextComponentValue.plainText(component);
            String channel = messageResultKind(node.blockId());
            if ("CHAT".equals(channel)) {
                services.sendPlayerMessage(context.actor().id(), message);
            }
            String trace = switch (channel) {
                case "TITLE" -> "向「" + context.actor().displayName() + "」显示标题：「" + message + "」";
                case "SUBTITLE" -> "向「" + context.actor().displayName() + "」显示副标题：「" + message + "」";
                case "ACTIONBAR" -> "向「" + context.actor().displayName() + "」显示快捷栏消息：「" + message + "」";
                default -> "向「" + context.actor().displayName() + "」发送聊天消息：「" + message + "」";
            };
            context.addMessageResult(new SimulationMessageResult(node.id(), context.actor().id(), message, channel, component));
            context.addActionResult(new SimulationActionResult(node.id(), "message", trace));
            return new RuntimeNodeExecutionResult("done", trace);
        }

        private String messageResultKind(String blockId) {
            return switch (blockId) {
                case BuiltInBlockCatalog.ACTION_MESSAGE_TITLE -> "TITLE";
                case BuiltInBlockCatalog.ACTION_MESSAGE_SUBTITLE -> "SUBTITLE";
                case BuiltInBlockCatalog.ACTION_MESSAGE_ACTIONBAR -> "ACTIONBAR";
                default -> "CHAT";
            };
        }
    }

    private static final class PlayerHasTagExecutor implements SimulationPredicateEvaluator {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_HAS_TAG_CONDITION;
        }

        @Override
        public RuntimePredicateResult evaluatePredicate(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String tag = tag(node);
            boolean passed = context.actor().hasTag(tag);
            String fact = "玩家 " + context.actor().displayName() + (passed ? " 拥有" : " 没有")
                    + "标签「" + tag + "」";
            return new RuntimePredicateResult(
                    passed,
                    fact + "。",
                    new RuntimeConditionResult(node.id(), node.blockId(), context.actor().reference(), passed, fact)
            );
        }

        @Override
        public String outputModeTrace(ConditionOutputMode mode, boolean value) {
            return SimulationPredicateEvaluator.contextualPathTrace(mode, value);
        }
    }

    private static final class PlayerIsAdminExecutor implements SimulationPredicateEvaluator {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_IS_ADMIN_CONDITION;
        }

        @Override
        public RuntimePredicateResult evaluatePredicate(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            boolean passed = context.actor().operator();
            String fact = "玩家 " + context.actor().displayName() + (passed ? " 是管理员" : " 不是管理员");
            return new RuntimePredicateResult(
                    passed,
                    fact + "。",
                    new RuntimeConditionResult(node.id(), node.blockId(), context.actor().reference(), passed, fact)
            );
        }

        @Override
        public String outputModeTrace(ConditionOutputMode mode, boolean value) {
            return SimulationPredicateEvaluator.contextualPathTrace(mode, value);
        }
    }

    private static final class PlayerDimensionExecutor implements SimulationPredicateEvaluator {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_DIMENSION_CONDITION;
        }

        @Override
        public RuntimePredicateResult evaluatePredicate(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String expected = config(node, "dimensionId", "minecraft:overworld");
            String actual = context.actorPosition().dimensionId();
            boolean passed = actual.equals(expected);
            return new RuntimePredicateResult(
                    passed,
                    "玩家维度条件" + (passed ? "通过" : "失败") + "：" + context.actor().displayName()
                            + " 位于「" + actual + "」，目标维度「" + expected + "」。"
            );
        }

        @Override
        public String outputModeTrace(ConditionOutputMode mode, boolean value) {
            return conditionModeTrace(mode, value, "在该维度", "不在该维度");
        }
    }

    private static final class PlayerInRegionExecutor implements SimulationPredicateEvaluator {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_IN_REGION_CONDITION;
        }

        @Override
        public RuntimePredicateResult evaluatePredicate(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String regionName = config(node, "regionName", "");
            Optional<SimulationRegionFact> region = context.world().findRegion(regionName);
            boolean passed = region.isPresent() && context.world().isPositionInsideRegion(context.actorPosition(), region.get());
            String detail = region.isEmpty()
                    ? "未找到测试区域「" + regionName + "」。"
                    : context.actor().displayName() + (passed ? " 在" : " 不在") + "测试区域「" + regionName + "」内。";
            return new RuntimePredicateResult(
                    passed,
                    "玩家区域条件" + (passed ? "通过" : "失败") + "：" + detail
            );
        }

        @Override
        public String outputModeTrace(ConditionOutputMode mode, boolean value) {
            return conditionModeTrace(mode, value, "在区域内", "不在区域内");
        }
    }

    private static final class PlayerYCompareExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_Y_COMPARE_CONDITION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            int y = context.actorPosition().y();
            boolean passed = yCompare(y, node);
            ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
            return new RuntimeNodeExecutionResult(
                    mode.outputSlot(passed),
                    "玩家高度条件" + (passed ? "通过" : "失败") + "：" + context.actor().displayName()
                            + " 当前 Y=" + y + "，" + yCompareDescription(node) + "。"
                            + conditionModeTrace(mode, passed, "满足高度", "不满足高度")
            );
        }
    }

    private static final class TargetBlockTypeExecutor implements SimulationPredicateEvaluator {
        @Override
        public NodeType nodeType() {
            return NodeType.TARGET_BLOCK_TYPE_CONDITION;
        }

        @Override
        public RuntimePredicateResult evaluatePredicate(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String expected = config(node, "blockId", "minecraft:stone");
            SimulationBlockFact target = context.world().targetBlock();
            boolean passed = target.enabled() && target.blockId().equals(expected);
            String detail = target.enabled()
                    ? "目标方块为「" + target.blockId() + "」，期望「" + expected + "」。"
                    : "未设置目标方块。";
            return new RuntimePredicateResult(
                    passed,
                    "目标方块类型条件" + (passed ? "通过" : "失败") + "：" + detail
            );
        }

        @Override
        public String outputModeTrace(ConditionOutputMode mode, boolean value) {
            return conditionModeTrace(mode, value, "为该方块", "不为该方块");
        }
    }

    private static final class TargetBlockInRegionExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.TARGET_BLOCK_IN_REGION_CONDITION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String regionName = config(node, "regionName", "");
            SimulationBlockFact target = context.world().targetBlock();
            Optional<SimulationRegionFact> region = context.world().findRegion(regionName);
            boolean passed = target.enabled() && region.isPresent()
                    && context.world().isPositionInsideRegion(target.position(), region.get());
            ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
            String detail;
            if (!target.enabled()) {
                detail = "未设置目标方块。";
            } else if (region.isEmpty()) {
                detail = "未找到测试区域「" + regionName + "」。";
            } else {
                detail = "目标方块" + (passed ? "在" : "不在") + "测试区域「" + regionName + "」内。";
            }
            return new RuntimeNodeExecutionResult(
                    mode.outputSlot(passed),
                    "目标方块区域条件" + (passed ? "通过" : "失败") + "：" + detail
                            + conditionModeTrace(mode, passed, "在区域内", "不在区域内")
            );
        }
    }

    private static final class TargetBlockYCompareExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.TARGET_BLOCK_Y_COMPARE_CONDITION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            SimulationBlockFact target = context.world().targetBlock();
            boolean passed = target.enabled() && yCompare(target.y(), node);
            ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
            String detail = target.enabled()
                    ? "目标方块当前 Y=" + target.y() + "，" + yCompareDescription(node) + "。"
                    : "未设置目标方块。";
            return new RuntimeNodeExecutionResult(
                    mode.outputSlot(passed),
                    "目标方块高度条件" + (passed ? "通过" : "失败") + "：" + detail
                            + conditionModeTrace(mode, passed, "满足高度", "不满足高度")
            );
        }
    }

    private static final class PlayerNearTargetBlockExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_NEAR_TARGET_BLOCK_CONDITION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            SimulationBlockFact target = context.world().targetBlock();
            ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
            boolean horizontalOnly = Boolean.parseBoolean(config(node, "horizontalOnly", "true"));
            double maxDistance = doubleConfig(node, "maxDistance", 5.0D);
            boolean sameDimension = target.enabled() && context.actorPosition().dimensionId().equals(target.dimensionId());
            double distanceSquared = sameDimension ? distanceSquared(context, target, horizontalOnly) : Double.POSITIVE_INFINITY;
            boolean passed = sameDimension && distanceSquared <= maxDistance * maxDistance;
            String detail;
            if (!target.enabled()) {
                detail = "未设置目标方块。";
            } else if (!sameDimension) {
                detail = "玩家与目标方块不在同一维度。";
            } else {
                detail = "距离不超过 " + formatDistance(maxDistance) + " 格，"
                        + (horizontalOnly ? "只计算水平距离。" : "计算三维距离。");
            }
            return new RuntimeNodeExecutionResult(
                    mode.outputSlot(passed),
                    "玩家靠近目标方块条件" + (passed ? "通过" : "失败") + "：" + detail
                            + conditionModeTrace(mode, passed, "靠近", "不靠近")
            );
        }
    }

    private static final class PlayerAddTagExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_ADD_TAG_ACTION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String tag = tag(node);
            context.actor().addTag(tag);
            String message = "玩家标签写入：给 " + context.actor().displayName() + " 添加标签 " + tag;
            context.addActionResult(new SimulationActionResult(node.id(), "player_tag", message));
            context.addStateChange(new SimulationStateChangeResult(node.id(), "actor.tags", String.join(",", context.actor().tags())));
            return new RuntimeNodeExecutionResult("done", message);
        }
    }

    private static final class PlayerRemoveTagExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_REMOVE_TAG_ACTION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String tag = tag(node);
            boolean removed = context.actor().removeTag(tag);
            String message = removed
                    ? "玩家标签移除：移除玩家「" + context.actor().displayName() + "」的标签「" + tag + "」。"
                    : "玩家标签移除：玩家「" + context.actor().displayName() + "」没有标签「" + tag + "」，未发生变化。";
            context.addActionResult(new SimulationActionResult(node.id(), "player_tag", message));
            context.addStateChange(new SimulationStateChangeResult(node.id(), "actor.tags", String.join(",", context.actor().tags())));
            return new RuntimeNodeExecutionResult("done", message);
        }
    }

    static String tag(NodeDefinition node) {
        String tag = node.config().getOrDefault("tag", "");
        if (tag.isBlank()) {
            throw new IllegalStateException("标签不能为空。");
        }
        return tag;
    }

    private static String config(NodeDefinition node, String key, String fallback) {
        String value = node.config().getOrDefault(key, "").trim();
        return value.isBlank() ? fallback : value;
    }

    private static double doubleConfig(NodeDefinition node, String key, double fallback) {
        try {
            return Double.parseDouble(config(node, key, Double.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int intConfig(NodeDefinition node, String key, int fallback) {
        try {
            return Integer.parseInt(config(node, key, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean yCompare(int y, NodeDefinition node) {
        return switch (config(node, "compareMode", "AT_OR_ABOVE")) {
            case "AT_OR_BELOW" -> y <= intConfig(node, "targetY", 64);
            case "EQUAL" -> y == intConfig(node, "targetY", 64);
            case "BETWEEN" -> y >= intConfig(node, "minY", 60) && y <= intConfig(node, "maxY", 80);
            default -> y >= intConfig(node, "targetY", 64);
        };
    }

    private static String yCompareDescription(NodeDefinition node) {
        return switch (config(node, "compareMode", "AT_OR_ABOVE")) {
            case "AT_OR_BELOW" -> "要求不高于 " + intConfig(node, "targetY", 64);
            case "EQUAL" -> "要求等于 " + intConfig(node, "targetY", 64);
            case "BETWEEN" -> "要求在 " + intConfig(node, "minY", 60) + " 到 " + intConfig(node, "maxY", 80) + " 之间";
            default -> "要求不低于 " + intConfig(node, "targetY", 64);
        };
    }

    private static double distanceSquared(SimulationContext context, SimulationBlockFact target, boolean horizontalOnly) {
        long dx = (long) context.actorPosition().x() - target.x();
        long dz = (long) context.actorPosition().z() - target.z();
        long dy = horizontalOnly ? 0 : (long) context.actorPosition().y() - target.y();
        return (double) dx * dx + (double) dy * dy + (double) dz * dz;
    }

    private static String formatDistance(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }

    private static String conditionModeTrace(ConditionOutputMode mode, boolean passed, String positive, String negative) {
        return switch (mode) {
            case PASS_ONLY -> passed ? positive + "时继续。" : negative + "，流程在此结束。";
            case FAIL_ONLY -> passed ? positive + "，流程在此结束。" : negative + "时继续。";
            case BRANCH -> passed ? "走" + positive + "分支。" : "走" + negative + "分支。";
        };
    }

}
