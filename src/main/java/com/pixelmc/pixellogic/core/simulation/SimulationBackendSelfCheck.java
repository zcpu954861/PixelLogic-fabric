package com.pixelmc.pixellogic.core.simulation;

import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.graph.DemoGraphFactory;
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
import com.pixelmc.pixellogic.core.simulation.context.SimulationWorld;
import com.pixelmc.pixellogic.core.simulation.event.SimulationEvent;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationExecutionRegistry;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionRequest;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionResult;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationRunOptions;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationRunner;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class SimulationBackendSelfCheck {
    private SimulationBackendSelfCheck() {
    }

    public static void main(String[] args) {
        run("simulationBackendSelfCheck", () -> {
        UUID playerId = UUID.randomUUID();
        RunHarness demo = harness(DemoGraphFactory.create(Duration.ofSeconds(1)));
        SimulationExecutionResult demoResult = demo.runner().run(SimulationExecutionRequest.manual(
                DemoGraphFactory.create(Duration.ofSeconds(1)).id(),
                DemoGraphFactory.TRIGGER_TYPE,
                "/pixellogic test start",
                playerId,
                "模拟玩家",
                "self-check",
                0L
        ));
        require(demoResult.success(), "SimulationRunner should run committed demo graph");
        require(demo.messages().contains("欢迎开始游戏"), "manual path should still send message");
        require(demo.debug().contains("倒计时结束"), "timer continuation should still run through GraphRuntime");
        require(demoResult.messageResults().stream().anyMatch(result -> result.message().equals("欢迎开始游戏")),
                "SimulationExecutionResult should collect message results");
        require(demoResult.timerScheduled(), "SimulationExecutionResult should report timer scheduling");

        GraphDefinition tagGraph = playerTagGraph();
        List<ValidationIssue> issues = new GraphValidator().validate(tagGraph);
        require(!new GraphValidator().hasErrors(issues), "player tag graph should validate: " + issues);

        RunHarness tagHarness = harness(tagGraph);
        SimulationActor taggedActor = new SimulationActor(playerId, "带标签玩家", true, false, Set.of("runner"));
        SimulationExecutionResult tagged = tagHarness.runner().run(new SimulationExecutionRequest(
                tagGraph.id(),
                SimulationEvent.manual(DemoGraphFactory.TRIGGER_TYPE, "/pixellogic test start", "tag-check"),
                taggedActor,
                SimulationWorld.overworld(),
                SimulationRunOptions.realTime(),
                0L
        ));
        require(tagged.success(), "actor with tag should pass condition");
        require(tagHarness.traces().get(tagged.traceId()).orElseThrow().containsMessage("玩家 带标签玩家 拥有标签「runner」"),
                "trace should include player tag pass branch");

        RunHarness addTagHarness = harness(tagGraph);
        SimulationActor plainActor = SimulationActor.player(playerId, "无标签玩家");
        SimulationExecutionResult added = addTagHarness.runner().run(new SimulationExecutionRequest(
                tagGraph.id(),
                SimulationEvent.manual(DemoGraphFactory.TRIGGER_TYPE, "/pixellogic test start", "tag-add"),
                plainActor,
                SimulationWorld.overworld(),
                SimulationRunOptions.realTime(),
                0L
        ));
        require(added.success(), "actor without tag should run fail branch and add tag");
        require(added.actorTags().contains("runner"), "action.player.add_tag should mutate simulated actor tags");
        require(added.actionResults().stream().anyMatch(result -> result.kind().equals("player_tag")),
                "player tag action should produce action result");
        require(added.stateChanges().stream().anyMatch(result -> result.target().equals("actor.tags") && result.value().contains("runner")),
                "player tag action should produce state change result");
        require(addTagHarness.traces().get(added.traceId()).orElseThrow().containsMessage("玩家标签写入"),
                "trace should include player tag action");
        });
    }

    private static RunHarness harness(GraphDefinition graph) {
        CompiledGraph compiled = new GraphCompiler().compile(graph);
        InMemoryStateStore state = new InMemoryStateStore();
        BoundedTraceBuffer traces = new BoundedTraceBuffer(4, 40);
        List<String> messages = new ArrayList<>();
        List<String> debug = new ArrayList<>();
        AtomicReference<GraphRuntime> runtimeRef = new AtomicReference<>();
        RuntimeServices delegate = new RuntimeServices() {
            @Override
            public void sendPlayerMessage(UUID playerId, String message) {
                messages.add(message);
            }

            @Override
            public void debug(String message) {
                debug.add(message);
            }

            @Override
            public void scheduleTimer(Duration delay, TimerContinuation continuation) {
                runtimeRef.get().resumeTimer(continuation);
            }
        };
        SimulationRunner runner = new SimulationRunner(services -> {
            GraphRuntime runtime = new GraphRuntime(compiled, state, traces, services, RuntimeLimits.spikeDefaults());
            runtimeRef.set(runtime);
            return runtime;
        }, delegate, SimulationExecutionRegistry.playerTags());
        return new RunHarness(runner, traces, messages, debug);
    }

    private static GraphDefinition playerTagGraph() {
        return new GraphDefinition(
                "player-tag-self-check",
                List.of(
                        node("manual-trigger", NodeType.MANUAL_TRIGGER, out("started"), Map.of()),
                        node("has-runner-tag", NodeType.PLAYER_HAS_TAG_CONDITION, in("input"), out("pass"), out("fail"), Map.of("tag", "runner")),
                        node("add-runner-tag", NodeType.PLAYER_ADD_TAG_ACTION, in("input"), out("done"), Map.of("tag", "runner")),
                        node("debug-has-tag", NodeType.DEBUG_LOG_ACTION, in("input"), out("done"), Map.of("message", "已有 runner 标签")),
                        node("debug-added-tag", NodeType.DEBUG_LOG_ACTION, in("input"), out("done"), Map.of("message", "已添加 runner 标签"))
                ),
                List.of(
                        edge("e1", "manual-trigger", "started", "has-runner-tag", "input"),
                        edge("e2", "has-runner-tag", "pass", "debug-has-tag", "input"),
                        edge("e3", "has-runner-tag", "fail", "add-runner-tag", "input"),
                        edge("e4", "add-runner-tag", "done", "debug-added-tag", "input")
                ),
                Map.of(DemoGraphFactory.TRIGGER_TYPE, "manual-trigger")
        );
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

    private record RunHarness(
            SimulationRunner runner,
            BoundedTraceBuffer traces,
            List<String> messages,
            List<String> debug
    ) {
    }
}
