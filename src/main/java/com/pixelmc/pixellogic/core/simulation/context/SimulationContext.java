package com.pixelmc.pixellogic.core.simulation.context;

import com.pixelmc.pixellogic.core.simulation.event.SimulationEvent;
import com.pixelmc.pixellogic.core.simulation.result.SimulationActionResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationMessageResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationStateChangeResult;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationRunOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    public SimulationPosition actorPosition() {
        return actor.position();
    }

    public SimulationWorld world() {
        return world;
    }

    public Optional<SimulationEntity> targetEntity() {
        return Optional.ofNullable(world.targetEntity());
    }

    public Optional<SimulationEntity> entity(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return Optional.empty();
        }
        if (actor.id().toString().equals(referenceId)) {
            return Optional.of(actor);
        }
        return targetEntity().filter(entity -> entity.id().toString().equals(referenceId));
    }

    public SimulationEvent event() {
        return event;
    }

    public SimulationRunOptions options() {
        return options;
    }

    public synchronized void addActionResult(SimulationActionResult result) {
        actionResults.add(result);
    }

    public synchronized void addMessageResult(SimulationMessageResult result) {
        messageResults.add(result);
    }

    public synchronized void addStateChange(SimulationStateChangeResult result) {
        stateChanges.add(result);
    }

    public synchronized void markTimerScheduled() {
        timerScheduled = true;
    }

    public synchronized boolean timerScheduled() {
        return timerScheduled;
    }

    public synchronized List<SimulationActionResult> actionResults() {
        return List.copyOf(actionResults);
    }

    public synchronized List<SimulationMessageResult> messageResults() {
        return List.copyOf(messageResults);
    }

    public synchronized List<SimulationStateChangeResult> stateChanges() {
        return List.copyOf(stateChanges);
    }
}
