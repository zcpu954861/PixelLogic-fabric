package com.pixelmc.pixellogic.server.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pixelmc.pixellogic.core.graph.DemoGraphFactory;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.EntityTargetRef;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;
import com.pixelmc.pixellogic.server.storage.GraphDocument;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class SimulationTestContextSelfCheck {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private SimulationTestContextSelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        run("simulationTestContextSelfCheck", () -> {
        Path storageRoot = Files.createTempDirectory("pixel-logic-simulation-context-self-check-");
        try (PixelLogicSpikeService service = new PixelLogicSpikeService((playerId, message) -> {
        }, Runnable::run, ignored -> {
        }, Duration.ofSeconds(1), storageRoot);
             PixelLogicApiServer server = PixelLogicApiServer.start(service, Runnable::run, PixelLogicApiServer.DEFAULT_HOST, 0)) {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://" + PixelLogicApiServer.DEFAULT_HOST + ":" + server.port();
            installGraph(client, base, tagGraph(), "Simulation Test Context Self Check");

            JsonObject defaultRun = postJson(client, base + "/api/pixellogic/test/start", "");
            JsonObject defaultSimulation = defaultRun.getAsJsonObject("simulation");
            require(defaultSimulation.get("actorDisplayName").getAsString().equals("WebUI 模拟玩家"),
                    "missing testContext should use default WebUI actor");
            require(!defaultSimulation.get("actorOperator").getAsBoolean(),
                    "missing operator should default to false");
            require(tags(defaultSimulation, "initialActorTags").isEmpty(),
                    "default actor should start with no tags");
            require(tags(defaultSimulation, "actorTags").contains("ready"),
                    "generic add-tag should add ready during the run");
            require(trace(defaultRun).contains("实体 WebUI 模拟玩家 没有标签「runner」"),
                    "empty default tags should fail the runner condition");

            JsonObject taggedRun = postJson(client, base + "/api/pixellogic/test/start", """
                    {"testContext":{
                      "actor":{"displayName":"测试逃生者","tags":["runner"],"operator":true,"health":12,"maxHealth":30,"invulnerable":true},
                      "world":{"targetEntity":{"enabled":true,"entityTypeId":"minecraft:zombie","displayName":"测试僵尸","tags":[],"living":true,"health":8,"maxHealth":40,"invulnerable":false}}
                    }}
                    """);
            JsonObject taggedSimulation = taggedRun.getAsJsonObject("simulation");
            require(taggedSimulation.get("actorDisplayName").getAsString().equals("测试逃生者"),
                    "displayName should flow into simulation result");
            require(taggedSimulation.get("actorOperator").getAsBoolean(),
                    "operator flag should flow into simulation result");
            require(taggedSimulation.get("initialActorHealth").getAsDouble() == 12
                            && taggedSimulation.get("actorHealth").getAsDouble() == 12
                            && taggedSimulation.get("actorMaxHealth").getAsDouble() == 30,
                    "actor health fixture should flow through the isolated simulation copy");
            require(taggedSimulation.get("initialTargetEntityHealth").getAsDouble() == 8
                            && taggedSimulation.get("targetEntityHealth").getAsDouble() == 8
                            && taggedSimulation.get("targetEntityMaxHealth").getAsDouble() == 40,
                    "target entity health fixture should flow into simulation results");
            require(tags(taggedSimulation, "initialActorTags").contains("runner"),
                    "initial tags should include request tags");
            require(!tags(taggedSimulation, "actorTags").contains("ready"),
                    "pass branch should not run add_tag action");
            require(trace(taggedRun).contains("实体 测试逃生者 拥有标签「runner」"),
                    "trace should use the configured displayName");

            JsonObject secondEmptyRun = postJson(client, base + "/api/pixellogic/test/start", """
                    {"testContext":{"actor":{"displayName":"无标签玩家","tags":[],"operator":false}}}
                    """);
            JsonObject secondEmptySimulation = secondEmptyRun.getAsJsonObject("simulation");
            require(tags(secondEmptySimulation, "initialActorTags").isEmpty(),
                    "previous action tags should not persist into the next run");
            require(tags(secondEmptySimulation, "actorTags").contains("ready"),
                    "empty-tag run should add ready again");
            require(trace(secondEmptyRun).contains("实体 无标签玩家 没有标签「runner」"),
                    "empty request tags should fail the condition");

            JsonObject duplicateRun = postJson(client, base + "/api/pixellogic/test/start", """
                    {"testContext":{"actor":{"displayName":"去重玩家","tags":[" runner ","runner",""],"operator":false}}}
                    """);
            require(tags(duplicateRun.getAsJsonObject("simulation"), "initialActorTags").size() == 1,
                    "tags should be trimmed and deduplicated");

            JsonObject badTag = postJson(client, base + "/api/pixellogic/test/start", """
                    {"testContext":{"actor":{"displayName":"坏标签","tags":["bad\\nvalue"]}}}
                    """);
            require(!badTag.get("ok").getAsBoolean() && badTag.getAsJsonObject("error").get("code").getAsString().equals("BAD_TEST_CONTEXT"),
                    "control characters in tags should be rejected");

            JsonObject badHealth = postJson(client, base + "/api/pixellogic/test/start", """
                    {"testContext":{"actor":{"displayName":"坏生命值","health":21,"maxHealth":20}}}
                    """);
            require(!badHealth.get("ok").getAsBoolean()
                            && badHealth.getAsJsonObject("error").get("code").getAsString().equals("BAD_TEST_CONTEXT"),
                    "health above maximum should be rejected at the API trust boundary");

            JsonObject badActorBoolean = postJson(client, base + "/api/pixellogic/test/start", """
                    {"testContext":{"actor":{"displayName":"坏布尔值","invulnerable":"yes"}}}
                    """);
            require(!badActorBoolean.get("ok").getAsBoolean()
                            && badActorBoolean.getAsJsonObject("error").get("code").getAsString().equals("BAD_TEST_CONTEXT"),
                    "actor health flags should require JSON booleans");

            JsonObject badTargetBoolean = postJson(client, base + "/api/pixellogic/test/start", """
                    {"testContext":{"world":{"targetEntity":{"enabled":true,"living":{}}}}}
                    """);
            require(!badTargetBoolean.get("ok").getAsBoolean()
                            && badTargetBoolean.getAsJsonObject("error").get("code").getAsString().equals("BAD_TEST_CONTEXT"),
                    "target entity health flags should reject non-boolean JSON without a 500");

            JsonObject badActorHealthType = postJson(client, base + "/api/pixellogic/test/start", """
                    {"testContext":{"actor":{"health":"20"}}}
                    """);
            require(!badActorHealthType.get("ok").getAsBoolean()
                            && badActorHealthType.getAsJsonObject("error").get("code").getAsString().equals("BAD_TEST_CONTEXT"),
                    "actor health should require a JSON number");

            JsonObject badTargetName = postJson(client, base + "/api/pixellogic/test/start", """
                    {"testContext":{"world":{"targetEntity":{"enabled":true,"displayName":{}}}}}
                    """);
            require(!badTargetName.get("ok").getAsBoolean()
                            && badTargetName.getAsJsonObject("error").get("code").getAsString().equals("BAD_TEST_CONTEXT"),
                    "string fields should reject object JSON without a 500");

            installGraph(client, base, removePlayerGraph(), "Immediate Failure Result Self Check");
            JsonObject failedRun = postJson(client, base + "/api/pixellogic/test/start", "");
            JsonObject failedSimulation = failedRun.getAsJsonObject("simulation");
            JsonObject failedOutcome = failedSimulation.getAsJsonArray("actionResults")
                    .get(0).getAsJsonObject().getAsJsonObject("outcome");
            JsonObject failedActionError = failedSimulation.getAsJsonObject("actionError");
            require(failedRun.get("ok").getAsBoolean()
                            && failedRun.get("runStatus").getAsString().equals("FAILED")
                            && failedRun.get("terminal").getAsBoolean()
                            && !failedSimulation.get("success").getAsBoolean()
                            && failedSimulation.getAsJsonObject("actionError").get("code").getAsString()
                            .equals("entity_remove_player_forbidden")
                            && failedOutcome.get("status").getAsString().equals("FAILURE")
                            && failedOutcome.get("code").getAsString().equals("entity_remove_player_forbidden")
                            && failedActionError.get("code").getAsString().equals(failedOutcome.get("code").getAsString())
                            && failedActionError.get("message").getAsString().equals(failedOutcome.get("message").getAsString()),
                    "an accepted immediate runtime failure should remain a readable 200 result with its typed outcome");
        }
        });
    }

    private static void installGraph(HttpClient client, String base, GraphDefinition graph, String title) throws Exception {
        GraphDocument document = GraphDocument.fromGraphDefinition(graph, title);
        JsonObject draft = postJson(client, base + "/api/pixellogic/graphs/demo-start-flow/draft", "{\"graph\":" + GSON.toJson(document) + "}", "PUT");
        require(draft.get("ok").getAsBoolean(), "tag graph draft should save");
        JsonObject validation = postJson(client, base + "/api/pixellogic/graphs/demo-start-flow/validate", "");
        require(validation.getAsJsonObject("validation").get("valid").getAsBoolean(), "tag graph draft should validate");
        JsonObject commit = postJson(client, base + "/api/pixellogic/graphs/demo-start-flow/commit", "");
        require(commit.get("ok").getAsBoolean(), "tag graph draft should commit");
    }

    private static JsonObject postJson(HttpClient client, String uri, String body) throws Exception {
        return postJson(client, uri, body, "POST");
    }

    private static JsonObject postJson(HttpClient client, String uri, String body, String method) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri)).header("Accept", "application/json");
        if ("PUT".equals(method)) {
            request.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body));
        } else if (body == null || body.isBlank()) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        require(response.headers().firstValue("content-type").orElse("").toLowerCase().contains("application/json"),
                "API response should be JSON");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static List<String> tags(JsonObject simulation, String field) {
        JsonArray tags = simulation.getAsJsonArray(field);
        return tags.asList().stream().map(element -> element.getAsString()).toList();
    }

    private static String trace(JsonObject response) {
        JsonArray steps = response.getAsJsonObject("trace").getAsJsonArray("steps");
        return steps.asList().stream()
                .map(element -> element.getAsJsonObject().get("message").getAsString())
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private static GraphDefinition tagGraph() {
        return new GraphDefinition(
                "demo-start-flow",
                List.of(
                        node("manual-trigger", NodeType.MANUAL_TRIGGER, out("started"), Map.of()),
                        node("has-runner-tag", NodeType.ENTITY_HAS_TAG_CONDITION, in("input"), out("pass"), out("fail"), Map.of("target", EntityTargetRef.currentEntity().toJson(), "tag", "runner")),
                        node("debug-has-tag", NodeType.DEBUG_LOG_ACTION, in("input"), out("done"), Map.of("message", "已有 runner 标签")),
                        node("add-ready-tag", NodeType.ENTITY_ADD_TAG_ACTION, in("input"), out("done"), Map.of("target", EntityTargetRef.currentEntity().toJson(), "tag", "ready")),
                        node("debug-added-ready", NodeType.DEBUG_LOG_ACTION, in("input"), out("done"), Map.of("message", "已添加 ready 标签"))
                ),
                List.of(
                        edge("e1", "manual-trigger", "started", "has-runner-tag", "input"),
                        edge("e2", "has-runner-tag", "pass", "debug-has-tag", "input"),
                        edge("e3", "has-runner-tag", "fail", "add-ready-tag", "input"),
                        edge("e4", "add-ready-tag", "done", "debug-added-ready", "input")
                ),
                Map.of(DemoGraphFactory.TRIGGER_TYPE, "manual-trigger")
        );
    }

    private static GraphDefinition removePlayerGraph() {
        return new GraphDefinition(
                "demo-start-flow",
                List.of(
                        node("manual-trigger", NodeType.MANUAL_TRIGGER, out("started"), Map.of()),
                        node(
                                "remove-player",
                                NodeType.ENTITY_REMOVE_ACTION,
                                in("input"),
                                out("done"),
                                Map.of("target", EntityTargetRef.currentEntity().toJson())
                        )
                ),
                List.of(edge("e1", "manual-trigger", "started", "remove-player", "input")),
                Map.of(DemoGraphFactory.TRIGGER_TYPE, "manual-trigger")
        );
    }

    private static NodeDefinition node(String id, NodeType type, SlotDefinition slot, Map<String, String> config) {
        return new NodeDefinition(id, type, List.of(slot), config);
    }

    private static NodeDefinition node(String id, NodeType type, SlotDefinition input, SlotDefinition output, Map<String, String> config) {
        return new NodeDefinition(id, type, List.of(input, output), config);
    }

    private static NodeDefinition node(
            String id,
            NodeType type,
            SlotDefinition input,
            SlotDefinition outputA,
            SlotDefinition outputB,
            Map<String, String> config
    ) {
        return new NodeDefinition(id, type, List.of(input, outputA, outputB), config);
    }

    private static SlotDefinition in(String id) {
        return new SlotDefinition(id, SlotDirection.INPUT, EdgeType.CONTROL);
    }

    private static SlotDefinition out(String id) {
        return new SlotDefinition(id, SlotDirection.OUTPUT, EdgeType.CONTROL);
    }

    private static EdgeDefinition edge(String id, String sourceNode, String sourceSlot, String targetNode, String targetSlot) {
        return new EdgeDefinition(id, sourceNode, sourceSlot, targetNode, targetSlot, EdgeType.CONTROL);
    }

}
