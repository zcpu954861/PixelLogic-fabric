package com.pixelmc.pixellogic.core.graph;

import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GraphCompiler {
    private final GraphValidator validator = new GraphValidator();

    public CompiledGraph compile(GraphDefinition graph) {
        List<ValidationIssue> issues = validator.validate(graph);
        if (validator.hasErrors(issues)) {
            throw new IllegalArgumentException("Graph validation failed: " + issues);
        }

        Map<String, NodeDefinition> nodesById = new HashMap<>();
        for (NodeDefinition node : graph.nodes()) {
            nodesById.put(node.id(), node);
        }

        Map<CompiledGraph.EdgeKey, List<EdgeDefinition>> outgoing = new HashMap<>();
        for (EdgeDefinition edge : graph.edges()) {
            outgoing.computeIfAbsent(new CompiledGraph.EdgeKey(edge.sourceNodeId(), edge.sourceSlotId()), ignored -> new ArrayList<>()).add(edge);
        }

        return new CompiledGraph(graph.id(), nodesById, outgoing, graph.triggerEntries());
    }
}
