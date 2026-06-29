package com.pixelmc.pixellogic.server.api;

import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.trace.ExecutionTrace;
import com.pixelmc.pixellogic.core.trace.TraceStep;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class PixelLogicApiServer implements AutoCloseable {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 18111;
    public static final UUID WEBUI_DEMO_PLAYER_ID = UUID.nameUUIDFromBytes("pixel-logic-webui-demo-player".getBytes(StandardCharsets.UTF_8));

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
        httpServer.createContext("/api/pixellogic", apiServer::handle);
        httpServer.setExecutor(null);
        httpServer.start();
        return apiServer;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            ApiResponse response = dispatch(exchange.getRequestMethod(), exchange.getRequestURI().getPath());
            send(exchange, response.status(), response.body());
        } catch (Exception exception) {
            send(exchange, 500, errorJson("INTERNAL_ERROR", "API 处理失败。"));
        }
    }

    private ApiResponse dispatch(String method, String path) {
        if ("GET".equals(method) && "/api/pixellogic/status".equals(path)) {
            return onServerThread(() -> okJson(
                    "\"message\":" + quote(service.status()) + "," +
                            "\"api\":\"v1-spike\"," +
                            "\"demoActor\":" + demoActorJson()
            ));
        }
        if ("POST".equals(method) && "/api/pixellogic/test/reset".equals(path)) {
            return onServerThread(() -> {
                service.resetPlayer(WEBUI_DEMO_PLAYER_ID);
                return okJson("\"message\":\"WebUI 模拟测试状态已重置。\",\"demoActor\":" + demoActorJson());
            });
        }
        if ("POST".equals(method) && "/api/pixellogic/test/start".equals(path)) {
            return onServerThread(() -> {
                RuntimeResult result = service.startManualTest(WEBUI_DEMO_PLAYER_ID);
                Optional<ExecutionTrace> trace = result.traceId().isBlank() ? Optional.empty() : service.trace(result.traceId());
                if (!result.success()) {
                    return "{\"ok\":false,\"message\":" + quote(result.message()) +
                            ",\"traceId\":" + quote(result.traceId()) +
                            ",\"trace\":" + trace.map(PixelLogicApiServer::traceJson).orElse("null") +
                            ",\"error\":{\"code\":\"RUNTIME_FAILED\",\"message\":" + quote(result.message()) + "}}";
                }
                return okJson("\"message\":" + quote(result.message()) +
                        ",\"traceId\":" + quote(result.traceId()) +
                        ",\"trace\":" + trace.map(PixelLogicApiServer::traceJson).orElse("null") +
                        ",\"demoActor\":" + demoActorJson());
            });
        }
        if ("GET".equals(method) && "/api/pixellogic/traces/latest".equals(path)) {
            return onServerThread(() -> okJson("\"trace\":" + service.latestTrace().map(PixelLogicApiServer::traceJson).orElse("null")));
        }
        if ("GET".equals(method) && "/api/pixellogic/traces".equals(path)) {
            return onServerThread(() -> okJson("\"traces\":" + tracesJson(service.recentTraces())));
        }
        if (path.startsWith("/api/pixellogic/")) {
            return new ApiResponse(404, errorJson("NOT_FOUND", "API endpoint 不存在。"));
        }
        return new ApiResponse(404, errorJson("NOT_FOUND", "API endpoint 不存在。"));
    }

    private ApiResponse onServerThread(Callable<String> action) {
        CompletableFuture<ApiResponse> future = new CompletableFuture<>();
        serverThreadExecutor.execute(() -> {
            try {
                future.complete(new ApiResponse(200, action.call()));
            } catch (Exception exception) {
                String message = exception.getMessage() == null ? "API 执行失败。" : exception.getMessage();
                future.complete(new ApiResponse(500, errorJson("SERVER_THREAD_FAILED", message)));
            }
        });
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return new ApiResponse(503, errorJson("SERVER_THREAD_TIMEOUT", "等待服务器线程超时。"));
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static String okJson(String fields) {
        return "{\"ok\":true," + fields + "}";
    }

    private static String errorJson(String code, String message) {
        return "{\"ok\":false,\"error\":{\"code\":" + quote(code) + ",\"message\":" + quote(message) + "}}";
    }

    private static String demoActorJson() {
        return "{\"id\":" + quote(WEBUI_DEMO_PLAYER_ID.toString()) + ",\"label\":\"WebUI 模拟玩家\"}";
    }

    private static String tracesJson(List<ExecutionTrace> traces) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < traces.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(traceJson(traces.get(index)));
        }
        return builder.append(']').toString();
    }

    private static String traceJson(ExecutionTrace trace) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"id\":").append(quote(trace.id()))
                .append(",\"truncated\":").append(trace.truncated())
                .append(",\"steps\":[");
        List<TraceStep> steps = trace.steps();
        for (int index = 0; index < steps.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            TraceStep step = steps.get(index);
            builder.append("{\"timestamp\":").append(quote(formatInstant(step.timestamp())))
                    .append(",\"nodeId\":").append(quote(step.nodeId()))
                    .append(",\"message\":").append(quote(step.message()))
                    .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private record ApiResponse(int status, String body) {
    }
}
