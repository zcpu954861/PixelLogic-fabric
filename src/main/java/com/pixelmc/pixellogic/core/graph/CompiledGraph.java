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

    public Optional<NodeDefinition> bodyEntry(String containerNodeId, String parentSlot) {
        return nodesById.values().stream()
                .filter(node -> node.parentContainerId().equals(containerNodeId))
                .filter(node -> node.parentSlot().equals(parentSlot))
                .filter(node -> incomingWithinParent(node.id(), containerNodeId, parentSlot).isEmpty())
                .findFirst();
    }

    public boolean isInBody(NodeDefinition node, String containerNodeId, String parentSlot) {
        return node.parentContainerId().equals(containerNodeId) && node.parentSlot().equals(parentSlot);
    }

    private List<EdgeDefinition> incomingWithinParent(String nodeId, String containerNodeId, String parentSlot) {
        return outgoingByNodeAndSlot.values().stream()
                .flatMap(List::stream)
                .filter(edge -> edge.targetNodeId().equals(nodeId))
                .filter(edge -> node(edge.sourceNodeId()).map(source -> isInBody(source, containerNodeId, parentSlot)).orElse(false))
                .toList();
    }

    record EdgeKey(String nodeId, String slotId) {
    }
}
