package com.pixelmc.pixellogic.core.simulation.context;

import com.pixelmc.pixellogic.core.simulation.event.SimulationEvent;
import com.pixelmc.pixellogic.core.simulation.result.SimulationActionResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationMessageResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationStateChangeResult;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationRunOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SimulationContext {
    private final String runId;
    private final long generation;
    private final SimulationActor actor;
    private final SimulationWorld world;
    private final SimulationEvent event;
    private final SimulationRunOptions options;
    private final List<SimulationActionResult> actionResults = new ArrayList<>();
    private final List<SimulationMessageResult> messageResults = new ArrayList<>();
    private final List<SimulationStateChangeResult> stateChanges = new ArrayList<>();
    private boolean timerScheduled;

    public SimulationContext(
            String runId,
            long generation,
            SimulationActor actor,
            SimulationWorld world,
            SimulationEvent event,
            SimulationRunOptions options
    ) {
        this.runId = runId == null || runId.isBlank() ? UUID.randomUUID().toString() : runId;
        this.generation = generation;
        this.actor = actor;
        this.world = world;
        this.event = event;
        this.options = options;
    }

    public String runId() {
        return runId;
    }

    public long generation() {
        return generation;
    }

    public SimulationActor actor() {
        return actor;
    }

    public SimulationWorld world() {
        return world;
    }

    public SimulationEvent event() {
        return event;
    }

    public SimulationRunOptions options() {
        return options;
    }

    public void addActionResult(SimulationActionResult result) {
        actionResults.add(result);
    }

    public void addMessageResult(SimulationMessageResult result) {
        messageResults.add(result);
    }

    public void addStateChange(SimulationStateChangeResult result) {
        stateChanges.add(result);
    }

    public void markTimerScheduled() {
        timerScheduled = true;
    }

    public boolean timerScheduled() {
        return timerScheduled;
    }

    public List<SimulationActionResult> actionResults() {
        return List.copyOf(actionResults);
    }

    public List<SimulationMessageResult> messageResults() {
        return List.copyOf(messageResults);
    }

    public List<SimulationStateChangeResult> stateChanges() {
        return List.copyOf(stateChanges);
    }
}
