package com.pixelmc.pixellogic.core.simulation.runner;

import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;
import com.pixelmc.pixellogic.core.simulation.result.SimulationActionResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationMessageResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationStateChangeResult;

import java.util.List;
import java.util.Set;

public record SimulationExecutionResult(
        boolean success,
        String traceId,
        String message,
        List<SimulationActionResult> actionResults,
        List<SimulationMessageResult> messageResults,
        List<SimulationStateChangeResult> stateChanges,
        Set<String> actorTags,
        boolean timerScheduled,
        List<String> errors
) {
    public static SimulationExecutionResult from(RuntimeResult result, SimulationContext context) {
        return new SimulationExecutionResult(
                result.success(),
                result.traceId(),
                result.message(),
                context.actionResults(),
                context.messageResults(),
                context.stateChanges(),
                context.actor().tags(),
                context.timerScheduled(),
                result.success() ? List.of() : List.of(result.message())
        );
    }
}
