package com.pixelmc.pixellogic.core.catalog;

import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.EntityTargetRef;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;
import com.pixelmc.pixellogic.core.runtime.GraphRuntime;
import com.pixelmc.pixellogic.core.runtime.RuntimeLimits;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.core.simulation.context.SimulationWorld;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationExecutionRegistry;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionRequest;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionResult;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationRunner;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class CatalogExpansionV1SelfCheck {
    private static final String TRIGGER_TYPE = "manual.test.start";

    private CatalogExpansionV1SelfCheck() {
    }

    public static void main(String[] args) {
        run("catalogExpansionV1SelfCheck", () -> {
        checkCatalog();
        checkPlayerTagConditionModes();
        checkAdminConditionModes();
        checkRemoveTagAction();
        checkMessageResults();
        });
    }

    private static void checkCatalog() {
        BlockCatalog catalog = BuiltInBlockCatalog.catalog();
        BlockDefinition hasTag = block(BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG);
        require(hasTag.displayName().equals("实体是否拥有标签"), "has_tag should use the generic user-facing title");
        require(modeLabels(hasTag).equals(List.of("拥有标签时继续", "不拥有标签时继续", "分开执行")),
                "has_tag should expose player-specific condition mode labels");
        require(hasTag.formSchema().stream().map(BlockFormFieldDefinition::key).toList().equals(List.of("outputMode", "target", "tag")),
                "has_tag should expose one shared target and no extra positive/negative field");

        BlockDefinition isAdmin = block(BuiltInBlockCatalog.CONDITION_PLAYER_IS_ADMIN);
        require(isAdmin.categoryId().equals("player-entity.identity-permissions"),
                "is_admin should live under player/entity identity category");
        require(modeLabels(isAdmin).equals(List.of("是管理员时继续", "不是管理员时继续", "分开执行")),
                "is_admin should expose admin-specific condition mode labels");
        require(isAdmin.formSchema().stream().map(BlockFormFieldDefinition::key).toList().equals(List.of("outputMode")),
                "is_admin should not add an extra boolean field");

        String negativeTagName = "玩家" + "没有标签";
        String negativeAdminName = "玩家" + "不是管理员";
        require(catalog.blocks().stream().noneMatch(block -> block.displayName().equals(negativeTagName)),
                "catalog should not add a negative player tag block");
        require(catalog.blocks().stream().noneMatch(block -> block.displayName().equals(negativeAdminName)),
                "catalog should not add a negative admin block");
        require(block(BuiltInBlockCatalog.ACTION_ENTITY_ADD_TAG).categoryId().equals("player-entity.tags"),
                "add_tag should remain under player/entity tags");
        require(block(BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_TAG).categoryId().equals("player-entity.tags"),
                "remove_tag should live under player/entity tags");

        List.of(
                BuiltInBlockCatalog.ACTION_MESSAGE_TITLE,
                BuiltInBlockCatalog.ACTION_MESSAGE_SUBTITLE,
                BuiltInBlockCatalog.ACTION_MESSAGE_ACTIONBAR
        ).forEach(blockId -> {
            BlockDefinition message = block(blockId);
            require(message.categoryId().equals("presentation-feedback.screen-prompts"),
                    blockId + " should live under presentation/feedback screen prompts");
            require(message.formSchema().stream().anyMatch(field -> field.key().equals("message") && field.type().equals("rich_text_component")),
                    blockId + " should use rich_text_component");
        });
    }

    private static void checkPlayerTagConditionModes() {
        require(valid(tagConditionGraph(ConditionOutputMode.PASS_ONLY.name(), true)), "has_tag PASS_ONLY graph should validate");
        SimulationExecutionResult pass = runGraph(
                tagConditionGraph(ConditionOutputMode.PASS_ONLY.name(), true),
                new SimulationActor(UUID.randomUUID(), "有标签玩家", true, false, Set.of("runner"))
        );
        require(pass.success(), "has_tag PASS_ONLY should pass for actor with tag");

        SimulationExecutionResult failOnly = runGraph(
                tagConditionGraph(ConditionOutputMode.FAIL_ONLY.name(), false),
                new SimulationActor(UUID.randomUUID(), "无标签玩家", true, false, Set.of())
        );
        require(failOnly.success(), "has_tag FAIL_ONLY should continue for actor without tag");

        SimulationExecutionResult branch = runGraph(
                tagConditionGraph(ConditionOutputMode.BRANCH.name(), false),
                new SimulationActor(UUID.randomUUID(), "分支玩家", true, false, Set.of())
        );
        require(branch.success(), "has_tag BRANCH should run the missing-tag branch");
    }

    private static void checkAdminConditionModes() {
        require(valid(adminConditionGraph(ConditionOutputMode.PASS_ONLY.name(), true)), "is_admin graph should validate with no extra field");
        SimulationExecutionResult adminPass = runGraph(
                adminConditionGraph(ConditionOutputMode.PASS_ONLY.name(), true),
                new SimulationActor(UUID.randomUUID(), "管理员玩家", true, true, Set.of())
        );
        require(adminPass.success(), "is_admin PASS_ONLY should continue for admin actor");

        SimulationExecutionResult notAdminPass = runGraph(
                adminConditionGraph(ConditionOutputMode.FAIL_ONLY.name(), false),
                new SimulationActor(UUID.randomUUID(), "普通玩家", true, false, Set.of())
        );
        require(notAdminPass.success(), "is_admin FAIL_ONLY should continue for non-admin actor");
    }

    private static void checkRemoveTagAction() {
        GraphDefinition graph = removeTagGraph();
        require(valid(graph), "remove_tag graph should validate");

        SimulationExecutionResult removed = runGraph(graph, new SimulationActor(UUID.randomUUID(), "带标签玩家", true, false, Set.of("runner", "ready")));
        require(!removed.actorTags().contains("runner") && removed.actorTags().contains("ready"),
                "remove_tag should remove only the configured tag in this run result");
        require(removed.stateChanges().stream().anyMatch(change -> change.target().equals("actor.tags")),
                "remove_tag should record actor tag state change");

        SimulationExecutionResult missing = runGraph(graph, new SimulationActor(UUID.randomUUID(), "无标签玩家", true, false, Set.of()));
        require(missing.success(), "remove_tag should not fail when the tag is absent");

        SimulationExecutionResult nextRun = runGraph(graph, new SimulationActor(UUID.randomUUID(), "新运行玩家", true, false, Set.of("runner")));
        require(nextRun.initialActorTags().contains("runner") && !nextRun.actorTags().contains("runner"),
                "remove_tag should not persist outside the supplied run actor");
    }

    private static void checkMessageResults() {
        GraphDefinition graph = messageGraph();
        require(valid(graph), "message title/subtitle/actionbar graph should validate");
        SimulationExecutionResult result = runGraph(graph, new SimulationActor(UUID.randomUUID(), "消息玩家", true, false, Set.of()));
        Set<String> channels = result.messageResults().stream()
                .map(message -> message.channel())
                .collect(java.util.stream.Collectors.toSet());
        require(channels.containsAll(Set.of("TITLE", "SUBTITLE", "ACTIONBAR")),
                "message result should distinguish TITLE/SUBTITLE/ACTIONBAR");
    }

    private static boolean valid(GraphDefinition graph) {
        GraphValidator validator = new GraphValidator();
        List<ValidationIssue> issues = validator.validate(graph);
        return !validator.hasErrors(issues);
    }

    private static SimulationExecutionResult runGraph(GraphDefinition graph, SimulationActor actor) {
        CompiledGraph compiled = new GraphCompiler().compile(graph);
        BoundedTraceBuffer traces = new BoundedTraceBuffer(4, 80);
        List<String> messages = new ArrayList<>();
        RuntimeServices delegate = new RuntimeServices() {
            @Override
            public void sendPlayerMessage(UUID playerId, String message) {
                messages.add(message);
            }

            @Override
            public void debug(String message) {
            }

            @Override
            public void scheduleTimer(Duration delay, TimerContinuation continuation) {
            }
        };
        SimulationRunner runner = new SimulationRunner(
                services -> new GraphRuntime(compiled, new InMemoryStateStore(), traces, services, RuntimeLimits.spikeDefaults()),
                delegate,
                SimulationExecutionRegistry.playerTags()
        );
        return runner.run(new SimulationExecutionRequest(
                graph.id(),
                TRIGGER_TYPE,
                "/pixellogic test start",
                actor,
                SimulationWorld.overworld(),
                "catalog-expansion",
                0L
        ));
    }

    private static GraphDefinition tagConditionGraph(String outputMode, boolean connectPass) {
        return new GraphDefinition(
                "catalog-expansion-has-tag",
                List.of(
                        node("manual-trigger", NodeType.MANUAL_TRIGGER, BuiltInBlockCatalog.TRIGGER_MANUAL_TEST, out("started"), Map.of()),
                        node("has-tag", NodeType.ENTITY_HAS_TAG_CONDITION, BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG, in("input"), out("pass"), out("fail"), Map.of("outputMode", outputMode, "target", EntityTargetRef.currentEntity().toJson(), "tag", "runner")),
                        node("debug-pass", NodeType.DEBUG_LOG_ACTION, BuiltInBlockCatalog.DEBUG_LOG, in("input"), out("done"), Map.of("message", "拥有标签")),
                        node("debug-fail", NodeType.DEBUG_LOG_ACTION, BuiltInBlockCatalog.DEBUG_LOG, in("input"), out("done"), Map.of("message", "不拥有标签"))
                ),
                List.of(
                        edge("e1", "manual-trigger", "started", "has-tag", "input"),
                        edge("e2", "has-tag", connectPass ? "pass" : "fail", connectPass ? "debug-pass" : "debug-fail", "input")
                ),
                Map.of(TRIGGER_TYPE, "manual-trigger")
        );
    }

    private static GraphDefinition adminConditionGraph(String outputMode, boolean connectPass) {
        return new GraphDefinition(
                "catalog-expansion-admin",
                List.of(
                        node("manual-trigger", NodeType.MANUAL_TRIGGER, BuiltInBlockCatalog.TRIGGER_MANUAL_TEST, out("started"), Map.of()),
                        node("is-admin", NodeType.PLAYER_IS_ADMIN_CONDITION, BuiltInBlockCatalog.CONDITION_PLAYER_IS_ADMIN, in("input"), out("pass"), out("fail"), Map.of("outputMode", outputMode)),
                        node("debug-pass", NodeType.DEBUG_LOG_ACTION, BuiltInBlockCatalog.DEBUG_LOG, in("input"), out("done"), Map.of("message", "是管理员")),
                        node("debug-fail", NodeType.DEBUG_LOG_ACTION, BuiltInBlockCatalog.DEBUG_LOG, in("input"), out("done"), Map.of("message", "不是管理员"))
                ),
                List.of(
                        edge("e1", "manual-trigger", "started", "is-admin", "input"),
                        edge("e2", "is-admin", connectPass ? "pass" : "fail", connectPass ? "debug-pass" : "debug-fail", "input")
                ),
                Map.of(TRIGGER_TYPE, "manual-trigger")
        );
    }

    private static GraphDefinition removeTagGraph() {
        return new GraphDefinition(
                "catalog-expansion-remove-tag",
                List.of(
                        node("manual-trigger", NodeType.MANUAL_TRIGGER, BuiltInBlockCatalog.TRIGGER_MANUAL_TEST, out("started"), Map.of()),
                        node("remove-tag", NodeType.ENTITY_REMOVE_TAG_ACTION, BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_TAG, in("input"), out("done"), Map.of("target", EntityTargetRef.currentEntity().toJson(), "tag", "runner"))
                ),
                List.of(edge("e1", "manual-trigger", "started", "remove-tag", "input")),
                Map.of(TRIGGER_TYPE, "manual-trigger")
        );
    }

    private static GraphDefinition messageGraph() {
        return new GraphDefinition(
                "catalog-expansion-messages",
                List.of(
                        node("manual-trigger", NodeType.MANUAL_TRIGGER, BuiltInBlockCatalog.TRIGGER_MANUAL_TEST, out("started"), Map.of()),
                        messageNode("title", BuiltInBlockCatalog.ACTION_MESSAGE_TITLE, "标题内容"),
                        messageNode("subtitle", BuiltInBlockCatalog.ACTION_MESSAGE_SUBTITLE, "副标题内容"),
                        messageNode("actionbar", BuiltInBlockCatalog.ACTION_MESSAGE_ACTIONBAR, "快捷栏内容")
                ),
                List.of(
                        edge("e1", "manual-trigger", "started", "title", "input"),
                        edge("e2", "title", "done", "subtitle", "input"),
                        edge("e3", "subtitle", "done", "actionbar", "input")
                ),
                Map.of(TRIGGER_TYPE, "manual-trigger")
        );
    }

    private static NodeDefinition messageNode(String id, String blockId, String message) {
        return node(id, NodeType.MESSAGE_ACTION, blockId, in("input"), out("done"), Map.of(
                "target", "CURRENT_PLAYER",
                "message", RichTextComponentValue.fromPlainText(message)
        ));
    }

    private static NodeDefinition node(String id, NodeType type, String blockId, SlotDefinition slot, Map<String, String> config) {
        return new NodeDefinition(id, type, blockId, List.of(slot), config);
    }

    private static NodeDefinition node(String id, NodeType type, String blockId, SlotDefinition input, SlotDefinition output, Map<String, String> config) {
        return new NodeDefinition(id, type, blockId, List.of(input, output), config);
    }

    private static NodeDefinition node(
            String id,
            NodeType type,
            String blockId,
            SlotDefinition input,
            SlotDefinition outputA,
            SlotDefinition outputB,
            Map<String, String> config
    ) {
        return new NodeDefinition(id, type, blockId, List.of(input, outputA, outputB), config);
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

    private static BlockDefinition block(String blockId) {
        return BuiltInBlockCatalog.block(blockId).orElseThrow();
    }

    private static List<String> modeLabels(BlockDefinition block) {
        return block.formSchema().stream()
                .filter(field -> field.key().equals(ConditionOutputMode.CONFIG_KEY))
                .findFirst()
                .orElseThrow()
                .options()
                .stream()
                .map(BlockFormFieldDefinition.FieldOption::label)
                .toList();
    }

}
