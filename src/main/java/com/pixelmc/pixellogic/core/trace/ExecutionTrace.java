package com.pixelmc.pixellogic.core.trace;

import java.util.ArrayList;
import java.util.List;

public final class ExecutionTrace {
    private final String id;
    private final int maxSteps;
    private final List<TraceStep> steps = new ArrayList<>();
    private boolean truncated;

    ExecutionTrace(String id, int maxSteps) {
        this.id = id;
        this.maxSteps = maxSteps;
    }

    public String id() {
        return id;
    }

    public synchronized void add(TraceStep step) {
        if (steps.size() >= maxSteps) {
            truncated = true;
            return;
        }
        steps.add(step);
    }

    public synchronized List<TraceStep> steps() {
        return List.copyOf(steps);
    }

    public synchronized boolean truncated() {
        return truncated;
    }

    public synchronized boolean containsMessage(String text) {
        return steps.stream().anyMatch(step -> step.message().contains(text));
    }
}
