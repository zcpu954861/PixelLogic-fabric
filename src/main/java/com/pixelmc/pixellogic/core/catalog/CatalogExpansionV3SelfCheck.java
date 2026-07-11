package com.pixelmc.pixellogic.core.catalog;

import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;
import com.pixelmc.pixellogic.core.runtime.GraphRuntime;
import com.pixelmc.pixellogic.core.runtime.RuntimeLimits;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.core.simulation.context.SimulationBlockFact;
import com.pixelmc.pixellogic.core.simulation.context.SimulationPosition;
import com.pixelmc.pixellogic.core.simulation.context.SimulationWorld;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationExecutionRegistry;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionRequest;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionResult;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationRunner;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;
import com.pixelmc.pixellogic.core.trace.ExecutionTrace;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class CatalogExpansionV3SelfCheck {
    private static final String TRIGGER_TYPE = "manual.test.start";

    private CatalogExpansionV3SelfCheck() {
    }

    public static void main(String[] args) {
        run("catalogExpansionV3SelfCheck", () -> {
            checkCatalog();
            checkValidation();
            checkPlayerYCompare();
            checkTargetBlockYCompare();
            checkNearTargetBlock();
        });
    }

    private static void checkCatalog() {
        require(block(BuiltInBlockCatalog.CONDITION_PLAYER_Y_COMPARE).subcategoryId().equals("condition.player"),
                "player y compare should be under player conditions");
        require(block(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_Y_COMPARE).subcategoryId().equals("condition.block"),
                "target block y compare should be under block conditions");
        require(block(BuiltInBlockCatalog.CONDITION_PLAYER_NEAR_TARGET_BLOCK).subcategoryId().equals("condition.spatial"),
                "near target block should be under spatial conditions");
        require(modeLabels(BuiltInBlockCatalog.CONDITION_PLAYER_Y_COMPARE)
                        .equals(List.of("不低于时继续", "不高于时继续", "分开执行")),
                "player y compare should expose height-specific mode labels");
        require(modeLabels(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_Y_COMPARE)
                        .equals(List.of("不低于时继续", "不高于时继续", "分开执行")),
                "target y compare should expose height-specific mode labels");
        require(modeLabels(BuiltInBlockCatalog.CONDITION_PLAYER_NEAR_TARGET_BLOCK)
                        .equals(List.of("靠近时继续", "不靠近时继续", "分开执行")),
                "near target block should expose near-specific mode labels");

        Set<String> ids = BuiltInBlockCatalog.catalog().blocks().stream()
                .map(BlockDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        require(!ids.contains("condition.target_block.exists"), "catalog should not add target block exists block");
        require(!ids.contains("condition.player.x_compare"), "catalog should not add player x compare");
        require(!ids.contains("condition.player.z_compare"), "catalog should not add player z compare");
        require(!ids.contains("condition.target_block.x_compare"), "catalog should not add target x compare");
        require(!ids.contains("condition.target_block.z_compare"), "catalog should not add target z compare");
        require(!ids.contains("condition.region.intersects"), "catalog should not add area geometry blocks");
    }

    private static void checkValidation() {
        require(hasIssue(playerYGraph(Map.of("outputMode", "PASS_ONLY", "compareMode", "BOGUS", "targetY", "64"), List.of(), true),
                        "config_option_invalid"),
                "invalid compare mode should fail validation");
        require(hasIssue(playerYGraph(Map.of("outputMode", "PASS_ONLY", "compareMode", "BETWEEN", "minY", "80", "maxY", "60"), List.of(), true),
                        "condition_y_range_invalid"),
                "minY greater than maxY should fail validation");
        require(hasIssue(nearGraph(Map.of("outputMode", "PASS_ONLY", "maxDistance", "0", "horizontalOnly", "true"), List.of(), true),
                        "config_number_range"),
                "maxDistance <= 0 should fail validation");
        require(hasIssue(nearGraph(Map.of("outputMode", "PASS_ONLY", "maxDistance", "5", "horizontalOnly", "maybe"), List.of(), true),
                        "config_option_invalid"),
                "horizontalOnly should be boolean");
        require(valid(playerYGraph(Map.of("outputMode", "PASS_ONLY", "compareMode", "AT_OR_ABOVE", "targetY", "64"), List.of(), false)),
                "loose condition input should still validate");
        require(valid(targetYGraph(Map.of("outputMode", "PASS_ONLY", "compareMode", "AT_OR_ABOVE", "targetY", "64"), List.of(), true)),
                "unconnected condition output should still validate");
        require(hasIssue(nearGraph(Map.of("outputMode", "BOGUS", "maxDistance", "5", "horizontalOnly", "true"), List.of(), true),
                        "config_option_invalid"),
                "invalid outputMode should fail validation");
    }

    private static void checkPlayerYCompare() {
        RunResult atOrAbove = runGraph(
                playerYGraph(Map.of("outputMode", "PASS_ONLY", "compareMode", "AT_OR_ABOVE", "targetY", "64"),
                        List.of(edge("e2", "condition", "pass", "debug-pass", "input")), true),
                actorAt("高度玩家", "minecraft:overworld", 0, 70, 0),
                SimulationWorld.overworld()
        );
        require(atOrAbove.result().success(), "player y AT_OR_ABOVE true should continue");
        require(atOrAbove.trace().containsMessage("玩家高度条件通过"), "player y pass should be traced");

        RunResult failOnly = runGraph(
                playerYGraph(Map.of("outputMode", "FAIL_ONLY", "compareMode", "AT_OR_BELOW", "targetY", "64"),
                        List.of(edge("e2", "condition", "fail", "debug-fail", "input")), true),
                actorAt("高度玩家", "minecraft:overworld", 0, 70, 0),
                SimulationWorld.overworld()
        );
        require(failOnly.result().success(), "player y AT_OR_BELOW false should continue FAIL_ONLY");

        RunResult between = runGraph(
                playerYGraph(Map.of("outputMode", "PASS_ONLY", "compareMode", "BETWEEN", "minY", "64", "maxY", "70"),
                        List.of(edge("e2", "condition", "pass", "debug-pass", "input")), true),
                actorAt("高度玩家", "minecraft:overworld", 0, 70, 0),
                SimulationWorld.overworld()
        );
        require(between.result().success(), "player y BETWEEN should include max bound");
    }

    private static void checkTargetBlockYCompare() {
        RunResult pass = runGraph(
                targetYGraph(Map.of("outputMode", "PASS_ONLY", "compareMode", "EQUAL", "targetY", "64"),
                        List.of(edge("e2", "condition", "pass", "debug-pass", "input")), true),
                actorAt("目标玩家", "minecraft:overworld", 0, 64, 0),
                worldWithTarget(true, "minecraft:overworld", 0, 64, 0, "minecraft:stone")
        );
        require(pass.result().success(), "target y EQUAL should continue when target y matches");

        RunResult disabled = runGraph(
                targetYGraph(Map.of("outputMode", "PASS_ONLY", "compareMode", "AT_OR_ABOVE", "targetY", "64"), List.of(), true),
                actorAt("目标玩家", "minecraft:overworld", 0, 64, 0),
                worldWithTarget(false, "minecraft:overworld", 0, 64, 0, "minecraft:stone")
        );
        require(disabled.result().success(), "disabled target block y should not throw");
        require(disabled.trace().containsMessage("未设置目标方块"), "disabled target block y should be traced");
    }

    private static void checkNearTargetBlock() {
        RunResult horizontal = runGraph(
                nearGraph(Map.of("outputMode", "PASS_ONLY", "maxDistance", "5", "horizontalOnly", "true"),
                        List.of(edge("e2", "condition", "pass", "debug-pass", "input")), true),
                actorAt("空间玩家", "minecraft:overworld", 0, 64, 0),
                worldWithTarget(true, "minecraft:overworld", 3, 90, 4, "minecraft:stone")
        );
        require(horizontal.result().success(), "near target horizontal distance should ignore y");

        RunResult threeDPass = runGraph(
                nearGraph(Map.of("outputMode", "PASS_ONLY", "maxDistance", "27", "horizontalOnly", "false"),
                        List.of(edge("e2", "condition", "pass", "debug-pass", "input")), true),
                actorAt("空间玩家", "minecraft:overworld", 0, 64, 0),
                worldWithTarget(true, "minecraft:overworld", 3, 90, 4, "minecraft:stone")
        );
        require(threeDPass.result().success(), "near target 3d distance should pass when within max");

        RunResult threeDFail = runGraph(
                nearGraph(Map.of("outputMode", "FAIL_ONLY", "maxDistance", "5", "horizontalOnly", "false"),
                        List.of(edge("e2", "condition", "fail", "debug-fail", "input")), true),
                actorAt("空间玩家", "minecraft:overworld", 0, 64, 0),
                worldWithTarget(true, "minecraft:overworld", 3, 90, 4, "minecraft:stone")
        );
        require(threeDFail.result().success(), "near target 3d distance should fail when beyond max");

        RunResult dimensionMismatch = runGraph(
                nearGraph(Map.of("outputMode", "FAIL_ONLY", "maxDistance", "5", "horizontalOnly", "true"),
                        List.of(edge("e2", "condition", "fail", "debug-fail", "input")), true),
                actorAt("空间玩家", "minecraft:overworld", 0, 64, 0),
                worldWithTarget(true, "minecraft:the_nether", 0, 64, 0, "minecraft:stone")
        );
        require(dimensionMismatch.result().success(), "dimension mismatch should be false and continue FAIL_ONLY");
        require(dimensionMismatch.trace().containsMessage("不在同一维度"), "dimension mismatch should be traced");

        RunResult disabled = runGraph(
                nearGraph(Map.of("outputMode", "PASS_ONLY", "maxDistance", "5", "horizontalOnly", "true"), List.of(), true),
                actorAt("空间玩家", "minecraft:overworld", 0, 64, 0),
                worldWithTarget(false, "minecraft:overworld", 0, 64, 0, "minecraft:stone")
        );
        require(disabled.result().success(), "disabled target block near check should not throw");
        require(disabled.trace().containsMessage("未设置目标方块"), "disabled target near should be traced");
    }

    private static GraphDefinition playerYGraph(Map<String, String> config, List<EdgeDefinition> conditionEdges, boolean connectInput) {
        return conditionGraph("catalog-v3-player-y", NodeType.PLAYER_Y_COMPARE_CONDITION,
                BuiltInBlockCatalog.CONDITION_PLAYER_Y_COMPARE, config, conditionEdges, connectInput);
    }

    private static GraphDefinition targetYGraph(Map<String, String> config, List<EdgeDefinition> conditionEdges, boolean connectInput) {
        return conditionGraph("catalog-v3-target-y", NodeType.TARGET_BLOCK_Y_COMPARE_CONDITION,
                BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_Y_COMPARE, config, conditionEdges, connectInput);
    }

    private static GraphDefinition nearGraph(Map<String, String> config, List<EdgeDefinition> conditionEdges, boolean connectInput) {
        return conditionGraph("catalog-v3-near-target", NodeType.PLAYER_NEAR_TARGET_BLOCK_CONDITION,
                BuiltInBlockCatalog.CONDITION_PLAYER_NEAR_TARGET_BLOCK, config, conditionEdges, connectInput);
    }

    private static GraphDefinition conditionGraph(
            String graphId,
            NodeType nodeType,
            String blockId,
            Map<String, String> config,
            List<EdgeDefinition> conditionEdges,
            boolean connectInput
    ) {
        java.util.ArrayList<EdgeDefinition> edges = new java.util.ArrayList<>();
        if (connectInput) {
            edges.add(edge("e1", "manual-trigger", "started", "condition", "input"));
        }
        edges.addAll(conditionEdges);
        return new GraphDefinition(
                graphId,
                List.of(
                        node("manual-trigger", NodeType.MANUAL_TRIGGER, BuiltInBlockCatalog.TRIGGER_MANUAL_TEST, out("started"), Map.of()),
                        node("condition", nodeType, blockId, in("input"), out("pass"), out("fail"), config),
                        node("debug-pass", NodeType.DEBUG_LOG_ACTION, BuiltInBlockCatalog.DEBUG_LOG, in("input"), out("done"), Map.of("message", "通过")),
                        node("debug-fail", NodeType.DEBUG_LOG_ACTION, BuiltInBlockCatalog.DEBUG_LOG, in("input"), out("done"), Map.of("message", "失败"))
                ),
                List.copyOf(edges),
                Map.of(TRIGGER_TYPE, "manual-trigger")
        );
    }

    private static boolean valid(GraphDefinition graph) {
        GraphValidator validator = new GraphValidator();
        List<ValidationIssue> issues = validator.validate(graph);
        return !validator.hasErrors(issues);
    }

    private static boolean hasIssue(GraphDefinition graph, String code) {
        return new GraphValidator().validate(graph).stream()
                .map(ValidationIssue::code)
                .anyMatch(code::equals);
    }

    private static RunResult runGraph(GraphDefinition graph, SimulationActor actor, SimulationWorld world) {
        require(valid(graph), "graph should validate before simulation: " + graph.id());
        CompiledGraph compiled = new GraphCompiler().compile(graph);
        BoundedTraceBuffer traces = new BoundedTraceBuffer(4, 80);
        RuntimeServices delegate = new RuntimeServices() {
            @Override
            public void sendPlayerMessage(UUID playerId, String message) {
            }

            @Override
            public void debug(String message) {
            }

            @Override
            public void scheduleTimer(Duration delay, TimerContinuation continuation) {
            }
        };
        SimulationRunner runner = new SimulationRunner(
                services -> new GraphRuntime(compiled, new InMemoryStateStore(), traces, services, RuntimeLimits.spikeDefaults()),
                delegate,
                SimulationExecutionRegistry.playerTags()
        );
        SimulationExecutionResult result = runner.run(new SimulationExecutionRequest(
                graph.id(),
                TRIGGER_TYPE,
                "/pixellogic test start",
                actor,
                world,
                "catalog-v3",
                0L
        ));
        ExecutionTrace trace = traces.get(result.traceId()).orElseThrow();
        return new RunResult(result, trace);
    }

    private static SimulationActor actorAt(String name, String dimensionId, int x, int y, int z) {
        return new SimulationActor(
                UUID.randomUUID(),
                name,
                true,
                false,
                Set.of(),
                new SimulationPosition(dimensionId, x, y, z)
        );
    }

    private static SimulationWorld worldWithTarget(boolean enabled, String dimensionId, int x, int y, int z, String blockId) {
        return new SimulationWorld("minecraft:overworld", new SimulationBlockFact(enabled, dimensionId, x, y, z, blockId), List.of());
    }

    private static NodeDefinition node(String id, NodeType type, String blockId, SlotDefinition slot, Map<String, String> config) {
        return new NodeDefinition(id, type, blockId, List.of(slot), config);
    }

    private static NodeDefinition node(
            String id,
            NodeType type,
            String blockId,
            SlotDefinition input,
            SlotDefinition output,
            Map<String, String> config
    ) {
        return new NodeDefinition(id, type, blockId, List.of(input, output), config);
    }

    private static NodeDefinition node(
            String id,
            NodeType type,
            String blockId,
            SlotDefinition input,
            SlotDefinition outputA,
            SlotDefinition outputB,
            Map<String, String> config
    ) {
        return new NodeDefinition(id, type, blockId, List.of(input, outputA, outputB), config);
    }

    private static SlotDefinition in(String id) {
        return new SlotDefinition(id, SlotDirection.INPUT, EdgeType.CONTROL);
    }

    private static SlotDefinition out(String id) {
        return new SlotDefinition(id, SlotDirection.OUTPUT, EdgeType.CONTROL);
    }

    private static EdgeDefinition edge(String id, String sourceNodeId, String sourceSlotId, String targetNodeId, String targetSlotId) {
        return new EdgeDefinition(id, sourceNodeId, sourceSlotId, targetNodeId, targetSlotId, EdgeType.CONTROL);
    }

    private static BlockDefinition block(String blockId) {
        return BuiltInBlockCatalog.block(blockId).orElseThrow();
    }

    private static List<String> modeLabels(String blockId) {
        return block(blockId).formSchema().stream()
                .filter(field -> field.key().equals("outputMode"))
                .findFirst()
                .orElseThrow()
                .options()
                .stream()
                .map(BlockFormFieldDefinition.FieldOption::label)
                .toList();
    }

    private record RunResult(SimulationExecutionResult result, ExecutionTrace trace) {
    }
}
