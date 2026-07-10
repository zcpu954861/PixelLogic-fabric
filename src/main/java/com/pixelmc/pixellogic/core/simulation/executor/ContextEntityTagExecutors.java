package com.pixelmc.pixellogic.core.simulation.executor;

import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.runtime.RuntimeConditionResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeExecutionContext;
import com.pixelmc.pixellogic.core.runtime.RuntimeNodeExecutionResult;
import com.pixelmc.pixellogic.core.runtime.RuntimePredicateResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.runtime.RuntimeSubjectReference;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;
import com.pixelmc.pixellogic.core.simulation.context.SimulationEntity;
import com.pixelmc.pixellogic.core.simulation.result.SimulationActionResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationStateChangeResult;

final class ContextEntityTagExecutors {
    private ContextEntityTagExecutors() {
    }

    static SimulationBlockExecutor hasTag() {
        return new HasTag();
    }

    static SimulationBlockExecutor addTag() {
        return new AddTag();
    }

    static SimulationBlockExecutor removeTag() {
        return new RemoveTag();
    }

    private static final class HasTag implements SimulationPredicateEvaluator {
        @Override
        public NodeType nodeType() {
            return NodeType.CONTEXT_ENTITY_HAS_TAG_CONDITION;
        }

        @Override
        public RuntimePredicateResult evaluatePredicate(
                NodeDefinition node,
                SimulationContext context,
                RuntimeExecutionContext runtimeContext,
                RuntimeServices services
        ) {
            SimulationEntity entity = currentEntity(context, runtimeContext);
            String tag = SimulationExecutionRegistry.tag(node);
            boolean passed = entity.hasTag(tag);
            String fact = "上下文实体 " + entity.displayName() + (passed ? " 拥有" : " 没有")
                    + "标签「" + tag + "」";
            return new RuntimePredicateResult(
                    passed,
                    fact + "。",
                    new RuntimeConditionResult(node.id(), node.blockId(), entity.reference(), passed, fact)
            );
        }

        @Override
        public String outputModeTrace(ConditionOutputMode mode, boolean value) {
            return SimulationPredicateEvaluator.contextualPathTrace(mode, value);
        }
    }

    private static final class AddTag implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.CONTEXT_ENTITY_ADD_TAG_ACTION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(
                NodeDefinition node,
                SimulationContext context,
                RuntimeExecutionContext runtimeContext,
                RuntimeServices services
        ) {
            SimulationEntity entity = currentEntity(context, runtimeContext);
            String tag = SimulationExecutionRegistry.tag(node);
            entity.addTag(tag);
            String message = "为上下文实体 " + entity.displayName() + " 添加标签「" + tag + "」。";
            recordTagChange(context, node, entity, message);
            return new RuntimeNodeExecutionResult("done", message);
        }
    }

    private static final class RemoveTag implements SimulationBlockExecutor {
        @Override
        public NodeType nodeType() {
            return NodeType.CONTEXT_ENTITY_REMOVE_TAG_ACTION;
        }

        @Override
        public RuntimeNodeExecutionResult execute(
                NodeDefinition node,
                SimulationContext context,
                RuntimeExecutionContext runtimeContext,
                RuntimeServices services
        ) {
            SimulationEntity entity = currentEntity(context, runtimeContext);
            String tag = SimulationExecutionRegistry.tag(node);
            boolean removed = entity.removeTag(tag);
            String message = removed
                    ? "移除上下文实体 " + entity.displayName() + " 的标签「" + tag + "」。"
                    : "上下文实体 " + entity.displayName() + " 没有标签「" + tag + "」，未发生变化。";
            recordTagChange(context, node, entity, message);
            return new RuntimeNodeExecutionResult("done", message);
        }
    }

    private static SimulationEntity currentEntity(
            SimulationContext context,
            RuntimeExecutionContext runtimeContext
    ) {
        RuntimeSubjectReference reference = runtimeContext.currentEntity();
        if (reference == null || !reference.isEntity()) {
            throw new IllegalStateException("当前实体上下文不可用。");
        }
        return context.entity(reference.id())
                .orElseThrow(() -> new IllegalStateException("当前实体无法解析。"));
    }

    private static void recordTagChange(
            SimulationContext context,
            NodeDefinition node,
            SimulationEntity entity,
            String message
    ) {
        context.addActionResult(new SimulationActionResult(node.id(), "context_entity_tag", message));
        context.addStateChange(new SimulationStateChangeResult(
                node.id(),
                "entity." + entity.id() + ".tags",
                String.join(",", entity.tags())
        ));
    }
}
