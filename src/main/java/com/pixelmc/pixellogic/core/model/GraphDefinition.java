package com.pixelmc.pixellogic.core.model;

import java.util.List;
import java.util.Map;

public record GraphDefinition(
        String id,
        List<NodeDefinition> nodes,
        List<EdgeDefinition> edges,
        Map<String, String> triggerEntries
) {
}
