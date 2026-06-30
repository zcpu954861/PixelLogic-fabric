package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.catalog.BlockDefinition;
import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.graph.DemoGraphFactory;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class ConditionOutputModeSelfCheck {
    private ConditionOutputModeSelfCheck() {
    }

    public static void main(String[] args) {
        run("conditionOutputModeSelfCheck", () -> {
        BlockDefinition stateCondition = BuiltInBlockCatalog.block(BuiltInBlockCatalog.CONDITION_STATE_EQUALS).orElseThrow();
        BlockDefinition hasTag = BuiltInBlockCatalog.block(BuiltInBlockCatalog.CONDITION_PLAYER_HAS_TAG).orElseThrow();
        BlockDefinition addTag = BuiltInBlockCatalog.block(BuiltInBlockCatalog.ACTION_PLAYER_ADD_TAG).orElseThrow();

        require(stateCondition.defaultConfig().get(ConditionOutputMode.CONFIG_KEY).equals(ConditionOutputMode.PASS_ONLY.name()),
                "new state conditions should default to PASS_ONLY");
        require(hasTag.categoryId().equals("condition") && hasTag.subcategoryId().equals("condition.player"),
                "player tag condition should live under condition/player condition catalog");
        require(addTag.categoryId().equals("player") && addTag.subcategoryId().equals("player.tag"),
                "player add tag action should remain under player/tag catalog");
        require(DemoGraphFactory.create(Duration.ofSeconds(1)).nodes().stream()
                        .filter(node -> node.id().equals("condition-started"))
                        .findFirst()
                        .orElseThrow()
                        .config()
                        .get(ConditionOutputMode.CONFIG_KEY)
                        .equals(ConditionOutputMode.BRANCH.name()),
                "demo graph condition should remain BRANCH");

        GraphValidator validator = new GraphValidator();
        require(!validator.hasErrors(validator.validate(conditionGraph(Map.of(), List.of(edge("e2", "condition", "pass", "debug-pass", "input"))))),
                "old condition graph without outputMode should validate as BRANCH");
        require(!validator.hasErrors(validator.validate(conditionGraph(Map.of("outputMode", "PASS_ONLY"), List.of()))),
                "PASS_ONLY with no connected output should validate");
        require(!validator.hasErrors(validator.validate(disconnectedConditionGraph(Map.of("outputMode", "PASS_ONLY"), List.of()))),
                "condition with no connected input should validate like other placed blocks");
        require(!validator.hasErrors(validator.validate(conditionGraph(Map.of("outputMode", "BRANCH"), List.of(edge("e2", "condition", "pass", "debug-pass", "input"))))),
                "BRANCH with one unconnected side should validate");
        require(validator.hasErrors(validator.validate(conditionGraph(Map.of("outputMode", "BOGUS"), List.of()))),
                "unknown outputMode should fail validation");

        RunResult passOnlyFalse = runGraph(conditionGraph(Map.of("outputMode", "PASS_ONLY", "expected", "true"), List.of()));
        require(passOnlyFalse.success(), "PASS_ONLY false should end gracefully");
        require(passOnlyFalse.trace().containsMessage("条件不满足，流程在此结束。"), "PASS_ONLY false should trace graceful end");

        RunResult failOnlyTrue = runGraph(conditionGraph(Map.of("outputMode", "FAIL_ONLY"), List.of()));
        require(failOnlyTrue.success(), "FAIL_ONLY true should end gracefully");
        require(failOnlyTrue.trace().containsMessage("条件满足，流程在此结束。"), "FAIL_ONLY true should trace graceful end");

        RunResult branchMissingEdge = runGraph(conditionGraph(Map.of("outputMode", "BRANCH"), List.of(edge("e2", "condition", "fail", "debug-fail", "input"))));
        require(branchMissingEdge.success(), "BRANCH selected unconnected output should end gracefully");
        require(branchMissingEdge.trace().containsMessage("未连接后续积木，流程在此结束。"), "missing selected output should trace end");
        });
    }

    private static RunResult runGraph(GraphDefinition graph) {
        CompiledGraph compiled = new GraphCompiler().compile(graph);
        BoundedTraceBuffer traces = new BoundedTraceBuffer(2, 20);
        GraphRuntime runtime = new GraphRuntime(compiled, new InMemoryStateStore(), traces, new RuntimeServices() {
            @Override
            public void sendPlayerMessage(UUID playerId, String message) {
            }

            @Override
            public void debug(String message) {
            }

            @Override
            public void scheduleTimer(Duration delay, com.pixelmc.pixellogic.core.timer.TimerContinuation continuation) {
            }
        }, RuntimeLimits.spikeDefaults());
        RuntimeResult result = runtime.start(new TriggerEvent(DemoGraphFactory.TRIGGER_TYPE, "/pixellogic test start", UUID.randomUUID(), "self-check"));
        return new RunResult(result.success(), traces.get(result.traceId()).orElseThrow());
    }

    private static GraphDefinition conditionGraph(Map<String, String> configPatch, List<EdgeDefinition> conditionEdges) {
        return conditionGraph(configPatch, conditionEdges, true);
    }

    private static GraphDefinition disconnectedConditionGraph(Map<String, String> configPatch, List<EdgeDefinition> conditionEdges) {
        return conditionGraph(configPatch, conditionEdges, false);
    }

    private static GraphDefinition conditionGraph(Map<String, String> configPatch, List<EdgeDefinition> conditionEdges, boolean connectConditionInput) {
        Map<String, String> config = new java.util.HashMap<>(Map.of(
                "scope", "PLAYER",
                "key", "started",
                "valueType", "BOOLEAN",
                "expected", "false",
                "missing", "false"
        ));
        config.putAll(configPatch);
        return new GraphDefinition(
                "condition-output-mode-self-check",
                List.of(
                        node("manual-trigger", NodeType.MANUAL_TRIGGER, out("started"), Map.of()),
                        node("condition", NodeType.STATE_COMPARE_CONDITION, in("input"), out("pass"), out("fail"), Map.copyOf(config)),
                        node("debug-pass", NodeType.DEBUG_LOG_ACTION, in("input"), out("done"), Map.of("message", "pass")),
                        node("debug-fail", NodeType.DEBUG_LOG_ACTION, in("input"), out("done"), Map.of("message", "fail"))
                ),
                join(connectConditionInput ? List.of(edge("e1", "manual-trigger", "started", "condition", "input")) : List.of(), conditionEdges),
                Map.of(DemoGraphFactory.TRIGGER_TYPE, "manual-trigger")
        );
    }

    private static List<EdgeDefinition> join(List<EdgeDefinition> first, List<EdgeDefinition> second) {
        java.util.ArrayList<EdgeDefinition> result = new java.util.ArrayList<>(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    private static NodeDefinition node(String id, NodeType type, SlotDefinition slot, Map<String, String> config) {
        return new NodeDefinition(id, type, List.of(slot), config);
    }

    private static NodeDefinition node(String id, NodeType type, SlotDefinition input, SlotDefinition output, Map<String, String> config) {
        return new NodeDefinition(id, type, List.of(input, output), config);
    }

    private static NodeDefinition node(
            String id,
            NodeType type,
            SlotDefinition input,
            SlotDefinition outputA,
            SlotDefinition outputB,
            Map<String, String> config
    ) {
        return new NodeDefinition(id, type, List.of(input, outputA, outputB), config);
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

    private record RunResult(boolean success, com.pixelmc.pixellogic.core.trace.ExecutionTrace trace) {
    }
}
