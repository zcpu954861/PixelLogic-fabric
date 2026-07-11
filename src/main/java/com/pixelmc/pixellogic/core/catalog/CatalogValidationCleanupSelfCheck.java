package com.pixelmc.pixellogic.core.catalog;

import com.google.gson.Gson;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.model.ConditionSlotDefinition;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;
import com.pixelmc.pixellogic.server.storage.GraphDocument;
import com.pixelmc.pixellogic.server.storage.GraphStorageService;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationBlockExecutor;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationExecutionRegistry;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationPredicateEvaluator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class CatalogValidationCleanupSelfCheck {
    private static final GraphValidator VALIDATOR = new GraphValidator();

    private CatalogValidationCleanupSelfCheck() {
    }

    public static void main(String[] args) {
        run("catalogValidationCleanupSelfCheck", () -> {
            schemaCases().forEach(test -> require(
                    hasIssue(graph(test.blockId(), test.overrides()), test.code()),
                    "Java catalog schema must reject " + test.name() + " with " + test.code()
            ));
            specializedCases().forEach(test -> require(
                    hasIssue(test.graph().get(), test.code()),
                    "specialized validator must reject " + test.name() + " with " + test.code()
            ));
            verifyNonWebUiInputs();
            verifyRegistryAndCatalogGuards();
        });
    }

    private static List<SchemaCase> schemaCases() {
        return List.of(
                schema("outputMode option", BuiltInBlockCatalog.CONDITION_PLAYER_HAS_TAG, "outputMode", "INVALID", "config_option_invalid"),
                schema("expected boolean", BuiltInBlockCatalog.CONDITION_STATE_EQUALS, "expected", "yes", "config_boolean_invalid"),
                schema("missing boolean", BuiltInBlockCatalog.CONDITION_STATE_EQUALS, "missing", "no", "config_boolean_invalid"),
                schema("state scope", BuiltInBlockCatalog.STATE_SET, "scope", "WORLD", "config_scope_invalid"),
                schema("required state key", BuiltInBlockCatalog.STATE_SET, "key", "", "config_required_missing"),
                schema("state.add amount type", BuiltInBlockCatalog.STATE_ADD, "amount", "one", "config_number_invalid"),
                schema("state.add amount range", BuiltInBlockCatalog.STATE_ADD, "amount", "1000000", "config_number_range"),
                schema("Y compare mode", BuiltInBlockCatalog.CONDITION_PLAYER_Y_COMPARE, "compareMode", "NEAR", "config_option_invalid"),
                schema("targetY type", BuiltInBlockCatalog.CONDITION_PLAYER_Y_COMPARE, "targetY", "high", "config_number_invalid"),
                schema("targetY range", BuiltInBlockCatalog.CONDITION_PLAYER_Y_COMPARE, "targetY", "5000", "config_number_range"),
                schema("minY type", BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_Y_COMPARE, "minY", "low", "config_number_invalid"),
                schema("maxY range", BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_Y_COMPARE, "maxY", "5000", "config_number_range"),
                schema("near distance type", BuiltInBlockCatalog.CONDITION_PLAYER_NEAR_TARGET_BLOCK, "maxDistance", "near", "config_number_invalid"),
                schema("near distance range", BuiltInBlockCatalog.CONDITION_PLAYER_NEAR_TARGET_BLOCK, "maxDistance", "0", "config_number_range"),
                schema("horizontalOnly option", BuiltInBlockCatalog.CONDITION_PLAYER_NEAR_TARGET_BLOCK, "horizontalOnly", "sometimes", "config_option_invalid"),
                schema("loop count type", BuiltInBlockCatalog.CONTROL_LOOP_COUNT, "count", "many", "config_number_invalid"),
                schema("loop count range", BuiltInBlockCatalog.CONTROL_LOOP_COUNT, "count", "101", "config_number_range"),
                schema("forever interval type", BuiltInBlockCatalog.CONTROL_LOOP_FOREVER, "intervalSeconds", "soon", "config_number_invalid"),
                schema("forever interval range", BuiltInBlockCatalog.CONTROL_LOOP_FOREVER, "intervalSeconds", "0", "config_number_range"),
                schema("timer duration type", BuiltInBlockCatalog.TIMER_WAIT, "durationSeconds", "later", "config_number_invalid"),
                schema("timer duration range", BuiltInBlockCatalog.TIMER_WAIT, "durationSeconds", "86401", "config_number_range")
        );
    }

    private static List<SpecializedCase> specializedCases() {
        return List.of(
                special("hidden condition valueType", () -> graph(BuiltInBlockCatalog.CONDITION_STATE_EQUALS, Map.of("valueType", "STRING")), "condition_state_type_invalid"),
                special("hidden state.add valueType", () -> graph(BuiltInBlockCatalog.STATE_ADD, Map.of("valueType", "BOOLEAN")), "state_add_type"),
                special("STATE_SET typed value", () -> graph(BuiltInBlockCatalog.STATE_SET, Map.of("valueType", "INTEGER", "value", "one")), "state_set_value_invalid"),
                special("tag whitespace", () -> graph(BuiltInBlockCatalog.ACTION_PLAYER_ADD_TAG, Map.of("tag", "bad tag")), "player_tag_invalid"),
                special("tag control character", () -> graph(BuiltInBlockCatalog.ACTION_PLAYER_ADD_TAG, Map.of("tag", "bad\ntag")), "player_tag_invalid"),
                special("tag length", () -> graph(BuiltInBlockCatalog.ACTION_PLAYER_ADD_TAG, Map.of("tag", "x".repeat(65))), "player_tag_invalid"),
                special("dimension namespaced id", () -> graph(BuiltInBlockCatalog.CONDITION_PLAYER_DIMENSION_IS, Map.of("dimensionId", "overworld")), "condition_dimension_id_invalid"),
                special("block namespaced id", () -> graph(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IS_TYPE, Map.of("blockId", "stone")), "condition_block_id_invalid"),
                special("region control character", () -> graph(BuiltInBlockCatalog.CONDITION_PLAYER_IN_REGION, Map.of("regionName", "bad\nregion")), "condition_region_name_invalid"),
                special("Y range relation", () -> graph(BuiltInBlockCatalog.CONDITION_PLAYER_Y_COMPARE, Map.of("compareMode", "BETWEEN", "minY", "80", "maxY", "60")), "condition_y_range_invalid"),
                special("rich text structure", () -> graph(BuiltInBlockCatalog.ACTION_MESSAGE_CHAT, Map.of("message", "{\"version\":2}")), "config_rich_text_invalid"),
                special("edge cycle", CatalogValidationCleanupSelfCheck::cycleGraph, "loop_risk"),
                special("container membership", CatalogValidationCleanupSelfCheck::invalidMembershipGraph, "container_slot_invalid"),
                special("condition rack member", CatalogValidationCleanupSelfCheck::invalidRackGraph, "condition_slot_predicate_required"),
                special("container depth", CatalogValidationCleanupSelfCheck::excessiveDepthGraph, "container_depth_exceeded")
        );
    }

    private static void verifyNonWebUiInputs() throws Exception {
        GraphDefinition invalid = graph(BuiltInBlockCatalog.CONTROL_LOOP_COUNT, Map.of("count", "0"));
        GraphDocument payload = GraphDocument.fromGraphDefinition(invalid, "invalid payload");
        GraphDocument parsedPayload = new Gson().fromJson(new Gson().toJson(payload), GraphDocument.class);
        require(!new GraphStorageService(Files.createTempDirectory("pixel-logic-api-payload")).validate(parsedPayload).valid(),
                "parsed API payload must use backend validation");

        Path storageRoot = Files.createTempDirectory("pixel-logic-validation-disk");
        GraphStorageService storage = new GraphStorageService(storageRoot);
        GraphDefinition valid = graph(BuiltInBlockCatalog.CONTROL_LOOP_COUNT, Map.of());
        storage.ensureCommitted(valid, "validation guard");
        storage.saveDraft(valid.id(), payload);
        require(!storage.validateDraft(valid.id()).valid(), "disk draft must use backend validation");

        GraphDocument legacy = legacyWithoutBlockIds(GraphDocument.fromGraphDefinition(graph(BuiltInBlockCatalog.ACTION_MESSAGE_CHAT, Map.of()), "legacy"));
        GraphDefinition legacyGraph = legacy.toGraphDefinition();
        require(legacyGraph.nodes().stream()
                        .filter(node -> node.type() == NodeType.MESSAGE_ACTION)
                        .allMatch(node -> node.blockId().equals(BuiltInBlockCatalog.ACTION_MESSAGE_CHAT)),
                "legacy MESSAGE_ACTION must resolve to the stable chat block default");
        require(!VALIDATOR.hasErrors(VALIDATOR.validate(legacyGraph)), "legacy graph without blockId/outputMode must remain valid");

        require(hasIssue(cycleGraph(), "loop_risk"), "direct malicious graph must fail closed");
    }

    private static void verifyRegistryAndCatalogGuards() {
        require(BuiltInBlockCatalog.blockIdFor(NodeType.MESSAGE_ACTION).equals(BuiltInBlockCatalog.ACTION_MESSAGE_CHAT),
                "shared MESSAGE_ACTION legacy fallback must be explicit and stable");
        require(BuiltInBlockCatalog.blockIdFor(NodeType.COMMAND_TRIGGER).isBlank(),
                "node types without a legacy catalog mapping must fail closed");
        BuiltInBlockCatalog.catalog().blocks().stream().map(BlockDefinition::nodeType).distinct().forEach(type -> {
            String fallback = BuiltInBlockCatalog.blockIdFor(type);
            require(!fallback.isBlank(), "catalog node type must declare an explicit legacy fallback: " + type);
            require(BuiltInBlockCatalog.block(fallback).orElseThrow().nodeType() == type,
                    "legacy fallback must preserve node type: " + type);
        });
        BuiltInBlockCatalog.catalog().blocks().forEach(block -> require(
                BuiltInBlockCatalog.isConditionBlock(block.id()) == "condition".equals(block.nodeKind()),
                "condition classification must come from catalog nodeKind: " + block.id()
        ));

        SimulationBlockExecutor first = executor(NodeType.MESSAGE_ACTION);
        expectDuplicateRegistry(List.of(first, executor(NodeType.MESSAGE_ACTION)), NodeType.MESSAGE_ACTION);
        SimulationPredicateEvaluator predicate = new SimulationPredicateEvaluator() {
            @Override
            public NodeType nodeType() {
                return NodeType.PLAYER_HAS_TAG_CONDITION;
            }
        };
        expectDuplicateRegistry(List.of(predicate, predicate), NodeType.PLAYER_HAS_TAG_CONDITION);
    }

    private static SimulationBlockExecutor executor(NodeType type) {
        return new SimulationBlockExecutor() {
            @Override
            public NodeType nodeType() {
                return type;
            }
        };
    }

    private static void expectDuplicateRegistry(List<SimulationBlockExecutor> executors, NodeType type) {
        try {
            new SimulationExecutionRegistry(executors);
            throw new IllegalStateException("duplicate registry should fail: " + type);
        } catch (IllegalArgumentException exception) {
            require(exception.getMessage().contains(type.name()), "duplicate error must include key: " + type);
        }
    }

    private static GraphDefinition graph(String blockId, Map<String, String> overrides) {
        BlockDefinition block = BuiltInBlockCatalog.block(blockId).orElseThrow();
        Map<String, String> config = new HashMap<>(block.defaultConfig());
        config.putAll(overrides);
        List<SlotDefinition> slots = new ArrayList<>(block.inputSlots());
        slots.addAll(block.outputSlots());
        return base(List.of(new NodeDefinition("subject", block.nodeType(), block.id(), slots, config)), List.of());
    }

    private static GraphDefinition cycleGraph() {
        NodeDefinition first = debug("first", "");
        NodeDefinition second = debug("second", "");
        return base(
                List.of(first, second),
                List.of(edge("cycle-a", "first", "done", "second", "input"), edge("cycle-b", "second", "done", "first", "input"))
        );
    }

    private static GraphDefinition invalidMembershipGraph() {
        NodeDefinition loop = node("loop", BuiltInBlockCatalog.CONTROL_LOOP_COUNT, "", "", List.of());
        NodeDefinition child = debug("child", "loop", "missing");
        return base(List.of(loop, child), List.of());
    }

    private static GraphDefinition invalidRackGraph() {
        NodeDefinition loop = node("loop", BuiltInBlockCatalog.CONTROL_LOOP_UNTIL, "", "", List.of(new ConditionSlotDefinition("condition-a", false)));
        NodeDefinition child = debug("child", "loop", "condition-a");
        return base(List.of(loop, child), List.of());
    }

    private static GraphDefinition excessiveDepthGraph() {
        List<NodeDefinition> loops = new ArrayList<>();
        String parent = "";
        for (int index = 0; index < 6; index += 1) {
            String id = "loop-" + index;
            loops.add(node(id, BuiltInBlockCatalog.CONTROL_LOOP_COUNT, parent, parent.isBlank() ? "" : "body", List.of()));
            parent = id;
        }
        return base(loops, List.of());
    }

    private static GraphDefinition base(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        List<NodeDefinition> all = new ArrayList<>();
        all.add(node("trigger", BuiltInBlockCatalog.TRIGGER_MANUAL_TEST, "", "", List.of()));
        all.addAll(nodes);
        return new GraphDefinition("catalog-validation-cleanup", List.copyOf(all), edges, Map.of("manual.test.start", "trigger"));
    }

    private static NodeDefinition node(String id, String blockId, String parentId, String parentSlot, List<ConditionSlotDefinition> conditionSlots) {
        BlockDefinition block = BuiltInBlockCatalog.block(blockId).orElseThrow();
        List<SlotDefinition> slots = new ArrayList<>(block.inputSlots());
        slots.addAll(block.outputSlots());
        return new NodeDefinition(id, block.nodeType(), block.id(), parentId, parentSlot, conditionSlots, slots, block.defaultConfig());
    }

    private static NodeDefinition debug(String id, String parentId) {
        return debug(id, parentId, parentId.isBlank() ? "" : "body");
    }

    private static NodeDefinition debug(String id, String parentId, String parentSlot) {
        return node(id, BuiltInBlockCatalog.DEBUG_LOG, parentId, parentSlot, List.of());
    }

    private static EdgeDefinition edge(String id, String source, String sourceSlot, String target, String targetSlot) {
        return new EdgeDefinition(id, source, sourceSlot, target, targetSlot, EdgeType.CONTROL);
    }

    private static GraphDocument legacyWithoutBlockIds(GraphDocument document) {
        return new GraphDocument(
                document.schemaVersion(), document.id(), document.displayName(), document.createdAt(), document.updatedAt(), document.fingerprint(),
                document.nodes().stream().map(node -> new GraphDocument.NodeDocument(
                        node.id(), node.type(), null, node.parentContainerId(), node.parentSlot(), node.conditionSlots(),
                        node.displayName(), node.config(), node.position(), node.slots()
                )).toList(),
                document.edges(), document.triggerEntries()
        );
    }

    private static boolean hasIssue(GraphDefinition graph, String code) {
        return VALIDATOR.validate(graph).stream().map(ValidationIssue::code).anyMatch(code::equals);
    }

    private static SchemaCase schema(String name, String blockId, String key, String value, String code) {
        return new SchemaCase(name, blockId, Map.of(key, value), code);
    }

    private static SpecializedCase special(String name, Supplier<GraphDefinition> graph, String code) {
        return new SpecializedCase(name, graph, code);
    }

    private record SchemaCase(String name, String blockId, Map<String, String> overrides, String code) {
    }

    private record SpecializedCase(String name, Supplier<GraphDefinition> graph, String code) {
    }
}
