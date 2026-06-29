package com.pixelmc.pixellogic.core.timer;

import java.util.UUID;

public record TimerContinuation(
        String graphId,
        String targetNodeId,
        String traceId,
        UUID playerId,
        String sessionId,
        int depth
) {
}
