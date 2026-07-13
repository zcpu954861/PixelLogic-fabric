package com.pixelmc.pixellogic.core.simulation.runner;

import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.core.simulation.context.SimulationWorld;

public record SimulationExecutionRequest(
        String graphId,
        String triggerType,
        String commandText,
        SimulationActor actor,
        SimulationWorld world,
        String sessionId,
        long generation,
        boolean initializeCurrentEntity
) {
    public SimulationExecutionRequest {
        if (actor == null) {
            throw new IllegalArgumentException("simulation actor is required");
        }
        graphId = graphId == null || graphId.isBlank() ? "demo-start-flow" : graphId;
        triggerType = triggerType == null || triggerType.isBlank() ? "manual.test.start" : triggerType;
        commandText = commandText == null ? "" : commandText;
        world = world == null ? SimulationWorld.overworld() : world;
        sessionId = sessionId == null || sessionId.isBlank() ? "manual-session" : sessionId;
    }

    public SimulationExecutionRequest(
            String graphId,
            String triggerType,
            String commandText,
            SimulationActor actor,
            SimulationWorld world,
            String sessionId,
            long generation
    ) {
        this(graphId, triggerType, commandText, actor, world, sessionId, generation, true);
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
                triggerType,
                commandText,
                actor,
                world,
                sessionId,
                generation,
                true
        );
    }
}
