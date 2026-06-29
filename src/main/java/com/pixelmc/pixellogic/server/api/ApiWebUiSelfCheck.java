package com.pixelmc.pixellogic.server.api;

import com.pixelmc.pixellogic.server.PixelLogicSpikeService;

import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

public final class ApiWebUiSelfCheck {
    private ApiWebUiSelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        try (PixelLogicSpikeService service = new PixelLogicSpikeService((playerId, message) -> {
        }, Runnable::run, ignored -> {
        }, Duration.ofSeconds(1));
             PixelLogicApiServer server = PixelLogicApiServer.start(service, Runnable::run, PixelLogicApiServer.DEFAULT_HOST, 0)) {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://" + PixelLogicApiServer.DEFAULT_HOST + ":" + server.port();

            String status = send(client, "GET", base + "/api/pixellogic/status");
            require(status.contains("\"ok\":true") && status.contains("demo-start-flow"), "status should return ready JSON");

            String reset = send(client, "POST", base + "/api/pixellogic/test/reset");
            require(reset.contains("\"ok\":true") && reset.contains("WebUI 模拟测试状态已重置"), "reset should return ok JSON");

            String first = send(client, "POST", base + "/api/pixellogic/test/start");
            require(first.contains("\"ok\":true") && first.contains("\"traceId\""), "first start should return trace id");
            require(first.contains("条件通过") && first.contains("计时器启动"), "first start should include pass branch and timer start trace");

            String latest = waitFor(client, base + "/api/pixellogic/traces/latest", "计时器完成");
            require(latest.contains("\"ok\":true") && latest.contains("计时器完成"), "latest trace should be JSON");

            String second = send(client, "POST", base + "/api/pixellogic/test/start");
            require(second.contains("\"ok\":true") && second.contains("条件失败") && second.contains("玩家已经开始过游戏"),
                    "second start should include fail branch trace");

            send(client, "POST", base + "/api/pixellogic/test/reset");
            String afterReset = send(client, "POST", base + "/api/pixellogic/test/start");
            require(afterReset.contains("\"ok\":true") && afterReset.contains("条件通过"),
                    "reset should clear demo actor state so the next start passes");

            String traces = send(client, "GET", base + "/api/pixellogic/traces");
            require(traces.contains("\"traces\"") && traces.contains("玩家已经开始过游戏"), "traces endpoint should return recent traces");

            String missing = send(client, "GET", base + "/api/pixellogic/missing");
            require(missing.contains("\"ok\":false") && missing.contains("\"error\"") && missing.contains("NOT_FOUND"),
                    "missing endpoint should return JSON error shape");

            String apiRoot = send(client, "GET", base + "/api");
            require(apiRoot.contains("\"ok\":false") && apiRoot.contains("\"error\"") && apiRoot.contains("NOT_FOUND"),
                    "api root should return JSON error shape");
        }
    }

    private static String send(HttpClient client, String method, String uri) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri));
        if ("POST".equals(method)) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.GET();
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String waitFor(HttpClient client, String uri, String expectedText) throws Exception {
        String body = "";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            body = send(client, "GET", uri);
            if (body.contains(expectedText)) {
                return body;
            }
            Thread.sleep(50L);
        }
        return body;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
