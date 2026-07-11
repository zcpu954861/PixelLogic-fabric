package com.pixelmc.pixellogic.server.api;

import com.google.gson.Gson;
import com.pixelmc.pixellogic.core.graph.DemoGraphFactory;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.model.StateScope;
import com.pixelmc.pixellogic.core.runtime.GraphRuntime;
import com.pixelmc.pixellogic.core.runtime.RuntimeLimits;
import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.runtime.TriggerEvent;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.state.StateKey;
import com.pixelmc.pixellogic.core.state.StateValue;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;
import com.pixelmc.pixellogic.server.storage.GraphDocument;
import com.pixelmc.pixellogic.server.storage.GraphStorageService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class RuntimeApiSafetySelfCheck {
    private RuntimeApiSafetySelfCheck() {
    }

    public static void main(String[] args) {
        run("runtimeApiSafetySelfCheck", () -> {
            checkRealApiEntriesCancelBeforeStart();
            checkTaskRacesAndClose();
            checkIntegerOverflow();
            checkTimerCloseFence();
        });
    }

    private static void checkRealApiEntriesCancelBeforeStart() throws Exception {
        Path root = Files.createTempDirectory("pixel-logic-runtime-api-safety-");
        QueuedExecutor executor = new QueuedExecutor();
        try (PixelLogicSpikeService service = service(root, Duration.ofSeconds(30));
             PixelLogicApiServer api = PixelLogicApiServer.start(
                     service, executor, PixelLogicApiServer.DEFAULT_HOST, 0, 20L, 200L
             )) {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://" + PixelLogicApiServer.DEFAULT_HOST + ":" + api.port();
            GraphDocument graph = service.committedGraph(GraphStorageService.DEFAULT_GRAPH_ID);

            assertCancelled(client, executor, "PUT", base + "/api/pixellogic/graphs/demo-start-flow/draft",
                    "{\"graph\":" + new Gson().toJson(graph) + "}");
            require(!service.draftExists(GraphStorageService.DEFAULT_GRAPH_ID), "timed-out save must not create a draft");

            service.saveDraft(GraphStorageService.DEFAULT_GRAPH_ID, graph);
            String committedFingerprint = service.committedGraph(GraphStorageService.DEFAULT_GRAPH_ID).fingerprint();
            assertCancelled(client, executor, "POST", base + "/api/pixellogic/graphs/demo-start-flow/commit", "");
            require(service.draftExists(GraphStorageService.DEFAULT_GRAPH_ID), "timed-out commit must leave the draft pending");
            require(committedFingerprint.equals(service.committedGraph(GraphStorageService.DEFAULT_GRAPH_ID).fingerprint()),
                    "timed-out commit must not change the committed graph");

            assertCancelled(client, executor, "POST", base + "/api/pixellogic/test/start", "");
            require(service.lastSimulationResult().isEmpty(), "timed-out start must not create a run");

            RuntimeResult existing = service.startManualTest(PixelLogicApiServer.WEBUI_DEMO_PLAYER_ID);
            require(service.lastSimulationResult().isPresent(), "reset precondition should create a run");
            assertCancelled(client, executor, "POST", base + "/api/pixellogic/test/reset", "");
            require(service.lastSimulationResult().orElseThrow().traceId().equals(existing.traceId()),
                    "timed-out reset must not replace the existing run");
        }
    }

    private static void checkTaskRacesAndClose() throws Exception {
        Path root = Files.createTempDirectory("pixel-logic-runtime-task-safety-");
        try (PixelLogicSpikeService service = service(root, Duration.ofSeconds(30));
             PixelLogicApiServer api = PixelLogicApiServer.start(
                     service, Runnable::run, PixelLogicApiServer.DEFAULT_HOST, 0, 20L, 200L
             )) {
            AtomicInteger effects = new AtomicInteger();
            PixelLogicApiServer.ServerThreadTask cancelled = api.new ServerThreadTask(() -> {
                effects.incrementAndGet();
                return new PixelLogicApiServer.ApiResponse(200, "ok");
            });
            require(cancelled.cancel(), "queued task should cancel");
            cancelled.run();
            require(effects.get() == 0 && cancelled.state.get() == PixelLogicApiServer.TaskState.CANCELLED,
                    "cancelled task must never execute");

            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            PixelLogicApiServer.ServerThreadTask running = api.new ServerThreadTask(() -> {
                started.countDown();
                release.await();
                effects.incrementAndGet();
                return new PixelLogicApiServer.ApiResponse(200, "done");
            });
            Thread thread = new Thread(running, "runtime-api-safety-running-task");
            thread.start();
            require(started.await(1, TimeUnit.SECONDS), "task should enter RUNNING");
            require(!running.cancel(), "RUNNING task must not be reported as cancelled");
            release.countDown();
            thread.join(1_000L);
            require(running.future.get(1, TimeUnit.SECONDS).body().equals("done")
                            && running.state.get() == PixelLogicApiServer.TaskState.COMPLETED,
                    "started task should return its real completion exactly once");

            for (int attempt = 0; attempt < 100; attempt++) {
                AtomicInteger raceEffects = new AtomicInteger();
                PixelLogicApiServer.ServerThreadTask racing = api.new ServerThreadTask(() -> {
                    raceEffects.incrementAndGet();
                    return new PixelLogicApiServer.ApiResponse(200, "race-done");
                });
                CountDownLatch race = new CountDownLatch(1);
                Thread starter = new Thread(() -> awaitAndRun(race, racing));
                Thread canceller = new Thread(() -> {
                    await(race);
                    racing.cancel();
                });
                starter.start();
                canceller.start();
                race.countDown();
                starter.join();
                canceller.join();
                require((racing.state.get() == PixelLogicApiServer.TaskState.CANCELLED && raceEffects.get() == 0)
                                || (racing.state.get() == PixelLogicApiServer.TaskState.COMPLETED && raceEffects.get() == 1),
                        "cancel/start race must have exactly one legal outcome");
            }

            CountDownLatch closeStarted = new CountDownLatch(1);
            CountDownLatch closeRelease = new CountDownLatch(1);
            PixelLogicApiServer.ServerThreadTask closeAfterStart = api.new ServerThreadTask(() -> {
                closeStarted.countDown();
                closeRelease.await();
                effects.incrementAndGet();
                return new PixelLogicApiServer.ApiResponse(200, "close-done");
            });
            Thread closeThread = new Thread(closeAfterStart, "runtime-api-safety-close-after-start");
            closeThread.start();
            require(closeStarted.await(1, TimeUnit.SECONDS), "close race task should start");
            api.close();
            closeRelease.countDown();
            closeThread.join(1_000L);
            require(closeAfterStart.future.get(1, TimeUnit.SECONDS).body().equals("close-done"),
                    "close-after-start should allow the existing short operation to finish");

            PixelLogicApiServer.ServerThreadTask afterClose = api.new ServerThreadTask(() -> {
                effects.incrementAndGet();
                return new PixelLogicApiServer.ApiResponse(200, "unexpected");
            });
            service.close();
            afterClose.run();
            require(afterClose.future.get(1, TimeUnit.SECONDS).body().contains("SERVICE_CLOSED")
                            && afterClose.state.get() == PixelLogicApiServer.TaskState.CANCELLED
                            && effects.get() == 2,
                    "close-before-start must skip the business action");
            assertClosed(() -> service.startManualTest(UUID.randomUUID()));
            assertClosed(() -> service.resetPlayer(UUID.randomUUID()));
            assertClosed(() -> service.saveDraft(GraphStorageService.DEFAULT_GRAPH_ID,
                    service.committedGraph(GraphStorageService.DEFAULT_GRAPH_ID)));
            assertClosed(() -> service.commitDraft(GraphStorageService.DEFAULT_GRAPH_ID));
        }
    }

    private static void checkIntegerOverflow() {
        InMemoryStateStore state = new InMemoryStateStore();
        StateKey key = StateKey.global("score");
        assertOverflowPreserves(state, key, Integer.MAX_VALUE, 1);
        assertOverflowPreserves(state, key, Integer.MAX_VALUE, 10);
        assertOverflowPreserves(state, key, Integer.MIN_VALUE, -1);
        assertOverflowPreserves(state, key, Integer.MIN_VALUE, -10);
        state.set(key, StateValue.integer(10));
        require(state.addInteger(key, 5).asInteger(0) == 15, "normal positive add should work");
        require(state.addInteger(key, -7).asInteger(0) == 8, "normal negative add should work");
        require(state.addInteger(key, 0).asInteger(0) == 8, "zero add should work");

        UUID playerId = UUID.randomUUID();
        InMemoryStateStore runtimeState = new InMemoryStateStore();
        StateKey count = StateKey.of(StateScope.PLAYER, playerId.toString(), "start_count");
        runtimeState.set(count, StateValue.integer(Integer.MAX_VALUE));
        BoundedTraceBuffer traces = new BoundedTraceBuffer(2, 30);
        GraphRuntime runtime = new GraphRuntime(
                new GraphCompiler().compile(DemoGraphFactory.create(Duration.ofSeconds(1))),
                runtimeState,
                traces,
                noOpServices(),
                RuntimeLimits.spikeDefaults()
        );
        RuntimeResult result = runtime.start(new TriggerEvent(
                DemoGraphFactory.TRIGGER_TYPE, "overflow", playerId, "runtime-api-safety"
        ));
        require(!result.success() && result.message().contains("超出整数允许范围"),
                "runtime overflow should fail with a readable Chinese result");
        require(runtimeState.get(count).orElseThrow().asInteger(0) == Integer.MAX_VALUE,
                "runtime overflow must preserve the original value");
        require(traces.get(result.traceId()).orElseThrow().containsMessage("原值未修改"),
                "runtime overflow should enter the execution trace");
    }

    private static void checkTimerCloseFence() throws Exception {
        Path root = Files.createTempDirectory("pixel-logic-runtime-timer-close-");
        PixelLogicSpikeService service = service(root, Duration.ofSeconds(1));
        RuntimeResult result = service.startManualTest(UUID.randomUUID());
        require(result.suspended() && service.pendingTimers() == 1, "timer close precondition should be pending");
        service.close();
        Thread.sleep(1_100L);
        require(service.pendingTimers() == 0
                        && service.lastSimulationResult().orElseThrow().status().terminal(),
                "close must cancel pending timer continuation without late resume");
    }

    private static void assertCancelled(
            HttpClient client,
            QueuedExecutor executor,
            String method,
            String uri,
            String body
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri));
        if ("PUT".equals(method)) {
            request.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.POST(HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() == 503 && response.body().contains("SERVER_THREAD_TIMEOUT")
                        && response.body().contains("操作未执行"),
                method + " should report a cancelled, unexecuted timeout");
        executor.runNext();
    }

    private static void assertOverflowPreserves(InMemoryStateStore state, StateKey key, int base, int amount) {
        state.set(key, StateValue.integer(base));
        try {
            state.addInteger(key, amount);
            throw new IllegalStateException("overflow should fail");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("超出整数允许范围"), "overflow error should be readable");
        }
        require(state.get(key).orElseThrow().asInteger(0) == base, "overflow must preserve " + base);
    }

    private static void assertClosed(CheckedOperation operation) throws Exception {
        try {
            operation.run();
            throw new IllegalStateException("closed service operation should fail");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("服务已关闭"), "closed service should reject the operation");
        }
    }

    private static PixelLogicSpikeService service(Path root, Duration timerDuration) {
        return new PixelLogicSpikeService((id, message) -> {
        }, Runnable::run, ignored -> {
        }, timerDuration, root);
    }

    private static RuntimeServices noOpServices() {
        return new RuntimeServices() {
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
    }

    private static void awaitAndRun(CountDownLatch latch, Runnable task) {
        await(latch);
        task.run();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayBlockingQueue<Runnable> tasks = new ArrayBlockingQueue<>(8);

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runNext() throws InterruptedException {
            Runnable task = tasks.poll(1, TimeUnit.SECONDS);
            require(task != null, "API should enqueue one server-thread task");
            task.run();
        }
    }

    @FunctionalInterface
    private interface CheckedOperation {
        void run() throws Exception;
    }
}
