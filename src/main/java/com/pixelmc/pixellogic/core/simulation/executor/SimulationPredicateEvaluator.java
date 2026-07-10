package com.pixelmc.pixellogic.core.simulation.executor;

import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.runtime.RuntimeNodeExecutionResult;
import com.pixelmc.pixellogic.core.runtime.RuntimePredicateResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;

public interface SimulationPredicateEvaluator extends SimulationBlockExecutor {
    RuntimePredicateResult evaluatePredicate(NodeDefinition node, SimulationContext context, RuntimeServices services);

    @Override
    default RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
        RuntimePredicateResult predicate = evaluatePredicate(node, context, services);
        ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
        return new RuntimeNodeExecutionResult(
                mode.outputSlot(predicate.value()),
                predicate.traceMessage() + outputModeTrace(mode, predicate.value())
        );
    }

    default String outputModeTrace(ConditionOutputMode mode, boolean value) {
        return mode.traceMessage(value);
    }
}
