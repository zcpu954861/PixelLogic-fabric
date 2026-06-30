package com.pixelmc.pixellogic.core.catalog;

import com.pixelmc.pixellogic.core.graph.DemoGraphFactory;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.server.storage.GraphDocument;
import com.pixelmc.pixellogic.server.storage.GraphStorageService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BlockCatalogSelfCheck {
    private BlockCatalogSelfCheck() {
    }

    public static void main(String[] args) {
        BlockCatalog catalog = BuiltInBlockCatalog.catalog();
        Set<String> expected = Set.of(
                BuiltInBlockCatalog.TRIGGER_MANUAL_TEST,
                BuiltInBlockCatalog.CONDITION_STATE_EQUALS,
                BuiltInBlockCatalog.ACTION_MESSAGE_CHAT,
                BuiltInBlockCatalog.STATE_SET,
                BuiltInBlockCatalog.STATE_ADD,
                BuiltInBlockCatalog.TIMER_WAIT,
                BuiltInBlockCatalog.DEBUG_LOG
        );
        Set<String> actual = new HashSet<>();
        catalog.blocks().forEach(block -> actual.add(block.id()));

        require(actual.equals(expected), "catalog should contain only the current demo concrete block ids");
        catalog.blocks().forEach(block -> {
            require(catalog.categories().stream().anyMatch(category -> category.id().equals(block.categoryId())),
                    "block category should exist: " + block.id());
            require(!block.displayName().isBlank() && !block.description().isBlank(), "block should have Chinese-facing copy: " + block.id());
            require(!block.nodeKind().isBlank(), "block should expose a UI node kind: " + block.id());
            require(!block.formSchema().isEmpty(), "block should expose schema-driven form fields: " + block.id());
            require(!block.summaryTemplate().isBlank(), "block should expose catalog summary metadata: " + block.id());
        });

        GraphDefinition demo = DemoGraphFactory.create(Duration.ofSeconds(1));
        require(demo.nodes().stream().allMatch(node -> expected.contains(node.blockId())), "demo graph nodes should resolve block ids");
        require(!new GraphValidator().hasErrors(new GraphValidator().validate(demo)), "demo graph should validate after catalog migration");

        GraphDocument document = GraphDocument.fromGraphDefinition(demo, GraphStorageService.DEFAULT_DISPLAY_NAME);
        require(document.nodes().stream().allMatch(node -> expected.contains(node.blockId())), "graph document should write blockId");
        GraphDefinition legacyGraph = legacyWithoutBlockId(document).toGraphDefinition();
        require(legacyGraph.nodes().stream().allMatch(node -> expected.contains(node.blockId())), "legacy document should infer blockId from node.type");
        require(!new GraphValidator().hasErrors(new GraphValidator().validate(legacyGraph)), "legacy demo graph should still validate");

        BlockDefinition message = BuiltInBlockCatalog.block(BuiltInBlockCatalog.ACTION_MESSAGE_CHAT).orElseThrow();
        require(message.formSchema().stream().anyMatch(field -> field.key().equals("message") && field.type().equals("rich_text_component")),
                "message block should expose rich_text_component field");
        require(RichTextComponentValue.isStructured(message.defaultConfig().get("message")),
                "message block default config should be structured rich text");
        List<SlotDefinition> messageSlots = new ArrayList<>();
        messageSlots.addAll(message.inputSlots());
        messageSlots.addAll(message.outputSlots());
        NodeDefinition created = new NodeDefinition("new-message", message.nodeType(), message.id(), messageSlots, message.defaultConfig());
        require(created.blockId().equals(BuiltInBlockCatalog.ACTION_MESSAGE_CHAT), "new catalog node should keep concrete blockId");
        require(RichTextComponentValue.plainText(created.config().get("message")).equals("新消息"), "new catalog node should use rich text default config");
        require(!new GraphValidator().hasErrors(new GraphValidator().validate(withMessageConfig(demo, "欢迎旧图"))),
                "legacy string message should remain valid");
        require(!new GraphValidator().hasErrors(new GraphValidator().validate(withMessageConfig(demo, RichTextComponentValue.fromPlainText("结构化消息")))),
                "structured rich text message should validate");

        require(hasIssue(withFirstNodeBlockId(demo, "unknown.block"), "unknown_block_id"), "unknown blockId should fail validation");
        require(hasIssue(withFirstNodeBlockId(demo, BuiltInBlockCatalog.STATE_SET), "block_type_mismatch"),
                "blockId/node.type mismatch should fail validation");
    }

    private static GraphDocument legacyWithoutBlockId(GraphDocument document) {
        return new GraphDocument(
                document.schemaVersion(),
                document.id(),
                document.displayName(),
                document.createdAt(),
                document.updatedAt(),
                document.fingerprint(),
                document.nodes().stream().map(node -> new GraphDocument.NodeDocument(
                        node.id(),
                        node.type(),
                        null,
                        node.displayName(),
                        node.config(),
                        node.position(),
                        node.slots()
                )).toList(),
                document.edges(),
                document.triggerEntries()
        );
    }

    private static GraphDefinition withFirstNodeBlockId(GraphDefinition graph, String blockId) {
        return new GraphDefinition(
                graph.id(),
                graph.nodes().stream().map(node -> node.id().equals("manual-trigger")
                        ? new NodeDefinition(node.id(), node.type(), blockId, node.slots(), node.config())
                        : node).toList(),
                graph.edges(),
                graph.triggerEntries()
        );
    }

    private static GraphDefinition withMessageConfig(GraphDefinition graph, String message) {
        return new GraphDefinition(
                graph.id(),
                graph.nodes().stream().map(node -> node.id().equals("welcome-message")
                        ? new NodeDefinition(node.id(), node.type(), node.blockId(), node.slots(), java.util.Map.of("message", message, "target", "CURRENT_PLAYER"))
                        : node).toList(),
                graph.edges(),
                graph.triggerEntries()
        );
    }

    private static boolean hasIssue(GraphDefinition graph, String code) {
        return new GraphValidator().validate(graph).stream()
                .map(ValidationIssue::code)
                .anyMatch(code::equals);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
