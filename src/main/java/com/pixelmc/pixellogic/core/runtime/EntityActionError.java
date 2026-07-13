package com.pixelmc.pixellogic.core.runtime;

public record EntityActionError(
        EntityActionErrorCode code,
        String nodeId,
        String fieldPath,
        RuntimeSubjectReference target,
        String message
) {
    public EntityActionError {
        nodeId = nodeId == null ? "" : nodeId;
        fieldPath = fieldPath == null ? "" : fieldPath;
        message = message == null ? "" : message;
        if (code == null) {
            throw new IllegalArgumentException("entity action error code is required");
        }
    }
}
