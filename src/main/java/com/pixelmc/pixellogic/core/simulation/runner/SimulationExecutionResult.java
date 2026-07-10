package com.pixelmc.pixellogic.core.simulation.runner;

import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.simulation.context.SimulationBlockFact;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;
import com.pixelmc.pixellogic.core.simulation.context.SimulationPosition;
import com.pixelmc.pixellogic.core.simulation.context.SimulationRegionFact;
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
        SimulationPosition playerPosition,
        SimulationBlockFact targetBlock,
        List<SimulationRegionFact> regions,
        Set<String> initialActorTags,
        List<SimulationActionResult> actionResults,
        List<SimulationMessageResult> messageResults,
        List<SimulationStateChangeResult> stateChanges,
        Set<String> actorTags,
        boolean targetEntityEnabled,
        String targetEntityTypeId,
        String targetEntityDisplayName,
        Set<String> initialTargetEntityTags,
        Set<String> targetEntityTags,
        boolean timerScheduled,
        Status status,
        List<String> errors
) {
    public static SimulationExecutionResult from(
            RuntimeResult result,
            SimulationContext context,
            Set<String> initialActorTags,
            Set<String> initialTargetEntityTags
    ) {
        var targetEntity = context.targetEntity().orElse(null);
        return new SimulationExecutionResult(
                result.success(),
                result.traceId(),
                result.message(),
                context.actor().displayName(),
                context.actor().operator(),
                context.actorPosition(),
                context.world().targetBlock(),
                context.world().regions(),
                Set.copyOf(initialActorTags),
                context.actionResults(),
                context.messageResults(),
                context.stateChanges(),
                context.actor().tags(),
                targetEntity != null,
                targetEntity == null ? "" : targetEntity.entityTypeId(),
                targetEntity == null ? "" : targetEntity.displayName(),
                Set.copyOf(initialTargetEntityTags),
                targetEntity == null ? Set.of() : targetEntity.tags(),
                context.timerScheduled(),
                result.suspended() ? Status.WAITING : result.success() ? Status.COMPLETED : Status.FAILED,
                result.success() ? List.of() : List.of(result.message())
        );
    }

    public SimulationExecutionResult cancelled(String message) {
        return new SimulationExecutionResult(
                false,
                traceId,
                message,
                actorDisplayName,
                actorOperator,
                playerPosition,
                targetBlock,
                regions,
                initialActorTags,
                actionResults,
                messageResults,
                stateChanges,
                actorTags,
                targetEntityEnabled,
                targetEntityTypeId,
                targetEntityDisplayName,
                initialTargetEntityTags,
                targetEntityTags,
                timerScheduled,
                Status.CANCELLED,
                List.of(message)
        );
    }

    public enum Status {
        WAITING(false),
        COMPLETED(true),
        FAILED(true),
        CANCELLED(true);

        private final boolean terminal;

        Status(boolean terminal) {
            this.terminal = terminal;
        }

        public boolean terminal() {
            return terminal;
        }
    }
}
