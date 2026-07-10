package com.pixelmc.pixellogic.core.runtime;

import java.util.UUID;

public record RuntimeExecutionContext(
        UUID playerId,
        String sessionId,
        RuntimeSubjectReference runEntity,
        RuntimeSubjectReference targetEntity,
        RuntimeSubjectReference currentEntity,
        RuntimeConditionResult currentCondition
) {
    public RuntimeExecutionContext {
        sessionId = sessionId == null ? "" : sessionId;
    }
}
