package com.pixelmc.pixellogic.core.graph;

import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CompiledGraph {
    private final String graphId;
    private final Map<String, NodeDefinition> nodesById;
    private final Map<EdgeKey, List<EdgeDefinition>> outgoingByNodeAndSlot;
    private final Map<String, String> triggerEntries;

    public CompiledGraph(
            String graphId,
            Map<String, NodeDefinition> nodesById,
            Map<EdgeKey, List<EdgeDefinition>> outgoingByNodeAndSlot,
            Map<String, String> triggerEntries
    ) {
        this.graphId = graphId;
        this.nodesById = Map.copyOf(nodesById);
        this.outgoingByNodeAndSlot = Map.copyOf(outgoingByNodeAndSlot);
        this.triggerEntries = Map.copyOf(triggerEntries);
    }

    public String graphId() {
        return graphId;
    }

    public Optional<NodeDefinition> node(String nodeId) {
        return Optional.ofNullable(nodesById.get(nodeId));
    }

    public Optional<NodeDefinition> entryForTrigger(String triggerType) {
        return Optional.ofNullable(triggerEntries.get(triggerType)).flatMap(this::node);
    }

    public List<EdgeDefinition> outgoing(String nodeId, String slotId) {
        return outgoingByNodeAndSlot.getOrDefault(new EdgeKey(nodeId, slotId), List.of());
    }

    public Optional<NodeDefinition> firstTarget(String nodeId, String slotId) {
        List<EdgeDefinition> edges = outgoing(nodeId, slotId);
        if (edges.isEmpty()) {
            return Optional.empty();
        }
        if (edges.size() > 1) {
            throw new IllegalStateException("同一输出槽位存在多条连接：" + nodeId + "." + slotId);
        }
        return node(edges.getFirst().targetNodeId());
    }

    record EdgeKey(String nodeId, String slotId) {
    }
}
