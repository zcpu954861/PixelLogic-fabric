package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.model.EntityTargetRequirement;
import com.pixelmc.pixellogic.core.model.EntityTargetSource;

public record EntityTargetError(
        EntityTargetErrorCode code,
        String nodeId,
        String fieldPath,
        EntityTargetSource source,
        EntityTargetRequirement requirement,
        String referenceId,
        String message
) {
    public EntityTargetError {
        nodeId = nodeId == null ? "" : nodeId;
        fieldPath = fieldPath == null ? "" : fieldPath;
        referenceId = referenceId == null ? "" : referenceId;
        message = message == null ? "" : message;
    }
}
