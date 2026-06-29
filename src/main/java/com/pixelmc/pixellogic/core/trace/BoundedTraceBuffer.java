package com.pixelmc.pixellogic.core.trace;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class BoundedTraceBuffer {
    private final int maxTraces;
    private final int maxStepsPerTrace;
    private final LinkedHashMap<String, ExecutionTrace> traces = new LinkedHashMap<>();
    private String latestTraceId;

    public BoundedTraceBuffer(int maxTraces, int maxStepsPerTrace) {
        this.maxTraces = maxTraces;
        this.maxStepsPerTrace = maxStepsPerTrace;
    }

    public synchronized ExecutionTrace startTrace(String traceId) {
        ExecutionTrace trace = new ExecutionTrace(traceId, maxStepsPerTrace);
        traces.put(traceId, trace);
        latestTraceId = traceId;
        while (traces.size() > maxTraces) {
            String oldest = traces.keySet().iterator().next();
            traces.remove(oldest);
        }
        return trace;
    }

    public synchronized Optional<ExecutionTrace> get(String traceId) {
        return Optional.ofNullable(traces.get(traceId));
    }

    public synchronized Optional<ExecutionTrace> latest() {
        return latestTraceId == null ? Optional.empty() : get(latestTraceId);
    }

    public synchronized List<ExecutionTrace> recent() {
        return List.copyOf(traces.values());
    }

    public void add(String traceId, String nodeId, String message) {
        ExecutionTrace trace;
        synchronized (this) {
            trace = traces.get(traceId);
            if (trace != null) {
                latestTraceId = traceId;
            }
        }
        if (trace != null) {
            trace.add(new TraceStep(Instant.now(), nodeId, message));
        }
    }
}
