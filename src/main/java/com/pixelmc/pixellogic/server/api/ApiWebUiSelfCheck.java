package com.pixelmc.pixellogic.server.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;
import com.pixelmc.pixellogic.server.storage.GraphStorageService;
import com.pixelmc.pixellogic.core.runtime.RuntimeEntityProvider;

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
        RuntimeEntityProvider devProvider = ApiWebUiDevServer.devEntityProvider();
        require(devProvider.statusEffectExists("minecraft:speed")
                        && !devProvider.statusEffectExists("example:not_registered"),
                "standalone dev server should expose an explicit fail-closed status-effect fixture");
        try (PixelLogicSpikeService service = new PixelLogicSpikeService((playerId, message) -> {
        }, Runnable::run, ignored -> {
        }, Duration.ofSeconds(1), storageRoot, devProvider);
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
            JsonObject catalogSnapshot = JsonParser.parseString(catalog.body()).getAsJsonObject().getAsJsonObject("catalog");
            require(catalogSnapshot.has("packs") && catalogSnapshot.has("categories")
                            && catalogSnapshot.has("subcategories") && catalogSnapshot.has("blocks"),
                    "catalog endpoint should extend the existing snapshot with packs");
            require(catalogSnapshot.getAsJsonArray("subcategories").size()
                            == catalogSnapshot.getAsJsonArray("categories").size()
                            && catalogSnapshot.getAsJsonArray("categories").get(0).getAsJsonObject().has("visibleByDefault"),
                    "catalog should retain its derived subcategory and category compatibility metadata");
            JsonObject manualBlock = catalogSnapshot.getAsJsonArray("blocks").asList().stream()
                    .map(element -> element.getAsJsonObject())
                    .filter(block -> "trigger.manual_test".equals(block.get("id").getAsString()))
                    .findFirst()
                    .orElseThrow();
            require(manualBlock.has("subcategoryId") && manualBlock.has("tags") && manualBlock.has("hidden")
                            && manualBlock.has("aliases") && manualBlock.has("searchKeywords")
                            && manualBlock.has("visibility")
                            && manualBlock.getAsJsonArray("aliases").get(0).getAsString().equals("manual.test.start"),
                    "catalog blocks should retain legacy metadata while adding taxonomy metadata");
            require(catalog.body().contains("trigger.manual_test") && catalog.body().contains("condition.state.equals"),
                    "catalog should include demo block ids");
            require(catalog.body().contains("\"formSchema\"") && catalog.body().contains("rich_text_component")
                            && catalog.body().contains("\"summaryTemplate\"")
                            && catalog.body().contains("\"predicateSummaryTemplate\"")
                            && catalog.body().contains("\"predicateNegatedSummaryTemplate\"")
                            && catalog.body().contains("\"capabilities\"")
                            && catalog.body().contains("control.loop.until"),
                    "catalog should expose form, predicate capability, summary metadata, and loop until");

            CheckedResponse onlinePlayers = send(client, "GET", base + "/api/pixellogic/runtime/online-players?query=Dev&limit=20");
            require(onlinePlayers.body().contains("DevPlayer")
                            && onlinePlayers.body().contains(ApiWebUiDevServer.DEV_PLAYER_ID.toString()),
                    "standalone dev server should expose its selectable online-player fixture");

            CheckedResponse graphResponse = send(client, "GET", base + "/api/pixellogic/graphs/demo-start-flow");
            requireJson(graphResponse, "graph should be JSON");
            require(graphResponse.body().contains("\"fingerprint\"") && graphResponse.body().contains("\"validation\""),
                    "graph endpoint should return graph, fingerprint, and validation");
            require(graphResponse.body().contains("\"blockId\"") && graphResponse.body().contains("trigger.manual_test"),
                    "graph endpoint should return blockId for catalog migration");
            require(graphResponse.body().contains("\"conditionSlots\""),
                    "graph endpoint should expose additive typed condition slot data");

            JsonObject graph = JsonParser.parseString(graphResponse.body()).getAsJsonObject().getAsJsonObject("graph");
            require(!graph.toString().contains("\"packId\"") && !graph.toString().contains("\"categoryId\"")
                            && !graph.toString().contains("\"visibility\"") && !graph.toString().contains("\"searchKeywords\""),
                    "graph payload must not persist library taxonomy metadata");
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

            JsonObject delayMessageGraph = delayMessageGraph(graph, "API continuation message");
            commitGraph(client, base, delayMessageGraph);
            send(client, "POST", base + "/api/pixellogic/test/reset");
            JsonObject delayedStart = json(send(client, "POST", base + "/api/pixellogic/test/start"));
            require("WAITING".equals(delayedStart.get("runStatus").getAsString())
                            && !delayedStart.get("terminal").getAsBoolean(),
                    "delay start should expose a non-terminal run identity");
            String delayedRunId = delayedStart.get("runId").getAsString();
            JsonObject delayedComplete = waitForRun(client, base, delayedRunId, 4);
            require("COMPLETED".equals(delayedComplete.get("runStatus").getAsString())
                            && delayedComplete.get("terminal").getAsBoolean(),
                    "delay run should become completed through the run status endpoint");
            require(traceMessageCount(delayedComplete, "API continuation message") == 1,
                    "delay resume should expose the message trace exactly once");

            JsonObject loopGraph = loopDelayMessageGraph(delayMessageGraph, "API loop message");
            commitGraph(client, base, loopGraph);
            send(client, "POST", base + "/api/pixellogic/test/reset");
            JsonObject loopStart = json(send(client, "POST", base + "/api/pixellogic/test/start"));
            String loopRunId = loopStart.get("runId").getAsString();
            require("WAITING".equals(loopStart.get("runStatus").getAsString()),
                    "loop delay should initially remain waiting");
            JsonObject loopComplete = waitForRun(client, base, loopRunId, 6);
            require("COMPLETED".equals(loopComplete.get("runStatus").getAsString()),
                    "loop delay run should complete after three resumptions");
            require(traceMessageCount(loopComplete, "API loop message") == 3,
                    "loop delay run should expose exactly three resumed message traces");
            require(traceMessageCount(loopComplete, "循环完成") == 1,
                    "loop delay run should complete the loop exactly once");

            commitGraph(client, base, delayMessageGraph);
            send(client, "POST", base + "/api/pixellogic/test/reset");
            JsonObject resetStart = json(send(client, "POST", base + "/api/pixellogic/test/start"));
            String resetRunId = resetStart.get("runId").getAsString();
            JsonObject resetResponse = json(send(client, "POST", base + "/api/pixellogic/test/reset"));
            require(resetResponse.get("ok").getAsBoolean(), "reset during delay should succeed");
            Thread.sleep(1_100L);
            require(service.trace(resetRunId).orElseThrow().steps().stream()
                            .noneMatch(step -> step.message().contains("API continuation message")),
                    "reset must prevent the old delayed message from executing");

            JsonObject oldStart = json(send(client, "POST", base + "/api/pixellogic/test/start"));
            String oldRunId = oldStart.get("runId").getAsString();
            JsonObject replacementStart = json(send(client, "POST", base + "/api/pixellogic/test/start"));
            String replacementRunId = replacementStart.get("runId").getAsString();
            JsonObject replacementComplete = waitForRun(client, base, replacementRunId, 4);
            require("COMPLETED".equals(replacementComplete.get("runStatus").getAsString()),
                    "replacement run should complete normally");
            require(service.trace(oldRunId).orElseThrow().steps().stream()
                            .noneMatch(step -> step.message().contains("API continuation message")),
                    "starting a new run must prevent the old delayed message from executing");

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

    private static JsonObject delayMessageGraph(JsonObject source, String message) {
        JsonObject graph = source.deepCopy();
        keepNodes(graph, "manual-trigger", "timer-start", "welcome-message", "debug-finished");
        setNodeConfig(graph, "timer-start", "durationSeconds", "1");
        setNodeConfig(graph, "welcome-message", "message", message);
        setMembership(graph, "timer-start", "", "");
        setMembership(graph, "welcome-message", "", "");
        setMembership(graph, "debug-finished", "", "");
        graph.add("edges", edges(
                edge("delay-entry", "manual-trigger", "started", "timer-start"),
                edge("delay-message", "timer-start", "timer_completed", "welcome-message")
        ));
        return graph;
    }

    private static JsonObject loopDelayMessageGraph(JsonObject source, String message) {
        JsonObject graph = source.deepCopy();
        setNodeConfig(graph, "welcome-message", "message", message);
        JsonArray nodes = graph.getAsJsonArray("nodes");
        nodes.add(JsonParser.parseString("""
                {
                  "id":"loop",
                  "type":"CONTROL_LOOP_COUNT",
                  "blockId":"control.loop.count",
                  "parentContainerId":"",
                  "parentSlot":"",
                  "displayName":"循环次数",
                  "config":{"count":"3"},
                  "position":{"x":294,"y":78},
                  "slots":[
                    {"id":"input","direction":"INPUT","edgeType":"CONTROL"},
                    {"id":"done","direction":"OUTPUT","edgeType":"CONTROL"}
                  ]
                }
                """).getAsJsonObject());
        setMembership(graph, "timer-start", "loop", "body");
        setMembership(graph, "welcome-message", "loop", "body");
        graph.add("edges", edges(
                edge("loop-entry", "manual-trigger", "started", "loop"),
                edge("loop-body", "timer-start", "timer_completed", "welcome-message"),
                edge("loop-done", "loop", "done", "debug-finished")
        ));
        return graph;
    }

    private static void keepNodes(JsonObject graph, String... nodeIds) {
        JsonArray nodes = new JsonArray();
        for (String nodeId : nodeIds) {
            nodes.add(node(graph, nodeId).deepCopy());
        }
        graph.add("nodes", nodes);
    }

    private static void setMembership(JsonObject graph, String nodeId, String parentId, String parentSlot) {
        JsonObject node = node(graph, nodeId);
        node.addProperty("parentContainerId", parentId);
        node.addProperty("parentSlot", parentSlot);
    }

    private static JsonObject node(JsonObject graph, String nodeId) {
        for (var nodeElement : graph.getAsJsonArray("nodes")) {
            JsonObject node = nodeElement.getAsJsonObject();
            if (nodeId.equals(node.get("id").getAsString())) {
                return node;
            }
        }
        throw new IllegalStateException("node not found: " + nodeId);
    }

    private static JsonArray edges(JsonObject... edges) {
        JsonArray result = new JsonArray();
        for (JsonObject edge : edges) {
            result.add(edge);
        }
        return result;
    }

    private static JsonObject edge(String id, String sourceNodeId, String sourceSlotId, String targetNodeId) {
        return JsonParser.parseString("""
                {
                  "id":"%s",
                  "sourceNodeId":"%s",
                  "sourceSlotId":"%s",
                  "targetNodeId":"%s",
                  "targetSlotId":"input",
                  "type":"CONTROL"
                }
                """.formatted(id, sourceNodeId, sourceSlotId, targetNodeId)).getAsJsonObject();
    }

    private static void commitGraph(HttpClient client, String base, JsonObject graph) throws Exception {
        CheckedResponse draft = send(client, "PUT", base + "/api/pixellogic/graphs/demo-start-flow/draft", "{\"graph\":" + graph + "}");
        require(draft.body().contains("\"ok\":true"), "integration graph draft should save");
        CheckedResponse validation = send(client, "POST", base + "/api/pixellogic/graphs/demo-start-flow/validate");
        require(validation.body().contains("\"valid\":true"), "integration graph should validate");
        CheckedResponse commit = send(client, "POST", base + "/api/pixellogic/graphs/demo-start-flow/commit");
        require(commit.body().contains("\"ok\":true"), "integration graph should commit");
    }

    private static JsonObject waitForRun(HttpClient client, String base, String runId, int seconds) throws Exception {
        JsonObject response = new JsonObject();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            response = json(send(client, "GET", base + "/api/pixellogic/test/runs/" + runId));
            if (response.has("terminal") && response.get("terminal").getAsBoolean()) {
                return response;
            }
            Thread.sleep(50L);
        }
        return response;
    }

    private static int traceMessageCount(JsonObject response, String text) {
        int count = 0;
        for (var step : response.getAsJsonObject("trace").getAsJsonArray("steps")) {
            if (step.getAsJsonObject().get("message").getAsString().contains(text)) {
                count += 1;
            }
        }
        return count;
    }

    private static JsonObject json(CheckedResponse response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
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
