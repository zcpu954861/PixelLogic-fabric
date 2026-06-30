package com.pixelmc.pixellogic.server.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;
import com.pixelmc.pixellogic.server.storage.GraphStorageService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class ApiWebUiSelfCheck {
    private ApiWebUiSelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        run("apiWebUiSelfCheck", () -> {
        Path storageRoot = Files.createTempDirectory("pixel-logic-api-self-check-");
        try (PixelLogicSpikeService service = new PixelLogicSpikeService((playerId, message) -> {
        }, Runnable::run, ignored -> {
        }, Duration.ofSeconds(1), storageRoot);
             PixelLogicApiServer server = PixelLogicApiServer.start(service, Runnable::run, PixelLogicApiServer.DEFAULT_HOST, 0)) {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://" + PixelLogicApiServer.DEFAULT_HOST + ":" + server.port();

            CheckedResponse status = send(client, "GET", base + "/api/pixellogic/status");
            requireJson(status, "status should be JSON");
            require(status.body().contains("\"ok\":true") && status.body().contains("demo-start-flow"), "status should return ready JSON");

            CheckedResponse graphs = send(client, "GET", base + "/api/pixellogic/graphs");
            requireJson(graphs, "graphs should be JSON");
            require(graphs.body().contains("\"graphs\"") && graphs.body().contains("demo-start-flow"), "graphs should list seeded graph");

            CheckedResponse catalog = send(client, "GET", base + "/api/pixellogic/catalog");
            requireJson(catalog, "catalog should be JSON");
            require(catalog.body().contains("\"categories\"") && catalog.body().contains("\"blocks\""),
                    "catalog endpoint should return categories and blocks");
            require(catalog.body().contains("trigger.manual_test") && catalog.body().contains("condition.state.equals"),
                    "catalog should include demo block ids");
            require(catalog.body().contains("\"formSchema\"") && catalog.body().contains("rich_text_component")
                            && catalog.body().contains("\"summaryTemplate\""),
                    "catalog should expose form schema, rich text field type, and summary metadata");

            CheckedResponse graphResponse = send(client, "GET", base + "/api/pixellogic/graphs/demo-start-flow");
            requireJson(graphResponse, "graph should be JSON");
            require(graphResponse.body().contains("\"fingerprint\"") && graphResponse.body().contains("\"validation\""),
                    "graph endpoint should return graph, fingerprint, and validation");
            require(graphResponse.body().contains("\"blockId\"") && graphResponse.body().contains("trigger.manual_test"),
                    "graph endpoint should return blockId for catalog migration");

            JsonObject graph = JsonParser.parseString(graphResponse.body()).getAsJsonObject().getAsJsonObject("graph");
            setNodeConfig(graph, "welcome-message", "message", "API self-check welcome");
            CheckedResponse draft = send(
                    client,
                    "PUT",
                    base + "/api/pixellogic/graphs/demo-start-flow/draft",
                    "{\"graph\":" + graph + "}"
            );
            requireJson(draft, "draft save should be JSON");
            require(draft.body().contains("\"ok\":true") && draft.body().contains("草稿已保存"), "draft save should return ok JSON");

            CheckedResponse validate = send(client, "POST", base + "/api/pixellogic/graphs/demo-start-flow/validate");
            requireJson(validate, "validate should be JSON");
            require(validate.body().contains("\"valid\":true"), "valid draft should validate");

            CheckedResponse commit = send(client, "POST", base + "/api/pixellogic/graphs/demo-start-flow/commit");
            requireJson(commit, "commit should be JSON");
            require(commit.body().contains("\"ok\":true") && commit.body().contains("提交生效"), "valid draft should commit");

            CheckedResponse reset = send(client, "POST", base + "/api/pixellogic/test/reset");
            require(reset.body().contains("\"ok\":true") && reset.body().contains("WebUI 模拟测试状态已重置"), "reset should return ok JSON");

            CheckedResponse first = send(client, "POST", base + "/api/pixellogic/test/start");
            require(first.body().contains("\"ok\":true") && first.body().contains("\"traceId\""), "first start should return trace id");
            require(first.body().contains("条件通过") && first.body().contains("API self-check welcome"),
                    "first start should use committed graph and include pass branch trace");

            CheckedResponse latest = waitFor(client, base + "/api/pixellogic/traces/latest", "计时器完成");
            require(latest.body().contains("\"ok\":true") && latest.body().contains("计时器完成"), "latest trace should be JSON");

            CheckedResponse second = send(client, "POST", base + "/api/pixellogic/test/start");
            require(second.body().contains("\"ok\":true") && second.body().contains("条件失败") && second.body().contains("玩家已经开始过游戏"),
                    "second start should include fail branch trace");

            send(client, "POST", base + "/api/pixellogic/test/reset");
            CheckedResponse afterReset = send(client, "POST", base + "/api/pixellogic/test/start");
            require(afterReset.body().contains("\"ok\":true") && afterReset.body().contains("条件通过"),
                    "reset should clear demo actor state so the next start passes");

            CheckedResponse traces = send(client, "GET", base + "/api/pixellogic/traces");
            require(traces.body().contains("\"traces\"") && traces.body().contains("玩家已经开始过游戏"), "traces endpoint should return recent traces");

            CheckedResponse invalidGraphId = send(client, "GET", base + "/api/pixellogic/graphs/../bad");
            requireJson(invalidGraphId, "invalid graph id should be JSON");
            require(invalidGraphId.body().contains("\"ok\":false") && invalidGraphId.body().contains("INVALID_GRAPH_ID"),
                    "path traversal graph id should be rejected");

            CheckedResponse missing = send(client, "GET", base + "/api/pixellogic/missing");
            require(missing.body().contains("\"ok\":false") && missing.body().contains("\"error\"") && missing.body().contains("NOT_FOUND"),
                    "missing endpoint should return JSON error shape");

            CheckedResponse apiRoot = send(client, "GET", base + "/api");
            require(apiRoot.body().contains("\"ok\":false") && apiRoot.body().contains("\"error\"") && apiRoot.body().contains("NOT_FOUND"),
                    "api root should return JSON error shape");
        }
        });
    }

    private static void setNodeConfig(JsonObject graph, String nodeId, String key, String value) {
        for (var nodeElement : graph.getAsJsonArray("nodes")) {
            JsonObject node = nodeElement.getAsJsonObject();
            if (nodeId.equals(node.get("id").getAsString())) {
                node.getAsJsonObject("config").addProperty(key, value);
                return;
            }
        }
        throw new IllegalStateException("node not found: " + nodeId);
    }

    private static CheckedResponse send(HttpClient client, String method, String uri) throws Exception {
        return send(client, method, uri, "");
    }

    private static CheckedResponse send(HttpClient client, String method, String uri, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri));
        if ("POST".equals(method)) {
            if (body == null || body.isBlank()) {
                request.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json");
                request.POST(HttpRequest.BodyPublishers.ofString(body));
            }
        } else if ("PUT".equals(method)) {
            request.header("Content-Type", "application/json");
            request.PUT(HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.GET();
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new CheckedResponse(response.headers().firstValue("content-type").orElse(""), response.body());
    }

    private static CheckedResponse waitFor(HttpClient client, String uri, String expectedText) throws Exception {
        CheckedResponse response = new CheckedResponse("", "");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            response = send(client, "GET", uri);
            if (response.body().contains(expectedText)) {
                return response;
            }
            Thread.sleep(50L);
        }
        return response;
    }

    private static void requireJson(CheckedResponse response, String message) {
        require(response.contentType().toLowerCase().contains("application/json"), message);
    }

    private record CheckedResponse(String contentType, String body) {
    }
}
