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
        List<LoopFrame> loopFrames,
        RuntimeSubjectReference runEntity,
        RuntimeSubjectReference targetEntity,
        RuntimeSubjectReference currentEntity,
        RuntimeConditionResult currentCondition,
        List<EntityContextFrame> entityContextFrames
) {
    public ExecutionCursor {
        runId = runId == null ? "" : runId;
        traceId = traceId == null ? "" : traceId;
        sessionId = sessionId == null ? "" : sessionId;
        nodeId = nodeId == null ? "" : nodeId;
        loopFrames = loopFrames == null ? List.of() : List.copyOf(loopFrames);
        entityContextFrames = entityContextFrames == null ? List.of() : List.copyOf(entityContextFrames);
    }

    public ExecutionCursor(
            String runId,
            String traceId,
            UUID playerId,
            String sessionId,
            int steps,
            String nodeId,
            List<LoopFrame> loopFrames
    ) {
        this(runId, traceId, playerId, sessionId, steps, nodeId, loopFrames, null, null, null, null, List.of());
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

    public record EntityContextFrame(
            String containerNodeId,
            String bodyEntryNodeId,
            String completionNodeId,
            RuntimeSubjectReference previousEntity,
            RuntimeSubjectReference selectedEntity,
            int loopDepth
    ) {
        public EntityContextFrame {
            bodyEntryNodeId = bodyEntryNodeId == null ? "" : bodyEntryNodeId;
            completionNodeId = completionNodeId == null ? "" : completionNodeId;
        }
    }
}
