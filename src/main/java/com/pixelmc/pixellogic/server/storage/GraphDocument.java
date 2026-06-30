package com.pixelmc.pixellogic.server.storage;

import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record GraphDocument(
        int schemaVersion,
        String id,
        String displayName,
        String createdAt,
        String updatedAt,
        String fingerprint,
        List<NodeDocument> nodes,
        List<EdgeDocument> edges,
        Map<String, String> triggerEntries
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static GraphDocument fromGraphDefinition(GraphDefinition graph, String displayName) {
        Instant now = Instant.now();
        return new GraphDocument(
                CURRENT_SCHEMA_VERSION,
                graph.id(),
                displayName,
                now.toString(),
                now.toString(),
                "",
                graph.nodes().stream().map(GraphDocument::nodeFromDefinition).toList(),
                graph.edges().stream().map(GraphDocument::edgeFromDefinition).toList(),
                sortedMap(graph.triggerEntries())
        );
    }

    public GraphDefinition toGraphDefinition() {
        require(schemaVersion == CURRENT_SCHEMA_VERSION, "schemaVersion must be 1");
        requireNonBlank(id, "graph id");
        require(nodes != null && !nodes.isEmpty(), "nodes must not be empty");
        require(edges != null, "edges must not be null");
        require(triggerEntries != null, "triggerEntries must not be null");

        return new GraphDefinition(
                id,
                nodes.stream().map(NodeDocument::toDefinition).toList(),
                edges.stream().map(EdgeDocument::toDefinition).toList(),
                sortedMap(triggerEntries)
        );
    }

    GraphDocument normalized(String graphId, String fallbackDisplayName, String fallbackCreatedAt, String updatedAt) {
        return new GraphDocument(
                CURRENT_SCHEMA_VERSION,
                graphId,
                blankToDefault(displayName, fallbackDisplayName),
                blankToDefault(createdAt, fallbackCreatedAt),
                updatedAt,
                "",
                nodes.stream().map(NodeDocument::normalized).toList(),
                edges,
                sortedMap(triggerEntries)
        );
    }

    private static NodeDocument nodeFromDefinition(NodeDefinition node) {
        return new NodeDocument(
                node.id(),
                node.type().name(),
                node.blockId(),
                defaultDisplayName(node),
                sortedMap(node.config()),
                defaultPosition(node.id()),
                node.slots().stream().map(slot -> new SlotDocument(
                        slot.id(),
                        slot.direction().name(),
                        slot.edgeType().name()
                )).toList()
        );
    }

    private static EdgeDocument edgeFromDefinition(EdgeDefinition edge) {
        return new EdgeDocument(
                edge.id(),
                edge.sourceNodeId(),
                edge.sourceSlotId(),
                edge.targetNodeId(),
                edge.targetSlotId(),
                edge.edgeType().name()
        );
    }

    private static String defaultDisplayName(NodeDefinition node) {
        return switch (node.type()) {
            case MANUAL_TRIGGER -> "WebUI 测试运行";
            case COMMAND_TRIGGER -> "命令触发";
            case STATE_COMPARE_CONDITION -> "是否未开始";
            case MESSAGE_ACTION -> "发送欢迎语";
            case PLAYER_HAS_TAG_CONDITION -> "判断玩家标签";
            case PLAYER_IS_ADMIN_CONDITION -> "判断管理员";
            case PLAYER_ADD_TAG_ACTION -> "添加玩家标签";
            case PLAYER_REMOVE_TAG_ACTION -> "移除玩家标签";
            case STATE_SET_ACTION -> "记录开始状态";
            case STATE_ADD_ACTION -> "累计开始次数";
            case TIMER_START_ACTION -> "等待倒计时";
            case DEBUG_LOG_ACTION -> node.config().getOrDefault("message", "调试记录");
        };
    }

    private static Position defaultPosition(String nodeId) {
        return switch (nodeId) {
            case "manual-trigger" -> new Position(48, 205);
            case "condition-started" -> new Position(294, 78);
            case "welcome-message" -> new Position(664, 78);
            case "set-started" -> new Position(910, 78);
            case "add-start-count" -> new Position(1156, 78);
            case "timer-start" -> new Position(1402, 78);
            case "debug-finished" -> new Position(1648, 78);
            case "debug-already-started" -> new Position(664, 332);
            default -> new Position(48, 78);
        };
    }

    private static Map<String, String> sortedMap(Map<String, String> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(source));
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }

    public record NodeDocument(
            String id,
            String type,
            String blockId,
            String displayName,
            Map<String, String> config,
            Position position,
            List<SlotDocument> slots
    ) {
        NodeDefinition toDefinition() {
            requireNonBlank(id, "node id");
            requireNonBlank(type, "node type");
            require(slots != null && !slots.isEmpty(), "node slots must not be empty");
            NodeType nodeType = NodeType.valueOf(type);
            return new NodeDefinition(
                    id,
                    nodeType,
                    BuiltInBlockCatalog.resolveBlockId(blockId, nodeType),
                    slots.stream().map(SlotDocument::toDefinition).toList(),
                    sortedMap(config)
            );
        }

        NodeDocument normalized() {
            NodeType nodeType = NodeType.valueOf(type);
            return new NodeDocument(
                    id,
                    type,
                    BuiltInBlockCatalog.resolveBlockId(blockId, nodeType),
                    blankToDefault(displayName, id),
                    sortedMap(config),
                    position == null ? new Position(48, 78) : position,
                    slots
            );
        }
    }

    public record SlotDocument(String id, String direction, String edgeType) {
        SlotDefinition toDefinition() {
            requireNonBlank(id, "slot id");
            return new SlotDefinition(id, SlotDirection.valueOf(direction), EdgeType.valueOf(edgeType));
        }

    }

    public record EdgeDocument(
            String id,
            String sourceNodeId,
            String sourceSlotId,
            String targetNodeId,
            String targetSlotId,
            String type
    ) {
        EdgeDefinition toDefinition() {
            requireNonBlank(id, "edge id");
            requireNonBlank(sourceNodeId, "edge sourceNodeId");
            requireNonBlank(sourceSlotId, "edge sourceSlotId");
            requireNonBlank(targetNodeId, "edge targetNodeId");
            requireNonBlank(targetSlotId, "edge targetSlotId");
            return new EdgeDefinition(
                    id,
                    sourceNodeId,
                    sourceSlotId,
                    targetNodeId,
                    targetSlotId,
                    EdgeType.valueOf(type)
            );
        }

    }

    public record Position(int x, int y) {
    }
}
