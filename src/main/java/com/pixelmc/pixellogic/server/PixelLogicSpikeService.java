package com.pixelmc.pixellogic.server;

import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.graph.DemoGraphFactory;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.runtime.GraphRuntime;
import com.pixelmc.pixellogic.core.runtime.RuntimeLimits;
import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.runtime.TriggerEvent;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.state.StateKey;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.timer.WallClockTimerScheduler;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;
import com.pixelmc.pixellogic.core.trace.ExecutionTrace;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PixelLogicSpikeService implements AutoCloseable {
    private static final int MAX_TRACES = 50;
    private static final int MAX_STEPS_PER_TRACE = 100;

    private final GraphDefinition demoGraph;
    private final GraphValidator validator = new GraphValidator();
    private final InMemoryStateStore stateStore = new InMemoryStateStore();
    private final BoundedTraceBuffer traces = new BoundedTraceBuffer(MAX_TRACES, MAX_STEPS_PER_TRACE);
    private final WallClockTimerScheduler timerScheduler = new WallClockTimerScheduler();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final List<ValidationIssue> validationIssues;
    private final GraphRuntime runtime;

    public PixelLogicSpikeService(
            BiConsumer<UUID, String> playerMessenger,
            Consumer<Runnable> serverThreadExecutor,
            Consumer<String> debugLogger
    ) {
        this(playerMessenger, serverThreadExecutor, debugLogger, Duration.ofSeconds(30));
    }

    public PixelLogicSpikeService(
            BiConsumer<UUID, String> playerMessenger,
            Consumer<Runnable> serverThreadExecutor,
            Consumer<String> debugLogger,
            Duration timerDuration
    ) {
        this.demoGraph = DemoGraphFactory.create(timerDuration);
        this.validationIssues = List.copyOf(validator.validate(demoGraph));
        if (validator.hasErrors(validationIssues)) {
            this.runtime = null;
            return;
        }

        CompiledGraph compiledGraph = new GraphCompiler().compile(demoGraph);
        RuntimeServices services = new RuntimeServices() {
            @Override
            public void sendPlayerMessage(UUID playerId, String message) {
                playerMessenger.accept(playerId, message);
            }

            @Override
            public void debug(String message) {
                debugLogger.accept(message);
            }

            @Override
            public void scheduleTimer(Duration delay, TimerContinuation continuation) {
                timerScheduler.schedule(delay, continuation, due -> {
                    if (closed.get()) {
                        return;
                    }
                    serverThreadExecutor.accept(() -> {
                        if (!closed.get()) {
                            runtime.resumeTimer(due);
                        }
                    });
                });
            }
        };
        this.runtime = new GraphRuntime(compiledGraph, stateStore, traces, services, RuntimeLimits.spikeDefaults());
    }

    public RuntimeResult startManualTest(UUID playerId) {
        if (validator.hasErrors(validationIssues) || runtime == null) {
            String message = "Demo graph validation failed: " + validationIssues.getFirst().message();
            return new RuntimeResult(false, "", message);
        }
        return runtime.start(new TriggerEvent(
                DemoGraphFactory.TRIGGER_TYPE,
                "/pixellogic test start",
                playerId,
                "manual-session"
        ));
    }

    public void resetPlayer(UUID playerId) {
        stateStore.remove(StateKey.of(com.pixelmc.pixellogic.core.model.StateScope.PLAYER, playerId.toString(), "started"));
        stateStore.remove(StateKey.of(com.pixelmc.pixellogic.core.model.StateScope.PLAYER, playerId.toString(), "start_count"));
    }

    public Optional<ExecutionTrace> latestTrace() {
        return traces.latest();
    }

    public Optional<ExecutionTrace> trace(String traceId) {
        return traces.get(traceId);
    }

    public List<ExecutionTrace> recentTraces() {
        return traces.recent();
    }

    public String status() {
        return "PixelLogic v1 manual simulation spike ready. Graph=" + demoGraph.id();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            timerScheduler.close();
        }
    }
}
