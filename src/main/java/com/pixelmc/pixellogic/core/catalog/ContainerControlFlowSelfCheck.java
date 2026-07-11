package com.pixelmc.pixellogic.core.catalog;

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
import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.runtime.TriggerEvent;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.state.StateKey;
import com.pixelmc.pixellogic.core.state.StateValue;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;
import com.pixelmc.pixellogic.server.storage.GraphDocument;
import com.pixelmc.pixellogic.selfcheck.SelfCheckSupport;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;

public final class ContainerControlFlowSelfCheck {
    private static final UUID PLAYER_ID = UUID.randomUUID();

    private ContainerControlFlowSelfCheck() {
    }

    public static void main(String[] args) {
        SelfCheckSupport.run("containerControlFlowSelfCheck", () -> {
            BlockDefinition count = BuiltInBlockCatalog.block(BuiltInBlockCatalog.CONTROL_LOOP_COUNT).orElseThrow();
            BlockDefinition forever = BuiltInBlockCatalog.block(BuiltInBlockCatalog.CONTROL_LOOP_FOREVER).orElseThrow();
            require(count.categoryId().equals("control"), "loop count should be in control category");
            require(forever.categoryId().equals("control"), "forever loop should be in control category");
            require(count.containerSlots().equals(List.of("body")), "loop count should expose a body slot");
            require(forever.containerSlots().equals(List.of("body")), "forever loop should expose a body slot");
            require(count.outputSlots().stream().anyMatch(slot -> slot.id().equals("done")), "loop count should have an outer done output");
            require(forever.outputSlots().isEmpty(), "forever loop should not expose a normal outer next output");

            GraphDocument stored = GraphDocument.fromGraphDefinition(loopCountGraph(3), "Loop self-check");
            GraphDefinition loaded = stored.toGraphDefinition();
            NodeDefinition loadedBody = loaded.nodes().stream().filter(node -> node.id().equals("body-add")).findFirst().orElseThrow();
            require(loadedBody.parentContainerId().equals("loop-count") && loadedBody.parentSlot().equals("body"),
                    "body membership should survive save/load");

            List<ValidationIssue> emptyBodyIssues = new GraphValidator().validate(emptyLoopGraph());
            require(emptyBodyIssues.stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.WARNING
                    && issue.code().equals("container_body_empty")), "empty body should be a warning");
            require(hasError(loopCountGraph(101), "config_number_range"), "loop count above catalog max should fail");
            require(hasError(foreverGraph(0), "config_number_range"), "forever interval <= 0 should fail");
            require(hasError(deepContainerGraph(), "container_depth_exceeded"), "too-deep nested containers should fail");

            InMemoryStateStore state = new InMemoryStateStore();
            BoundedTraceBuffer traces = new BoundedTraceBuffer(4, 80);
            GraphRuntime runtime = new GraphRuntime(
                    new GraphCompiler().compile(loopCountGraph(3)),
                    state,
                    traces,
                    services(),
                    new RuntimeLimits(256, 8)
            );
            RuntimeResult countResult = runtime.start(new TriggerEvent("manual", "/pixellogic test start", PLAYER_ID, "self-check"));
            require(countResult.success(), "loop count graph should run");
            require(state.get(StateKey.of(com.pixelmc.pixellogic.core.model.StateScope.PLAYER, PLAYER_ID.toString(), "loop_count"))
                    .map(value -> value.asInteger(0)).orElse(0) == 3, "loop body should run three times");
            require(traces.get(countResult.traceId()).map(trace -> trace.containsMessage("第 3 次循环结束")).orElse(false),
                    "loop count trace should include iteration");

            BoundedTraceBuffer foreverTraces = new BoundedTraceBuffer(2, 160);
            ArrayDeque<com.pixelmc.pixellogic.core.timer.TimerContinuation> foreverTimers = new ArrayDeque<>();
            GraphRuntime foreverRuntime = new GraphRuntime(
                    new GraphCompiler().compile(foreverGraph(1)),
                    new InMemoryStateStore(),
                    foreverTraces,
                    services(foreverTimers),
                    new RuntimeLimits(256, 8)
            );
            RuntimeResult foreverResult = foreverRuntime.start(new TriggerEvent("manual", "/pixellogic test start", PLAYER_ID, "self-check"));
            require(foreverResult.success(), "forever loop simulation should stop safely");
            for (int resume = 0; resume < 32 && !foreverTimers.isEmpty(); resume += 1) {
                foreverRuntime.resumeTimer(foreverTimers.removeFirst());
            }
            require(foreverTimers.isEmpty(), "forever continuation queue should stop within the simulation cap");
            require(foreverTraces.get(foreverResult.traceId()).map(trace -> trace.containsMessage("已达到测试模拟循环上限")).orElse(false),
                    "forever loop should stop at simulation cap");
        });
    }

    private static boolean hasError(GraphDefinition graph, String code) {
        return new GraphValidator().validate(graph).stream()
                .anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR && issue.code().equals(code));
    }

    private static RuntimeServices services() {
        return services(new ArrayDeque<>());
    }

    private static RuntimeServices services(ArrayDeque<com.pixelmc.pixellogic.core.timer.TimerContinuation> timers) {
        return new RuntimeServices() {
            @Override
            public void sendPlayerMessage(UUID playerId, String message) {
            }

            @Override
            public void debug(String message) {
            }

            @Override
            public void scheduleTimer(Duration delay, com.pixelmc.pixellogic.core.timer.TimerContinuation continuation) {
                timers.addLast(continuation);
            }
        };
    }

    private static GraphDefinition loopCountGraph(int count) {
        return graph(
                List.of(
                        manualTrigger(),
                        loopCount("loop-count", "", "", count),
                        stateAdd("body-add", "loop-count", "body"),
                        debug("after-loop", "", "")
                ),
                List.of(
                        edge("e1", "manual-trigger", "started", "loop-count", "input"),
                        edge("e2", "loop-count", "done", "after-loop", "input")
                )
        );
    }

    private static GraphDefinition emptyLoopGraph() {
        return graph(
                List.of(manualTrigger(), loopCount("loop-count", "", "", 3)),
                List.of(edge("e1", "manual-trigger", "started", "loop-count", "input"))
        );
    }

    private static GraphDefinition foreverGraph(int intervalSeconds) {
        return graph(
                List.of(
                        manualTrigger(),
                        forever("forever", "", "", intervalSeconds),
                        debug("body-debug", "forever", "body")
                ),
                List.of(edge("e1", "manual-trigger", "started", "forever", "input"))
        );
    }

    private static GraphDefinition deepContainerGraph() {
        return graph(
                List.of(
                        manualTrigger(),
                        loopCount("loop-1", "", "", 1),
                        loopCount("loop-2", "loop-1", "body", 1),
                        loopCount("loop-3", "loop-2", "body", 1),
                        loopCount("loop-4", "loop-3", "body", 1),
                        loopCount("loop-5", "loop-4", "body", 1),
                        debug("too-deep", "loop-5", "body")
                ),
                List.of(edge("e1", "manual-trigger", "started", "loop-1", "input"))
        );
    }

    private static GraphDefinition graph(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        return new GraphDefinition("container-self-check", nodes, edges, Map.of("manual", "manual-trigger"));
    }

    private static NodeDefinition manualTrigger() {
        return new NodeDefinition(
                "manual-trigger",
                NodeType.MANUAL_TRIGGER,
                BuiltInBlockCatalog.TRIGGER_MANUAL_TEST,
                "",
                "",
                List.of(new SlotDefinition("started", SlotDirection.OUTPUT, EdgeType.CONTROL)),
                Map.of()
        );
    }

    private static NodeDefinition loopCount(String id, String parentId, String parentSlot, int count) {
        return new NodeDefinition(
                id,
                NodeType.CONTROL_LOOP_COUNT,
                BuiltInBlockCatalog.CONTROL_LOOP_COUNT,
                parentId,
                parentSlot,
                List.of(
                        new SlotDefinition("input", SlotDirection.INPUT, EdgeType.CONTROL),
                        new SlotDefinition("done", SlotDirection.OUTPUT, EdgeType.CONTROL)
                ),
                Map.of("count", Integer.toString(count))
        );
    }

    private static NodeDefinition forever(String id, String parentId, String parentSlot, int intervalSeconds) {
        return new NodeDefinition(
                id,
                NodeType.CONTROL_LOOP_FOREVER,
                BuiltInBlockCatalog.CONTROL_LOOP_FOREVER,
                parentId,
                parentSlot,
                List.of(new SlotDefinition("input", SlotDirection.INPUT, EdgeType.CONTROL)),
                Map.of("intervalSeconds", Integer.toString(intervalSeconds))
        );
    }

    private static NodeDefinition stateAdd(String id, String parentId, String parentSlot) {
        return new NodeDefinition(
                id,
                NodeType.STATE_ADD_ACTION,
                BuiltInBlockCatalog.STATE_ADD,
                parentId,
                parentSlot,
                List.of(
                        new SlotDefinition("input", SlotDirection.INPUT, EdgeType.CONTROL),
                        new SlotDefinition("done", SlotDirection.OUTPUT, EdgeType.CONTROL)
                ),
                Map.of("scope", "PLAYER", "key", "loop_count", "valueType", "INTEGER", "amount", "1")
        );
    }

    private static NodeDefinition debug(String id, String parentId, String parentSlot) {
        return new NodeDefinition(
                id,
                NodeType.DEBUG_LOG_ACTION,
                BuiltInBlockCatalog.DEBUG_LOG,
                parentId,
                parentSlot,
                List.of(
                        new SlotDefinition("input", SlotDirection.INPUT, EdgeType.CONTROL),
                        new SlotDefinition("done", SlotDirection.OUTPUT, EdgeType.CONTROL)
                ),
                Map.of("message", id)
        );
    }

    private static EdgeDefinition edge(String id, String sourceNodeId, String sourceSlotId, String targetNodeId, String targetSlotId) {
        return new EdgeDefinition(id, sourceNodeId, sourceSlotId, targetNodeId, targetSlotId, EdgeType.CONTROL);
    }
}
