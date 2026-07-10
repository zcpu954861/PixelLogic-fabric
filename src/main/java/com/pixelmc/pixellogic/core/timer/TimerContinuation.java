package com.pixelmc.pixellogic.core.timer;

import com.pixelmc.pixellogic.core.runtime.ExecutionCursor;

import java.util.UUID;

public record TimerContinuation(
        String graphId,
        String targetNodeId,
        String traceId,
        UUID playerId,
        String sessionId,
        int depth,
        long generation,
        String continuationId,
        String sourceNodeId,
        Reason reason,
        ExecutionCursor cursor
) {
    public TimerContinuation(
            String graphId,
            String targetNodeId,
            String traceId,
            UUID playerId,
            String sessionId,
            int depth,
            long generation
    ) {
        this(graphId, targetNodeId, traceId, playerId, sessionId, depth, generation, "", "", Reason.DELAY, null);
    }

    public enum Reason {
        DELAY,
        LOOP_INTERVAL
    }
}
