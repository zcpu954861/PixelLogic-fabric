package com.pixelmc.pixellogic.core.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pixelmc.pixellogic.core.catalog.BlockDefinition;
import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.catalog.RichTextComponentValue;
import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.EntityTargetRef;
import com.pixelmc.pixellogic.core.model.EntityTargetRequirement;
import com.pixelmc.pixellogic.core.model.EntityTargetSource;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.core.simulation.context.SimulationWorld;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationExecutionRegistry;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionRequest;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionResult;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationRunner;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;
import com.pixelmc.pixellogic.server.api.PixelLogicApiServer;
import com.pixelmc.pixellogic.server.storage.GraphDocument;
import com.pixelmc.pixellogic.server.storage.GraphStorageService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class EntityTargetReferenceSelfCheck {
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID ONLINE_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID OFFLINE_ID = UUID.fromString("00000000-0000-0000-0000-000000000404");
    private static final String TRIGGER = "manual.test.start";

    private EntityTargetReferenceSelfCheck() {
    }

    public static void main(String[] args) {
        run("entityTargetReferenceSelfCheck", () -> {
            checkModelAndCatalog();
            checkGraphWireAndValidation();
            checkResolverMatrix();
            checkGenericTagExecution();
            checkFailuresDoNotSelectOutputs();
            checkOnlinePlayerSimulationSnapshot();
            checkNullCurrentContinuation();
            checkContinuationRejectsReboundIdentity();
            checkOnlinePlayerApi();
        });
    }

    private static void checkModelAndCatalog() {
        require(new Gson().toJson(EntityTargetErrorCode.ENTITY_TARGET_MISSING).equals("\"entity_target_missing\""),
                "entity target error codes should use stable wire IDs");
        require(EnumSet.allOf(EntityTargetSource.class).equals(EnumSet.of(
                        EntityTargetSource.CURRENT_ENTITY,
                        EntityTargetSource.CONDITION_SUBJECT,
                        EntityTargetSource.TARGET_ENTITY,
                        EntityTargetSource.ONLINE_PLAYER
                )), "entity target should expose exactly four sources");
        require(EnumSet.allOf(EntityTargetRequirement.class).equals(EnumSet.of(
                        EntityTargetRequirement.ANY_ENTITY,
                        EntityTargetRequirement.LIVING_ENTITY,
                        EntityTargetRequirement.PLAYER_ONLY
                )), "entity target should expose exactly three requirements");
        require(java.util.Arrays.stream(EntityTargetSource.values())
                        .noneMatch(value -> value.name().equals("RUN_" + "ENTITY")),
                "removed run-start source must not exist");

        List<EntityTargetRef> refs = List.of(
                source(EntityTargetSource.CURRENT_ENTITY),
                source(EntityTargetSource.CONDITION_SUBJECT),
                source(EntityTargetSource.TARGET_ENTITY),
                EntityTargetRef.onlinePlayer(ONLINE_ID, "Steve")
        );
        refs.forEach(ref -> require(EntityTargetRef.parse(ref.toJson()).equals(ref),
                "target ref should round-trip: " + ref.source()));
        expectInvalidTarget("", "required");
        expectInvalidTarget("{}", "source");
        expectInvalidTarget("{\"source\":\"OTHER\"}", "source");
        expectInvalidTarget("{\"source\":\"ONLINE_PLAYER\"}", "playerUuid");
        expectInvalidTarget("{\"source\":\"ONLINE_PLAYER\",\"playerUuid\":\"bad\"}", "playerUuid");
        expectInvalidTarget("{\"source\":\"CURRENT_ENTITY\",\"playerUuid\":\"" + ONLINE_ID + "\"}", "identity");
        expectInvalidTarget("{\"source\":\"CURRENT_ENTITY\",\"playerNameHint\":\"Steve\"}", "identity");
        expectInvalidTarget("{\"source\":\"ONLINE_PLAYER\",\"playerUuid\":\"" + ONLINE_ID
                + "\",\"playerNameHint\":\"" + "x".repeat(65) + "\"}", "playerNameHint");
        expectInvalidTarget("{\"source\":\"ONLINE_PLAYER\",\"playerUuid\":\"" + ONLINE_ID
                + "\",\"playerNameHint\":\"bad\\nname\"}", "playerNameHint");

        require(BuiltInBlockCatalog.catalog().blocks().size() == 30,
                "catalog should contain 30 blocks after health and termination actions");
        List<String> newIds = List.of(
                BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG,
                BuiltInBlockCatalog.ACTION_ENTITY_ADD_TAG,
                BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_TAG
        );
        newIds.forEach(id -> {
            BlockDefinition block = BuiltInBlockCatalog.block(id).orElseThrow();
            require(block.categoryId().equals("player-entity.tags")
                            && block.entityTargetRequirement() == EntityTargetRequirement.ANY_ENTITY,
                    "generic tag block should declare category and ANY_ENTITY: " + id);
            require(EntityTargetRef.parse(block.defaultConfig().get("target")).source()
                            == EntityTargetSource.CURRENT_ENTITY,
                    "generic tag default should explicitly target current entity: " + id);
            require(block.formSchema().stream().anyMatch(field -> field.key().equals("target")
                            && field.type().equals("entity_target")),
                    "generic tag block should use shared entity target field: " + id);
            require(JsonParser.parseString(new Gson().toJson(block)).getAsJsonObject()
                            .getAsJsonObject("defaultConfig").get("target").isJsonObject(),
                    "catalog JSON should expose defaultConfig.target as one object: " + id);
        });
        BlockDefinition executeAs = BuiltInBlockCatalog.block(BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS).orElseThrow();
        require(EntityTargetRef.parse(executeAs.defaultConfig().get("target")).source()
                        == EntityTargetSource.CONDITION_SUBJECT,
                "execute-as should explicitly default to condition subject");
        removedIds().forEach(id -> require(BuiltInBlockCatalog.block(id).isEmpty(),
                "removed tag id should be unknown"));
    }

    private static void checkGraphWireAndValidation() {
        NodeDefinition action = tagNode(
                "tag",
                NodeType.ENTITY_ADD_TAG_ACTION,
                BuiltInBlockCatalog.ACTION_ENTITY_ADD_TAG,
                EntityTargetRef.onlinePlayer(ONLINE_ID, "Steve"),
                "ready",
                null
        );
        NodeDefinition message = node(
                "message", NodeType.MESSAGE_ACTION, BuiltInBlockCatalog.ACTION_MESSAGE_CHAT,
                List.of(in("input"), out("done")),
                Map.of("target", "CURRENT_PLAYER", "message", RichTextComponentValue.fromPlainText("hello"))
        );
        GraphDefinition graph = graph(
                "wire", List.of(trigger(), action, message),
                List.of(
                        edge("e1", "start", "started", "tag", "input"),
                        edge("e2", "tag", "done", "message", "input")
                )
        );
        GraphDocument document = GraphDocument.fromGraphDefinition(graph, "wire");
        String json = new Gson().toJson(document);
        JsonObject target = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonArray("nodes").get(1).getAsJsonObject()
                .getAsJsonObject("config").getAsJsonObject("target");
        require(target.get("source").getAsString().equals("ONLINE_PLAYER")
                        && target.get("playerUuid").getAsString().equals(ONLINE_ID.toString()),
                "Graph JSON should store one nested target object");
        require(JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("nodes").get(2).getAsJsonObject()
                        .getAsJsonObject("config").get("target").getAsString().equals("CURRENT_PLAYER"),
                "unrelated message target config should remain a string");
        GraphDocument decoded = new Gson().fromJson(json, GraphDocument.class);
        require(EntityTargetRef.parse(decoded.nodes().get(1).config().get("target")).playerUuid().equals(ONLINE_ID),
                "Graph target object should decode through the formal serializer");
        require(new Gson().toJson(decoded).equals(json),
                "Graph target wire encoding should remain deterministic after decode");

        JsonObject stringTargetJson = JsonParser.parseString(json).getAsJsonObject();
        stringTargetJson.getAsJsonArray("nodes").get(1).getAsJsonObject()
                .getAsJsonObject("config").addProperty("target", target.toString());
        GraphDefinition stringTargetGraph = new Gson().fromJson(stringTargetJson, GraphDocument.class).toGraphDefinition();

        GraphValidator validator = new GraphValidator();
        require(!validator.hasErrors(validator.validate(graph)), "valid nested target graph should pass validation");
        require(hasIssue(stringTargetGraph, "entity_target_invalid_config"),
                "Graph wire target must be a nested object, not a JSON-encoded string");
        require(hasIssue(invalidTargetGraph(null), "entity_target_invalid_config"), "missing target should fail closed");
        require(hasIssue(invalidTargetGraph("{\"source\":\"OTHER\"}"), "entity_target_source_invalid"),
                "invalid source should use stable validation code");
        require(hasIssue(invalidTargetGraph("{\"source\":\"ONLINE_PLAYER\"}"), "entity_target_player_uuid_missing"),
                "missing online-player UUID should use stable validation code");
    }

    private static void checkResolverMatrix() {
        TestEntity actor = TestEntity.player(ACTOR_ID, "Actor", Set.of("ready"));
        TestEntity target = TestEntity.entity(TARGET_ID, "Target", true, true, Set.of());
        TestEntity online = TestEntity.player(ONLINE_ID, "Steve", Set.of());
        TestProvider provider = new TestProvider(List.of(actor, target, online), Map.of(OFFLINE_ID, "Alex"));
        RuntimeConditionResult condition = new RuntimeConditionResult(
                "condition", BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG, target.reference(), false, "fact"
        );
        RuntimeExecutionContext context = new RuntimeExecutionContext(
                ACTOR_ID, "session", target.reference(), actor.reference(), condition
        );

        require(resolve(source(EntityTargetSource.CURRENT_ENTITY), EntityTargetRequirement.ANY_ENTITY, context, provider)
                        .reference().equals(actor.reference()), "CURRENT_ENTITY should resolve current context");
        require(resolve(source(EntityTargetSource.CONDITION_SUBJECT), EntityTargetRequirement.ANY_ENTITY, context, provider)
                        .reference().equals(target.reference()), "CONDITION_SUBJECT should resolve condition subject");
        require(resolve(source(EntityTargetSource.TARGET_ENTITY), EntityTargetRequirement.ANY_ENTITY, context, provider)
                        .reference().equals(target.reference()), "TARGET_ENTITY should remain independent");
        require(resolve(EntityTargetRef.onlinePlayer(ONLINE_ID, "stale name"), EntityTargetRequirement.PLAYER_ONLY, context, provider)
                        .reference().equals(online.reference()), "ONLINE_PLAYER should resolve exact UUID, not name hint");
        String unsafeName = "A".repeat(70) + "\nInjected";
        TestEntity unsafe = TestEntity.entity(UUID.randomUUID(), unsafeName, true, true, Set.of());
        ResolvedEntityTarget bounded = resolve(
                source(EntityTargetSource.CURRENT_ENTITY),
                EntityTargetRequirement.ANY_ENTITY,
                new RuntimeExecutionContext(ACTOR_ID, "session", null, unsafe.reference(), null),
                new TestProvider(List.of(unsafe), Map.of())
        );
        require(bounded.reference().displayName().length() == 64
                        && bounded.reference().displayName().chars().noneMatch(Character::isISOControl),
                "resolved target names should use the bounded lookup display name");
        expectResolutionError(source(EntityTargetSource.CURRENT_ENTITY), EntityTargetRequirement.ANY_ENTITY,
                context, providerReturning(target), EntityTargetErrorCode.ENTITY_TARGET_UNRESOLVABLE);
        expectResolutionError(EntityTargetRef.onlinePlayer(ONLINE_ID, "Steve"), EntityTargetRequirement.ANY_ENTITY,
                context, providerReturning(actor), EntityTargetErrorCode.ENTITY_TARGET_UNRESOLVABLE);
        expectResolutionError(EntityTargetRef.onlinePlayer(ONLINE_ID, "Steve"), EntityTargetRequirement.ANY_ENTITY,
                context, providerReturning(TestEntity.entity(ONLINE_ID, "Impostor", true, true, Set.of())),
                EntityTargetErrorCode.ENTITY_TARGET_TYPE_MISMATCH);
        expectResolutionError(EntityTargetRef.onlinePlayer(ONLINE_ID, "Steve"), EntityTargetRequirement.ANY_ENTITY,
                context, providerReturning(TestEntity.offlinePlayer(ONLINE_ID, "Steve")),
                EntityTargetErrorCode.ENTITY_TARGET_OFFLINE);

        expectResolutionError(source(EntityTargetSource.CURRENT_ENTITY), EntityTargetRequirement.ANY_ENTITY,
                new RuntimeExecutionContext(ACTOR_ID, "session", null, null, null), provider,
                EntityTargetErrorCode.ENTITY_TARGET_MISSING);
        expectResolutionError(source(EntityTargetSource.CONDITION_SUBJECT), EntityTargetRequirement.ANY_ENTITY,
                new RuntimeExecutionContext(ACTOR_ID, "session", null, actor.reference(), null), provider,
                EntityTargetErrorCode.ENTITY_TARGET_MISSING);
        expectResolutionError(source(EntityTargetSource.TARGET_ENTITY), EntityTargetRequirement.ANY_ENTITY,
                new RuntimeExecutionContext(ACTOR_ID, "session", null, actor.reference(), condition), provider,
                EntityTargetErrorCode.ENTITY_TARGET_MISSING);
        expectResolutionError(EntityTargetRef.onlinePlayer(OFFLINE_ID, "Alex"), EntityTargetRequirement.PLAYER_ONLY,
                context, provider, EntityTargetErrorCode.ENTITY_TARGET_OFFLINE);
        expectResolutionError(EntityTargetRef.onlinePlayer(UUID.randomUUID(), "Unknown"), EntityTargetRequirement.PLAYER_ONLY,
                context, provider, EntityTargetErrorCode.ENTITY_TARGET_UNRESOLVABLE);
        expectResolutionError(source(EntityTargetSource.CURRENT_ENTITY), EntityTargetRequirement.ANY_ENTITY,
                context, RuntimeEntityProvider.UNAVAILABLE, EntityTargetErrorCode.ENTITY_TARGET_PROVIDER_UNAVAILABLE);
        expectResolutionError(source(EntityTargetSource.TARGET_ENTITY), EntityTargetRequirement.PLAYER_ONLY,
                context, provider, EntityTargetErrorCode.ENTITY_TARGET_TYPE_MISMATCH);

        TestEntity nonLiving = TestEntity.entity(UUID.randomUUID(), "Armor Stand", false, true, Set.of());
        TestEntity dead = TestEntity.entity(UUID.randomUUID(), "Dead", true, false, Set.of());
        TestProvider lifecycle = new TestProvider(List.of(nonLiving, dead), Map.of());
        expectResolutionError(source(EntityTargetSource.CURRENT_ENTITY), EntityTargetRequirement.LIVING_ENTITY,
                new RuntimeExecutionContext(ACTOR_ID, "", null, nonLiving.reference(), null), lifecycle,
                EntityTargetErrorCode.ENTITY_TARGET_TYPE_MISMATCH);
        expectResolutionError(source(EntityTargetSource.CURRENT_ENTITY), EntityTargetRequirement.LIVING_ENTITY,
                new RuntimeExecutionContext(ACTOR_ID, "", null, dead.reference(), null), lifecycle,
                EntityTargetErrorCode.ENTITY_TARGET_NOT_ALIVE);
    }

    private static void checkGenericTagExecution() {
        TestEntity actor = TestEntity.player(ACTOR_ID, "Actor", Set.of("ready"));
        TestProvider provider = new TestProvider(List.of(actor), Map.of());
        List<RuntimeActionOutcome> recorded = new ArrayList<>();
        RuntimeServices services = services(provider, recorded, new AtomicReference<>());
        RuntimeExecutionContext context = new RuntimeExecutionContext(ACTOR_ID, "", null, actor.reference(), null);

        NodeDefinition condition = tagNode(
                "condition", NodeType.ENTITY_HAS_TAG_CONDITION, BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG,
                source(EntityTargetSource.CURRENT_ENTITY), "ready", ConditionOutputMode.BRANCH
        );
        RuntimePredicateResult truePredicate = services.evaluatePredicate(condition, context).orElseThrow();
        require(truePredicate.value() && truePredicate.conditionResult().subject().equals(actor.reference()),
                "tag predicate true should retain resolved subject");
        NodeDefinition falseCondition = withTag(condition, "missing");
        RuntimeNodeExecutionResult falseResult = services.executeSimulationNode(falseCondition, context).orElseThrow();
        require("fail".equals(falseResult.outputSlot()) && !falseResult.conditionResult().rawResult()
                        && falseResult.conditionResult().subject().equals(actor.reference()),
                "tag condition false should use fail output and retain subject");

        NodeDefinition add = tagNode(
                "add", NodeType.ENTITY_ADD_TAG_ACTION, BuiltInBlockCatalog.ACTION_ENTITY_ADD_TAG,
                source(EntityTargetSource.CURRENT_ENTITY), "new", null
        );
        RuntimeActionOutcome added = services.executeSimulationNode(add, context).orElseThrow().actionOutcome();
        RuntimeActionOutcome unchangedAdd = services.executeSimulationNode(add, context).orElseThrow().actionOutcome();
        require(added.status() == RuntimeActionOutcome.Status.SUCCESS && added.changed() && added.affectedCount() == 1,
                "real add should return changed success");
        require(unchangedAdd.status() == RuntimeActionOutcome.Status.SUCCESS && !unchangedAdd.changed()
                        && unchangedAdd.affectedCount() == 1,
                "idempotent add should return successful no-change");

        NodeDefinition remove = tagNode(
                "remove", NodeType.ENTITY_REMOVE_TAG_ACTION, BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_TAG,
                source(EntityTargetSource.CURRENT_ENTITY), "new", null
        );
        RuntimeActionOutcome removed = services.executeSimulationNode(remove, context).orElseThrow().actionOutcome();
        RuntimeActionOutcome unchangedRemove = services.executeSimulationNode(remove, context).orElseThrow().actionOutcome();
        require(removed.changed() && removed.affectedCount() == 1,
                "real remove should return changed success");
        require(!unchangedRemove.changed() && unchangedRemove.affectedCount() == 1,
                "idempotent remove should return successful no-change");
    }

    private static void checkFailuresDoNotSelectOutputs() throws Exception {
        NodeDefinition condition = tagNode(
                "condition", NodeType.ENTITY_HAS_TAG_CONDITION, BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG,
                source(EntityTargetSource.CURRENT_ENTITY), "ready", ConditionOutputMode.BRANCH
        );
        GraphDefinition conditionGraph = graph(
                "missing-condition",
                List.of(trigger(), condition, debug("false-branch")),
                List.of(
                        edge("e1", "start", "started", "condition", "input"),
                        edge("e2", "condition", "fail", "false-branch", "input")
                )
        );
        List<String> debug = new ArrayList<>();
        RuntimeResult conditionResult = runtime(conditionGraph, RuntimeEntityProvider.UNAVAILABLE, debug, new AtomicReference<>())
                .start(new TriggerEvent(TRIGGER, "test", ACTOR_ID, "session"));
        require(!conditionResult.success() && conditionResult.targetError() != null
                        && conditionResult.targetError().code() == EntityTargetErrorCode.ENTITY_TARGET_MISSING
                        && debug.isEmpty(),
                "missing condition target must terminate without selecting false output");

        NodeDefinition action = tagNode(
                "action", NodeType.ENTITY_ADD_TAG_ACTION, BuiltInBlockCatalog.ACTION_ENTITY_ADD_TAG,
                source(EntityTargetSource.CURRENT_ENTITY), "ready", null
        );
        AtomicReference<RuntimeActionOutcome> outcome = new AtomicReference<>();
        GraphDefinition actionGraph = graph(
                "missing-action", List.of(trigger(), action),
                List.of(edge("e1", "start", "started", "action", "input"))
        );
        RuntimeResult actionResult = runtime(actionGraph, RuntimeEntityProvider.UNAVAILABLE, debug, outcome)
                .start(new TriggerEvent(TRIGGER, "test", ACTOR_ID, "session"));
        require(!actionResult.success() && outcome.get() != null
                        && outcome.get().status() == RuntimeActionOutcome.Status.FAILURE
                        && !outcome.get().changed() && outcome.get().affectedCount() == 0,
                "target failure should record one structured failure action outcome");

        CompiledGraph compiled = new GraphCompiler().compile(actionGraph);
        SimulationRunner runner = new SimulationRunner(
                services -> new GraphRuntime(
                        compiled, new InMemoryStateStore(), new BoundedTraceBuffer(4, 40), services,
                        RuntimeLimits.spikeDefaults()
                ),
                services(RuntimeEntityProvider.UNAVAILABLE, new ArrayList<>(), new AtomicReference<>()),
                SimulationExecutionRegistry.playerTags()
        );
        SimulationExecutionResult globalSimulation = runner.run(new SimulationExecutionRequest(
                actionGraph.id(), TRIGGER, "test", SimulationActor.player(ACTOR_ID, "Actor"),
                SimulationWorld.overworld(), "session", 0L, false
        ));
        require(!globalSimulation.success() && globalSimulation.targetError() != null
                        && globalSimulation.targetError().code() == EntityTargetErrorCode.ENTITY_TARGET_MISSING,
                "Simulation should support a deliberate global run with no initial current entity");

        try (PixelLogicSpikeService service = new PixelLogicSpikeService(
                (ignored, message) -> { }, Runnable::run, ignored -> { }, Duration.ofSeconds(1),
                Files.createTempDirectory("pixel-logic-target-service")
        )) {
            NodeDefinition serviceAction = tagNode(
                    "action", NodeType.ENTITY_ADD_TAG_ACTION, BuiltInBlockCatalog.ACTION_ENTITY_ADD_TAG,
                    source(EntityTargetSource.TARGET_ENTITY), "ready", null
            );
            GraphDefinition serviceGraph = graph(
                    "missing-service-target", List.of(trigger(), serviceAction),
                    List.of(edge("e1", "start", "started", "action", "input"))
            );
            service.saveDraft(
                    GraphStorageService.DEFAULT_GRAPH_ID,
                    GraphDocument.fromGraphDefinition(serviceGraph, "Entity Target Service Self Check")
            );
            require(service.commitDraft(GraphStorageService.DEFAULT_GRAPH_ID).committed(),
                    "service target-error graph should commit");
            RuntimeResult serviceResult = service.startManualTest(SimulationActor.player(ACTOR_ID, "Actor"));
            require(!serviceResult.success() && serviceResult.targetError() != null
                            && serviceResult.targetError().code() == EntityTargetErrorCode.ENTITY_TARGET_MISSING,
                    "service RuntimeResult must preserve the structured target error");
        }
    }

    private static void checkNullCurrentContinuation() {
        NodeDefinition wait = node(
                "wait", NodeType.TIMER_START_ACTION, BuiltInBlockCatalog.TIMER_WAIT,
                List.of(in("input"), out("timer_completed")), Map.of("durationSeconds", "1")
        );
        GraphDefinition graph = graph(
                "null-current-delay", List.of(trigger(), wait, debug("done")),
                List.of(
                        edge("e1", "start", "started", "wait", "input"),
                        edge("e2", "wait", "timer_completed", "done", "input")
                )
        );
        AtomicReference<TimerContinuation> continuation = new AtomicReference<>();
        List<String> debug = new ArrayList<>();
        GraphRuntime runtime = runtime(graph, RuntimeEntityProvider.UNAVAILABLE, debug, continuation);
        RuntimeResult waiting = runtime.start(new TriggerEvent(TRIGGER, "test", ACTOR_ID, "session"));
        require(waiting.success() && waiting.suspended() && continuation.get() != null
                        && continuation.get().cursor().currentEntity() == null,
                "global run should suspend with an empty current entity");
        RuntimeResult resumed = runtime.resumeTimer(continuation.get());
        require(resumed.success() && debug.equals(List.of("done")),
                "empty current entity should survive continuation and resume normally");
    }

    private static void checkOnlinePlayerSimulationSnapshot() throws Exception {
        TestEntity online = TestEntity.player(ONLINE_ID, "Steve", Set.of());
        TestProvider provider = new TestProvider(List.of(online), Map.of());
        EntityTargetRef target = EntityTargetRef.onlinePlayer(ONLINE_ID, "Steve");
        NodeDefinition add = tagNode(
                "add", NodeType.ENTITY_ADD_TAG_ACTION, BuiltInBlockCatalog.ACTION_ENTITY_ADD_TAG,
                target, "simulated", null
        );
        NodeDefinition condition = tagNode(
                "condition", NodeType.ENTITY_HAS_TAG_CONDITION, BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG,
                target, "simulated", ConditionOutputMode.PASS_ONLY
        );
        GraphDefinition graph = graph(
                "online-player-snapshot",
                List.of(trigger(), add, condition, debug("online-snapshot-ok")),
                List.of(
                        edge("e1", "start", "started", "add", "input"),
                        edge("e2", "add", "done", "condition", "input"),
                        edge("e3", "condition", "pass", "online-snapshot-ok", "input")
                )
        );
        try (PixelLogicSpikeService service = new PixelLogicSpikeService(
                (ignored, message) -> { }, Runnable::run, ignored -> { }, Duration.ofSeconds(1),
                Files.createTempDirectory("pixel-logic-online-player-snapshot"), provider
        )) {
            service.saveDraft(
                    GraphStorageService.DEFAULT_GRAPH_ID,
                    GraphDocument.fromGraphDefinition(graph, "Online Player Snapshot Self Check")
            );
            require(service.commitDraft(GraphStorageService.DEFAULT_GRAPH_ID).committed(),
                    "online-player simulation graph should commit");
            RuntimeResult result = service.startManualTest(SimulationActor.player(ACTOR_ID, "Actor"));
            require(result.success()
                            && service.trace(result.traceId()).orElseThrow().containsMessage("online-snapshot-ok")
                            && !online.hasTag("simulated"),
                    "simulation must snapshot one selected online player without mutating the live provider entity");
        }
    }

    private static void checkContinuationRejectsReboundIdentity() {
        TestEntity actor = TestEntity.player(ACTOR_ID, "Actor", Set.of());
        TestEntity other = TestEntity.player(ONLINE_ID, "Other", Set.of());
        AtomicReference<RuntimeEntityProvider> provider = new AtomicReference<>(providerReturning(actor));
        AtomicReference<TimerContinuation> continuation = new AtomicReference<>();
        List<String> debug = new ArrayList<>();
        GraphDefinition graph = graph(
                "rebound-continuation", List.of(trigger(), node(
                        "wait", NodeType.TIMER_START_ACTION, BuiltInBlockCatalog.TIMER_WAIT,
                        List.of(in("input"), out("timer_completed")), Map.of("durationSeconds", "1")
                ), debug("done")),
                List.of(
                        edge("e1", "start", "started", "wait", "input"),
                        edge("e2", "wait", "timer_completed", "done", "input")
                )
        );
        RuntimeServices services = new RuntimeServices() {
            @Override
            public Optional<RuntimeSubjectReference> initialCurrentEntity(UUID playerId, String sessionId) {
                return Optional.of(actor.reference());
            }

            @Override
            public RuntimeEntityProvider entityProvider() {
                return provider.get();
            }

            @Override
            public void sendPlayerMessage(UUID playerId, String message) {
            }

            @Override
            public void debug(String message) {
                debug.add(message);
            }

            @Override
            public void scheduleTimer(Duration delay, TimerContinuation scheduled) {
                continuation.set(scheduled);
            }
        };
        GraphRuntime runtime = new GraphRuntime(
                new GraphCompiler().compile(graph), new InMemoryStateStore(), new BoundedTraceBuffer(8, 100),
                services, RuntimeLimits.spikeDefaults()
        );
        RuntimeResult waiting = runtime.start(new TriggerEvent(TRIGGER, "test", ACTOR_ID, "session"));
        require(waiting.suspended() && continuation.get() != null
                        && continuation.get().cursor().currentEntity().equals(actor.reference()),
                "continuation should snapshot the current stable identity");
        provider.set(providerReturning(other));
        RuntimeResult resumed = runtime.resumeTimer(continuation.get());
        require(!resumed.success() && debug.isEmpty(),
                "continuation must reject a provider that rebinds its saved identity");
    }

    private static void checkOnlinePlayerApi() throws Exception {
        TestEntity online = TestEntity.player(ONLINE_ID, "Steve", Set.of());
        TestProvider provider = new TestProvider(List.of(online), Map.of(OFFLINE_ID, "Alex"));
        try (PixelLogicSpikeService service = new PixelLogicSpikeService(
                (ignored, message) -> { }, Runnable::run, ignored -> { }, Duration.ofSeconds(1),
                Files.createTempDirectory("pixel-logic-target-api"), provider
        ); PixelLogicApiServer server = PixelLogicApiServer.start(
                service, Runnable::run, PixelLogicApiServer.DEFAULT_HOST, 0
        )) {
            String base = "http://" + PixelLogicApiServer.DEFAULT_HOST + ":" + server.port();
            JsonObject catalog = get(base + "/api/pixellogic/catalog").getAsJsonObject("catalog");
            JsonObject catalogTarget = catalog.getAsJsonArray("blocks").asList().stream()
                    .map(element -> element.getAsJsonObject())
                    .filter(block -> block.get("id").getAsString().equals(BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG))
                    .findFirst().orElseThrow()
                    .getAsJsonObject("defaultConfig").getAsJsonObject("target");
            require(catalogTarget.get("source").getAsString().equals("CURRENT_ENTITY"),
                    "Catalog API should expose the entity target default as an object");
            JsonObject onlineResponse = get(base + "/api/pixellogic/runtime/online-players?query=ste&limit=20&selectedUuid=" + ONLINE_ID);
            require(onlineResponse.get("ok").getAsBoolean()
                            && onlineResponse.getAsJsonArray("players").size() == 1
                            && onlineResponse.getAsJsonObject("selected").get("availability").getAsString().equals("ONLINE"),
                    "online-player API should list and exactly resolve UUID");
            JsonObject offlineResponse = get(base + "/api/pixellogic/runtime/online-players?query=&limit=20&selectedUuid=" + OFFLINE_ID);
            require(offlineResponse.getAsJsonObject("selected").get("availability").getAsString().equals("OFFLINE")
                            && offlineResponse.getAsJsonObject("selected").get("uuid").getAsString().equals(OFFLINE_ID.toString()),
                    "online-player API should preserve a known offline UUID");
            JsonObject exactOutsideList = get(base + "/api/pixellogic/runtime/online-players?query=no-match&limit=1&selectedUuid=" + ONLINE_ID);
            require(exactOutsideList.getAsJsonArray("players").isEmpty()
                            && exactOutsideList.getAsJsonObject("selected").get("availability").getAsString().equals("ONLINE"),
                    "selected UUID lookup should remain independent from the bounded list");
            JsonObject invalid = get(base + "/api/pixellogic/runtime/online-players?limit=51");
            require(!invalid.get("ok").getAsBoolean()
                            && invalid.getAsJsonObject("error").get("code").getAsString().equals("BAD_ONLINE_PLAYER_QUERY"),
                    "online-player API should enforce bounded query input");
            require(errorCode(base + "/api/pixellogic/runtime/online-players?unknown=1").equals("BAD_ONLINE_PLAYER_QUERY"),
                    "online-player API should reject unknown query parameters");
            require(errorCode(base + "/api/pixellogic/runtime/online-players?limit=1&limit=2").equals("BAD_ONLINE_PLAYER_QUERY"),
                    "online-player API should reject duplicate query parameters");
            require(errorCode(base + "/api/pixellogic/runtime/online-players?selectedUuid="
                            + "AAAAAAAA-0000-0000-0000-000000000303").equals("BAD_ONLINE_PLAYER_QUERY"),
                    "selected UUID should require canonical lowercase form");
        }

        try (PixelLogicSpikeService service = new PixelLogicSpikeService(
                (ignored, message) -> { }, Runnable::run, ignored -> { }, Duration.ofSeconds(1),
                Files.createTempDirectory("pixel-logic-target-api-unavailable")
        ); PixelLogicApiServer server = PixelLogicApiServer.start(
                service, Runnable::run, PixelLogicApiServer.DEFAULT_HOST, 0
        )) {
            String base = "http://" + PixelLogicApiServer.DEFAULT_HOST + ":" + server.port();
            String uri = base + "/api/pixellogic/runtime/online-players";
            require(status(uri) == 503 && errorCode(uri).equals("ENTITY_TARGET_PROVIDER_UNAVAILABLE"),
                    "unavailable online-player provider should return HTTP 503 and its stable API error");
        }

        RuntimeEntityProvider invalidProvider = new RuntimeEntityProvider() {
            @Override
            public RuntimeEntityLookup resolve(RuntimeSubjectReference reference) {
                return RuntimeEntityLookup.unresolvable();
            }

            @Override
            public RuntimeEntityLookup resolveOnlinePlayer(UUID playerUuid) {
                if (playerUuid.equals(ONLINE_ID)) {
                    return RuntimeEntityLookup.resolved(TestEntity.player(ACTOR_ID, "Rebound", Set.of()));
                }
                if (playerUuid.equals(OFFLINE_ID)) {
                    return RuntimeEntityLookup.resolved(TestEntity.offlinePlayer(OFFLINE_ID, "Alex"));
                }
                return RuntimeEntityLookup.resolved(TestEntity.entity(TARGET_ID, "Not a player", true, true, Set.of()));
            }

            @Override
            public RuntimeOnlinePlayerList listOnlinePlayers(String query, int limit) {
                return new RuntimeOnlinePlayerList(true, List.of());
            }
        };
        try (PixelLogicSpikeService service = new PixelLogicSpikeService(
                (ignored, message) -> { }, Runnable::run, ignored -> { }, Duration.ofSeconds(1),
                Files.createTempDirectory("pixel-logic-target-api-defensive"), invalidProvider
        ); PixelLogicApiServer server = PixelLogicApiServer.start(
                service, Runnable::run, PixelLogicApiServer.DEFAULT_HOST, 0
        )) {
            String base = "http://" + PixelLogicApiServer.DEFAULT_HOST + ":" + server.port();
            JsonObject rebound = get(base + "/api/pixellogic/runtime/online-players?selectedUuid=" + ONLINE_ID)
                    .getAsJsonObject("selected");
            JsonObject offline = get(base + "/api/pixellogic/runtime/online-players?selectedUuid=" + OFFLINE_ID)
                    .getAsJsonObject("selected");
            JsonObject wrongKind = get(base + "/api/pixellogic/runtime/online-players?selectedUuid=" + TARGET_ID)
                    .getAsJsonObject("selected");
            require(rebound.get("availability").getAsString().equals("UNRESOLVABLE")
                            && wrongKind.get("availability").getAsString().equals("UNRESOLVABLE")
                            && offline.get("availability").getAsString().equals("OFFLINE"),
                    "selected UUID lookup must reject rebound/non-player identities and resolved offline players");
        }
    }

    private static GraphRuntime runtime(
            GraphDefinition graph,
            RuntimeEntityProvider provider,
            List<String> debug,
            AtomicReference<?> capture
    ) {
        CompiledGraph compiled = new GraphCompiler().compile(graph);
        RuntimeServices services = new RuntimeServices() {
            @Override
            public RuntimeEntityProvider entityProvider() {
                return provider;
            }

            @Override
            public void sendPlayerMessage(UUID playerId, String message) {
            }

            @Override
            public void debug(String message) {
                debug.add(message);
            }

            @SuppressWarnings("unchecked")
            @Override
            public void scheduleTimer(Duration delay, TimerContinuation continuation) {
                if (capture != null) {
                    ((AtomicReference<TimerContinuation>) capture).set(continuation);
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public void recordActionOutcome(String nodeId, RuntimeActionOutcome outcome) {
                if (capture != null && outcome.status() == RuntimeActionOutcome.Status.FAILURE) {
                    ((AtomicReference<RuntimeActionOutcome>) capture).set(outcome);
                }
            }
        };
        return new GraphRuntime(
                compiled, new InMemoryStateStore(), new BoundedTraceBuffer(8, 100), services,
                RuntimeLimits.spikeDefaults()
        );
    }

    private static RuntimeServices services(
            RuntimeEntityProvider provider,
            List<RuntimeActionOutcome> outcomes,
            AtomicReference<TimerContinuation> timer
    ) {
        return new RuntimeServices() {
            @Override
            public RuntimeEntityProvider entityProvider() {
                return provider;
            }

            @Override
            public void sendPlayerMessage(UUID playerId, String message) {
            }

            @Override
            public void debug(String message) {
            }

            @Override
            public void scheduleTimer(Duration delay, TimerContinuation continuation) {
                timer.set(continuation);
            }

            @Override
            public void recordActionOutcome(String nodeId, RuntimeActionOutcome outcome) {
                outcomes.add(outcome);
            }
        };
    }

    private static RuntimeEntityProvider providerReturning(RuntimeEntityAccess entity) {
        return new RuntimeEntityProvider() {
            @Override
            public RuntimeEntityLookup resolve(RuntimeSubjectReference reference) {
                return RuntimeEntityLookup.resolved(entity);
            }

            @Override
            public RuntimeEntityLookup resolveOnlinePlayer(UUID playerUuid) {
                return RuntimeEntityLookup.resolved(entity);
            }

            @Override
            public RuntimeOnlinePlayerList listOnlinePlayers(String query, int limit) {
                return new RuntimeOnlinePlayerList(true, List.of());
            }
        };
    }

    private static ResolvedEntityTarget resolve(
            EntityTargetRef ref,
            EntityTargetRequirement requirement,
            RuntimeExecutionContext context,
            RuntimeEntityProvider provider
    ) {
        return EntityTargetResolver.resolve("node", "target", ref, requirement, context, provider);
    }

    private static void expectResolutionError(
            EntityTargetRef ref,
            EntityTargetRequirement requirement,
            RuntimeExecutionContext context,
            RuntimeEntityProvider provider,
            EntityTargetErrorCode code
    ) {
        try {
            resolve(ref, requirement, context, provider);
            throw new IllegalStateException("expected target error: " + code);
        } catch (EntityTargetException exception) {
            require(exception.error().code() == code && exception.error().fieldPath().equals("node.target"),
                    "unexpected target error: " + exception.error());
        }
    }

    private static void expectInvalidTarget(String json, String messagePart) {
        try {
            EntityTargetRef.parse(json);
            throw new IllegalStateException("expected invalid target");
        } catch (IllegalArgumentException exception) {
            require(exception.getMessage().contains(messagePart), "unexpected target validation message: " + exception.getMessage());
        }
    }

    private static EntityTargetRef source(EntityTargetSource source) {
        return new EntityTargetRef(source, null, null);
    }

    private static NodeDefinition withTag(NodeDefinition node, String tag) {
        Map<String, String> config = new LinkedHashMap<>(node.config());
        config.put("tag", tag);
        return new NodeDefinition(
                node.id(), node.type(), node.blockId(), node.parentContainerId(), node.parentSlot(),
                node.conditionSlots(), node.slots(), config
        );
    }

    private static NodeDefinition tagNode(
            String id,
            NodeType type,
            String blockId,
            EntityTargetRef target,
            String tag,
            ConditionOutputMode mode
    ) {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("target", target.toJson());
        config.put("tag", tag);
        if (mode != null) {
            config.put("outputMode", mode.name());
        }
        List<SlotDefinition> slots = type == NodeType.ENTITY_HAS_TAG_CONDITION
                ? List.of(in("input"), out("pass"), out("fail"))
                : List.of(in("input"), out("done"));
        return node(id, type, blockId, slots, config);
    }

    private static GraphDefinition invalidTargetGraph(String target) {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("tag", "ready");
        config.put("outputMode", ConditionOutputMode.PASS_ONLY.name());
        if (target != null) {
            config.put("target", target);
        }
        NodeDefinition condition = node(
                "condition", NodeType.ENTITY_HAS_TAG_CONDITION, BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG,
                List.of(in("input"), out("pass"), out("fail")), config
        );
        return graph("invalid-target", List.of(trigger(), condition), List.of());
    }

    private static boolean hasIssue(GraphDefinition graph, String code) {
        return new GraphValidator().validate(graph).stream().anyMatch(issue -> issue.code().equals(code));
    }

    private static NodeDefinition trigger() {
        return node("start", NodeType.MANUAL_TRIGGER, BuiltInBlockCatalog.TRIGGER_MANUAL_TEST,
                List.of(out("started")), Map.of());
    }

    private static NodeDefinition debug(String id) {
        return node(id, NodeType.DEBUG_LOG_ACTION, BuiltInBlockCatalog.DEBUG_LOG,
                List.of(in("input"), out("done")), Map.of("message", id));
    }

    private static NodeDefinition node(
            String id,
            NodeType type,
            String blockId,
            List<SlotDefinition> slots,
            Map<String, String> config
    ) {
        return new NodeDefinition(id, type, blockId, slots, config);
    }

    private static GraphDefinition graph(
            String id,
            List<NodeDefinition> nodes,
            List<EdgeDefinition> edges
    ) {
        return new GraphDefinition(id, nodes, edges, Map.of(TRIGGER, "start"));
    }

    private static SlotDefinition in(String id) {
        return new SlotDefinition(id, SlotDirection.INPUT, EdgeType.CONTROL);
    }

    private static SlotDefinition out(String id) {
        return new SlotDefinition(id, SlotDirection.OUTPUT, EdgeType.CONTROL);
    }

    private static EdgeDefinition edge(
            String id,
            String sourceNode,
            String sourceSlot,
            String targetNode,
            String targetSlot
    ) {
        return new EdgeDefinition(id, sourceNode, sourceSlot, targetNode, targetSlot, EdgeType.CONTROL);
    }

    private static List<String> removedIds() {
        return List.of(
                String.join(".", "condition", "player", "has_tag"),
                String.join(".", "action", "player", "add_tag"),
                String.join(".", "action", "player", "remove_tag"),
                String.join(".", "condition", "context_entity", "has_tag"),
                String.join(".", "action", "context_entity", "add_tag"),
                String.join(".", "action", "context_entity", "remove_tag")
        );
    }

    private static JsonObject get(String uri) throws Exception {
        return JsonParser.parseString(response(uri).body()).getAsJsonObject();
    }

    private static HttpResponse<String> response(String uri) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(uri)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static int status(String uri) throws Exception {
        return response(uri).statusCode();
    }

    private static String errorCode(String uri) throws Exception {
        return get(uri).getAsJsonObject("error").get("code").getAsString();
    }

    private static final class TestProvider implements RuntimeEntityProvider {
        private final Map<String, TestEntity> entities;
        private final Map<UUID, String> offline;

        private TestProvider(List<TestEntity> entities, Map<UUID, String> offline) {
            this.entities = entities.stream().collect(java.util.stream.Collectors.toMap(
                    entity -> entity.reference().id(), entity -> entity
            ));
            this.offline = Map.copyOf(offline);
        }

        @Override
        public RuntimeEntityLookup resolve(RuntimeSubjectReference reference) {
            if (reference == null) {
                return RuntimeEntityLookup.unresolvable();
            }
            TestEntity entity = entities.get(reference.id());
            return entity == null ? RuntimeEntityLookup.unresolvable() : RuntimeEntityLookup.resolved(entity);
        }

        @Override
        public RuntimeEntityLookup resolveOnlinePlayer(UUID playerUuid) {
            TestEntity entity = entities.get(playerUuid.toString());
            if (entity != null && entity.reference().kind() == RuntimeSubjectReference.Kind.PLAYER) {
                return RuntimeEntityLookup.resolved(entity);
            }
            return offline.containsKey(playerUuid)
                    ? RuntimeEntityLookup.offline(offline.get(playerUuid))
                    : RuntimeEntityLookup.unresolvable();
        }

        @Override
        public RuntimeOnlinePlayerList listOnlinePlayers(String query, int limit) {
            String normalized = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
            List<RuntimeOnlinePlayer> players = entities.values().stream()
                    .filter(entity -> entity.reference().kind() == RuntimeSubjectReference.Kind.PLAYER)
                    .filter(entity -> entity.reference().displayName().toLowerCase(java.util.Locale.ROOT).contains(normalized))
                    .limit(limit)
                    .map(entity -> new RuntimeOnlinePlayer(
                            UUID.fromString(entity.reference().id()), entity.reference().displayName()
                    ))
                    .toList();
            return new RuntimeOnlinePlayerList(true, players);
        }
    }

    private static final class TestEntity implements RuntimeEntityAccess {
        private final RuntimeSubjectReference reference;
        private final boolean living;
        private final boolean alive;
        private final boolean online;
        private final Set<String> tags;

        private TestEntity(RuntimeSubjectReference reference, boolean living, boolean alive, boolean online, Set<String> tags) {
            this.reference = reference;
            this.living = living;
            this.alive = alive;
            this.online = online;
            this.tags = new LinkedHashSet<>(tags);
        }

        static TestEntity player(UUID id, String name, Set<String> tags) {
            return new TestEntity(new RuntimeSubjectReference(id.toString(), RuntimeSubjectReference.Kind.PLAYER, name), true, true, true, tags);
        }

        static TestEntity offlinePlayer(UUID id, String name) {
            return new TestEntity(new RuntimeSubjectReference(id.toString(), RuntimeSubjectReference.Kind.PLAYER, name), true, true, false, Set.of());
        }

        static TestEntity entity(UUID id, String name, boolean living, boolean alive, Set<String> tags) {
            return new TestEntity(new RuntimeSubjectReference(id.toString(), RuntimeSubjectReference.Kind.ENTITY, name), living, alive, true, tags);
        }

        @Override
        public RuntimeSubjectReference reference() {
            return reference;
        }

        @Override
        public boolean living() {
            return living;
        }

        @Override
        public boolean alive() {
            return alive;
        }

        @Override
        public boolean online() {
            return online;
        }

        @Override
        public boolean hasTag(String tag) {
            return tags.contains(tag);
        }

        @Override
        public boolean addTag(String tag) {
            return tags.add(tag);
        }

        @Override
        public boolean removeTag(String tag) {
            return tags.remove(tag);
        }

        @Override
        public Set<String> tags() {
            return Set.copyOf(tags);
        }
    }
}
