package com.pixelmc.pixellogic.core.simulation.runner;

import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.core.simulation.context.SimulationWorld;
import com.pixelmc.pixellogic.core.simulation.event.SimulationEvent;

import java.util.UUID;

public record SimulationExecutionRequest(
        String graphId,
        SimulationEvent event,
        SimulationActor actor,
        SimulationWorld world,
        SimulationRunOptions options,
        long generation
) {
    public SimulationExecutionRequest {
        if (actor == null) {
            throw new IllegalArgumentException("simulation actor is required");
        }
        graphId = graphId == null || graphId.isBlank() ? "demo-start-flow" : graphId;
        event = event == null ? SimulationEvent.manual("manual.test.start", "", "manual-session") : event;
        world = world == null ? SimulationWorld.overworld() : world;
        options = options == null ? SimulationRunOptions.realTime() : options;
    }

    public static SimulationExecutionRequest manual(
            String graphId,
            String triggerType,
            String commandText,
            UUID playerId,
            String playerName,
            String sessionId,
            long generation
    ) {
        return manual(
                graphId,
                triggerType,
                commandText,
                SimulationActor.player(playerId, playerName),
                sessionId,
                generation
        );
    }

    public static SimulationExecutionRequest manual(
            String graphId,
            String triggerType,
            String commandText,
            SimulationActor actor,
            SimulationWorld world,
            String sessionId,
            long generation
    ) {
        return new SimulationExecutionRequest(
                graphId,
                SimulationEvent.manual(triggerType, commandText, sessionId),
                actor,
                world,
                SimulationRunOptions.realTime(),
                generation
        );
    }

    public static SimulationExecutionRequest manual(
            String graphId,
            String triggerType,
            String commandText,
            SimulationActor actor,
            String sessionId,
            long generation
    ) {
        return manual(graphId, triggerType, commandText, actor, SimulationWorld.overworld(), sessionId, generation);
    }
}
