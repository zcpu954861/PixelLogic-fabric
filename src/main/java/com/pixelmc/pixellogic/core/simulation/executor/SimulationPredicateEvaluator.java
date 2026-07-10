package com.pixelmc.pixellogic.core.simulation.executor;

import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.runtime.RuntimeNodeExecutionResult;
import com.pixelmc.pixellogic.core.runtime.RuntimePredicateResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeExecutionContext;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;

public interface SimulationPredicateEvaluator extends SimulationBlockExecutor {
    default RuntimePredicateResult evaluatePredicate(NodeDefinition node, SimulationContext context, RuntimeServices services) {
        throw new IllegalStateException("条件执行器需要运行时上下文：" + node.type());
    }

    default RuntimePredicateResult evaluatePredicate(
            NodeDefinition node,
            SimulationContext context,
            RuntimeExecutionContext runtimeContext,
            RuntimeServices services
    ) {
        return evaluatePredicate(node, context, services);
    }

    @Override
    default RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
        RuntimePredicateResult predicate = evaluatePredicate(node, context, services);
        ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
        return new RuntimeNodeExecutionResult(
                mode.outputSlot(predicate.value()),
                predicate.traceMessage() + outputModeTrace(mode, predicate.value()),
                predicate.conditionResult()
        );
    }

    @Override
    default RuntimeNodeExecutionResult execute(
            NodeDefinition node,
            SimulationContext context,
            RuntimeExecutionContext runtimeContext,
            RuntimeServices services
    ) {
        RuntimePredicateResult predicate = evaluatePredicate(node, context, runtimeContext, services);
        ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
        return new RuntimeNodeExecutionResult(
                mode.outputSlot(predicate.value()),
                predicate.traceMessage() + outputModeTrace(mode, predicate.value()),
                predicate.conditionResult()
        );
    }

    default String outputModeTrace(ConditionOutputMode mode, boolean value) {
        return mode.traceMessage(value);
    }

    static String contextualPathTrace(ConditionOutputMode mode, boolean passed) {
        return switch (mode) {
            case PASS_ONLY -> passed ? "进入“满足”路径。" : "流程在此结束。";
            case FAIL_ONLY -> passed ? "流程在此结束。" : "进入“不满足”路径。";
            case BRANCH -> passed ? "进入“满足”路径。" : "进入“不满足”路径。";
        };
    }
}
