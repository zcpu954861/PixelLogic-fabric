package com.pixelmc.pixellogic.core.catalog;

import com.pixelmc.pixellogic.core.graph.DemoGraphFactory;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.server.storage.GraphDocument;
import com.pixelmc.pixellogic.server.storage.GraphStorageService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class BlockCatalogSelfCheck {
    private BlockCatalogSelfCheck() {
    }

    public static void main(String[] args) {
        run("blockCatalogSelfCheck", () -> {
        BlockCatalog catalog = BuiltInBlockCatalog.catalog();
        Set<String> expected = Set.of(
                BuiltInBlockCatalog.TRIGGER_MANUAL_TEST,
                BuiltInBlockCatalog.CONDITION_STATE_EQUALS,
                BuiltInBlockCatalog.ACTION_MESSAGE_CHAT,
                BuiltInBlockCatalog.ACTION_MESSAGE_TITLE,
                BuiltInBlockCatalog.ACTION_MESSAGE_SUBTITLE,
                BuiltInBlockCatalog.ACTION_MESSAGE_ACTIONBAR,
                BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG,
                BuiltInBlockCatalog.CONDITION_PLAYER_IS_ADMIN,
                BuiltInBlockCatalog.CONDITION_PLAYER_DIMENSION_IS,
                BuiltInBlockCatalog.CONDITION_PLAYER_IN_REGION,
                BuiltInBlockCatalog.CONDITION_PLAYER_Y_COMPARE,
                BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IS_TYPE,
                BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IN_REGION,
                BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_Y_COMPARE,
                BuiltInBlockCatalog.CONDITION_PLAYER_NEAR_TARGET_BLOCK,
                BuiltInBlockCatalog.ACTION_ENTITY_ADD_TAG,
                BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_TAG,
                BuiltInBlockCatalog.CONTROL_LOOP_COUNT,
                BuiltInBlockCatalog.CONTROL_LOOP_FOREVER,
                BuiltInBlockCatalog.CONTROL_LOOP_UNTIL,
                BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS,
                BuiltInBlockCatalog.STATE_SET,
                BuiltInBlockCatalog.STATE_ADD,
                BuiltInBlockCatalog.TIMER_WAIT,
                BuiltInBlockCatalog.DEBUG_LOG
        );
        Set<String> actual = new HashSet<>();
        catalog.blocks().forEach(block -> actual.add(block.id()));

        require(actual.equals(expected), "catalog should contain demo, player, and message concrete block ids");
        require(catalog.packs().stream()
                        .anyMatch(pack -> pack.id().equals("player-entity")
                                && pack.displayName().equals("玩家与实体")),
                "player/entity pack should be user-visible");
        require(catalog.categories().stream()
                        .anyMatch(category -> category.id().equals("player-entity.execution-context")
                                && category.displayName().equals("实体上下文")),
                "execution context category should be user-visible");
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

        BlockDefinition hasTag = BuiltInBlockCatalog.block(BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG).orElseThrow();
        BlockDefinition isAdmin = BuiltInBlockCatalog.block(BuiltInBlockCatalog.CONDITION_PLAYER_IS_ADMIN).orElseThrow();
        BlockDefinition addTag = BuiltInBlockCatalog.block(BuiltInBlockCatalog.ACTION_ENTITY_ADD_TAG).orElseThrow();
        BlockDefinition removeTag = BuiltInBlockCatalog.block(BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_TAG).orElseThrow();
        require(hasTag.categoryId().equals("player-entity.tags"),
                "entity tag condition should be under player/entity tags");
        require(isAdmin.categoryId().equals("player-entity.identity-permissions"),
                "player admin condition should be under identity/permissions");
        require(addTag.categoryId().equals("player-entity.tags"),
                "entity tag action should stay under player/entity tags");
        require(removeTag.categoryId().equals("player-entity.tags"),
                "entity remove tag action should stay under player/entity tags");
        require(hasTag.formSchema().stream().anyMatch(field -> field.key().equals("target") && field.type().equals("entity_target")),
                "entity tag condition should expose the shared target field");
        require(hasTag.formSchema().stream().anyMatch(field -> field.key().equals("tag") && field.type().equals("string")),
                "entity tag condition should expose tag field");
        require(hasTag.formSchema().stream().anyMatch(field -> field.key().equals(ConditionOutputMode.CONFIG_KEY) && field.type().equals("segmented")),
                "entity tag condition should expose condition output mode field");
        require(addTag.formSchema().stream().anyMatch(field -> field.key().equals("tag") && field.type().equals("string")),
                "entity tag action should expose tag field");
        require(removeTag.formSchema().stream().anyMatch(field -> field.key().equals("tag") && field.type().equals("string")),
                "entity remove tag action should expose tag field");
        require(hasTag.simulationCapability() == BlockCapabilityLevel.FULLY_SIMULATABLE
                        && addTag.simulationCapability() == BlockCapabilityLevel.FULLY_SIMULATABLE
                        && removeTag.simulationCapability() == BlockCapabilityLevel.FULLY_SIMULATABLE,
                "entity tag blocks should be fully simulatable");
        require(messageBlock(BuiltInBlockCatalog.ACTION_MESSAGE_TITLE).categoryId().equals("presentation-feedback.screen-prompts"),
                "title message block should live under presentation/feedback screen prompts");
        require(messageBlock(BuiltInBlockCatalog.ACTION_MESSAGE_SUBTITLE).formSchema().stream()
                        .anyMatch(field -> field.key().equals("message") && field.type().equals("rich_text_component")),
                "subtitle message block should use rich_text_component");
        require(messageBlock(BuiltInBlockCatalog.ACTION_MESSAGE_ACTIONBAR).formSchema().stream()
                        .anyMatch(field -> field.key().equals("message") && field.type().equals("rich_text_component")),
                "actionbar message block should use rich_text_component");
        require(block(BuiltInBlockCatalog.CONDITION_PLAYER_DIMENSION_IS).categoryId().equals("location-region.dimensions-heights"),
                "player dimension condition should live under dimensions/heights");
        require(block(BuiltInBlockCatalog.CONDITION_PLAYER_IN_REGION).categoryId().equals("location-region.regions"),
                "player region condition should live under regions");
        require(block(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IS_TYPE).categoryId().equals("block-world.target-block"),
                "target block type condition should live under target block");
        require(block(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IN_REGION).categoryId().equals("location-region.regions"),
                "target block region condition should live under regions");
        require(block(BuiltInBlockCatalog.CONDITION_PLAYER_Y_COMPARE).categoryId().equals("location-region.dimensions-heights"),
                "player y compare condition should live under dimensions/heights");
        require(block(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_Y_COMPARE).categoryId().equals("location-region.dimensions-heights"),
                "target block y compare condition should live under dimensions/heights");
        require(block(BuiltInBlockCatalog.CONDITION_PLAYER_NEAR_TARGET_BLOCK).categoryId().equals("location-region.spatial-relations"),
                "near target condition should live under spatial relations");
        BlockDefinition loopUntil = block(BuiltInBlockCatalog.CONTROL_LOOP_UNTIL);
        require(loopUntil.capabilities().equals(List.of(BlockCapability.PREDICATE_RACK))
                        && loopUntil.containerSlots().equals(List.of("body")),
                "loop until should declare a dynamic predicate rack beside its static body");
        Set<String> predicateBlocks = new HashSet<>();
        catalog.blocks().stream()
                .filter(item -> item.capabilities().contains(BlockCapability.PREDICATE))
                .forEach(item -> {
                    predicateBlocks.add(item.id());
                    require(!item.predicateSummaryTemplate().isBlank(), "predicate capsule summary should exist: " + item.id());
                    require(!item.predicateNegatedSummaryTemplate().isBlank(), "negated predicate capsule summary should exist: " + item.id());
                });
        require(predicateBlocks.equals(Set.of(
                        BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG,
                        BuiltInBlockCatalog.CONDITION_PLAYER_IS_ADMIN,
                        BuiltInBlockCatalog.CONDITION_PLAYER_DIMENSION_IS,
                BuiltInBlockCatalog.CONDITION_PLAYER_IN_REGION,
                        BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IS_TYPE
                )), "predicate capability should include the generic entity tag condition");
        });
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
                        "",
                        "",
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

    private static BlockDefinition messageBlock(String blockId) {
        return BuiltInBlockCatalog.block(blockId).orElseThrow();
    }

    private static BlockDefinition block(String blockId) {
        return BuiltInBlockCatalog.block(blockId).orElseThrow();
    }

}
