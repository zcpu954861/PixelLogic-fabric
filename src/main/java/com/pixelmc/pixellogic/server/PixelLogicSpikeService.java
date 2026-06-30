package com.pixelmc.pixellogic.server;

import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.graph.DemoGraphFactory;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.StateScope;
import com.pixelmc.pixellogic.core.runtime.GraphRuntime;
import com.pixelmc.pixellogic.core.runtime.RuntimeLimits;
import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.runtime.TriggerEvent;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.timer.WallClockTimerScheduler;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;
import com.pixelmc.pixellogic.core.trace.ExecutionTrace;
import com.pixelmc.pixellogic.server.storage.GraphDocument;
import com.pixelmc.pixellogic.server.storage.GraphStorageService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PixelLogicSpikeService implements AutoCloseable {
    private static final int MAX_TRACES = 50;
    private static final int MAX_STEPS_PER_TRACE = 100;
    private static final String MANUAL_SESSION_ID = "manual-session";

    private final GraphValidator validator = new GraphValidator();
    private final InMemoryStateStore stateStore = new InMemoryStateStore();
    private final BoundedTraceBuffer traces = new BoundedTraceBuffer(MAX_TRACES, MAX_STEPS_PER_TRACE);
    private final WallClockTimerScheduler timerScheduler = new WallClockTimerScheduler();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong runtimeGeneration = new AtomicLong();
    private final GraphStorageService graphStorage;
    private final RuntimeServices services;

    private volatile GraphDocument committedGraph;
    private volatile List<ValidationIssue> validationIssues = List.of();
    private volatile GraphRuntime runtime;

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
        this(playerMessenger, serverThreadExecutor, debugLogger, timerDuration, Path.of("world", "pixellogic"));
    }

    public PixelLogicSpikeService(
            BiConsumer<UUID, String> playerMessenger,
            Consumer<Runnable> serverThreadExecutor,
            Consumer<String> debugLogger,
            Duration timerDuration,
            Path storageRoot
    ) {
        this.graphStorage = new GraphStorageService(storageRoot);
        this.services = new RuntimeServices() {
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
                if (continuation.generation() != runtimeGeneration.get()) {
                    return;
                }
                timerScheduler.schedule(delay, continuation, due -> {
                    if (closed.get() || due.generation() != runtimeGeneration.get()) {
                        return;
                    }
                    serverThreadExecutor.accept(() -> {
                        GraphRuntime currentRuntime = runtime;
                        if (!closed.get() && currentRuntime != null && due.generation() == runtimeGeneration.get()) {
                            currentRuntime.resumeTimer(due);
                        }
                    });
                });
            }
        };

        try {
            GraphDocument loaded = graphStorage.ensureCommitted(
                    DemoGraphFactory.create(timerDuration),
                    GraphStorageService.DEFAULT_DISPLAY_NAME
            );
            installCommittedGraph(loaded);
        } catch (IOException exception) {
            validationIssues = List.of(new ValidationIssue(
                    ValidationIssue.Severity.ERROR,
                    "graph_storage_unavailable",
                    "Graph storage 初始化失败：" + exception.getMessage()
            ));
        }
    }

    public RuntimeResult startManualTest(UUID playerId) {
        GraphRuntime currentRuntime = runtime;
        if (validator.hasErrors(validationIssues) || currentRuntime == null) {
            String message = validationIssues.isEmpty()
                    ? "Graph runtime 未就绪。"
                    : "Committed graph validation failed: " + validationIssues.getFirst().message();
            return new RuntimeResult(false, "", message);
        }
        return currentRuntime.start(new TriggerEvent(
                DemoGraphFactory.TRIGGER_TYPE,
                "/pixellogic test start",
                playerId,
                MANUAL_SESSION_ID
        ));
    }

    public void resetPlayer(UUID playerId) {
        runtimeGeneration.incrementAndGet();
        timerScheduler.clearPendingTimers();
        stateStore.removeOwner(StateScope.PLAYER, playerId.toString());
        stateStore.removeOwner(StateScope.SESSION, MANUAL_SESSION_ID);
        GraphDocument graph = committedGraph;
        if (graph != null) {
            installCommittedGraph(graph);
        }
    }

    public List<GraphStorageService.GraphSummary> graphs() throws IOException {
        return graphStorage.listGraphs();
    }

    public GraphDocument committedGraph(String graphId) throws IOException {
        GraphStorageService.validateGraphId(graphId);
        GraphDocument graph = committedGraph;
        if (graph == null) {
            throw new IllegalStateException("Committed graph 未就绪。");
        }
        if (!graph.id().equals(graphId)) {
            return graphStorage.loadCommitted(graphId);
        }
        return graph;
    }

    public Optional<GraphDocument> draftGraph(String graphId) throws IOException {
        return graphStorage.loadDraft(graphId);
    }

    public GraphDocument saveDraft(String graphId, GraphDocument draft) throws IOException {
        return graphStorage.saveDraft(graphId, draft);
    }

    public GraphStorageService.ValidationReport validateDraft(String graphId) throws IOException {
        return graphStorage.validateDraft(graphId);
    }

    public synchronized GraphStorageService.CommitReport commitDraft(String graphId) throws IOException {
        GraphStorageService.CommitReport report = graphStorage.commitDraft(graphId);
        if (report.committed()) {
            installCommittedGraph(report.graph());
        }
        return report;
    }

    public GraphStorageService.ValidationReport committedValidation() {
        return new GraphStorageService.ValidationReport(!validator.hasErrors(validationIssues), validationIssues);
    }

    public boolean draftExists(String graphId) {
        return graphStorage.draftExists(graphId);
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
        GraphDocument graph = committedGraph;
        String graphId = graph == null ? "unavailable" : graph.id();
        String fingerprint = graph == null ? "" : graph.fingerprint();
        return "PixelLogic v1 graph runtime ready. Graph=" + graphId + " fingerprint=" + fingerprint;
    }

    public int pendingTimers() {
        return timerScheduler.pendingTimers();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            runtimeGeneration.incrementAndGet();
            timerScheduler.close();
            stateStore.clear();
        }
    }

    private void installCommittedGraph(GraphDocument document) {
        long generation = runtimeGeneration.incrementAndGet();
        timerScheduler.clearPendingTimers();
        GraphDefinition graph = document.toGraphDefinition();
        List<ValidationIssue> issues = List.copyOf(validator.validate(graph));
        if (validator.hasErrors(issues)) {
            validationIssues = issues;
            runtime = null;
            committedGraph = document;
            return;
        }

        CompiledGraph compiledGraph = new GraphCompiler().compile(graph);
        runtime = new GraphRuntime(compiledGraph, stateStore, traces, services, RuntimeLimits.spikeDefaults(), generation);
        validationIssues = issues;
        committedGraph = document;
    }
}
