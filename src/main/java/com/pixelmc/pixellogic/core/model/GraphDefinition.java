package com.pixelmc.pixellogic.core.model;

import java.util.List;
import java.util.Map;

public record GraphDefinition(
        String id,
        List<NodeDefinition> nodes,
        List<EdgeDefinition> edges,
        Map<String, String> triggerEntries
) {
    public GraphDefinition withNodeConfig(String nodeId, String key, String value) {
        List<NodeDefinition> updatedNodes = nodes.stream()
                .map(node -> {
                    if (!node.id().equals(nodeId)) {
                        return node;
                    }
                    java.util.Map<String, String> config = new java.util.HashMap<>(node.config());
                    config.put(key, value);
                    return new NodeDefinition(node.id(), node.type(), node.slots(), Map.copyOf(config));
                })
                .toList();
        return new GraphDefinition(id, updatedNodes, edges, triggerEntries);
    }
}
