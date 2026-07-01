package com.pixelmc.pixellogic.core.simulation.executor;

import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.runtime.RuntimeNodeExecutionResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
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
                new TargetBlockTypeExecutor(),
                new TargetBlockInRegionExecutor(),
                new PlayerAddTagExecutor(),
                new PlayerRemoveTagExecutor()
        ));
    }

    public Optional<RuntimeNodeExecutionResult> execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
        return Optional.ofNullable(executors.get(node.type()))
                .map(executor -> executor.execute(node, context, services));
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

    private static final class PlayerHasTagExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_HAS_TAG_CONDITION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String tag = tag(node);
            boolean passed = context.actor().hasTag(tag);
            ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
            return new RuntimeNodeExecutionResult(
                    mode.outputSlot(passed),
                    "玩家标签条件" + (passed ? "通过" : "失败") + "：" + context.actor().displayName() + " "
                            + (passed ? "拥有" : "不拥有") + "标签 " + tag + "。"
                            + playerTagModeTrace(mode, passed)
            );
        }
    }

    private static final class PlayerIsAdminExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_IS_ADMIN_CONDITION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            boolean passed = context.actor().operator();
            ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
            return new RuntimeNodeExecutionResult(
                    mode.outputSlot(passed),
                    "管理员条件" + (passed ? "通过" : "失败") + "：" + context.actor().displayName()
                            + (passed ? " 是管理员。" : " 不是管理员。")
                            + adminModeTrace(mode, passed)
            );
        }
    }

    private static final class PlayerDimensionExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_DIMENSION_CONDITION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String expected = config(node, "dimensionId", "minecraft:overworld");
            String actual = context.actorPosition().dimensionId();
            boolean passed = actual.equals(expected);
            ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
            return new RuntimeNodeExecutionResult(
                    mode.outputSlot(passed),
                    "玩家维度条件" + (passed ? "通过" : "失败") + "：" + context.actor().displayName()
                            + " 位于「" + actual + "」，目标维度「" + expected + "」。"
                            + conditionModeTrace(mode, passed, "在该维度", "不在该维度")
            );
        }
    }

    private static final class PlayerInRegionExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_IN_REGION_CONDITION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String regionName = config(node, "regionName", "");
            Optional<SimulationRegionFact> region = context.world().findRegion(regionName);
            boolean passed = region.isPresent() && context.world().isPositionInsideRegion(context.actorPosition(), region.get());
            ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
            String detail = region.isEmpty()
                    ? "未找到测试区域「" + regionName + "」。"
                    : context.actor().displayName() + (passed ? " 在" : " 不在") + "测试区域「" + regionName + "」内。";
            return new RuntimeNodeExecutionResult(
                    mode.outputSlot(passed),
                    "玩家区域条件" + (passed ? "通过" : "失败") + "：" + detail
                            + conditionModeTrace(mode, passed, "在区域内", "不在区域内")
            );
        }
    }

    private static final class TargetBlockTypeExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.TARGET_BLOCK_TYPE_CONDITION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
            String expected = config(node, "blockId", "minecraft:stone");
            SimulationBlockFact target = context.world().targetBlock();
            boolean passed = target.enabled() && target.blockId().equals(expected);
            ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
            String detail = target.enabled()
                    ? "目标方块为「" + target.blockId() + "」，期望「" + expected + "」。"
                    : "未设置目标方块。";
            return new RuntimeNodeExecutionResult(
                    mode.outputSlot(passed),
                    "目标方块类型条件" + (passed ? "通过" : "失败") + "：" + detail
                            + conditionModeTrace(mode, passed, "为该方块", "不为该方块")
            );
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

    private static String tag(NodeDefinition node) {
        String tag = node.config().getOrDefault("tag", "");
        if (tag.isBlank()) {
            throw new IllegalStateException("玩家标签不能为空。");
        }
        return tag;
    }

    private static String config(NodeDefinition node, String key, String fallback) {
        String value = node.config().getOrDefault(key, "").trim();
        return value.isBlank() ? fallback : value;
    }

    private static String conditionModeTrace(ConditionOutputMode mode, boolean passed, String positive, String negative) {
        return switch (mode) {
            case PASS_ONLY -> passed ? positive + "时继续。" : negative + "，流程在此结束。";
            case FAIL_ONLY -> passed ? positive + "，流程在此结束。" : negative + "时继续。";
            case BRANCH -> passed ? "走" + positive + "分支。" : "走" + negative + "分支。";
        };
    }

    private static String playerTagModeTrace(ConditionOutputMode mode, boolean passed) {
        return switch (mode) {
            case PASS_ONLY -> passed ? "拥有标签时继续。" : "不拥有标签，流程在此结束。";
            case FAIL_ONLY -> passed ? "拥有标签，流程在此结束。" : "不拥有标签时继续。";
            case BRANCH -> passed ? "走拥有标签分支。" : "走不拥有标签分支。";
        };
    }

    private static String adminModeTrace(ConditionOutputMode mode, boolean passed) {
        return switch (mode) {
            case PASS_ONLY -> passed ? "是管理员时继续。" : "不是管理员，流程在此结束。";
            case FAIL_ONLY -> passed ? "是管理员，流程在此结束。" : "不是管理员时继续。";
            case BRANCH -> passed ? "走是管理员分支。" : "走不是管理员分支。";
        };
    }
}
