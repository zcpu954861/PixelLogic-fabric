package com.pixelmc.pixellogic.core.runtime;

import java.util.UUID;

final class ExecutionContext {
    private final String traceId;
    private final UUID playerId;
    private final String sessionId;
    private final RuntimeSubjectReference targetEntity;
    private RuntimeSubjectReference currentEntity;
    private RuntimeConditionResult currentCondition;

    ExecutionContext(
            String traceId,
            UUID playerId,
            String sessionId,
            RuntimeSubjectReference targetEntity,
            RuntimeSubjectReference currentEntity,
            RuntimeConditionResult currentCondition
    ) {
        this.traceId = traceId;
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.targetEntity = targetEntity;
        this.currentEntity = currentEntity;
        this.currentCondition = currentCondition;
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

    RuntimeSubjectReference currentEntity() {
        return currentEntity;
    }

    RuntimeSubjectReference targetEntity() {
        return targetEntity;
    }

    void currentEntity(RuntimeSubjectReference entity) {
        currentEntity = entity;
    }

    RuntimeConditionResult currentCondition() {
        return currentCondition;
    }

    void currentCondition(RuntimeConditionResult result) {
        currentCondition = result;
    }

    RuntimeExecutionContext snapshot() {
        return new RuntimeExecutionContext(playerId, sessionId, targetEntity, currentEntity, currentCondition);
    }

}
