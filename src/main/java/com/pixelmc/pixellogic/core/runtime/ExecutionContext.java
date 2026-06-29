package com.pixelmc.pixellogic.core.runtime;

import java.util.UUID;

final class ExecutionContext {
    private final String traceId;
    private final UUID playerId;
    private final String sessionId;
    private final int continuationDepth;
    private int steps;

    ExecutionContext(String traceId, UUID playerId, String sessionId, int continuationDepth) {
        this.traceId = traceId;
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.continuationDepth = continuationDepth;
    }

    String traceId() {
        return traceId;
    }

    UUID playerId() {
        return playerId;
    }

    String sessionId() {
        return sessionId;
    }

    int continuationDepth() {
        return continuationDepth;
    }

    int nextStep() {
        steps += 1;
        return steps;
    }
}
