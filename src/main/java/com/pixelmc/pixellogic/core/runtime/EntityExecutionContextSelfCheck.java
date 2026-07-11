package com.pixelmc.pixellogic.core.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pixelmc.pixellogic.core.catalog.BlockDefinition;
import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.ConditionSlotDefinition;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.core.simulation.context.SimulationBlockFact;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;
import com.pixelmc.pixellogic.core.simulation.context.SimulationEntity;
import com.pixelmc.pixellogic.core.simulation.context.SimulationPosition;
import com.pixelmc.pixellogic.core.simulation.context.SimulationWorld;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationExecutionRegistry;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionRequest;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionResult;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationRunner;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;
import com.pixelmc.pixellogic.server.api.PixelLogicApiServer;
import com.pixelmc.pixellogic.server.storage.GraphDocument;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class EntityExecutionContextSelfCheck {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final UUID PLAYER_ID = UUID.nameUUIDFromBytes("entity-context-player".getBytes());
    private static final UUID TARGET_ID = UUID.nameUUIDFromBytes("entity-context-target".getBytes());

    private EntityExecutionContextSelfCheck() {
    }

    public static void main(String[] args) {
        run("entityExecutionContextSelfCheck", () -> {
            catalogAndValidation();
            conditionResultsCarrySubjects();
            trueAndFalsePathsKeepTheActor();
            entitySourcesAndEmptyBody();
            invalidConditionSubjectsFailClosed();
            laterConditionReplacesTheSubject();
            nestedContextsRestoreEntities();
            delayKeepsEntityAndConditionContext();
            interleavedLoopContextFramesResume();
            loopUntilUsesCurrentEntityPredicate();
            runsAreIsolatedAndCancellationIsSafe();
            realApiExecutionContextRoundTrip();
        });
    }

    private static void catalogAndValidation() {
        BlockDefinition context = block(BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS);
        require(context.categoryId().equals("context") && context.nodeKind().equals("control"),
                "execute-as should use the context category and shared C-shaped control geometry");
        require(context.containerSlots().equals(List.of("body")), "execute-as should expose one body slot");
        require(context.inputSlots().stream().anyMatch(slot -> slot.id().equals("input")), "execute-as input missing");
        require(context.outputSlots().stream().anyMatch(slot -> slot.id().equals("done")), "execute-as done missing");
        require(context.formSchema().stream().anyMatch(field -> field.key().equals("entitySource")
                        && field.options().stream().map(option -> option.value()).collect(java.util.stream.Collectors.toSet())
                        .equals(Set.of("CONDITION_SUBJECT", "RUN_ENTITY", "TARGET_ENTITY"))),
                "execute-as should expose exactly three entity sources");
        require(block(BuiltInBlockCatalog.CONDITION_CONTEXT_ENTITY_HAS_TAG).capabilities().stream()
                        .anyMatch(capability -> capability.name().equals("PREDICATE")),
                "context entity tag condition should be predicate-compatible");

        GraphDefinition invalid = graph(
                "invalid-source",
                List.of(trigger(), node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "", Map.of("entitySource", "OTHER"))),
                List.of(edge("trigger", "started", "context"))
        );
        require(new GraphValidator().validate(invalid).stream().anyMatch(issue -> issue.code().equals("config_option_invalid")),
                "unknown entitySource should block commit");
    }

    private static void conditionResultsCarrySubjects() {
        SimulationExecutionRegistry registry = SimulationExecutionRegistry.playerTags();
        RuntimeServices noop = noopServices();
        SimulationActor tagged = actor("Steve", true, Set.of("ready"));
        SimulationContext taggedContext = simulationContext(tagged, world(null));
        RuntimeExecutionContext taggedRuntime = runtimeContext(tagged, null, tagged.reference(), null);
        NodeDefinition tagCondition = node("tag", BuiltInBlockCatalog.CONDITION_PLAYER_HAS_TAG, "", "",
                Map.of("tag", "ready", "outputMode", "BRANCH"));
        RuntimePredicateResult tagTrue = registry.evaluatePredicate(tagCondition, taggedContext, taggedRuntime, noop).orElseThrow();
        require(tagTrue.value() && tagTrue.conditionResult() != null
                        && tagTrue.conditionResult().subject().equals(tagged.reference())
                        && tagTrue.conditionResult().rawResult()
                        && tagTrue.conditionResult().factSummary().contains("ready"),
                "true tag predicate should retain actor and fact");

        SimulationActor untagged = actor("Alex", false, Set.of());
        SimulationContext untaggedContext = simulationContext(untagged, world(null));
        RuntimeExecutionContext untaggedRuntime = runtimeContext(untagged, null, untagged.reference(), null);
        RuntimePredicateResult tagFalse = registry.evaluatePredicate(tagCondition, untaggedContext, untaggedRuntime, noop).orElseThrow();
        require(!tagFalse.value() && tagFalse.conditionResult() != null
                        && tagFalse.conditionResult().subject().equals(untagged.reference())
                        && !tagFalse.conditionResult().rawResult(),
                "false tag predicate should retain the same checked actor");

        NodeDefinition admin = node("admin", BuiltInBlockCatalog.CONDITION_PLAYER_IS_ADMIN, "", "",
                Map.of("outputMode", "BRANCH"));
        require(registry.evaluatePredicate(admin, taggedContext, taggedRuntime, noop).orElseThrow().conditionResult().subject()
                        .equals(tagged.reference()),
                "admin true should retain actor");
        require(registry.evaluatePredicate(admin, untaggedContext, untaggedRuntime, noop).orElseThrow().conditionResult().subject()
                        .equals(untagged.reference()),
                "admin false should retain actor");

        for (ConditionOutputMode mode : ConditionOutputMode.values()) {
            RuntimeNodeExecutionResult result = block(BuiltInBlockCatalog.CONDITION_PLAYER_HAS_TAG).nodeType() == tagCondition.type()
                    ? registry.execute(
                    withConfig(tagCondition, Map.of("tag", "ready", "outputMode", mode.name())),
                    taggedContext,
                    taggedRuntime,
                    noop
            ).orElseThrow() : null;
            require(result != null && result.conditionResult() != null,
                    "every output mode should return the contextual condition result: " + mode);
        }
    }

    private static void trueAndFalsePathsKeepTheActor() {
        GraphDefinition trueGraph = conditionContextGraph("true-path", "pass", "done");
        Scenario trueRun = scenario(trueGraph, actor("Steve", false, Set.of("ready")), world(null));
        SimulationExecutionResult trueResult = trueRun.start();
        require(trueResult.success() && trueResult.actorTags().contains("done"),
                "true path should add done to the checked player");
        require(trueRun.trace().contains("玩家 Steve 拥有标签「ready」") && trueRun.trace().contains("进入“满足”路径"),
                "true trace should show actor, fact and selected path");

        GraphDefinition falseGraph = conditionContextGraph("false-path", "fail", "waiting");
        Scenario falseRun = scenario(falseGraph, actor("Alex", false, Set.of()), world(null));
        SimulationExecutionResult falseResult = falseRun.start();
        require(falseResult.success() && falseResult.actorTags().contains("waiting"),
                "false path should retain the checked player and add waiting");
        require(falseRun.trace().contains("玩家 Alex 没有标签「ready」") && falseRun.trace().contains("进入“不满足”路径"),
                "false trace should show actor, fact and selected path");
    }

    private static void entitySourcesAndEmptyBody() {
        SimulationEntity target = target("测试僵尸", Set.of());
        GraphDefinition targetGraph = graph(
                "target-source",
                List.of(
                        trigger(),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "", Map.of("entitySource", "TARGET_ENTITY")),
                        node("tag", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "context", "body", Map.of("tag", "boss"))
                ),
                List.of(edge("trigger", "started", "context"))
        );
        Scenario targetRun = scenario(targetGraph, actor("Steve", false, Set.of()), world(target));
        SimulationExecutionResult targetResult = targetRun.start();
        require(targetResult.success() && targetResult.targetEntityTags().contains("boss")
                        && !targetResult.actorTags().contains("boss"),
                "TARGET_ENTITY should only mutate the test target");

        GraphDefinition runGraph = graph(
                "run-source",
                List.of(
                        trigger(),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "", Map.of("entitySource", "RUN_ENTITY")),
                        node("tag", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "context", "body", Map.of("tag", "runner"))
                ),
                List.of(edge("trigger", "started", "context"))
        );
        SimulationExecutionResult runResult = scenario(runGraph, actor("Steve", false, Set.of()), world(target("目标", Set.of()))).start();
        require(runResult.actorTags().contains("runner") && !runResult.targetEntityTags().contains("runner"),
                "RUN_ENTITY should resolve the run actor");

        GraphDefinition missingTargetGraph = graph(
                "missing-target-source",
                List.of(
                        trigger(),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "", Map.of("entitySource", "TARGET_ENTITY")),
                        node("tag", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "context", "body", Map.of("tag", "wrong"))
                ),
                List.of(edge("trigger", "started", "context"))
        );
        Scenario missingTarget = scenario(missingTargetGraph, actor("Steve", false, Set.of()), world(null));
        require(!missingTarget.start().success() && missingTarget.trace().contains("目标实体缺失"),
                "missing TARGET_ENTITY should fail closed before body execution");

        GraphDefinition emptyBody = graph(
                "empty-context-body",
                List.of(trigger(), node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "", Map.of("entitySource", "RUN_ENTITY"))),
                List.of(edge("trigger", "started", "context"))
        );
        require(scenario(emptyBody, actor("Steve", false, Set.of()), world(null)).start().success(),
                "empty execute-as body should restore and complete");

        GraphDefinition missing = graph(
                "missing-condition-subject",
                List.of(
                        trigger(),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "", Map.of("entitySource", "CONDITION_SUBJECT")),
                        node("tag", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "context", "body", Map.of("tag", "wrong"))
                ),
                List.of(edge("trigger", "started", "context"))
        );
        Scenario missingRun = scenario(missing, actor("Steve", false, Set.of()), world(null));
        SimulationExecutionResult missingResult = missingRun.start();
        require(!missingResult.success() && !missingResult.actorTags().contains("wrong")
                        && missingRun.trace().contains("当前路径没有可用的条件对象"),
                "missing condition subject should fail closed before body execution");
    }

    private static void invalidConditionSubjectsFailClosed() {
        assertInvalidConditionSubject(
                new RuntimeSubjectReference("block", RuntimeSubjectReference.Kind.BLOCK, "目标方块"),
                "当前条件对象不是实体"
        );
        assertInvalidConditionSubject(
                new RuntimeSubjectReference("missing-entity", RuntimeSubjectReference.Kind.ENTITY, "已消失实体"),
                "实体无法解析"
        );
    }

    private static void assertInvalidConditionSubject(RuntimeSubjectReference subject, String expectedTrace) {
        GraphDefinition graph = graph(
                "invalid-subject-" + subject.kind(),
                List.of(
                        trigger(),
                        node("condition", BuiltInBlockCatalog.CONDITION_PLAYER_HAS_TAG, "", "",
                                Map.of("tag", "ready", "outputMode", "BRANCH")),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "",
                                Map.of("entitySource", "CONDITION_SUBJECT")),
                        node("body", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "context", "body", Map.of("tag", "wrong"))
                ),
                List.of(edge("trigger", "started", "condition"), edge("condition", "pass", "context"))
        );
        SimulationActor actor = actor("Steve", false, Set.of());
        BoundedTraceBuffer traces = new BoundedTraceBuffer(2, 64);
        boolean[] bodyRan = {false};
        RuntimeServices services = new RuntimeServices() {
            @Override
            public java.util.Optional<RuntimeNodeExecutionResult> executeSimulationNode(
                    NodeDefinition node,
                    RuntimeExecutionContext context
            ) {
                if (node.id().equals("condition")) {
                    return java.util.Optional.of(new RuntimeNodeExecutionResult(
                            "pass",
                            "测试条件。",
                            new RuntimeConditionResult(node.id(), node.blockId(), subject, true, "测试条件")
                    ));
                }
                if (node.id().equals("body")) {
                    bodyRan[0] = true;
                    return java.util.Optional.of(new RuntimeNodeExecutionResult("done", "body ran"));
                }
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<RuntimeSubjectReference> runEntity(UUID playerId, String sessionId) {
                return java.util.Optional.of(actor.reference());
            }

            @Override
            public boolean entityResolvable(RuntimeSubjectReference entity, UUID playerId, String sessionId) {
                return actor.reference().equals(entity);
            }

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
        RuntimeResult result = new GraphRuntime(
                new GraphCompiler().compile(graph),
                new InMemoryStateStore(),
                traces,
                services,
                RuntimeLimits.spikeDefaults(),
                1L
        ).start(new TriggerEvent("manual.test.start", "", actor.id(), "invalid-subject"));
        String trace = traces.get(result.traceId()).orElseThrow().steps().stream()
                .map(step -> step.message()).reduce("", (left, right) -> left + "\n" + right);
        require(!result.success() && !bodyRan[0] && trace.contains(expectedTrace),
                "invalid condition subject should fail closed: " + subject.kind());
    }

    private static void laterConditionReplacesTheSubject() {
        SimulationEntity target = target("目标僵尸", Set.of());
        GraphDefinition graph = graph(
                "replace-condition",
                List.of(
                        trigger(),
                        node("player-condition", BuiltInBlockCatalog.CONDITION_PLAYER_HAS_TAG, "", "",
                                Map.of("tag", "ready", "outputMode", "FAIL_ONLY")),
                        node("target-context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "",
                                Map.of("entitySource", "TARGET_ENTITY")),
                        node("entity-condition", BuiltInBlockCatalog.CONDITION_CONTEXT_ENTITY_HAS_TAG, "target-context", "body",
                                Map.of("tag", "ready", "outputMode", "FAIL_ONLY")),
                        node("subject-context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "target-context", "body",
                                Map.of("entitySource", "CONDITION_SUBJECT")),
                        node("mark", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "subject-context", "body", Map.of("tag", "latest"))
                ),
                List.of(
                        edge("trigger", "started", "player-condition"),
                        edge("player-condition", "fail", "target-context"),
                        edge("entity-condition", "fail", "subject-context")
                )
        );
        SimulationExecutionResult result = scenario(graph, actor("Steve", false, Set.of()), world(target)).start();
        require(result.success() && result.targetEntityTags().contains("latest") && !result.actorTags().contains("latest"),
                "later context-entity condition should replace the earlier player condition subject");
    }

    private static void nestedContextsRestoreEntities() {
        SimulationEntity target = target("目标僵尸", Set.of());
        GraphDefinition graph = graph(
                "nested-context",
                List.of(
                        trigger(),
                        node("outer", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "", Map.of("entitySource", "TARGET_ENTITY")),
                        node("target-before", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "outer", "body", Map.of("tag", "before")),
                        node("inner", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "outer", "body", Map.of("entitySource", "RUN_ENTITY")),
                        node("player", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "inner", "body", Map.of("tag", "inner")),
                        node("target-after", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "outer", "body", Map.of("tag", "after"))
                ),
                List.of(
                        edge("trigger", "started", "outer"),
                        edge("target-before", "done", "inner"),
                        edge("inner", "done", "target-after")
                )
        );
        SimulationExecutionResult result = scenario(graph, actor("Steve", false, Set.of()), world(target)).start();
        require(result.actorTags().contains("inner")
                        && result.targetEntityTags().containsAll(Set.of("before", "after"))
                        && !result.targetEntityTags().contains("inner"),
                "nested context should restore target after the inner run-entity scope");
    }

    private static void delayKeepsEntityAndConditionContext() {
        GraphDefinition graph = graph(
                "context-delay",
                List.of(
                        trigger(),
                        node("condition", BuiltInBlockCatalog.CONDITION_PLAYER_HAS_TAG, "", "",
                                Map.of("tag", "ready", "outputMode", "FAIL_ONLY")),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "",
                                Map.of("entitySource", "CONDITION_SUBJECT")),
                        node("delay", BuiltInBlockCatalog.TIMER_WAIT, "context", "body", Map.of("durationSeconds", "1")),
                        node("tag", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "context", "body", Map.of("tag", "delayed"))
                ),
                List.of(
                        edge("trigger", "started", "condition"),
                        edge("condition", "fail", "context"),
                        edge("delay", "timer_completed", "tag")
                )
        );
        Scenario scenario = scenario(graph, actor("Alex", false, Set.of()), world(null));
        SimulationExecutionResult waiting = scenario.start();
        require(waiting.status() == SimulationExecutionResult.Status.WAITING, "delay should suspend context body");
        SimulationExecutionResult completed = scenario.drain();
        require(completed.success() && completed.actorTags().contains("delayed")
                        && scenario.trace().contains("计时器完成")
                        && scenario.trace().contains("上下文实体 Alex"),
                "delay resume should keep current entity and condition subject");
    }

    private static void interleavedLoopContextFramesResume() {
        SimulationEntity target = target("循环目标", Set.of());
        GraphDefinition graph = graph(
                "loop-context-loop-delay",
                List.of(
                        trigger(),
                        node("outer-loop", BuiltInBlockCatalog.CONTROL_LOOP_COUNT, "", "", Map.of("count", "1")),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "outer-loop", "body", Map.of("entitySource", "TARGET_ENTITY")),
                        node("inner-loop", BuiltInBlockCatalog.CONTROL_LOOP_COUNT, "context", "body", Map.of("count", "1")),
                        node("delay", BuiltInBlockCatalog.TIMER_WAIT, "inner-loop", "body", Map.of("durationSeconds", "1")),
                        node("tag", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "inner-loop", "body", Map.of("tag", "deep"))
                ),
                List.of(
                        edge("trigger", "started", "outer-loop"),
                        edge("delay", "timer_completed", "tag")
                )
        );
        Scenario scenario = scenario(graph, actor("Steve", false, Set.of()), world(target));
        require(scenario.start().status() == SimulationExecutionResult.Status.WAITING,
                "interleaved scopes should suspend at deep delay");
        SimulationExecutionResult result = scenario.drain();
        require(result.success() && result.targetEntityTags().contains("deep") && !result.actorTags().contains("deep"),
                "outer loop -> context -> inner loop should validate and resume the target entity");
    }

    private static void loopUntilUsesCurrentEntityPredicate() {
        SimulationEntity target = target("就绪目标", Set.of("ready"));
        NodeDefinition until = node(
                "until",
                BuiltInBlockCatalog.CONTROL_LOOP_UNTIL,
                "context",
                "body",
                Map.of(),
                List.of(new ConditionSlotDefinition("condition-1", false))
        );
        GraphDefinition graph = graph(
                "context-loop-until",
                List.of(
                        trigger(),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "", Map.of("entitySource", "TARGET_ENTITY")),
                        until,
                        node("predicate", BuiltInBlockCatalog.CONDITION_CONTEXT_ENTITY_HAS_TAG, "until", "condition-1",
                                Map.of("tag", "ready", "outputMode", "BRANCH")),
                        node("body", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "until", "body", Map.of("tag", "should-not-run"))
                ),
                List.of(edge("trigger", "started", "context"))
        );
        Scenario scenario = scenario(graph, actor("Steve", false, Set.of()), world(target));
        SimulationExecutionResult result = scenario.start();
        require(result.success() && !result.targetEntityTags().contains("should-not-run")
                        && scenario.trace().contains("上下文实体 就绪目标 拥有标签「ready」"),
                "loop-until rack should evaluate the current context entity without entering body");
    }

    private static void runsAreIsolatedAndCancellationIsSafe() {
        SimulationActor sharedInput = actor("Shared", false, Set.of());
        GraphDefinition graph = graph(
                "run-isolation",
                List.of(
                        trigger(),
                        node("condition", BuiltInBlockCatalog.CONDITION_PLAYER_HAS_TAG, "", "",
                                Map.of("tag", "done", "outputMode", "BRANCH")),
                        node("leaked", BuiltInBlockCatalog.ACTION_PLAYER_ADD_TAG, "", "", Map.of("tag", "leaked")),
                        node("clean", BuiltInBlockCatalog.ACTION_PLAYER_ADD_TAG, "", "", Map.of("tag", "done"))
                ),
                List.of(
                        edge("trigger", "started", "condition"),
                        edge("condition", "pass", "leaked"),
                        edge("condition", "fail", "clean")
                )
        );
        SimulationExecutionResult first = scenario(graph, sharedInput, world(null)).start();
        SimulationExecutionResult second = scenario(graph, sharedInput, world(null)).start();
        require(first.actorTags().contains("done") && second.actorTags().contains("done")
                        && !second.actorTags().contains("leaked") && sharedInput.tags().isEmpty(),
                "each run should copy mutable actor state and keep the input untouched");

        GraphDefinition delayed = graph(
                "cancel-context",
                List.of(
                        trigger(),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "", Map.of("entitySource", "RUN_ENTITY")),
                        node("delay", BuiltInBlockCatalog.TIMER_WAIT, "context", "body", Map.of("durationSeconds", "1")),
                        node("tag", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "context", "body", Map.of("tag", "stale"))
                ),
                List.of(edge("trigger", "started", "context"), edge("delay", "timer_completed", "tag"))
        );
        Scenario cancelled = scenario(delayed, actor("Cancelled", false, Set.of()), world(null));
        cancelled.start();
        cancelled.cancel();
        SimulationExecutionResult cancelledResult = cancelled.drain();
        require(cancelledResult.status() == SimulationExecutionResult.Status.FAILED
                        && !cancelledResult.actorTags().contains("stale"),
                "cancelled continuation must not resume entity actions");
    }

    private static void realApiExecutionContextRoundTrip() throws Exception {
        Path storageRoot = Files.createTempDirectory("pixel-logic-entity-context-api-self-check-");
        try (PixelLogicSpikeService service = new PixelLogicSpikeService((playerId, message) -> {
        }, Runnable::run, ignored -> {
        }, Duration.ofSeconds(1), storageRoot);
             PixelLogicApiServer server = PixelLogicApiServer.start(
                     service,
                     Runnable::run,
                     PixelLogicApiServer.DEFAULT_HOST,
                     0
             )) {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://" + PixelLogicApiServer.DEFAULT_HOST + ":" + server.port();

            installApiGraph(client, base, apiConditionSubjectGraph());
            JsonObject delayedStart = apiJson(client, "POST", base + "/api/pixellogic/test/start", """
                    {"testContext":{"actor":{"displayName":"API玩家","tags":[],"operator":false}}}
                    """);
            require(delayedStart.get("ok").getAsBoolean()
                            && "WAITING".equals(delayedStart.get("runStatus").getAsString()),
                    "real API condition-subject run should expose WAITING while delay is pending");
            JsonObject delayedComplete = waitForApiRun(
                    client,
                    base,
                    delayedStart.get("runId").getAsString(),
                    5
            );
            JsonObject delayedSimulation = delayedComplete.getAsJsonObject("simulation");
            String delayedTrace = apiTrace(delayedComplete);
            require("COMPLETED".equals(delayedComplete.get("runStatus").getAsString())
                            && delayedComplete.get("terminal").getAsBoolean(),
                    "real API condition-subject run should complete after polling");
            require(apiTags(delayedSimulation, "actorTags").contains("waiting"),
                    "false condition subject should still receive waiting after API delay resume");
            require(delayedTrace.contains("玩家 API玩家 没有标签「ready」")
                            && delayedTrace.contains("使用条件对象 API玩家 进入实体执行上下文")
                            && delayedTrace.contains("计时器完成")
                            && delayedTrace.contains("为上下文实体 API玩家 添加标签「waiting」"),
                    "real API trace should retain the false condition subject across delay");

            installApiGraph(client, base, apiTargetEntityGraph());
            JsonObject targetRun = apiJson(client, "POST", base + "/api/pixellogic/test/start", """
                    {"testContext":{
                      "actor":{"displayName":"API玩家","tags":["actor-only"],"operator":false},
                      "world":{"targetEntity":{
                        "enabled":true,
                        "entityTypeId":"minecraft:zombie",
                        "displayName":"API目标",
                        "tags":[" seed ",null,"seed",""]
                      }}
                    }}
                    """);
            JsonObject targetSimulation = targetRun.getAsJsonObject("simulation");
            require(targetRun.get("ok").getAsBoolean()
                            && "COMPLETED".equals(targetRun.get("runStatus").getAsString()),
                    "real API TARGET_ENTITY run should complete");
            require(targetSimulation.get("targetEntityEnabled").getAsBoolean()
                            && "minecraft:zombie".equals(targetSimulation.get("targetEntityTypeId").getAsString())
                            && "API目标".equals(targetSimulation.get("targetEntityDisplayName").getAsString()),
                    "target entity type and display name should round-trip through parser and result");
            require(apiTags(targetSimulation, "initialTargetEntityTags").equals(List.of("seed"))
                            && apiTags(targetSimulation, "targetEntityTags").containsAll(List.of("seed", "target"))
                            && !apiTags(targetSimulation, "actorTags").contains("target"),
                    "TARGET_ENTITY should normalize request tags and mutate only the target result");
            require(apiTrace(targetRun).contains("使用目标实体 API目标 进入实体执行上下文")
                            && apiTrace(targetRun).contains("为上下文实体 API目标 添加标签「target」"),
                    "real API TARGET_ENTITY trace should name the selected target");

            JsonObject nullTagsRun = apiJson(client, "POST", base + "/api/pixellogic/test/start", """
                    {"testContext":{"world":{"targetEntity":{
                      "enabled":true,
                      "entityTypeId":"minecraft:zombie",
                      "displayName":"空标签目标",
                      "tags":null
                    }}}}
                    """);
            JsonObject nullTagsSimulation = nullTagsRun.getAsJsonObject("simulation");
            require(apiTags(nullTagsSimulation, "initialTargetEntityTags").isEmpty()
                            && apiTags(nullTagsSimulation, "targetEntityTags").equals(List.of("target")),
                    "null target tags should use the existing empty-list boundary");

            requireBadTargetTags(client, base, "[\"bad tag\"]");
            requireBadTargetTags(client, base, "\"not-an-array\"");
        }
    }

    private static GraphDefinition apiConditionSubjectGraph() {
        return graph(
                "demo-start-flow",
                List.of(
                        trigger(),
                        node("condition", BuiltInBlockCatalog.CONDITION_PLAYER_HAS_TAG, "", "",
                                Map.of("tag", "ready", "outputMode", "BRANCH")),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "",
                                Map.of("entitySource", "CONDITION_SUBJECT")),
                        node("delay", BuiltInBlockCatalog.TIMER_WAIT, "context", "body",
                                Map.of("durationSeconds", "1")),
                        node("waiting", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "context", "body",
                                Map.of("tag", "waiting"))
                ),
                List.of(
                        edge("trigger", "started", "condition"),
                        edge("condition", "fail", "context"),
                        edge("delay", "timer_completed", "waiting")
                )
        );
    }

    private static GraphDefinition apiTargetEntityGraph() {
        return graph(
                "demo-start-flow",
                List.of(
                        trigger(),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "",
                                Map.of("entitySource", "TARGET_ENTITY")),
                        node("target", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "context", "body",
                                Map.of("tag", "target"))
                ),
                List.of(edge("trigger", "started", "context"))
        );
    }

    private static void installApiGraph(HttpClient client, String base, GraphDefinition graph) throws Exception {
        GraphDocument document = GraphDocument.fromGraphDefinition(graph, "Entity Execution Context API Self Check");
        JsonObject draft = apiJson(
                client,
                "PUT",
                base + "/api/pixellogic/graphs/demo-start-flow/draft",
                "{\"graph\":" + GSON.toJson(document) + "}"
        );
        require(draft.get("ok").getAsBoolean(), "entity context API graph draft should save");
        JsonObject validation = apiJson(client, "POST", base + "/api/pixellogic/graphs/demo-start-flow/validate", "");
        require(validation.getAsJsonObject("validation").get("valid").getAsBoolean(),
                "entity context API graph should validate");
        JsonObject commit = apiJson(client, "POST", base + "/api/pixellogic/graphs/demo-start-flow/commit", "");
        require(commit.get("ok").getAsBoolean(), "entity context API graph should commit");
    }

    private static JsonObject waitForApiRun(HttpClient client, String base, String runId, int seconds) throws Exception {
        JsonObject response = new JsonObject();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            response = apiJson(client, "GET", base + "/api/pixellogic/test/runs/" + runId, "");
            if (response.has("terminal") && response.get("terminal").getAsBoolean()) {
                return response;
            }
            Thread.sleep(50L);
        }
        return response;
    }

    private static JsonObject apiJson(HttpClient client, String method, String uri, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri)).header("Accept", "application/json");
        if ("PUT".equals(method)) {
            request.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body));
        } else if ("POST".equals(method)) {
            if (body == null || body.isBlank()) {
                request.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            }
        } else {
            request.GET();
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        require(response.headers().firstValue("content-type").orElse("").toLowerCase().contains("application/json"),
                "entity context API response should be JSON");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static List<String> apiTags(JsonObject simulation, String field) {
        JsonArray tags = simulation.getAsJsonArray(field);
        return tags.asList().stream().map(element -> element.getAsString()).toList();
    }

    private static String apiTrace(JsonObject response) {
        return response.getAsJsonObject("trace").getAsJsonArray("steps").asList().stream()
                .map(element -> element.getAsJsonObject().get("message").getAsString())
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private static void requireBadTargetTags(HttpClient client, String base, String tagsJson) throws Exception {
        JsonObject response = apiJson(client, "POST", base + "/api/pixellogic/test/start", """
                {"testContext":{"world":{"targetEntity":{
                  "enabled":true,
                  "entityTypeId":"minecraft:zombie",
                  "displayName":"坏标签目标",
                  "tags":%s
                }}}}
                """.formatted(tagsJson));
        require(!response.get("ok").getAsBoolean()
                        && "BAD_TEST_CONTEXT".equals(response.getAsJsonObject("error").get("code").getAsString()),
                "malformed target tags should preserve the existing BAD_TEST_CONTEXT boundary: " + tagsJson);
    }

    private static GraphDefinition conditionContextGraph(String id, String conditionOutput, String tag) {
        return graph(
                id,
                List.of(
                        trigger(),
                        node("condition", BuiltInBlockCatalog.CONDITION_PLAYER_HAS_TAG, "", "",
                                Map.of("tag", "ready", "outputMode", "BRANCH")),
                        node("context", BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "", "",
                                Map.of("entitySource", "CONDITION_SUBJECT")),
                        node("tag", BuiltInBlockCatalog.ACTION_CONTEXT_ENTITY_ADD_TAG, "context", "body", Map.of("tag", tag))
                ),
                List.of(edge("trigger", "started", "condition"), edge("condition", conditionOutput, "context"))
        );
    }

    private static Scenario scenario(GraphDefinition graph, SimulationActor actor, SimulationWorld world) {
        return new Scenario(graph, actor, world);
    }

    private static SimulationActor actor(String name, boolean operator, Set<String> tags) {
        return new SimulationActor(
                PLAYER_ID,
                name,
                true,
                operator,
                tags,
                SimulationPosition.overworldSpawn()
        );
    }

    private static SimulationEntity target(String name, Set<String> tags) {
        return new SimulationEntity(TARGET_ID, "minecraft:zombie", name, tags);
    }

    private static SimulationWorld world(SimulationEntity target) {
        return new SimulationWorld("minecraft:overworld", SimulationBlockFact.disabled(), List.of(), target);
    }

    private static SimulationContext simulationContext(SimulationActor actor, SimulationWorld world) {
        return new SimulationContext(
                UUID.randomUUID().toString(),
                1L,
                actor,
                world
        );
    }

    private static RuntimeExecutionContext runtimeContext(
            SimulationActor actor,
            SimulationEntity target,
            RuntimeSubjectReference current,
            RuntimeConditionResult condition
    ) {
        return new RuntimeExecutionContext(
                actor.id(),
                "entity-context-self-check",
                actor.reference(),
                target == null ? null : target.reference(),
                current,
                condition
        );
    }

    private static NodeDefinition trigger() {
        return node("trigger", BuiltInBlockCatalog.TRIGGER_MANUAL_TEST, "", "", Map.of());
    }

    private static NodeDefinition node(
            String id,
            String blockId,
            String parentId,
            String parentSlot,
            Map<String, String> config
    ) {
        return node(id, blockId, parentId, parentSlot, config, List.of());
    }

    private static NodeDefinition node(
            String id,
            String blockId,
            String parentId,
            String parentSlot,
            Map<String, String> config,
            List<ConditionSlotDefinition> conditionSlots
    ) {
        BlockDefinition block = block(blockId);
        Map<String, String> merged = new LinkedHashMap<>(block.defaultConfig());
        merged.putAll(config);
        List<SlotDefinition> slots = new ArrayList<>(block.inputSlots());
        slots.addAll(block.outputSlots());
        return new NodeDefinition(
                id,
                block.nodeType(),
                block.id(),
                parentId,
                parentSlot,
                conditionSlots,
                slots,
                merged
        );
    }

    private static NodeDefinition withConfig(NodeDefinition node, Map<String, String> config) {
        return new NodeDefinition(
                node.id(), node.type(), node.blockId(), node.parentContainerId(), node.parentSlot(),
                node.conditionSlots(), node.slots(), config
        );
    }

    private static EdgeDefinition edge(String source, String sourceSlot, String target) {
        return new EdgeDefinition(
                source + "-" + sourceSlot + "-" + target,
                source,
                sourceSlot,
                target,
                "input",
                EdgeType.CONTROL
        );
    }

    private static GraphDefinition graph(String id, List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        return new GraphDefinition(id, nodes, edges, Map.of("manual.test.start", "trigger"));
    }

    private static BlockDefinition block(String blockId) {
        return BuiltInBlockCatalog.block(blockId).orElseThrow();
    }

    private static RuntimeServices noopServices() {
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

    private static final class Scenario {
        private final SimulationActor actor;
        private final SimulationWorld world;
        private final BoundedTraceBuffer traces = new BoundedTraceBuffer(8, 256);
        private final HarnessServices services = new HarnessServices();
        private final AtomicReference<SimulationExecutionResult> latest = new AtomicReference<>();
        private final SimulationRunner runner;

        private Scenario(GraphDefinition graph, SimulationActor actor, SimulationWorld world) {
            this.actor = actor;
            this.world = world;
            var compiled = new GraphCompiler().compile(graph);
            this.runner = new SimulationRunner(runtimeServices -> {
                GraphRuntime runtime = new GraphRuntime(
                        compiled,
                        new InMemoryStateStore(),
                        traces,
                        runtimeServices,
                        new RuntimeLimits(256, 16),
                        1L
                );
                services.runtime = runtime;
                return runtime;
            }, services, SimulationExecutionRegistry.playerTags());
        }

        private SimulationExecutionResult start() {
            SimulationExecutionResult result = runner.run(new SimulationExecutionRequest(
                    "self-check",
                    "manual.test.start",
                    "",
                    actor,
                    world,
                    "entity-context-self-check",
                    1L
            ), latest::set);
            latest.set(result);
            return result;
        }

        private SimulationExecutionResult drain() {
            for (int index = 0; index < 64 && !services.timers.isEmpty(); index += 1) {
                services.runtime.resumeTimer(services.timers.removeFirst());
            }
            require(services.timers.isEmpty(), "timer queue should drain within its bound");
            return latest.get();
        }

        private void cancel() {
            services.runtime.cancelPendingContinuations();
        }

        private String trace() {
            SimulationExecutionResult result = latest.get();
            return result == null ? "" : traces.get(result.traceId()).orElseThrow().steps().stream()
                    .map(step -> step.message())
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }

    private static final class HarnessServices implements RuntimeServices {
        private final ArrayDeque<TimerContinuation> timers = new ArrayDeque<>();
        private GraphRuntime runtime;

        @Override
        public void sendPlayerMessage(UUID playerId, String message) {
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void scheduleTimer(Duration delay, TimerContinuation continuation) {
            timers.addLast(continuation);
        }
    }
}
