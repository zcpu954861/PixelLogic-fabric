package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.graph.DemoGraphFactory;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.catalog.RichTextComponentValue;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.StateScope;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.state.StateKey;
import com.pixelmc.pixellogic.core.state.StateValue;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.timer.WallClockTimerScheduler;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;
import com.pixelmc.pixellogic.core.trace.ExecutionTrace;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ManualSimulationSelfCheck {
    public static void main(String[] args) {
        UUID playerId = UUID.randomUUID();
        InMemoryStateStore state = new InMemoryStateStore();
        BoundedTraceBuffer traces = new BoundedTraceBuffer(2, 20);
        GraphDefinition graph = DemoGraphFactory.create(Duration.ofSeconds(1));
        List<ValidationIssue> issues = new GraphValidator().validate(graph);
        require(issues.isEmpty(), "demo graph should validate: " + issues);

        CompiledGraph compiled = new GraphCompiler().compile(graph);
        List<String> messages = new ArrayList<>();
        List<String> debug = new ArrayList<>();
        AtomicReference<GraphRuntime> runtimeRef = new AtomicReference<>();
        RuntimeServices services = new RuntimeServices() {
            @Override
            public void sendPlayerMessage(UUID targetPlayerId, String message) {
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
        GraphRuntime runtime = new GraphRuntime(compiled, state, traces, services, RuntimeLimits.spikeDefaults());
        runtimeRef.set(runtime);

        RuntimeResult first = runtime.start(new TriggerEvent(DemoGraphFactory.TRIGGER_TYPE, "/pixellogic test start", playerId, "self-check"));
        require(first.success(), "first run should pass");
        require(messages.contains("欢迎开始游戏"), "message action should run");
        require(debug.contains("倒计时结束"), "timer continuation debug should run");
        require(state.get(StateKey.of(StateScope.PLAYER, playerId.toString(), "started")).map(value -> value.asBoolean(false)).orElse(false),
                "PLAYER.started should be true");
        require(state.get(StateKey.of(StateScope.PLAYER, playerId.toString(), "start_count")).map(value -> value.asInteger(0)).orElse(0) == 1,
                "State Add should increment PLAYER.start_count");
        require(traces.get(first.traceId()).map(trace -> trace.containsMessage("计时器完成")).orElse(false), "trace should include timer completed");

        List<String> richMessages = new ArrayList<>();
        GraphRuntime richRuntime = new GraphRuntime(
                new GraphCompiler().compile(withNodeConfig(DemoGraphFactory.create(Duration.ofSeconds(1)), "welcome-message", "message", RichTextComponentValue.fromPlainText("富文本消息\n第二行"))),
                new InMemoryStateStore(),
                new BoundedTraceBuffer(2, 20),
                new RuntimeServices() {
                    @Override
                    public void sendPlayerMessage(UUID targetPlayerId, String message) {
                        richMessages.add(message);
                    }

                    @Override
                    public void debug(String message) {
                    }

                    @Override
                    public void scheduleTimer(Duration delay, TimerContinuation continuation) {
                    }
                },
                RuntimeLimits.spikeDefaults()
        );
        require(richRuntime.start(new TriggerEvent(DemoGraphFactory.TRIGGER_TYPE, "/pixellogic test start", playerId, "self-check")).success(),
                "structured rich text message graph should run");
        require(richMessages.contains("富文本消息\n第二行"), "runtime should send rich text plainText");

        RuntimeResult second = runtime.start(new TriggerEvent(DemoGraphFactory.TRIGGER_TYPE, "/pixellogic test start", playerId, "self-check"));
        require(second.success(), "second run should fail branch cleanly");
        require(traces.get(second.traceId()).map(trace -> trace.containsMessage("玩家已经开始过游戏")).orElse(false),
                "trace should include fail branch debug");

        InMemoryStateStore boundedState = new InMemoryStateStore(2);
        StateKey firstState = StateKey.of(StateScope.PLAYER, playerId.toString(), "a");
        StateKey sessionState = StateKey.of(StateScope.SESSION, "self-check", "b");
        boundedState.set(firstState, StateValue.bool(true));
        boundedState.set(sessionState, StateValue.integer(1));
        boolean stateRejected = false;
        try {
            boundedState.set(StateKey.of(StateScope.PLAYER, UUID.randomUUID().toString(), "c"), StateValue.bool(false));
        } catch (IllegalStateException exception) {
            stateRejected = true;
        }
        require(stateRejected && boundedState.size() == 2, "state store should fail closed at capacity");
        boundedState.removeOwner(StateScope.PLAYER, playerId.toString());
        require(boundedState.get(firstState).isEmpty() && boundedState.size() == 1, "player state reset should remove that owner only");
        boundedState.clear();
        require(boundedState.size() == 0, "state store clear should remove all entries");

        BoundedTraceBuffer small = new BoundedTraceBuffer(1, 3);
        ExecutionTrace trace = small.startTrace("bounded");
        small.add(trace.id(), "n1", "one");
        small.add(trace.id(), "n2", "two");
        small.add(trace.id(), "n3", "three");
        small.add(trace.id(), "n4", "four");
        require(trace.steps().size() == 3 && trace.truncated(), "trace buffer should be bounded");
        small.startTrace("newest");
        require(small.get(trace.id()).isEmpty(), "trace buffer should evict oldest trace");

        BoundedTraceBuffer latestCheck = new BoundedTraceBuffer(2, 3);
        ExecutionTrace oldTrace = latestCheck.startTrace("old");
        latestCheck.startTrace("new");
        latestCheck.add(oldTrace.id(), "timer", "计时器完成：继续执行");
        require(latestCheck.latest().map(ExecutionTrace::id).orElse("").equals("old"), "timer-updated trace should become latest");

        GraphDefinition invalidCondition = withNodeConfig(DemoGraphFactory.create(Duration.ofSeconds(1)), "condition-started", "expected", "maybe");
        require(new GraphValidator().hasErrors(new GraphValidator().validate(invalidCondition)), "invalid boolean condition config should fail validation");

        state.set(StateKey.of(StateScope.PLAYER, playerId.toString(), "started"), StateValue.string("bad"));
        RuntimeResult badState = runtime.start(new TriggerEvent(DemoGraphFactory.TRIGGER_TYPE, "/pixellogic test start", playerId, "self-check"));
        require(!badState.success(), "state type mismatch should fail closed");

        GraphDefinition conflictingSetGraph = withNodeConfig(DemoGraphFactory.create(Duration.ofSeconds(1)), "set-started", "valueType", "STRING");
        InMemoryStateStore conflictingState = new InMemoryStateStore();
        GraphRuntime conflictingRuntime = new GraphRuntime(
                new GraphCompiler().compile(conflictingSetGraph),
                conflictingState,
                new BoundedTraceBuffer(2, 20),
                services,
                RuntimeLimits.spikeDefaults()
        );
        UUID conflictPlayer = UUID.randomUUID();
        conflictingState.set(StateKey.of(StateScope.PLAYER, conflictPlayer.toString(), "started"), StateValue.bool(false));
        RuntimeResult conflict = conflictingRuntime.start(new TriggerEvent(DemoGraphFactory.TRIGGER_TYPE, "/pixellogic test start", conflictPlayer, "self-check"));
        require(!conflict.success(), "State Set should not change an existing key type");

        try (WallClockTimerScheduler fullScheduler = new WallClockTimerScheduler(0)) {
            boolean rejected = false;
            try {
                fullScheduler.schedule(Duration.ofSeconds(1), new TimerContinuation("g", "n", "t", playerId, "s", 1, 0L), ignored -> {
                });
            } catch (RejectedExecutionException exception) {
                rejected = true;
            }
            require(rejected && fullScheduler.pendingTimers() == 0, "timer scheduler should reject over capacity without leaking pending count");
        }

        try (WallClockTimerScheduler scheduler = new WallClockTimerScheduler(4)) {
            AtomicBoolean fired = new AtomicBoolean();
            scheduler.schedule(Duration.ofMillis(100), new TimerContinuation("g", "n", "t", playerId, "s", 1, 0L), ignored -> fired.set(true));
            require(scheduler.pendingTimers() == 1, "timer scheduler should track pending timers");
            scheduler.clearPendingTimers();
            Thread.sleep(150L);
            require(!fired.get() && scheduler.pendingTimers() == 0, "timer clear should cancel pending callbacks");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("timer clear self-check interrupted", exception);
        }

        BoundedTraceBuffer staleTraces = new BoundedTraceBuffer(2, 5);
        staleTraces.startTrace("stale");
        GraphRuntime generatedRuntime = new GraphRuntime(compiled, state, staleTraces, services, RuntimeLimits.spikeDefaults(), 2L);
        RuntimeResult stale = generatedRuntime.resumeTimer(new TimerContinuation(graph.id(), "debug-finished", "stale", playerId, "self-check", 1, 1L));
        require(!stale.success() && !staleTraces.get("stale").orElseThrow().containsMessage("计时器完成"),
                "stale timer generation should not resume graph execution");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static GraphDefinition withNodeConfig(GraphDefinition graph, String nodeId, String key, String value) {
        List<NodeDefinition> updatedNodes = graph.nodes().stream()
                .map(node -> {
                    if (!node.id().equals(nodeId)) {
                        return node;
                    }
                    Map<String, String> config = new HashMap<>(node.config());
                    config.put(key, value);
                    return new NodeDefinition(node.id(), node.type(), node.slots(), Map.copyOf(config));
                })
                .toList();
        return new GraphDefinition(graph.id(), updatedNodes, graph.edges(), graph.triggerEntries());
    }
}
