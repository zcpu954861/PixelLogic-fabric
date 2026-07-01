package com.pixelmc.pixellogic.server.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class SimulationContextExpansionSelfCheck {
    private SimulationContextExpansionSelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        run("simulationContextExpansionSelfCheck", () -> {
        Path storageRoot = Files.createTempDirectory("pixel-logic-simulation-context-expansion-self-check-");
        try (PixelLogicSpikeService service = new PixelLogicSpikeService((playerId, message) -> {
        }, Runnable::run, ignored -> {
        }, Duration.ofSeconds(1), storageRoot);
             PixelLogicApiServer server = PixelLogicApiServer.start(service, Runnable::run, PixelLogicApiServer.DEFAULT_HOST, 0)) {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://" + PixelLogicApiServer.DEFAULT_HOST + ":" + server.port();

            postJson(client, base + "/api/pixellogic/test/reset", "");
            JsonObject defaultRun = postJson(client, base + "/api/pixellogic/test/start", "");
            JsonObject defaultSimulation = defaultRun.getAsJsonObject("simulation");
            require(position(defaultSimulation, "playerPosition").get("dimensionId").getAsString().equals("minecraft:overworld"),
                    "missing testContext should use default player dimension");
            require(position(defaultSimulation, "playerPosition").get("y").getAsInt() == 64,
                    "missing testContext should use default player y");
            require(!defaultSimulation.getAsJsonObject("targetBlock").get("enabled").getAsBoolean(),
                    "target block should default to disabled");
            require(defaultSimulation.getAsJsonArray("regions").isEmpty(),
                    "regions should default to empty");

            postJson(client, base + "/api/pixellogic/test/reset", "");
            JsonObject customRun = postJson(client, base + "/api/pixellogic/test/start", """
                    {"testContext":{
                      "actor":{"displayName":"上下文玩家","tags":["runner"],"operator":true},
                      "world":{
                        "playerPosition":{"dimensionId":"minecraft:the_nether","x":12,"y":70,"z":-5},
                        "targetBlock":{"enabled":true,"dimensionId":"minecraft:overworld","x":3,"y":64,"z":4,"blockId":"minecraft:stone"},
                        "regions":[{"name":"出生区","dimensionId":"minecraft:overworld","minX":10,"minY":80,"minZ":10,"maxX":0,"maxY":60,"maxZ":0}]
                      }
                    }}
                    """);
            JsonObject customSimulation = customRun.getAsJsonObject("simulation");
            JsonObject playerPosition = position(customSimulation, "playerPosition");
            require(playerPosition.get("dimensionId").getAsString().equals("minecraft:the_nether") && playerPosition.get("x").getAsInt() == 12,
                    "custom player position should flow into simulation result");
            JsonObject targetBlock = customSimulation.getAsJsonObject("targetBlock");
            require(targetBlock.get("enabled").getAsBoolean() && targetBlock.get("blockId").getAsString().equals("minecraft:stone"),
                    "enabled target block should flow into simulation result");
            JsonObject region = customSimulation.getAsJsonArray("regions").get(0).getAsJsonObject();
            require(region.get("name").getAsString().equals("出生区") && region.get("minX").getAsInt() == 0 && region.get("maxX").getAsInt() == 10,
                    "region bounds should normalize min/max");
            require(customSimulation.get("actorOperator").getAsBoolean(),
                    "administrator flag should still flow into simulation result");
            require(tags(customSimulation, "initialActorTags").contains("runner"),
                    "actor tags should still flow into simulation result");
            require(trace(customRun).contains("欢迎开始游戏"),
                    "message action should still run with expanded test context");

            requireBadContext(client, base, """
                    {"testContext":{"world":{"playerPosition":{"dimensionId":"overworld","x":0,"y":64,"z":0}}}}
                    """, "invalid dimensionId should be rejected");
            requireBadContext(client, base, """
                    {"testContext":{"world":{"targetBlock":{"enabled":true,"blockId":"stone"}}}}
                    """, "invalid blockId should be rejected");
            requireBadContext(client, base, tooManyRegions(), "too many regions should be rejected");
        }
        });
    }

    private static JsonObject position(JsonObject simulation, String field) {
        return simulation.getAsJsonObject(field);
    }

    private static void requireBadContext(HttpClient client, String base, String body, String message) throws Exception {
        JsonObject response = postJson(client, base + "/api/pixellogic/test/start", body);
        require(!response.get("ok").getAsBoolean()
                        && response.getAsJsonObject("error").get("code").getAsString().equals("BAD_TEST_CONTEXT"),
                message);
    }

    private static String tooManyRegions() {
        String region = "{\"name\":\"区域\",\"dimensionId\":\"minecraft:overworld\",\"minX\":0,\"minY\":64,\"minZ\":0,\"maxX\":1,\"maxY\":65,\"maxZ\":1}";
        return "{\"testContext\":{\"world\":{\"regions\":[" + String.join(",", java.util.Collections.nCopies(9, region)) + "]}}}";
    }

    private static JsonObject postJson(HttpClient client, String uri, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri)).header("Accept", "application/json");
        if (body == null || body.isBlank()) {
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
}
