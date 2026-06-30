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
        String actorDisplayName,
        boolean actorOperator,
        Set<String> initialActorTags,
        List<SimulationActionResult> actionResults,
        List<SimulationMessageResult> messageResults,
        List<SimulationStateChangeResult> stateChanges,
        Set<String> actorTags,
        boolean timerScheduled,
        List<String> errors
) {
    public static SimulationExecutionResult from(RuntimeResult result, SimulationContext context, Set<String> initialActorTags) {
        return new SimulationExecutionResult(
                result.success(),
                result.traceId(),
                result.message(),
                context.actor().displayName(),
                context.actor().operator(),
                Set.copyOf(initialActorTags),
                context.actionResults(),
                context.messageResults(),
                context.stateChanges(),
                context.actor().tags(),
                context.timerScheduled(),
                result.success() ? List.of() : List.of(result.message())
        );
    }
}
