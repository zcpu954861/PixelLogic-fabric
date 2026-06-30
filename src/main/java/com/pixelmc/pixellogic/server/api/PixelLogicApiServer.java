package com.pixelmc.pixellogic.server.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PixelLogicApiServer implements AutoCloseable {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 18111;
    public static final UUID WEBUI_DEMO_PLAYER_ID = UUID.nameUUIDFromBytes("pixel-logic-webui-demo-player".getBytes(StandardCharsets.UTF_8));

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern GRAPH_PATH = Pattern.compile("^/api/pixellogic/graphs/([A-Za-z0-9_-]+)(?:/(draft|validate|commit))?$");

    private final PixelLogicSpikeService service;
    private final Executor serverThreadExecutor;
    private final HttpServer server;

    private PixelLogicApiServer(PixelLogicSpikeService service, Executor serverThreadExecutor, HttpServer server) {
        this.service = service;
        this.serverThreadExecutor = serverThreadExecutor;
        this.server = server;
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
        PixelLogicApiServer apiServer = new PixelLogicApiServer(service, serverThreadExecutor, httpServer);
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
            ApiResponse response = dispatch(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body);
            send(exchange, response.status(), response.body());
        } catch (Exception exception) {
            send(exchange, 500, errorBody("INTERNAL_ERROR", "API 处理失败。"));
        }
    }

    private ApiResponse dispatch(String method, String path, String body) {
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
            return onServerThread(() -> {
                RuntimeResult result = service.startManualTest(WEBUI_DEMO_PLAYER_ID);
                Optional<ExecutionTrace> trace = result.traceId().isBlank() ? Optional.empty() : service.trace(result.traceId());
                Map<String, Object> fields = fields(
                        "message", result.message(),
                        "traceId", result.traceId(),
                        "trace", trace.map(PixelLogicApiServer::traceView).orElse(null),
                        "demoActor", demoActor()
                );
                if (!result.success()) {
                    fields.put("error", errorMap("RUNTIME_FAILED", result.message()));
                    return json(500, false, fields);
                }
                return ok(fields);
            });
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
        CompletableFuture<ApiResponse> future = new CompletableFuture<>();
        serverThreadExecutor.execute(() -> {
            try {
                future.complete(action.call());
            } catch (IllegalArgumentException exception) {
                future.complete(error(400, "BAD_REQUEST", exception.getMessage()));
            } catch (IllegalStateException exception) {
                future.complete(error(409, "CONFLICT", exception.getMessage()));
            } catch (Exception exception) {
                String message = exception.getMessage() == null ? "API 执行失败。" : exception.getMessage();
                future.complete(error(500, "SERVER_THREAD_FAILED", message));
            }
        });
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return error(503, "SERVER_THREAD_TIMEOUT", "等待服务器线程超时。");
        }
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
        return fields("id", WEBUI_DEMO_PLAYER_ID.toString(), "label", "WebUI 模拟玩家");
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

    private static TraceStepView traceStepView(TraceStep step) {
        return new TraceStepView(formatInstant(step.timestamp()), step.nodeId(), step.message());
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private record ApiResponse(int status, String body) {
    }

    private record TraceView(String id, boolean truncated, List<TraceStepView> steps) {
    }

    private record TraceStepView(String timestamp, String nodeId, String message) {
    }
}
