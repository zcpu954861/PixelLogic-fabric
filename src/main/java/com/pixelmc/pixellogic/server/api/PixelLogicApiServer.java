package com.pixelmc.pixellogic.server.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeEntityLookup;
import com.pixelmc.pixellogic.core.runtime.RuntimeSubjectReference;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionResult;
import com.pixelmc.pixellogic.core.trace.ExecutionTrace;
import com.pixelmc.pixellogic.core.trace.TraceStep;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;
import com.pixelmc.pixellogic.server.storage.GraphDocument;
import com.pixelmc.pixellogic.server.storage.GraphStorageService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PixelLogicApiServer implements AutoCloseable {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 18111;
    public static final UUID WEBUI_DEMO_PLAYER_ID = UUID.nameUUIDFromBytes("pixel-logic-webui-demo-player".getBytes(StandardCharsets.UTF_8));

    private static final String WEBUI_DEMO_PLAYER_NAME = "WebUI 模拟玩家";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final SimulationTestContextParser TEST_CONTEXT_PARSER =
            new SimulationTestContextParser(GSON, WEBUI_DEMO_PLAYER_ID, WEBUI_DEMO_PLAYER_NAME);
    private static final Pattern GRAPH_PATH = Pattern.compile("^/api/pixellogic/graphs/([A-Za-z0-9_-]+)(?:/(draft|validate|commit))?$");
    private static final Pattern TEST_RUN_PATH = Pattern.compile(
            "^/api/pixellogic/test/runs/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$"
    );

    private final PixelLogicSpikeService service;
    private final Executor serverThreadExecutor;
    private final HttpServer server;
    private final long queuedTimeoutMillis;
    private final long runningTimeoutMillis;
    private final AtomicBoolean closed = new AtomicBoolean();

    private PixelLogicApiServer(
            PixelLogicSpikeService service,
            Executor serverThreadExecutor,
            HttpServer server,
            long queuedTimeoutMillis,
            long runningTimeoutMillis
    ) {
        this.service = service;
        this.serverThreadExecutor = serverThreadExecutor;
        this.server = server;
        this.queuedTimeoutMillis = queuedTimeoutMillis;
        this.runningTimeoutMillis = runningTimeoutMillis;
    }

    public static PixelLogicApiServer start(PixelLogicSpikeService service, Executor serverThreadExecutor) throws IOException {
        return start(service, serverThreadExecutor, DEFAULT_HOST, DEFAULT_PORT);
    }

    public static PixelLogicApiServer start(
            PixelLogicSpikeService service,
            Executor serverThreadExecutor,
            String host,
            int port
    ) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getByName(host), port), 0);
        return start(service, serverThreadExecutor, httpServer, 5_000L, 5_000L);
    }

    static PixelLogicApiServer start(
            PixelLogicSpikeService service,
            Executor serverThreadExecutor,
            String host,
            int port,
            long queuedTimeoutMillis,
            long runningTimeoutMillis
    ) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getByName(host), port), 0);
        return start(service, serverThreadExecutor, httpServer, queuedTimeoutMillis, runningTimeoutMillis);
    }

    private static PixelLogicApiServer start(
            PixelLogicSpikeService service,
            Executor serverThreadExecutor,
            HttpServer httpServer,
            long queuedTimeoutMillis,
            long runningTimeoutMillis
    ) {
        PixelLogicApiServer apiServer = new PixelLogicApiServer(
                service, serverThreadExecutor, httpServer, queuedTimeoutMillis, runningTimeoutMillis
        );
        httpServer.createContext("/api", apiServer::handle);
        httpServer.setExecutor(null);
        httpServer.start();
        return apiServer;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            ApiResponse response = dispatch(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    body
            );
            send(exchange, response.status(), response.body());
        } catch (Exception exception) {
            send(exchange, 500, errorBody("INTERNAL_ERROR", "API 处理失败。"));
        }
    }

    private ApiResponse dispatch(String method, String path, String rawQuery, String body) {
        if ("GET".equals(method) && "/api/pixellogic/status".equals(path)) {
            return onServerThread(() -> ok(Map.of(
                    "message", service.status(),
                    "api", "v1-spike",
                    "demoActor", demoActor()
            )));
        }
        if ("POST".equals(method) && "/api/pixellogic/test/reset".equals(path)) {
            return onServerThread(() -> {
                service.resetPlayer(WEBUI_DEMO_PLAYER_ID);
                return ok(Map.of(
                        "message", "WebUI 模拟测试状态已重置。",
                        "demoActor", demoActor()
                ));
            });
        }
        if ("POST".equals(method) && "/api/pixellogic/test/start".equals(path)) {
            SimulationTestContextParser.Parsed testContext;
            try {
                testContext = TEST_CONTEXT_PARSER.parse(body);
            } catch (IllegalArgumentException exception) {
                return error(400, "BAD_TEST_CONTEXT", exception.getMessage());
            }
            return onServerThread(() -> {
                RuntimeResult result = service.startManualTest(testContext.actor(), testContext.world());
                Optional<SimulationExecutionResult> run = service.simulationRun(result.traceId());
                Map<String, Object> fields = run
                        .map(this::testRunFields)
                        .orElseGet(() -> fields("message", result.message(), "traceId", result.traceId()));
                fields.put("demoActor", demoActor());
                if (run.isPresent()) {
                    return ok(fields);
                }
                if (!result.success()) {
                    fields.put("error", errorMap("RUNTIME_FAILED", result.message()));
                    return json(500, false, fields);
                }
                return ok(fields);
            });
        }
        Matcher testRunMatcher = TEST_RUN_PATH.matcher(path);
        if ("GET".equals(method) && testRunMatcher.matches()) {
            String runId = testRunMatcher.group(1);
            return onServerThread(() -> service.simulationRun(runId)
                    .map(run -> ok(testRunFields(run)))
                    .orElseGet(() -> error(404, "RUN_NOT_FOUND", "测试运行不存在或已被新的运行替代。")));
        }
        if (path.startsWith("/api/pixellogic/test/runs/")) {
            return error(400, "INVALID_RUN_ID", "测试运行 ID 无效。");
        }
        if ("GET".equals(method) && "/api/pixellogic/traces/latest".equals(path)) {
            return onServerThread(() -> ok(fields(
                    "trace", service.latestTrace().map(PixelLogicApiServer::traceView).orElse(null)
            )));
        }
        if ("GET".equals(method) && "/api/pixellogic/traces".equals(path)) {
            return onServerThread(() -> ok(Map.of(
                    "traces", service.recentTraces().stream().map(PixelLogicApiServer::traceView).toList()
            )));
        }
        if ("GET".equals(method) && "/api/pixellogic/graphs".equals(path)) {
            return onServerThread(() -> ok(Map.of("graphs", service.graphs())));
        }
        if ("GET".equals(method) && "/api/pixellogic/catalog".equals(path)) {
            return ok(Map.of("catalog", BuiltInBlockCatalog.catalog()));
        }
        if ("GET".equals(method) && "/api/pixellogic/runtime/online-players".equals(path)) {
            OnlinePlayerQuery query;
            try {
                query = parseOnlinePlayerQuery(rawQuery);
            } catch (IllegalArgumentException exception) {
                return error(400, "BAD_ONLINE_PLAYER_QUERY", exception.getMessage());
            }
            return onServerThread(() -> {
                var listed = service.onlinePlayers(query.query(), query.limit());
                if (!listed.providerAvailable()) {
                    return error(503, "ENTITY_TARGET_PROVIDER_UNAVAILABLE", "在线玩家提供器当前不可用。");
                }
                Object selected = null;
                if (query.selectedUuid() != null) {
                    RuntimeEntityLookup lookup = service.onlinePlayer(query.selectedUuid());
                    if (lookup.status() == RuntimeEntityLookup.Status.PROVIDER_UNAVAILABLE) {
                        return error(503, "ENTITY_TARGET_PROVIDER_UNAVAILABLE", "在线玩家提供器当前不可用。");
                    }
                    selected = selectedPlayerView(query.selectedUuid(), lookup);
                }
                return ok(fields("players", listed.players(), "selected", selected));
            });
        }

        Matcher graphMatcher = GRAPH_PATH.matcher(path);
        if (graphMatcher.matches()) {
            return dispatchGraph(method, graphMatcher.group(1), graphMatcher.group(2), body);
        }
        if (path.startsWith("/api/pixellogic/graphs/")) {
            return error(400, "INVALID_GRAPH_ID", "graph id 只能包含字母、数字、下划线和短横线。");
        }
        return error(404, "NOT_FOUND", "API endpoint 不存在。");
    }

    private ApiResponse dispatchGraph(String method, String graphId, String action, String body) {
        if (action == null && "GET".equals(method)) {
            return onServerThread(() -> {
                GraphDocument graph = service.committedGraph(graphId);
                return ok(fields(
                        "graph", graph,
                        "fingerprint", graph.fingerprint(),
                        "validation", service.committedValidation(),
                        "hasDraft", service.draftExists(graphId)
                ));
            });
        }
        if ("draft".equals(action) && "GET".equals(method)) {
            return onServerThread(() -> {
                Optional<GraphDocument> draft = service.draftGraph(graphId);
                return ok(fields(
                        "graph", draft.orElse(null),
                        "hasDraft", draft.isPresent()
                ));
            });
        }
        if ("draft".equals(action) && "PUT".equals(method)) {
            GraphDocument draft;
            try {
                draft = parseGraphDocument(body);
            } catch (IllegalArgumentException exception) {
                return error(400, "BAD_GRAPH_JSON", exception.getMessage());
            }
            return onServerThread(() -> {
                GraphDocument saved = service.saveDraft(graphId, draft);
                return ok(fields(
                        "message", "草稿已保存，尚未提交生效。",
                        "graph", saved,
                        "fingerprint", saved.fingerprint(),
                        "hasDraft", true
                ));
            });
        }
        if ("validate".equals(action) && "POST".equals(method)) {
            return onServerThread(() -> {
                GraphStorageService.ValidationReport validation = service.validateDraft(graphId);
                return ok(Map.of("validation", validation));
            });
        }
        if ("commit".equals(action) && "POST".equals(method)) {
            return onServerThread(() -> {
                GraphStorageService.CommitReport report = service.commitDraft(graphId);
                if (!report.committed()) {
                    return json(409, false, fields(
                            "error", errorMap("VALIDATION_FAILED", "图验证失败，未提交。"),
                            "validation", report.validation(),
                            "graph", report.graph()
                    ));
                }
                return ok(fields(
                        "message", "图已提交生效，测试运行将使用该版本。",
                        "graph", report.graph(),
                        "validation", report.validation(),
                        "fingerprint", report.graph().fingerprint(),
                        "hasDraft", false
                ));
            });
        }
        return error(405, "METHOD_NOT_ALLOWED", "该 Graph API 不支持当前请求方法。");
    }

    private ApiResponse onServerThread(Callable<ApiResponse> action) {
        if (closed.get() || service.isClosed()) {
            return error(503, "SERVICE_CLOSED", "PixelLogic 服务已关闭，操作未执行。");
        }
        ServerThreadTask task = new ServerThreadTask(action);
        serverThreadExecutor.execute(task);
        try {
            return task.future.get(queuedTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            if (task.cancel()) {
                return error(503, "SERVER_THREAD_TIMEOUT", "等待服务器线程超时，操作未执行。");
            }
            try {
                return task.future.get(runningTimeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException runningTimeout) {
                return error(503, "SERVER_THREAD_RUNNING", "操作已经开始，但尚未完成，请确认最终状态。");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return error(503, "SERVER_THREAD_INTERRUPTED", "等待服务器线程时被中断，操作状态未知。");
            } catch (ExecutionException impossible) {
                return error(500, "SERVER_THREAD_FAILED", "API 执行失败。");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            task.cancel();
            return error(503, "SERVER_THREAD_INTERRUPTED", "等待服务器线程时被中断。");
        } catch (ExecutionException impossible) {
            return error(500, "SERVER_THREAD_FAILED", "API 执行失败。");
        }
    }

    final class ServerThreadTask implements Runnable {
        final AtomicReference<TaskState> state = new AtomicReference<>(TaskState.QUEUED);
        final CompletableFuture<ApiResponse> future = new CompletableFuture<>();
        private final Callable<ApiResponse> action;

        ServerThreadTask(Callable<ApiResponse> action) {
            this.action = action;
        }

        @Override
        public void run() {
            if ((closed.get() || service.isClosed()) && cancel()) {
                future.complete(error(503, "SERVICE_CLOSED", "PixelLogic 服务已关闭，操作未执行。"));
                return;
            }
            if (!state.compareAndSet(TaskState.QUEUED, TaskState.RUNNING)) {
                return;
            }
            if (closed.get() || service.isClosed()) {
                complete(error(503, "SERVICE_CLOSED", "PixelLogic 服务已关闭，操作未执行。"));
                return;
            }
            try {
                complete(action.call());
            } catch (IllegalArgumentException exception) {
                complete(error(400, "BAD_REQUEST", exception.getMessage()));
            } catch (IllegalStateException exception) {
                complete(error(409, "CONFLICT", exception.getMessage()));
            } catch (Exception exception) {
                String message = exception.getMessage() == null ? "API 执行失败。" : exception.getMessage();
                complete(error(500, "SERVER_THREAD_FAILED", message));
            }
        }

        boolean cancel() {
            return state.compareAndSet(TaskState.QUEUED, TaskState.CANCELLED);
        }

        private void complete(ApiResponse response) {
            state.set(TaskState.COMPLETED);
            future.complete(response);
        }
    }

    enum TaskState {
        QUEUED, RUNNING, COMPLETED, CANCELLED
    }

    private static GraphDocument parseGraphDocument(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("请求体缺少 graph JSON。");
        }
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject() || !element.getAsJsonObject().has("graph")) {
                throw new IllegalArgumentException("请求体缺少 graph JSON。");
            }
            GraphDocument graph = GSON.fromJson(element.getAsJsonObject().get("graph"), GraphDocument.class);
            if (graph == null) {
                throw new IllegalArgumentException("请求体缺少 graph JSON。");
            }
            return graph;
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("graph JSON 无法解析。", exception);
        }
    }

    private static OnlinePlayerQuery parseOnlinePlayerQuery(String rawQuery) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (rawQuery != null && !rawQuery.isBlank()) {
            for (String pair : rawQuery.split("&", -1)) {
                int separator = pair.indexOf('=');
                if (separator < 0) {
                    throw new IllegalArgumentException("查询参数格式无效。");
                }
                String key = decodeQueryPart(pair.substring(0, separator));
                String value = decodeQueryPart(pair.substring(separator + 1));
                if (!List.of("query", "limit", "selectedUuid").contains(key)) {
                    throw new IllegalArgumentException("不支持的查询参数：" + key);
                }
                if (parameters.putIfAbsent(key, value) != null) {
                    throw new IllegalArgumentException("查询参数不能重复：" + key);
                }
            }
        }

        String query = parameters.getOrDefault("query", "");
        if (query.length() > 64 || query.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("query 必须不超过 64 个字符且不能包含控制字符。");
        }
        int limit = parseOnlinePlayerLimit(parameters.get("limit"));
        UUID selectedUuid = parseSelectedUuid(parameters.get("selectedUuid"));
        return new OnlinePlayerQuery(query, limit, selectedUuid);
    }

    private static String decodeQueryPart(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("查询参数编码无效。", exception);
        }
    }

    private static int parseOnlinePlayerLimit(String value) {
        if (value == null) {
            return 20;
        }
        try {
            int limit = Integer.parseInt(value);
            if (limit < 1 || limit > 50) {
                throw new IllegalArgumentException("limit 必须在 1 到 50 之间。");
            }
            return limit;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("limit 必须是 1 到 50 之间的整数。", exception);
        }
    }

    private static UUID parseSelectedUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equals(value)) {
                throw new IllegalArgumentException("selectedUuid 必须是 canonical UUID。");
            }
            return uuid;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("selectedUuid 必须是 canonical UUID。", exception);
        }
    }

    private static Map<String, Object> selectedPlayerView(UUID uuid, RuntimeEntityLookup lookup) {
        return switch (lookup.status()) {
            case RESOLVED -> {
                var entity = lookup.entity();
                var reference = entity.reference();
                if (reference == null
                        || reference.kind() != RuntimeSubjectReference.Kind.PLAYER
                        || !uuid.toString().equals(reference.id())) {
                    yield fields("uuid", uuid, "name", null, "availability", "UNRESOLVABLE");
                }
                yield fields(
                        "uuid", uuid,
                        "name", lookup.displayName(),
                        "availability", entity.online() ? "ONLINE" : "OFFLINE"
                );
            }
            case OFFLINE -> fields(
                    "uuid", uuid,
                    "name", lookup.displayName().isBlank() ? null : lookup.displayName(),
                    "availability", "OFFLINE"
            );
            case UNRESOLVABLE -> fields("uuid", uuid, "name", null, "availability", "UNRESOLVABLE");
            case PROVIDER_UNAVAILABLE -> throw new IllegalStateException("在线玩家提供器当前不可用。");
        };
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static ApiResponse ok(Map<String, Object> fields) {
        return json(200, true, fields);
    }

    private static ApiResponse error(int status, String code, String message) {
        return new ApiResponse(status, errorBody(code, message));
    }

    private static ApiResponse json(int status, boolean ok, Map<String, Object> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", ok);
        body.putAll(fields);
        return new ApiResponse(status, GSON.toJson(body));
    }

    private static String errorBody(String code, String message) {
        return GSON.toJson(fields("ok", false, "error", errorMap(code, message)));
    }

    private static Map<String, Object> errorMap(String code, String message) {
        return fields("code", code, "message", message);
    }

    private static Map<String, Object> demoActor() {
        return fields("id", WEBUI_DEMO_PLAYER_ID.toString(), "label", WEBUI_DEMO_PLAYER_NAME);
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1]);
        }
        return result;
    }

    private static TraceView traceView(ExecutionTrace trace) {
        return new TraceView(
                trace.id(),
                trace.truncated(),
                trace.steps().stream().map(PixelLogicApiServer::traceStepView).toList()
        );
    }

    private Map<String, Object> testRunFields(SimulationExecutionResult run) {
        JsonObject simulation = GSON.toJsonTree(run).getAsJsonObject();
        if (run.targetError() != null) {
            simulation.getAsJsonObject("targetError").addProperty("code", run.targetError().code().id());
        }
        if (run.actionError() != null) {
            simulation.getAsJsonObject("actionError").addProperty("code", run.actionError().code().id());
        }
        return fields(
                "message", run.message(),
                "traceId", run.traceId(),
                "runId", run.traceId(),
                "runStatus", run.status().name(),
                "terminal", run.status().terminal(),
                "trace", service.trace(run.traceId()).map(PixelLogicApiServer::traceView).orElse(null),
                "simulation", simulation
        );
    }

    private static TraceStepView traceStepView(TraceStep step) {
        return new TraceStepView(formatInstant(step.timestamp()), step.nodeId(), step.message());
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            server.stop(0);
        }
    }

    record ApiResponse(int status, String body) {
    }

    private record TraceView(String id, boolean truncated, List<TraceStepView> steps) {
    }

    private record OnlinePlayerQuery(String query, int limit, UUID selectedUuid) {
    }

    private record TraceStepView(String timestamp, String nodeId, String message) {
    }
}
