package com.pixelmc.pixellogic.core.model;

public record EdgeDefinition(
        String id,
        String sourceNodeId,
        String sourceSlotId,
        String targetNodeId,
        String targetSlotId,
        EdgeType edgeType
) {
}
