package com.pixelmc.pixellogic.core.runtime;

import java.util.List;
import java.util.UUID;

public record ExecutionCursor(
        String runId,
        String traceId,
        UUID playerId,
        String sessionId,
        int steps,
        String nodeId,
        List<LoopFrame> loopFrames
) {
    public ExecutionCursor {
        runId = runId == null ? "" : runId;
        traceId = traceId == null ? "" : traceId;
        sessionId = sessionId == null ? "" : sessionId;
        nodeId = nodeId == null ? "" : nodeId;
        loopFrames = loopFrames == null ? List.of() : List.copyOf(loopFrames);
    }

    public enum LoopKind {
        COUNT,
        FOREVER,
        UNTIL
    }

    public record LoopFrame(
            String containerNodeId,
            LoopKind kind,
            int iteration,
            int iterationLimit,
            String bodyEntryNodeId,
            String completionNodeId,
            int intervalSeconds
    ) {
        public LoopFrame {
            completionNodeId = completionNodeId == null ? "" : completionNodeId;
        }
    }
}
