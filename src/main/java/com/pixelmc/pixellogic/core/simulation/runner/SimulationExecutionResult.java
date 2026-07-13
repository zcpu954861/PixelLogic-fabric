package com.pixelmc.pixellogic.core.simulation.runner;

import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.runtime.EntityTargetError;
import com.pixelmc.pixellogic.core.runtime.EntityActionError;
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
        double initialActorHealth,
        List<SimulationActionResult> actionResults,
        List<SimulationMessageResult> messageResults,
        List<SimulationStateChangeResult> stateChanges,
        Set<String> actorTags,
        double actorHealth,
        double actorMaxHealth,
        boolean actorAlive,
        boolean actorRemoved,
        boolean targetEntityEnabled,
        String targetEntityTypeId,
        String targetEntityDisplayName,
        Set<String> initialTargetEntityTags,
        double initialTargetEntityHealth,
        Set<String> targetEntityTags,
        double targetEntityHealth,
        double targetEntityMaxHealth,
        boolean targetEntityAlive,
        boolean targetEntityRemoved,
        boolean timerScheduled,
        Status status,
        List<String> errors,
        EntityTargetError targetError,
        EntityActionError actionError
) {
    public static SimulationExecutionResult from(
            RuntimeResult result,
            SimulationContext context,
            Set<String> initialActorTags,
            double initialActorHealth,
            Set<String> initialTargetEntityTags,
            double initialTargetEntityHealth
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
                initialActorHealth,
                context.actionResults(),
                context.messageResults(),
                context.stateChanges(),
                context.actor().tags(),
                context.actor().health(),
                context.actor().maxHealth(),
                context.actor().alive(),
                context.actor().removed(),
                targetEntity != null,
                targetEntity == null ? "" : targetEntity.entityTypeId(),
                targetEntity == null ? "" : targetEntity.displayName(),
                Set.copyOf(initialTargetEntityTags),
                initialTargetEntityHealth,
                targetEntity == null ? Set.of() : targetEntity.tags(),
                targetEntity == null ? 0 : targetEntity.health(),
                targetEntity == null ? 0 : targetEntity.maxHealth(),
                targetEntity != null && targetEntity.alive(),
                targetEntity != null && targetEntity.removed(),
                context.timerScheduled(),
                result.suspended() ? Status.WAITING : result.success() ? Status.COMPLETED : Status.FAILED,
                result.success() ? List.of() : List.of(result.message()),
                result.targetError(),
                result.actionError()
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
                initialActorHealth,
                actionResults,
                messageResults,
                stateChanges,
                actorTags,
                actorHealth,
                actorMaxHealth,
                actorAlive,
                actorRemoved,
                targetEntityEnabled,
                targetEntityTypeId,
                targetEntityDisplayName,
                initialTargetEntityTags,
                initialTargetEntityHealth,
                targetEntityTags,
                targetEntityHealth,
                targetEntityMaxHealth,
                targetEntityAlive,
                targetEntityRemoved,
                timerScheduled,
                Status.CANCELLED,
                List.of(message),
                targetError,
                actionError
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
