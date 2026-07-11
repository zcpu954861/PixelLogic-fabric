package com.pixelmc.pixellogic.core.catalog;

import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
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
import com.pixelmc.pixellogic.core.simulation.context.SimulationRegionFact;
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

public final class CatalogExpansionV2SelfCheck {
    private static final String TRIGGER_TYPE = "manual.test.start";

    private CatalogExpansionV2SelfCheck() {
    }

    public static void main(String[] args) {
        run("catalogExpansionV2SelfCheck", () -> {
            checkCatalog();
            checkValidation();
            checkPlayerDimension();
            checkPlayerRegion();
            checkTargetBlockType();
            checkTargetBlockRegion();
        });
    }

    private static void checkCatalog() {
        require(block(BuiltInBlockCatalog.CONDITION_PLAYER_DIMENSION_IS).categoryId().equals("location-region.dimensions-heights"),
                "player dimension condition should be under dimensions/heights");
        require(block(BuiltInBlockCatalog.CONDITION_PLAYER_IN_REGION).categoryId().equals("location-region.regions"),
                "player region condition should be under regions");
        require(block(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IS_TYPE).categoryId().equals("block-world.target-block"),
                "target block type condition should be under target block");
        require(block(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IN_REGION).categoryId().equals("location-region.regions"),
                "target block region condition should be under regions");
        require(modeLabels(BuiltInBlockCatalog.CONDITION_PLAYER_DIMENSION_IS)
                        .equals(List.of("在该维度时继续", "不在该维度时继续", "分开执行")),
                "dimension condition should expose block-specific mode labels");
        require(modeLabels(BuiltInBlockCatalog.CONDITION_PLAYER_IN_REGION)
                        .equals(List.of("在区域内时继续", "不在区域内时继续", "分开执行")),
                "player region condition should expose block-specific mode labels");
        require(modeLabels(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IS_TYPE)
                        .equals(List.of("为该方块时继续", "不为该方块时继续", "分开执行")),
                "target block condition should expose block-specific mode labels");
        require(modeLabels(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IN_REGION)
                        .equals(List.of("在区域内时继续", "不在区域内时继续", "分开执行")),
                "target block region condition should expose block-specific mode labels");

        Set<String> ids = BuiltInBlockCatalog.catalog().blocks().stream()
                .map(BlockDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        require(!ids.contains("condition.player.not_in_region"), "catalog should not add a negative player region block");
        require(!ids.contains("condition.target_block.not_type"), "catalog should not add a negative target block type block");
        require(!ids.contains("condition.target_block.not_in_region"), "catalog should not add a negative target block region block");
    }

    private static void checkValidation() {
        require(hasIssue(dimensionGraph(Map.of("outputMode", "PASS_ONLY", "dimensionId", "overworld"), List.of(), true),
                        "condition_dimension_id_invalid"),
                "invalid dimensionId should fail validation");
        require(hasIssue(targetBlockTypeGraph(Map.of("outputMode", "PASS_ONLY", "blockId", "stone"), List.of(), true),
                        "condition_block_id_invalid"),
                "invalid blockId should fail validation");
        require(hasIssue(playerRegionGraph(Map.of("outputMode", "PASS_ONLY", "regionName", ""), List.of(), true),
                        "condition_region_name_missing"),
                "empty regionName should fail validation");
        require(valid(playerRegionGraph(Map.of("outputMode", "PASS_ONLY", "regionName", "出生区"), List.of(), false)),
                "loose condition input should still validate");
        require(valid(targetBlockRegionGraph(Map.of("outputMode", "PASS_ONLY", "regionName", "出生区"), List.of(), true)),
                "unconnected condition output should still validate");
        require(hasIssue(targetBlockRegionGraph(Map.of("outputMode", "BOGUS", "regionName", "出生区"), List.of(), true),
                        "config_option_invalid"),
                "invalid outputMode should fail validation");
    }

    private static void checkPlayerDimension() {
        GraphDefinition passGraph = dimensionGraph(
                Map.of("outputMode", "PASS_ONLY", "dimensionId", "minecraft:the_nether"),
                List.of(edge("e2", "condition", "pass", "debug-pass", "input")),
                true
        );
        RunResult pass = runGraph(passGraph, actorAt("维度玩家", "minecraft:the_nether", 0, 64, 0), SimulationWorld.overworld());
        require(pass.result().success(), "dimension PASS_ONLY should continue when dimensions match");
        require(pass.trace().containsMessage("玩家维度条件通过"), "dimension pass should be traced");

        GraphDefinition failGraph = dimensionGraph(
                Map.of("outputMode", "FAIL_ONLY", "dimensionId", "minecraft:the_nether"),
                List.of(edge("e2", "condition", "fail", "debug-fail", "input")),
                true
        );
        RunResult failOnly = runGraph(failGraph, actorAt("维度玩家", "minecraft:overworld", 0, 64, 0), SimulationWorld.overworld());
        require(failOnly.result().success(), "dimension FAIL_ONLY should continue when dimensions mismatch");
    }

    private static void checkPlayerRegion() {
        SimulationWorld world = worldWithRegion("出生区", "minecraft:overworld", true);
        GraphDefinition passGraph = playerRegionGraph(
                Map.of("outputMode", "PASS_ONLY", "regionName", "出生区"),
                List.of(edge("e2", "condition", "pass", "debug-pass", "input")),
                true
        );
        RunResult pass = runGraph(passGraph, actorAt("区域玩家", "minecraft:overworld", 10, 64, 10), world);
        require(pass.result().success(), "player in-region PASS_ONLY should include inclusive bounds");

        GraphDefinition failGraph = playerRegionGraph(
                Map.of("outputMode", "FAIL_ONLY", "regionName", "出生区"),
                List.of(edge("e2", "condition", "fail", "debug-fail", "input")),
                true
        );
        RunResult outside = runGraph(failGraph, actorAt("区域玩家", "minecraft:overworld", 11, 64, 10), world);
        require(outside.result().success(), "player in-region FAIL_ONLY should continue outside region");

        RunResult missing = runGraph(
                playerRegionGraph(Map.of("outputMode", "PASS_ONLY", "regionName", "不存在"), List.of(), true),
                actorAt("区域玩家", "minecraft:overworld", 0, 64, 0),
                world
        );
        require(missing.result().success(), "missing player region should not throw");
        require(missing.trace().containsMessage("未找到测试区域「不存在」"), "missing player region should be traced");
    }

    private static void checkTargetBlockType() {
        GraphDefinition passGraph = targetBlockTypeGraph(
                Map.of("outputMode", "PASS_ONLY", "blockId", "minecraft:stone"),
                List.of(edge("e2", "condition", "pass", "debug-pass", "input")),
                true
        );
        RunResult pass = runGraph(passGraph, actorAt("目标玩家", "minecraft:overworld", 0, 64, 0),
                worldWithTarget(true, "minecraft:overworld", 0, 64, 0, "minecraft:stone"));
        require(pass.result().success(), "target block type PASS_ONLY should continue when blockId matches");

        RunResult disabled = runGraph(
                targetBlockTypeGraph(Map.of("outputMode", "PASS_ONLY", "blockId", "minecraft:stone"), List.of(), true),
                actorAt("目标玩家", "minecraft:overworld", 0, 64, 0),
                worldWithTarget(false, "minecraft:overworld", 0, 64, 0, "minecraft:stone")
        );
        require(disabled.result().success(), "disabled target block should not throw");
        require(disabled.trace().containsMessage("未设置目标方块"), "disabled target block should be traced");
    }

    private static void checkTargetBlockRegion() {
        SimulationWorld world = worldWithRegion("出生区", "minecraft:overworld", true);
        GraphDefinition passGraph = targetBlockRegionGraph(
                Map.of("outputMode", "PASS_ONLY", "regionName", "出生区"),
                List.of(edge("e2", "condition", "pass", "debug-pass", "input")),
                true
        );
        RunResult pass = runGraph(passGraph, actorAt("目标玩家", "minecraft:overworld", 0, 64, 0), world);
        require(pass.result().success(), "target block in-region PASS_ONLY should include inclusive bounds");

        RunResult disabled = runGraph(
                targetBlockRegionGraph(Map.of("outputMode", "PASS_ONLY", "regionName", "出生区"), List.of(), true),
                actorAt("目标玩家", "minecraft:overworld", 0, 64, 0),
                worldWithTarget(false, "minecraft:overworld", 10, 64, 10, "minecraft:stone")
        );
        require(disabled.result().success(), "missing target block region check should not throw");
        require(disabled.trace().containsMessage("未设置目标方块"), "missing target block should be traced for region condition");

        RunResult missingRegion = runGraph(
                targetBlockRegionGraph(Map.of("outputMode", "PASS_ONLY", "regionName", "不存在"), List.of(), true),
                actorAt("目标玩家", "minecraft:overworld", 0, 64, 0),
                world
        );
        require(missingRegion.result().success(), "missing target region should not throw");
        require(missingRegion.trace().containsMessage("未找到测试区域「不存在」"), "missing target region should be traced");
    }

    private static GraphDefinition dimensionGraph(Map<String, String> config, List<EdgeDefinition> conditionEdges, boolean connectInput) {
        return conditionGraph("catalog-v2-dimension", NodeType.PLAYER_DIMENSION_CONDITION,
                BuiltInBlockCatalog.CONDITION_PLAYER_DIMENSION_IS, config, conditionEdges, connectInput);
    }

    private static GraphDefinition playerRegionGraph(Map<String, String> config, List<EdgeDefinition> conditionEdges, boolean connectInput) {
        return conditionGraph("catalog-v2-player-region", NodeType.PLAYER_IN_REGION_CONDITION,
                BuiltInBlockCatalog.CONDITION_PLAYER_IN_REGION, config, conditionEdges, connectInput);
    }

    private static GraphDefinition targetBlockTypeGraph(Map<String, String> config, List<EdgeDefinition> conditionEdges, boolean connectInput) {
        return conditionGraph("catalog-v2-target-block", NodeType.TARGET_BLOCK_TYPE_CONDITION,
                BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IS_TYPE, config, conditionEdges, connectInput);
    }

    private static GraphDefinition targetBlockRegionGraph(Map<String, String> config, List<EdgeDefinition> conditionEdges, boolean connectInput) {
        return conditionGraph("catalog-v2-target-region", NodeType.TARGET_BLOCK_IN_REGION_CONDITION,
                BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IN_REGION, config, conditionEdges, connectInput);
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
                "catalog-v2",
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

    private static SimulationWorld worldWithRegion(String name, String dimensionId, boolean targetEnabled) {
        return new SimulationWorld(
                "minecraft:overworld",
                new SimulationBlockFact(targetEnabled, dimensionId, 10, 64, 10, "minecraft:stone"),
                List.of(new SimulationRegionFact(name, dimensionId, 0, 60, 0, 10, 70, 10))
        );
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

    private static EdgeDefinition edge(String id, String sourceNode, String sourceSlot, String targetNode, String targetSlot) {
        return new EdgeDefinition(id, sourceNode, sourceSlot, targetNode, targetSlot, EdgeType.CONTROL);
    }

    private static BlockDefinition block(String blockId) {
        return BuiltInBlockCatalog.block(blockId).orElseThrow();
    }

    private static List<String> modeLabels(String blockId) {
        return block(blockId).formSchema().stream()
                .filter(field -> field.key().equals(ConditionOutputMode.CONFIG_KEY))
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
