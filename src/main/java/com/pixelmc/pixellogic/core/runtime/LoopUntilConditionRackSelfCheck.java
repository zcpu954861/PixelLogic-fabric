package com.pixelmc.pixellogic.core.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pixelmc.pixellogic.core.catalog.BlockCapability;
import com.pixelmc.pixellogic.core.catalog.BlockDefinition;
import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.model.ConditionSlotDefinition;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.EntityTargetRef;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;
import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.core.simulation.context.SimulationBlockFact;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;
import com.pixelmc.pixellogic.core.simulation.context.SimulationPosition;
import com.pixelmc.pixellogic.core.simulation.context.SimulationRegionFact;
import com.pixelmc.pixellogic.core.simulation.context.SimulationWorld;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationExecutionRegistry;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;
import com.pixelmc.pixellogic.core.trace.ExecutionTrace;
import com.pixelmc.pixellogic.server.storage.GraphDocument;
import com.pixelmc.pixellogic.server.storage.GraphStorageService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class LoopUntilConditionRackSelfCheck {
    private static final UUID PLAYER_ID = UUID.nameUUIDFromBytes("loop-until-condition-rack-self-check".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final String SLOT_A = "condition-a";
    private static final String SLOT_B = "condition-b";
    private static final RuntimeServices NOOP_SERVICES = new RuntimeServices() {
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

    private LoopUntilConditionRackSelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        run("loopUntilConditionRackSelfCheck", () -> {
            checkCatalogAndModel();
            checkStorageRoundTrip();
            checkValidation();
            checkSharedPredicates();
            checkRuntimeMatrix();
        });
    }

    private static void checkCatalogAndModel() {
        BlockDefinition untilBlock = block(BuiltInBlockCatalog.CONTROL_LOOP_UNTIL);
        require(untilBlock.nodeType() == NodeType.CONTROL_LOOP_UNTIL, "loop until catalog node type should match");
        require(untilBlock.capabilities().equals(List.of(BlockCapability.PREDICATE_RACK)), "loop until should declare one predicate rack capability");
        require(untilBlock.containerSlots().equals(List.of("body")), "dynamic condition ids must not enter static container slots");
        require(untilBlock.inputSlots().stream().anyMatch(slot -> slot.id().equals("input")), "loop until should expose input");
        require(untilBlock.outputSlots().stream().anyMatch(slot -> slot.id().equals("done")), "loop until should expose done");

        Set<String> expectedPredicates = Set.of(
                BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG,
                BuiltInBlockCatalog.CONDITION_PLAYER_IS_ADMIN,
                BuiltInBlockCatalog.CONDITION_PLAYER_DIMENSION_IS,
                BuiltInBlockCatalog.CONDITION_PLAYER_IN_REGION,
                BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IS_TYPE
        );
        Set<String> actualPredicates = new HashSet<>();
        BuiltInBlockCatalog.catalog().blocks().stream()
                .filter(item -> item.capabilities().contains(BlockCapability.PREDICATE))
                .forEach(item -> {
                    actualPredicates.add(item.id());
                    require(!item.predicateSummaryTemplate().isBlank(), "predicate should expose a capsule summary: " + item.id());
                    require(!item.predicateNegatedSummaryTemplate().isBlank(), "predicate should expose a negated capsule summary: " + item.id());
                });
        require(actualPredicates.equals(expectedPredicates), "predicate capability set should include generic entity tags");

        GraphDefinition graph = storageGraph("loop-until-model");
        GraphDefinition loaded = GraphDocument.fromGraphDefinition(graph, "Loop Until Model").toGraphDefinition();
        NodeDefinition loadedLoop = node(loaded, "until");
        require(loadedLoop.conditionSlots().equals(List.of(
                new ConditionSlotDefinition(SLOT_A, false),
                new ConditionSlotDefinition(SLOT_B, true)
        )), "condition slot order, stable ids, and negation should survive document conversion");
        require(node(loaded, "tag-condition").parentContainerId().equals("until")
                        && node(loaded, "tag-condition").parentSlot().equals(SLOT_A),
                "predicate membership should survive document conversion");
        require(node(loaded, "body").parentContainerId().equals("until")
                        && node(loaded, "body").parentSlot().equals("body"),
                "body membership should remain separate from dynamic predicate slots");

        List<ConditionSlotDefinition> slotsAfterMiddleDelete = loadedLoop.conditionSlots().stream()
                .filter(slot -> !slot.slotId().equals(SLOT_A))
                .toList();
        require(slotsAfterMiddleDelete.equals(List.of(new ConditionSlotDefinition(SLOT_B, true))),
                "deleting one slot must not renumber or rewrite the remaining stable id");
    }

    private static void checkStorageRoundTrip() throws Exception {
        GraphDefinition graph = storageGraph("loop-until-storage");
        Path root = Files.createTempDirectory("pixel-logic-loop-until-storage-");
        try {
            GraphStorageService storage = new GraphStorageService(root);
            GraphDocument committed = storage.ensureCommitted(graph, "Loop Until Storage");
            GraphDocument loaded = storage.loadCommitted(graph.id());
            require(node(loaded.toGraphDefinition(), "until").conditionSlots().equals(node(graph, "until").conditionSlots()),
                    "real storage roundtrip should preserve typed condition slots");

            GraphDefinition toggledGraph = replaceConditionSlots(graph, "until", List.of(
                    new ConditionSlotDefinition(SLOT_A, true),
                    new ConditionSlotDefinition(SLOT_B, true)
            ));
            GraphDocument savedDraft = storage.saveDraft(graph.id(), GraphDocument.fromGraphDefinition(toggledGraph, "Loop Until Storage"));
            GraphDocument loadedDraft = storage.loadDraft(graph.id()).orElseThrow();
            require(!savedDraft.fingerprint().equals(committed.fingerprint()), "negation change should affect graph fingerprint");
            require(node(loadedDraft.toGraphDefinition(), "until").conditionSlots().getFirst().negated(),
                    "draft save/load should preserve negation");

            Gson gson = new Gson();
            JsonObject legacyJson = JsonParser.parseString(gson.toJson(GraphDocument.fromGraphDefinition(graph, "Legacy"))).getAsJsonObject();
            JsonArray nodes = legacyJson.getAsJsonArray("nodes");
            nodes.forEach(element -> element.getAsJsonObject().remove("conditionSlots"));
            GraphDocument legacy = gson.fromJson(legacyJson, GraphDocument.class);
            require(legacy.toGraphDefinition().nodes().stream().allMatch(item -> item.conditionSlots().isEmpty()),
                    "old graph JSON without conditionSlots should normalize to empty lists");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void checkValidation() {
        GraphValidator validator = new GraphValidator();

        GraphDefinition valid = simpleUntilGraph(
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(hasTag("condition", "until", SLOT_A, "ready", "BRANCH")),
                List.of(debug("body", "until", "body", "body")),
                List.of()
        );
        require(!validator.hasErrors(validator.validate(valid)), "valid condition rack should pass validation");

        GraphDefinition noSlots = simpleUntilGraph(List.of(), List.of(), List.of(), List.of());
        require(hasIssue(noSlots, ValidationIssue.Severity.WARNING, "condition_rack_empty"), "zero slots should be a saveable warning");
        require(hasIssue(noSlots, ValidationIssue.Severity.WARNING, "container_body_empty"), "empty body should remain a warning");
        require(!validator.hasErrors(validator.validate(noSlots)), "zero slots and empty body should remain saveable");

        GraphDefinition emptySlot = simpleUntilGraph(
                List.of(new ConditionSlotDefinition(SLOT_A, false)), List.of(), List.of(), List.of());
        require(hasIssue(emptySlot, ValidationIssue.Severity.WARNING, "condition_slot_empty"), "empty slot should be a warning");
        require(!validator.hasErrors(validator.validate(emptySlot)), "empty slot should not block saving");

        require(hasError(simpleUntilGraph(
                List.of(new ConditionSlotDefinition("", false)), List.of(), List.of(), List.of()), "condition_slot_id_missing"),
                "blank stable slot id should fail validation");
        require(hasError(simpleUntilGraph(
                List.of(new ConditionSlotDefinition(SLOT_A, false), new ConditionSlotDefinition(SLOT_A, true)),
                List.of(hasTag("condition", "until", SLOT_A, "ready", "BRANCH")), List.of(), List.of()),
                "condition_slot_id_duplicate"), "duplicate stable slot id should fail validation");
        require(hasError(simpleUntilGraph(
                List.of(new ConditionSlotDefinition("body", false)),
                List.of(hasTag("condition", "until", "body", "ready", "BRANCH")), List.of(), List.of()),
                "condition_slot_id_conflict"), "dynamic slot id must not collide with body");

        require(hasError(simpleUntilGraph(
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(debug("not-predicate", "until", SLOT_A, "bad")), List.of(), List.of()),
                "condition_slot_predicate_required"), "non-predicate block should not enter the condition rack");
        require(hasError(simpleUntilGraph(
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(
                        hasTag("condition-a", "until", SLOT_A, "ready", "BRANCH"),
                        isAdmin("condition-b", "until", SLOT_A, "BRANCH")
                ), List.of(), List.of()), "condition_slot_multiple_nodes"), "one dynamic slot should accept at most one node");
        require(hasError(simpleUntilGraph(
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(hasTag("condition", "until", SLOT_A, "ready", "BRANCH")),
                List.of(),
                List.of(edge("capsule-edge", "condition", "pass", "done", "input"))
        ), "condition_slot_control_edge"), "rack capsule should not retain control edges");
        require(hasError(simpleUntilGraph(
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(hasTag("condition", "until", "missing-slot", "ready", "BRANCH")), List.of(), List.of()),
                "container_slot_invalid"), "capsule membership must reference a declared stable slot id");

        NodeDefinition countWithRack = new NodeDefinition(
                "count",
                NodeType.CONTROL_LOOP_COUNT,
                BuiltInBlockCatalog.CONTROL_LOOP_COUNT,
                "",
                "",
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(in("input"), out("done")),
                Map.of("count", "1")
        );
        GraphDefinition unsupportedRack = graph(
                "unsupported-rack",
                List.of(manualTrigger(), countWithRack),
                List.of(edge("e1", "trigger", "started", "count", "input"))
        );
        require(hasError(unsupportedRack, "condition_rack_not_supported"), "non-rack blocks must reject conditionSlots data");

        GraphDefinition ordinaryLooseCondition = graph(
                "ordinary-condition",
                List.of(manualTrigger(), hasTag("condition", "", "", "ready", "BRANCH")),
                List.of()
        );
        require(!validator.hasErrors(validator.validate(ordinaryLooseCondition)),
                "ordinary loose condition behavior should not regress");
    }

    private static void checkSharedPredicates() {
        SimulationRegionFact lobby = new SimulationRegionFact("大厅", "minecraft:overworld", -5, 60, -5, 5, 70, 5);
        SimulationBlockFact stone = new SimulationBlockFact(true, "minecraft:overworld", 0, 64, 0, "minecraft:stone");
        SimulationWorld matchingWorld = new SimulationWorld("minecraft:overworld", stone, List.of(lobby));
        SimulationWorld falseTargetWorld = new SimulationWorld("minecraft:overworld", SimulationBlockFact.disabled(), List.of(lobby));

        assertSharedPredicate(
                hasTag("tag", "", "", "ready", "BRANCH"),
                context(actor(true, Set.of("ready"), SimulationPosition.overworldSpawn()), matchingWorld),
                context(actor(true, Set.of(), SimulationPosition.overworldSpawn()), matchingWorld)
        );
        assertSharedPredicate(
                isAdmin("admin", "", "", "BRANCH"),
                context(actor(true, Set.of(), SimulationPosition.overworldSpawn()), matchingWorld),
                context(actor(false, Set.of(), SimulationPosition.overworldSpawn()), matchingWorld)
        );
        assertSharedPredicate(
                dimension("dimension", "", "", "minecraft:overworld", "BRANCH"),
                context(actor(false, Set.of(), SimulationPosition.overworldSpawn()), matchingWorld),
                context(actor(false, Set.of(), new SimulationPosition("minecraft:the_nether", 0, 64, 0)), matchingWorld)
        );
        assertSharedPredicate(
                inRegion("region", "", "", "大厅", "BRANCH"),
                context(actor(false, Set.of(), SimulationPosition.overworldSpawn()), matchingWorld),
                context(actor(false, Set.of(), new SimulationPosition("minecraft:overworld", 20, 64, 20)), matchingWorld)
        );
        assertSharedPredicate(
                targetBlock("target", "", "", "minecraft:stone", "BRANCH"),
                context(actor(false, Set.of(), SimulationPosition.overworldSpawn()), matchingWorld),
                context(actor(false, Set.of(), SimulationPosition.overworldSpawn()), falseTargetWorld)
        );

        NodeDefinition unsupported = new NodeDefinition(
                "unsupported",
                NodeType.PLAYER_Y_COMPARE_CONDITION,
                BuiltInBlockCatalog.CONDITION_PLAYER_Y_COMPARE,
                List.of(in("input"), out("pass"), out("fail")),
                Map.of("outputMode", "BRANCH", "compareMode", "EQUAL", "targetY", "64", "minY", "60", "maxY", "80")
        );
        require(SimulationExecutionRegistry.playerTags().evaluatePredicate(unsupported,
                context(actor(false, Set.of(), SimulationPosition.overworldSpawn()), matchingWorld), NOOP_SERVICES).isEmpty(),
                "predicate registry should stay limited to the five catalog-capable conditions");
    }

    private static void checkRuntimeMatrix() {
        // R1: initially true, body zero times, done once.
        RunHarness r1 = runGraph(runtimeGraph(
                "r1",
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(hasTag("condition", "until", SLOT_A, "ready", "BRANCH")),
                List.of(debug("body", "until", "body", "r1-body")),
                List.of(),
                List.of(debug("done", "", "", "r1-done"))
        ), actor(false, Set.of("ready"), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        require(r1.result.success() && r1.debugCount("r1-body") == 0 && r1.debugCount("r1-done") == 1,
                "R1 initially true should skip body and run done once");

        // R2: body mutation makes the predicate true on the next pre-check.
        RunHarness r2 = runGraph(runtimeGraph(
                "r2",
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(hasTag("condition", "until", SLOT_A, "ready", "BRANCH")),
                List.of(addTag("add-ready", "until", "body", "ready")),
                List.of(),
                List.of(debug("done", "", "", "r2-done"))
        ), actor(false, Set.of(), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        require(r2.result.success() && r2.actor().hasTag("ready") && r2.debugCount("r2-done") == 1,
                "R2 body should execute once, mutate context, and then exit");
        require(r2.trace().containsMessage("第 1 轮完成，重新检查结束条件"), "R2 should trace the pre-check return");

        // R3: two conditions use AND.
        GraphDefinition r3Graph = runtimeGraph(
                "r3",
                List.of(new ConditionSlotDefinition(SLOT_A, false), new ConditionSlotDefinition(SLOT_B, false)),
                List.of(
                        hasTag("tag", "until", SLOT_A, "ready", "BRANCH"),
                        isAdmin("admin", "until", SLOT_B, "BRANCH")
                ),
                List.of(), List.of(), List.of(debug("done", "", "", "r3-done"))
        );
        RunHarness r3True = runGraph(r3Graph, actor(true, Set.of("ready"), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        RunHarness r3False = runGraph(r3Graph, actor(false, Set.of("ready"), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        require(r3True.result.success() && r3True.debugCount("r3-done") == 1, "R3 should exit only when both conditions are true");
        require(!r3False.result.success() && r3False.debugCount("r3-done") == 0,
                "R3 one false condition must not pass AND");

        // R4: slot-owned NOT reverses the raw result and ignores ordinary output mode.
        RunHarness r4 = runGraph(runtimeGraph(
                "r4",
                List.of(new ConditionSlotDefinition(SLOT_A, true)),
                List.of(isAdmin("admin", "until", SLOT_A, "FAIL_ONLY")),
                List.of(), List.of(), List.of(debug("done", "", "", "r4-done"))
        ), actor(false, Set.of(), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        require(r4.result.success() && r4.debugCount("r4-done") == 1 && r4.trace().containsMessage("取反后 true"),
                "R4 non-admin with NOT should satisfy the slot");

        // R5: delay resumes the existing until/count frame before re-checking.
        RunHarness r5 = runGraph(runtimeGraph(
                "r5",
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(hasTag("condition", "until", SLOT_A, "ready", "BRANCH")),
                List.of(
                        timer("delay", "until", "body", 1),
                        addTag("add-ready", "until", "body", "ready")
                ),
                List.of(edge("body-edge", "delay", "timer_completed", "add-ready", "input")),
                List.of(debug("done", "", "", "r5-done"))
        ), actor(false, Set.of(), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        require(r5.result.suspended() && r5.services.timers.size() == 1, "R5 should suspend on the body delay");
        r5.drainTimers();
        require(r5.result.success() && !r5.result.suspended() && r5.actor().hasTag("ready") && r5.debugCount("r5-done") == 1,
                "R5 should resume body, re-check, and exit");

        // R6: permanently false condition stops at the finite simulation cap.
        RunHarness r6 = runGraph(runtimeGraph(
                "r6",
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(hasTag("condition", "until", SLOT_A, "never", "BRANCH")),
                List.of(debug("body", "until", "body", "r6-body")),
                List.of(),
                List.of(debug("done", "", "", "r6-done"))
        ), actor(false, Set.of(), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        require(r6.result.success() && r6.debugCount("r6-body") == 20 && r6.debugCount("r6-done") == 0,
                "R6 should execute exactly 20 unsuccessful rounds");
        require(r6.trace().containsMessage("已达到测试模拟循环上限"), "R6 should trace the cap stop");

        // R7/R8: incomplete racks are saveable warnings but fail closed at runtime.
        RunHarness r7 = runGraph(runtimeGraph(
                "r7",
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(), List.of(debug("body", "until", "body", "r7-body")), List.of(), List.of()
        ), actor(false, Set.of(), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        require(!r7.result.success() && r7.debugCount("r7-body") == 0 && r7.trace().containsMessage("尚未设置"),
                "R7 empty slot should fail closed before body");

        RunHarness r8 = runGraph(runtimeGraph(
                "r8", List.of(), List.of(), List.of(debug("body", "until", "body", "r8-body")), List.of(), List.of()
        ), actor(false, Set.of(), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        require(!r8.result.success() && r8.debugCount("r8-body") == 0 && r8.trace().containsMessage("缺少结束条件"),
                "R8 no slots should fail closed before body");

        // R9: empty body exits when true and fails without spinning when false.
        GraphDefinition r9Graph = runtimeGraph(
                "r9",
                List.of(new ConditionSlotDefinition(SLOT_A, false)),
                List.of(hasTag("condition", "until", SLOT_A, "ready", "BRANCH")),
                List.of(), List.of(), List.of(debug("done", "", "", "r9-done"))
        );
        RunHarness r9True = runGraph(r9Graph, actor(false, Set.of("ready"), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        RunHarness r9False = runGraph(r9Graph, actor(false, Set.of(), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        require(r9True.result.success() && r9True.debugCount("r9-done") == 1, "R9 true empty body should exit normally");
        require(!r9False.result.success() && r9False.trace().containsMessage("循环内容为空"),
                "R9 false empty body should fail without a busy loop");

        // R10a: count contains until and returns through both frames.
        GraphDefinition countContainsUntil = graph(
                "r10-count-until",
                List.of(
                        manualTrigger(),
                        count("count", "", "", 2),
                        until("until", "count", "body", List.of(new ConditionSlotDefinition(SLOT_A, false))),
                        isAdmin("admin", "until", SLOT_A, "BRANCH"),
                        debug("inner-done", "count", "body", "r10-inner"),
                        debug("outer-done", "", "", "r10-outer")
                ),
                List.of(
                        edge("e1", "trigger", "started", "count", "input"),
                        edge("e2", "until", "done", "inner-done", "input"),
                        edge("e3", "count", "done", "outer-done", "input")
                )
        );
        RunHarness r10a = runGraph(countContainsUntil, actor(true, Set.of(), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        require(r10a.result.success() && r10a.debugCount("r10-inner") == 2 && r10a.debugCount("r10-outer") == 1,
                "R10 count body should execute nested until and return twice");

        // R10b: until contains count and a nested delay; resume must preserve both frames.
        GraphDefinition untilContainsCount = graph(
                "r10-until-count",
                List.of(
                        manualTrigger(),
                        until("until", "", "", List.of(new ConditionSlotDefinition(SLOT_A, false))),
                        hasTag("condition", "until", SLOT_A, "ready", "BRANCH"),
                        count("count", "until", "body", 1),
                        timer("delay", "count", "body", 1),
                        addTag("add-ready", "count", "body", "ready"),
                        debug("done", "", "", "r10-done")
                ),
                List.of(
                        edge("e1", "trigger", "started", "until", "input"),
                        edge("e2", "delay", "timer_completed", "add-ready", "input"),
                        edge("e3", "until", "done", "done", "input")
                )
        );
        RunHarness r10b = runGraph(untilContainsCount, actor(false, Set.of(), SimulationPosition.overworldSpawn()), SimulationWorld.overworld());
        require(r10b.result.suspended(), "R10 nested count delay should suspend");
        r10b.drainTimers();
        require(r10b.result.success() && r10b.actor().hasTag("ready") && r10b.debugCount("r10-done") == 1,
                "R10 resume should unwind count then re-check and complete until");
    }

    private static void assertSharedPredicate(NodeDefinition condition, SimulationContext trueContext, SimulationContext falseContext) {
        HarnessServices trueServices = new HarnessServices(trueContext);
        HarnessServices falseServices = new HarnessServices(falseContext);
        RuntimeExecutionContext trueRuntime = runtimeContext(trueContext);
        RuntimeExecutionContext falseRuntime = runtimeContext(falseContext);
        RuntimePredicateResult rawTrue = trueServices.evaluatePredicate(condition, trueRuntime).orElseThrow();
        RuntimePredicateResult rawFalse = falseServices.evaluatePredicate(condition, falseRuntime).orElseThrow();
        RuntimeNodeExecutionResult ordinaryTrue = trueServices.executeSimulationNode(condition, trueRuntime).orElseThrow();
        RuntimeNodeExecutionResult ordinaryFalse = falseServices.executeSimulationNode(condition, falseRuntime).orElseThrow();
        require(rawTrue.value() && !rawFalse.value(), "predicate should expose both true and false facts: " + condition.blockId());
        require("pass".equals(ordinaryTrue.outputSlot()) && "fail".equals(ordinaryFalse.outputSlot()),
                "ordinary BRANCH execution must map the same raw predicate result: " + condition.blockId());
        require(!rawTrue.traceMessage().isBlank() && !rawFalse.traceMessage().isBlank(),
                "predicate results should retain readable trace text: " + condition.blockId());
    }

    private static RuntimeExecutionContext runtimeContext(SimulationContext context) {
        return new RuntimeExecutionContext(
                context.actor().id(),
                "loop-until-self-check",
                context.targetEntity().map(com.pixelmc.pixellogic.core.simulation.context.SimulationEntity::reference).orElse(null),
                context.actor().reference(),
                null
        );
    }

    private static RunHarness runGraph(GraphDefinition graph, SimulationActor actor, SimulationWorld world) {
        InMemoryStateStore state = new InMemoryStateStore();
        BoundedTraceBuffer traces = new BoundedTraceBuffer(4, 512);
        SimulationContext context = context(actor, world);
        HarnessServices services = new HarnessServices(context);
        GraphRuntime runtime = new GraphRuntime(
                new GraphCompiler().compile(graph),
                state,
                traces,
                services,
                new RuntimeLimits(1024, 8)
        );
        RuntimeResult result = runtime.start(new TriggerEvent("manual", "/pixellogic test start", actor.id(), "loop-until-self-check"));
        return new RunHarness(runtime, services, traces, result);
    }

    private static GraphDefinition runtimeGraph(
            String id,
            List<ConditionSlotDefinition> slots,
            List<NodeDefinition> conditions,
            List<NodeDefinition> body,
            List<EdgeDefinition> bodyEdges,
            List<NodeDefinition> after
    ) {
        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(manualTrigger());
        nodes.add(until("until", "", "", slots));
        nodes.addAll(conditions);
        nodes.addAll(body);
        nodes.addAll(after);

        List<EdgeDefinition> edges = new ArrayList<>();
        edges.add(edge("entry", "trigger", "started", "until", "input"));
        edges.addAll(bodyEdges);
        if (!after.isEmpty()) {
            edges.add(edge("done-edge", "until", "done", after.getFirst().id(), "input"));
        }
        return graph(id, nodes, edges);
    }

    private static GraphDefinition simpleUntilGraph(
            List<ConditionSlotDefinition> slots,
            List<NodeDefinition> conditions,
            List<NodeDefinition> body,
            List<EdgeDefinition> extraEdges
    ) {
        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(manualTrigger());
        nodes.add(until("until", "", "", slots));
        nodes.addAll(conditions);
        nodes.addAll(body);
        nodes.add(debug("done", "", "", "done"));
        List<EdgeDefinition> edges = new ArrayList<>();
        edges.add(edge("entry", "trigger", "started", "until", "input"));
        edges.add(edge("done", "until", "done", "done", "input"));
        edges.addAll(extraEdges);
        return graph("loop-until-validation", nodes, edges);
    }

    private static GraphDefinition storageGraph(String id) {
        return graph(
                id,
                List.of(
                        manualTrigger(),
                        until("until", "", "", List.of(
                                new ConditionSlotDefinition(SLOT_A, false),
                                new ConditionSlotDefinition(SLOT_B, true)
                        )),
                        hasTag("tag-condition", "until", SLOT_A, "ready", "BRANCH"),
                        isAdmin("admin-condition", "until", SLOT_B, "BRANCH"),
                        debug("body", "until", "body", "body"),
                        debug("done", "", "", "done")
                ),
                List.of(
                        edge("entry", "trigger", "started", "until", "input"),
                        edge("done-edge", "until", "done", "done", "input")
                )
        );
    }

    private static GraphDefinition replaceConditionSlots(
            GraphDefinition graph,
            String nodeId,
            List<ConditionSlotDefinition> slots
    ) {
        return new GraphDefinition(
                graph.id(),
                graph.nodes().stream().map(item -> item.id().equals(nodeId)
                        ? new NodeDefinition(
                        item.id(), item.type(), item.blockId(), item.parentContainerId(), item.parentSlot(),
                        slots, item.slots(), item.config())
                        : item).toList(),
                graph.edges(),
                graph.triggerEntries()
        );
    }

    private static GraphDefinition graph(String id, List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        return new GraphDefinition(id, List.copyOf(nodes), List.copyOf(edges), Map.of("manual", "trigger"));
    }

    private static NodeDefinition manualTrigger() {
        return new NodeDefinition(
                "trigger",
                NodeType.MANUAL_TRIGGER,
                BuiltInBlockCatalog.TRIGGER_MANUAL_TEST,
                List.of(out("started")),
                Map.of()
        );
    }

    private static NodeDefinition until(
            String id,
            String parentId,
            String parentSlot,
            List<ConditionSlotDefinition> conditionSlots
    ) {
        return new NodeDefinition(
                id,
                NodeType.CONTROL_LOOP_UNTIL,
                BuiltInBlockCatalog.CONTROL_LOOP_UNTIL,
                parentId,
                parentSlot,
                conditionSlots,
                List.of(in("input"), out("done")),
                Map.of()
        );
    }

    private static NodeDefinition count(String id, String parentId, String parentSlot, int count) {
        return new NodeDefinition(
                id,
                NodeType.CONTROL_LOOP_COUNT,
                BuiltInBlockCatalog.CONTROL_LOOP_COUNT,
                parentId,
                parentSlot,
                List.of(in("input"), out("done")),
                Map.of("count", Integer.toString(count))
        );
    }

    private static NodeDefinition hasTag(String id, String parentId, String parentSlot, String tag, String outputMode) {
        return condition(id, NodeType.ENTITY_HAS_TAG_CONDITION, BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG,
                parentId, parentSlot, Map.of("target", EntityTargetRef.currentEntity().toJson(), "tag", tag, "outputMode", outputMode));
    }

    private static NodeDefinition isAdmin(String id, String parentId, String parentSlot, String outputMode) {
        return condition(id, NodeType.PLAYER_IS_ADMIN_CONDITION, BuiltInBlockCatalog.CONDITION_PLAYER_IS_ADMIN,
                parentId, parentSlot, Map.of("outputMode", outputMode));
    }

    private static NodeDefinition dimension(
            String id,
            String parentId,
            String parentSlot,
            String dimensionId,
            String outputMode
    ) {
        return condition(id, NodeType.PLAYER_DIMENSION_CONDITION, BuiltInBlockCatalog.CONDITION_PLAYER_DIMENSION_IS,
                parentId, parentSlot, Map.of("dimensionId", dimensionId, "outputMode", outputMode));
    }

    private static NodeDefinition inRegion(
            String id,
            String parentId,
            String parentSlot,
            String regionName,
            String outputMode
    ) {
        return condition(id, NodeType.PLAYER_IN_REGION_CONDITION, BuiltInBlockCatalog.CONDITION_PLAYER_IN_REGION,
                parentId, parentSlot, Map.of("regionName", regionName, "outputMode", outputMode));
    }

    private static NodeDefinition targetBlock(
            String id,
            String parentId,
            String parentSlot,
            String blockId,
            String outputMode
    ) {
        return condition(id, NodeType.TARGET_BLOCK_TYPE_CONDITION, BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IS_TYPE,
                parentId, parentSlot, Map.of("blockId", blockId, "outputMode", outputMode));
    }

    private static NodeDefinition condition(
            String id,
            NodeType type,
            String blockId,
            String parentId,
            String parentSlot,
            Map<String, String> config
    ) {
        return new NodeDefinition(
                id,
                type,
                blockId,
                parentId,
                parentSlot,
                List.of(in("input"), out("pass"), out("fail")),
                config
        );
    }

    private static NodeDefinition addTag(String id, String parentId, String parentSlot, String tag) {
        return new NodeDefinition(
                id,
                NodeType.ENTITY_ADD_TAG_ACTION,
                BuiltInBlockCatalog.ACTION_ENTITY_ADD_TAG,
                parentId,
                parentSlot,
                List.of(in("input"), out("done")),
                Map.of("target", EntityTargetRef.currentEntity().toJson(), "tag", tag)
        );
    }

    private static NodeDefinition timer(String id, String parentId, String parentSlot, int seconds) {
        return new NodeDefinition(
                id,
                NodeType.TIMER_START_ACTION,
                BuiltInBlockCatalog.TIMER_WAIT,
                parentId,
                parentSlot,
                List.of(in("input"), out("timer_completed")),
                Map.of("durationSeconds", Integer.toString(seconds))
        );
    }

    private static NodeDefinition debug(String id, String parentId, String parentSlot, String message) {
        return new NodeDefinition(
                id,
                NodeType.DEBUG_LOG_ACTION,
                BuiltInBlockCatalog.DEBUG_LOG,
                parentId,
                parentSlot,
                List.of(in("input"), out("done")),
                Map.of("message", message)
        );
    }

    private static SlotDefinition in(String id) {
        return new SlotDefinition(id, SlotDirection.INPUT, EdgeType.CONTROL);
    }

    private static SlotDefinition out(String id) {
        return new SlotDefinition(id, SlotDirection.OUTPUT, EdgeType.CONTROL);
    }

    private static EdgeDefinition edge(
            String id,
            String sourceNodeId,
            String sourceSlotId,
            String targetNodeId,
            String targetSlotId
    ) {
        return new EdgeDefinition(id, sourceNodeId, sourceSlotId, targetNodeId, targetSlotId, EdgeType.CONTROL);
    }

    private static SimulationActor actor(
            boolean operator,
            Set<String> tags,
            SimulationPosition position
    ) {
        return new SimulationActor(PLAYER_ID, "Loop Until Player", true, operator, tags, position);
    }

    private static SimulationContext context(SimulationActor actor, SimulationWorld world) {
        return new SimulationContext(
                UUID.randomUUID().toString(),
                0,
                actor,
                world
        );
    }

    private static BlockDefinition block(String blockId) {
        return BuiltInBlockCatalog.block(blockId).orElseThrow();
    }

    private static NodeDefinition node(GraphDefinition graph, String id) {
        return graph.nodes().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    private static boolean hasError(GraphDefinition graph, String code) {
        return hasIssue(graph, ValidationIssue.Severity.ERROR, code);
    }

    private static boolean hasIssue(GraphDefinition graph, ValidationIssue.Severity severity, String code) {
        return new GraphValidator().validate(graph).stream()
                .anyMatch(issue -> issue.severity() == severity && issue.code().equals(code));
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class HarnessServices implements RuntimeServices {
        private final SimulationExecutionRegistry registry = SimulationExecutionRegistry.playerTags();
        private final SimulationContext context;
        private final ArrayDeque<TimerContinuation> timers = new ArrayDeque<>();
        private final List<String> debug = new ArrayList<>();

        private HarnessServices(SimulationContext context) {
            this.context = context;
        }

        @Override
        public java.util.Optional<RuntimeNodeExecutionResult> executeSimulationNode(
                NodeDefinition node,
                RuntimeExecutionContext runtimeContext
        ) {
            return registry.execute(node, context, runtimeContext, this)
                    .or(() -> RuntimeServices.super.executeSimulationNode(node, runtimeContext));
        }

        @Override
        public java.util.Optional<RuntimePredicateResult> evaluatePredicate(
                NodeDefinition node,
                RuntimeExecutionContext runtimeContext
        ) {
            return registry.evaluatePredicate(node, context, runtimeContext, this)
                    .or(() -> RuntimeServices.super.evaluatePredicate(node, runtimeContext));
        }

        @Override
        public java.util.Optional<RuntimeSubjectReference> initialCurrentEntity(UUID playerId, String sessionId) {
            return java.util.Optional.of(context.actor().reference());
        }

        @Override
        public RuntimeEntityProvider entityProvider() {
            return context;
        }

        @Override
        public void sendPlayerMessage(UUID playerId, String message) {
        }

        @Override
        public void debug(String message) {
            debug.add(message);
        }

        @Override
        public void scheduleTimer(Duration delay, TimerContinuation continuation) {
            timers.addLast(continuation);
        }
    }

    private static final class RunHarness {
        private final GraphRuntime runtime;
        private final HarnessServices services;
        private final BoundedTraceBuffer traces;
        private RuntimeResult result;

        private RunHarness(
                GraphRuntime runtime,
                HarnessServices services,
                BoundedTraceBuffer traces,
                RuntimeResult result
        ) {
            this.runtime = runtime;
            this.services = services;
            this.traces = traces;
            this.result = result;
        }

        private void drainTimers() {
            for (int resumes = 0; resumes < 64 && !services.timers.isEmpty(); resumes += 1) {
                result = runtime.resumeTimer(services.timers.removeFirst());
            }
            require(services.timers.isEmpty(), "continuation queue should drain within the bounded test guard");
        }

        private SimulationActor actor() {
            return services.context.actor();
        }

        private int debugCount(String message) {
            return (int) services.debug.stream().filter(message::equals).count();
        }

        private ExecutionTrace trace() {
            return traces.get(result.traceId()).orElseThrow();
        }
    }
}
