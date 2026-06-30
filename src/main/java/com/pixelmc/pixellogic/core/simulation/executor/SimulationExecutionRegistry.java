package com.pixelmc.pixellogic.core.simulation.executor;

import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;
import com.pixelmc.pixellogic.core.simulation.result.SimulationActionResult;
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
                new PlayerHasTagExecutor(),
                new PlayerAddTagExecutor()
        ));
    }

    public Optional<RuntimeServices.NodeExecution> execute(NodeDefinition node, SimulationContext context) {
        return Optional.ofNullable(executors.get(node.type()))
                .map(executor -> executor.execute(node, context));
    }

    private static final class PlayerHasTagExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_HAS_TAG_CONDITION;
        }

        @Override
        public RuntimeServices.NodeExecution execute(NodeDefinition node, SimulationContext context) {
            String tag = tag(node);
            boolean passed = context.actor().hasTag(tag);
            return new RuntimeServices.NodeExecution(
                    passed ? "pass" : "fail",
                    "玩家标签条件" + (passed ? "通过" : "失败") + "：" + context.actor().displayName() + " 拥有标签 " + tag
            );
        }
    }

    private static final class PlayerAddTagExecutor implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.PLAYER_ADD_TAG_ACTION;
        }

        @Override
        public RuntimeServices.NodeExecution execute(NodeDefinition node, SimulationContext context) {
            String tag = tag(node);
            context.actor().addTag(tag);
            String message = "玩家标签写入：给 " + context.actor().displayName() + " 添加标签 " + tag;
            context.addActionResult(new SimulationActionResult(node.id(), "player_tag", message));
            context.addStateChange(new SimulationStateChangeResult(node.id(), "actor.tags", String.join(",", context.actor().tags())));
            return new RuntimeServices.NodeExecution("done", message);
        }
    }

    private static String tag(NodeDefinition node) {
        String tag = node.config().getOrDefault("tag", "");
        if (tag.isBlank()) {
            throw new IllegalStateException("玩家标签不能为空。");
        }
        return tag;
    }
}
