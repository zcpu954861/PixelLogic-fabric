package com.pixelmc.pixellogic.core.runtime;

import java.util.UUID;

final class ExecutionContext {
    private final String traceId;
    private final UUID playerId;
    private final String sessionId;

    ExecutionContext(String traceId, UUID playerId, String sessionId) {
        this.traceId = traceId;
        this.playerId = playerId;
        this.sessionId = sessionId;
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

}
