package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;
import com.pixelmc.pixellogic.core.trace.ExecutionTrace;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class ControlFlowContinuationSelfCheck {
    private static final UUID PLAYER_ID = UUID.randomUUID();

    private ControlFlowContinuationSelfCheck() {
    }

    public static void main(String[] args) {
        run("controlFlowContinuationSelfCheck", () -> {
            countLoopResumesAfterDelay();
            delayAtBodyStartAndEnd();
            multipleDelaysResumeExactlyOnce();
            foreverLoopKeepsCapsAndInterval();
            nestedLoopsRestoreFrameStack();
            nestedTailDelayRestoresFrameStack();
            nestedLoopsWithOuterAndInnerDelay();
            cancellationGenerationAndConsumptionAreSafe();
            pendingContinuationCapFailsClosed();
            stepCapSurvivesResume();
            ordinaryTimerAndEmptyBodiesDoNotRegress();
        });
    }

    private static void countLoopResumesAfterDelay() {
        Scenario scenario = scenario(countGraph("count-basic", 3, List.of("A", "delay", "B")), new RuntimeLimits(64, 8), 1L);
        RuntimeResult initial = scenario.start();
        require(initial.success() && initial.suspended(), "count loop should suspend on body delay");
        RuntimeResult completed = scenario.drain();
        require(completed.success() && !completed.suspended(), "count loop should finish after all resumptions");
        require(scenario.services.events.equals(List.of(
                "debug:A", "schedule:DELAY:delay", "resume:DELAY:delay", "debug:B",
                "debug:A", "schedule:DELAY:delay", "resume:DELAY:delay", "debug:B",
                "debug:A", "schedule:DELAY:delay", "resume:DELAY:delay", "debug:B",
                "debug:done"
        )), "count loop order should remain A-delay-B for every iteration: " + scenario.services.events);
        require(count(scenario.services.events, "schedule:DELAY:delay") == 3, "delay should schedule exactly three times");
        ExecutionTrace trace = scenario.trace(initial.traceId());
        require(trace.containsMessage("等待已调度") && trace.containsMessage("计时器完成")
                        && trace.containsMessage("第 3 次循环结束") && trace.containsMessage("循环完成"),
                "count trace should distinguish suspend, resume, iteration complete and loop complete");
    }

    private static void delayAtBodyStartAndEnd() {
        Scenario atStart = scenario(countGraph("delay-start", 2, List.of("delay", "A")), new RuntimeLimits(32, 8), 1L);
        atStart.start();
        atStart.drain();
        require(atStart.services.events.equals(List.of(
                "schedule:DELAY:delay", "resume:DELAY:delay", "debug:A",
                "schedule:DELAY:delay", "resume:DELAY:delay", "debug:A",
                "debug:done"
        )), "delay at body start should resume before A");

        Scenario atEnd = scenario(countGraph("delay-end", 2, List.of("A", "delay")), new RuntimeLimits(32, 8), 1L);
        atEnd.start();
        atEnd.drain();
        require(atEnd.services.events.equals(List.of(
                "debug:A", "schedule:DELAY:delay", "resume:DELAY:delay",
                "debug:A", "schedule:DELAY:delay", "resume:DELAY:delay",
                "debug:done"
        )), "delay at body end should complete the current iteration after resume");
    }

    private static void multipleDelaysResumeExactlyOnce() {
        Scenario scenario = scenario(
                countGraph("multi-delay", 2, List.of("A", "delay1", "B", "delay2", "C")),
                new RuntimeLimits(64, 8),
                1L
        );
        scenario.start();
        scenario.drain();
        require(scenario.services.debug.equals(List.of("A", "B", "C", "A", "B", "C", "done")),
                "multiple delays must resume after the correct node: " + scenario.services.debug);
        require(count(scenario.services.events, "schedule:DELAY:delay1") == 2
                        && count(scenario.services.events, "schedule:DELAY:delay2") == 2,
                "each delay should schedule once per iteration");
    }

    private static void foreverLoopKeepsCapsAndInterval() {
        Scenario scenario = scenario(foreverGraph(), RuntimeLimits.spikeDefaults(), 1L);
        RuntimeResult initial = scenario.start();
        require(initial.success() && initial.suspended(), "forever body delay should suspend the run");
        RuntimeResult completed = scenario.drain();
        require(completed.success(), "forever simulation should stop successfully at its cap");
        require(count(scenario.services.debug, "A") == 20 && count(scenario.services.debug, "B") == 20,
                "forever loop should execute exactly twenty complete iterations across resumes");
        require(scenario.services.scheduled(TimerContinuation.Reason.DELAY) == 20,
                "forever body delay should schedule once per iteration");
        require(scenario.services.scheduled(TimerContinuation.Reason.LOOP_INTERVAL) == 19,
                "forever interval should occur between iterations without replacing body delays");
        require(scenario.trace(initial.traceId()).containsMessage("已达到测试模拟循环上限"),
                "forever cap trace should survive timer resumptions");
    }

    private static void nestedLoopsRestoreFrameStack() {
        Scenario scenario = scenario(nestedGraph(false), new RuntimeLimits(128, 8), 1L);
        scenario.start();
        scenario.drain();
        require(scenario.services.debug.equals(List.of(
                "A", "B", "C", "B", "C", "D",
                "A", "B", "C", "B", "C", "D",
                "done"
        )), "nested delay should return through inner and outer frames in order: " + scenario.services.debug);
        require(scenario.services.scheduled(TimerContinuation.Reason.DELAY) == 4,
                "inner delay should schedule once per inner iteration");
    }

    private static void nestedLoopsWithOuterAndInnerDelay() {
        Scenario scenario = scenario(nestedGraph(true), new RuntimeLimits(160, 8), 1L);
        scenario.start();
        scenario.drain();
        require(scenario.services.debug.equals(List.of(
                "A", "B", "C", "B", "C", "D",
                "A", "B", "C", "B", "C", "D",
                "done"
        )), "outer and inner delays must preserve both loop frames");
        require(count(scenario.services.events, "schedule:DELAY:outer-delay") == 2,
                "outer delay should schedule once per outer iteration");
        require(count(scenario.services.events, "schedule:DELAY:inner-delay") == 4,
                "inner delay should schedule once per inner iteration");
    }

    private static void nestedTailDelayRestoresFrameStack() {
        Scenario scenario = scenario(nestedTailDelayGraph(), new RuntimeLimits(96, 8), 1L);
        RuntimeResult initial = scenario.start();
        require(initial.success() && initial.suspended(), "nested tail delay should suspend");
        RuntimeResult completed = scenario.drain();
        require(completed.success() && !completed.suspended(), "nested tail delay should complete all frames");
        require(count(scenario.services.events, "schedule:DELAY:inner-delay") == 4,
                "nested tail delay should run once per inner iteration");
        require(scenario.services.debug.equals(List.of("D", "D", "done")),
                "nested tail delay should unwind inner and outer frames in order");
    }

    private static void cancellationGenerationAndConsumptionAreSafe() {
        GraphDefinition graph = ordinaryTimerGraph();
        Scenario cancelled = scenario(graph, new RuntimeLimits(32, 8), 7L);
        RuntimeResult cancelledStart = cancelled.start();
        cancelled.runtime.cancelPendingContinuations();
        RuntimeResult cancelledResume = cancelled.services.runNext();
        require(!cancelledResume.success() && !cancelled.services.debug.contains("B"),
                "cancelled run must not resume after delay");
        require(cancelled.trace(cancelledStart.traceId()).containsMessage("等待恢复已忽略"),
                "cancelled continuation should leave a concise trace");

        Scenario stale = scenario(graph, new RuntimeLimits(32, 8), 11L);
        RuntimeResult staleStart = stale.start();
        TimerContinuation old = stale.services.takeNext();
        GraphRuntime newer = runtime(graph, stale.services, stale.traces, new RuntimeLimits(32, 8), 12L);
        require(!newer.resumeTimer(old).success() && !stale.services.debug.contains("B"),
                "generation change must invalidate the old continuation");
        require(stale.trace(staleStart.traceId()).containsMessage("generation 已变化"),
                "stale generation should be traced");
        stale.runtime.cancelPendingContinuations();

        Scenario once = scenario(graph, new RuntimeLimits(32, 8), 1L);
        once.start();
        TimerContinuation token = once.services.takeNext();
        RuntimeResult firstResume = once.runtime.resumeTimer(token);
        RuntimeResult duplicateResume = once.runtime.resumeTimer(token);
        require(firstResume.success() && !duplicateResume.success() && count(once.services.debug, "B") == 1,
                "the same continuation must be consumed exactly once");
    }

    private static void pendingContinuationCapFailsClosed() {
        GraphDefinition graph = ordinaryTimerGraph();
        QueueServices services = new QueueServices();
        BoundedTraceBuffer traces = new BoundedTraceBuffer(2, 20);
        GraphRuntime runtime = runtime(graph, services, traces, new RuntimeLimits(32, 8), 1L);
        services.runtime = runtime;
        RuntimeResult result = null;
        for (int index = 0; index <= 128; index += 1) {
            result = runtime.start(event());
        }
        require(result != null && !result.success(), "the 129th pending continuation should fail closed");
        require(runtime.pendingContinuations() == 128 && services.timers.size() == 128,
                "continuation cap must not leak an extra pending timer");
        runtime.cancelPendingContinuations();
    }

    private static void stepCapSurvivesResume() {
        Scenario scenario = scenario(countGraph("step-cap", 10, List.of("A", "delay", "B")), new RuntimeLimits(8, 8), 1L);
        scenario.start();
        RuntimeResult result = scenario.drain();
        require(!result.success() && scenario.trace(result.traceId()).containsMessage("超过最大执行步数"),
                "step cap must accumulate across timer resumptions");
    }

    private static void ordinaryTimerAndEmptyBodiesDoNotRegress() {
        GraphDefinition disconnectedTimer = graph(
                "disconnected-delay",
                List.of(trigger(), delay("delay", "")),
                List.of()
        );
        require(!new GraphValidator().hasErrors(new GraphValidator().validate(disconnectedTimer)),
                "a disconnected timer should remain a valid unreachable node");

        Scenario terminalTimer = scenario(
                graph(
                        "terminal-delay",
                        List.of(trigger(), delay("delay", "")),
                        List.of(edge("trigger", "started", "delay"))
                ),
                new RuntimeLimits(32, 8),
                1L
        );
        RuntimeResult terminalStart = terminalTimer.start();
        require(terminalStart.success() && terminalStart.suspended(), "top-level terminal timer should suspend");
        RuntimeResult terminalComplete = terminalTimer.drain();
        require(terminalComplete.success() && !terminalComplete.suspended(),
                "top-level terminal timer should naturally complete after resume");

        Scenario ordinary = scenario(ordinaryTimerGraph(), new RuntimeLimits(32, 8), 1L);
        ordinary.start();
        ordinary.drain();
        require(ordinary.services.debug.equals(List.of("A", "B")), "ordinary timer chain should still resume B");

        Scenario emptyCount = scenario(emptyCountGraph(), new RuntimeLimits(32, 8), 1L);
        RuntimeResult countResult = emptyCount.start();
        require(countResult.success() && !countResult.suspended() && emptyCount.services.debug.equals(List.of("done")),
                "empty count body should continue to done without scheduling");

        Scenario emptyForever = scenario(emptyForeverGraph(), new RuntimeLimits(32, 8), 1L);
        RuntimeResult foreverResult = emptyForever.start();
        require(foreverResult.success() && !foreverResult.suspended() && emptyForever.services.timers.isEmpty(),
                "empty forever body should stop simulation without scheduling");
    }

    private static Scenario scenario(GraphDefinition graph, RuntimeLimits limits, long generation) {
        List<com.pixelmc.pixellogic.core.graph.ValidationIssue> issues = new GraphValidator().validate(graph);
        require(issues.stream().noneMatch(issue -> issue.severity() == com.pixelmc.pixellogic.core.graph.ValidationIssue.Severity.ERROR),
                "scenario graph should validate: " + issues);
        QueueServices services = new QueueServices();
        BoundedTraceBuffer traces = new BoundedTraceBuffer(256, 512);
        GraphRuntime runtime = runtime(graph, services, traces, limits, generation);
        services.runtime = runtime;
        return new Scenario(runtime, services, traces);
    }

    private static GraphRuntime runtime(
            GraphDefinition graph,
            QueueServices services,
            BoundedTraceBuffer traces,
            RuntimeLimits limits,
            long generation
    ) {
        CompiledGraph compiled = new GraphCompiler().compile(graph);
        return new GraphRuntime(compiled, new InMemoryStateStore(), traces, services, limits, generation);
    }

    private static GraphDefinition countGraph(String id, int count, List<String> body) {
        List<NodeDefinition> nodes = new ArrayList<>();
        List<EdgeDefinition> edges = new ArrayList<>();
        nodes.add(trigger());
        nodes.add(loopCount("loop", "", count));
        nodes.add(debug("done", "", "done"));
        edges.add(edge("trigger", "started", "loop"));
        edges.add(edge("loop", "done", "done"));
        appendBody(nodes, edges, "loop", body);
        return graph(id, nodes, edges);
    }

    private static GraphDefinition foreverGraph() {
        List<NodeDefinition> nodes = new ArrayList<>(List.of(trigger(), loopForever("forever", "", 1)));
        List<EdgeDefinition> edges = new ArrayList<>(List.of(edge("trigger", "started", "forever")));
        appendBody(nodes, edges, "forever", List.of("A", "delay", "B"));
        return graph("forever-delay", nodes, edges);
    }

    private static GraphDefinition nestedGraph(boolean outerDelay) {
        List<NodeDefinition> nodes = new ArrayList<>(List.of(
                trigger(),
                loopCount("outer", "", 2),
                debug("A", "outer", "A"),
                loopCount("inner", "outer", 2),
                debug("B", "inner", "B"),
                delay("inner-delay", "inner"),
                debug("C", "inner", "C"),
                debug("D", "outer", "D"),
                debug("done", "", "done")
        ));
        List<EdgeDefinition> edges = new ArrayList<>(List.of(
                edge("trigger", "started", "outer"),
                edge("outer", "done", "done"),
                edge("inner", "done", "D"),
                edge("B", "done", "inner-delay"),
                edge("inner-delay", "timer_completed", "C")
        ));
        if (outerDelay) {
            nodes.add(delay("outer-delay", "outer"));
            edges.add(edge("A", "done", "outer-delay"));
            edges.add(edge("outer-delay", "timer_completed", "inner"));
        } else {
            edges.add(edge("A", "done", "inner"));
        }
        return graph(outerDelay ? "nested-both-delay" : "nested-delay", nodes, edges);
    }

    private static GraphDefinition nestedTailDelayGraph() {
        return graph(
                "nested-tail-delay",
                List.of(
                        trigger(),
                        loopCount("outer", "", 2),
                        loopCount("inner", "outer", 2),
                        delay("inner-delay", "inner"),
                        debug("D", "outer", "D"),
                        debug("done", "", "done")
                ),
                List.of(
                        edge("trigger", "started", "outer"),
                        edge("outer", "done", "done"),
                        edge("inner", "done", "D")
                )
        );
    }

    private static GraphDefinition ordinaryTimerGraph() {
        return graph(
                "ordinary-delay",
                List.of(trigger(), debug("A", "", "A"), delay("delay", ""), debug("B", "", "B")),
                List.of(
                        edge("trigger", "started", "A"),
                        edge("A", "done", "delay"),
                        edge("delay", "timer_completed", "B")
                )
        );
    }

    private static GraphDefinition emptyCountGraph() {
        return graph(
                "empty-count",
                List.of(trigger(), loopCount("loop", "", 3), debug("done", "", "done")),
                List.of(edge("trigger", "started", "loop"), edge("loop", "done", "done"))
        );
    }

    private static GraphDefinition emptyForeverGraph() {
        return graph(
                "empty-forever",
                List.of(trigger(), loopForever("forever", "", 1)),
                List.of(edge("trigger", "started", "forever"))
        );
    }

    private static void appendBody(
            List<NodeDefinition> nodes,
            List<EdgeDefinition> edges,
            String containerId,
            List<String> body
    ) {
        NodeDefinition previous = null;
        for (String id : body) {
            NodeDefinition node = id.startsWith("delay") ? delay(id, containerId) : debug(id, containerId, id);
            nodes.add(node);
            if (previous != null) {
                String slot = previous.type() == NodeType.TIMER_START_ACTION ? "timer_completed" : "done";
                edges.add(edge(previous.id(), slot, id));
            }
            previous = node;
        }
    }

    private static GraphDefinition graph(String id, List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        return new GraphDefinition(id, List.copyOf(nodes), List.copyOf(edges), Map.of("manual", "trigger"));
    }

    private static NodeDefinition trigger() {
        return new NodeDefinition(
                "trigger",
                NodeType.MANUAL_TRIGGER,
                BuiltInBlockCatalog.TRIGGER_MANUAL_TEST,
                "",
                "",
                List.of(new SlotDefinition("started", SlotDirection.OUTPUT, EdgeType.CONTROL)),
                Map.of()
        );
    }

    private static NodeDefinition loopCount(String id, String parentId, int count) {
        return new NodeDefinition(
                id,
                NodeType.CONTROL_LOOP_COUNT,
                BuiltInBlockCatalog.CONTROL_LOOP_COUNT,
                parentId,
                parentId.isBlank() ? "" : "body",
                List.of(
                        new SlotDefinition("input", SlotDirection.INPUT, EdgeType.CONTROL),
                        new SlotDefinition("done", SlotDirection.OUTPUT, EdgeType.CONTROL)
                ),
                Map.of("count", Integer.toString(count))
        );
    }

    private static NodeDefinition loopForever(String id, String parentId, int intervalSeconds) {
        return new NodeDefinition(
                id,
                NodeType.CONTROL_LOOP_FOREVER,
                BuiltInBlockCatalog.CONTROL_LOOP_FOREVER,
                parentId,
                parentId.isBlank() ? "" : "body",
                List.of(new SlotDefinition("input", SlotDirection.INPUT, EdgeType.CONTROL)),
                Map.of("intervalSeconds", Integer.toString(intervalSeconds))
        );
    }

    private static NodeDefinition debug(String id, String parentId, String message) {
        return new NodeDefinition(
                id,
                NodeType.DEBUG_LOG_ACTION,
                BuiltInBlockCatalog.DEBUG_LOG,
                parentId,
                parentId.isBlank() ? "" : "body",
                List.of(
                        new SlotDefinition("input", SlotDirection.INPUT, EdgeType.CONTROL),
                        new SlotDefinition("done", SlotDirection.OUTPUT, EdgeType.CONTROL)
                ),
                Map.of("message", message)
        );
    }

    private static NodeDefinition delay(String id, String parentId) {
        return new NodeDefinition(
                id,
                NodeType.TIMER_START_ACTION,
                BuiltInBlockCatalog.TIMER_WAIT,
                parentId,
                parentId.isBlank() ? "" : "body",
                List.of(
                        new SlotDefinition("input", SlotDirection.INPUT, EdgeType.CONTROL),
                        new SlotDefinition("timer_completed", SlotDirection.OUTPUT, EdgeType.CONTROL)
                ),
                Map.of("durationSeconds", "1")
        );
    }

    private static EdgeDefinition edge(String sourceNodeId, String sourceSlotId, String targetNodeId) {
        return new EdgeDefinition(
                sourceNodeId + "-" + sourceSlotId + "-" + targetNodeId,
                sourceNodeId,
                sourceSlotId,
                targetNodeId,
                "input",
                EdgeType.CONTROL
        );
    }

    private static TriggerEvent event() {
        return new TriggerEvent("manual", "/pixellogic test start", PLAYER_ID, "continuation-self-check");
    }

    private static int count(List<String> values, String expected) {
        return (int) values.stream().filter(expected::equals).count();
    }

    private record Scenario(GraphRuntime runtime, QueueServices services, BoundedTraceBuffer traces) {
        private RuntimeResult start() {
            return runtime.start(event());
        }

        private RuntimeResult drain() {
            RuntimeResult result = null;
            for (int index = 0; index < 256 && !services.timers.isEmpty(); index += 1) {
                result = services.runNext();
            }
            require(services.timers.isEmpty(), "continuation queue should drain within its safety bound");
            return result == null ? new RuntimeResult(true, "", "执行完成。") : result;
        }

        private ExecutionTrace trace(String traceId) {
            return traces.get(traceId).orElseThrow();
        }
    }

    private static final class QueueServices implements RuntimeServices {
        private final ArrayDeque<TimerContinuation> timers = new ArrayDeque<>();
        private final List<String> events = new ArrayList<>();
        private final List<String> debug = new ArrayList<>();
        private GraphRuntime runtime;

        @Override
        public void sendPlayerMessage(UUID playerId, String message) {
        }

        @Override
        public void debug(String message) {
            debug.add(message);
            events.add("debug:" + message);
        }

        @Override
        public void scheduleTimer(Duration delay, TimerContinuation continuation) {
            events.add("schedule:" + continuation.reason() + ":" + continuation.sourceNodeId());
            timers.addLast(continuation);
        }

        private RuntimeResult runNext() {
            TimerContinuation continuation = takeNext();
            events.add("resume:" + continuation.reason() + ":" + continuation.sourceNodeId());
            return runtime.resumeTimer(continuation);
        }

        private TimerContinuation takeNext() {
            return timers.removeFirst();
        }

        private int scheduled(TimerContinuation.Reason reason) {
            String prefix = "schedule:" + reason + ":";
            return (int) events.stream().filter(event -> event.startsWith(prefix)).count();
        }
    }
}
