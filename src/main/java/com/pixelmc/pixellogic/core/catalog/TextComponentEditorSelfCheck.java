package com.pixelmc.pixellogic.core.catalog;

import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
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
import com.pixelmc.pixellogic.core.simulation.event.SimulationEvent;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationExecutionRegistry;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionRequest;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionResult;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationRunOptions;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationRunner;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;
import com.pixelmc.pixellogic.core.trace.ExecutionTrace;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class TextComponentEditorSelfCheck {
    private static final String TRIGGER_TYPE = "manual.test.start";
    private static final String FORMATTED = "{\"version\":1,\"plainText\":\"欢迎 PixelMC\\n开始\",\"segments\":["
            + "{\"text\":\"欢迎 \",\"style\":{}},"
            + "{\"text\":\"PixelMC\",\"style\":{\"color\":\"blue\",\"bold\":true,\"italic\":true,\"underlined\":true,\"strikethrough\":true,\"obfuscated\":true}},"
            + "{\"text\":\"\\n开始\",\"style\":{\"color\":\"green\"}}"
            + "]}";

    private TextComponentEditorSelfCheck() {
    }

    public static void main(String[] args) {
        run("textComponentEditorSelfCheck", () -> {
            checkCompatibilityAndValidation();
            checkMessageBlocks();
            checkSimulationResultAndTrace();
        });
    }

    private static void checkCompatibilityAndValidation() {
        require(RichTextComponentValue.plainText("旧纯文本").equals("旧纯文本"), "old plain text should still load");
        require(RichTextComponentValue.normalize("旧纯文本").contains("\"segments\""), "old plain text should normalize to segments");
        require(RichTextComponentValue.validationErrors("旧纯文本").isEmpty(), "old plain text should validate");
        require(RichTextComponentValue.validationErrors(FORMATTED).isEmpty(), "formatted component should validate");
        require(RichTextComponentValue.plainText(FORMATTED).equals("欢迎 PixelMC\n开始"), "formatted plain text should preserve newline");
        require(!RichTextComponentValue.validationErrors(FORMATTED.replace("\"blue\"", "\"pink\"")).isEmpty(),
                "invalid color should be rejected");
        require(!RichTextComponentValue.validationErrors(FORMATTED.replace("\"bold\":true", "\"bold\":\"true\"")).isEmpty(),
                "style flags should be booleans");
        require(!RichTextComponentValue.validationErrors("{\"version\":1,\"plainText\":\"\",\"segments\":[{\"text\":\"\",\"style\":{}}]}").isEmpty(),
                "empty segments should not be accepted as meaningful saved content");
    }

    private static void checkMessageBlocks() {
        List.of(
                BuiltInBlockCatalog.ACTION_MESSAGE_CHAT,
                BuiltInBlockCatalog.ACTION_MESSAGE_TITLE,
                BuiltInBlockCatalog.ACTION_MESSAGE_SUBTITLE,
                BuiltInBlockCatalog.ACTION_MESSAGE_ACTIONBAR
        ).forEach(blockId -> {
            BlockDefinition block = BuiltInBlockCatalog.block(blockId).orElseThrow();
            require(block.formSchema().stream().anyMatch(field -> field.key().equals("message") && field.type().equals("rich_text_component")),
                    blockId + " should use the shared rich text editor field");
        });
        require(valid(messageGraph()), "all message blocks should validate with formatted rich text");
    }

    private static void checkSimulationResultAndTrace() {
        BoundedTraceBuffer traces = new BoundedTraceBuffer(4, 80);
        SimulationExecutionResult result = runGraph(messageGraph(), traces);
        require(result.success(), "formatted message graph should run");
        require(result.messageResults().size() == 4, "chat/title/subtitle/actionbar should all record message results");
        require(result.messageResults().stream().allMatch(message -> message.message().equals("欢迎 PixelMC\n开始")),
                "message result plain text should be readable");
        require(result.messageResults().stream().allMatch(message -> message.component().contains("\"segments\"") && message.component().contains("\"color\":\"blue\"")),
                "message result should retain structured formatting payload");
        ExecutionTrace trace = traces.latest().orElseThrow();
        require(trace.steps().stream().noneMatch(step -> step.message().contains("\"segments\"") || step.message().contains("{")),
                "trace should not expose raw structured payload");
        require(trace.containsMessage("PixelMC"), "trace should keep plain text content");
    }

    private static boolean valid(GraphDefinition graph) {
        GraphValidator validator = new GraphValidator();
        List<ValidationIssue> issues = validator.validate(graph);
        return !validator.hasErrors(issues);
    }

    private static SimulationExecutionResult runGraph(GraphDefinition graph, BoundedTraceBuffer traces) {
        CompiledGraph compiled = new GraphCompiler().compile(graph);
        RuntimeServices delegate = new RuntimeServices() {
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
        SimulationRunner runner = new SimulationRunner(
                services -> new GraphRuntime(compiled, new InMemoryStateStore(), traces, services, RuntimeLimits.spikeDefaults()),
                delegate,
                SimulationExecutionRegistry.playerTags()
        );
        return runner.run(new SimulationExecutionRequest(
                graph.id(),
                SimulationEvent.manual(TRIGGER_TYPE, "/pixellogic test start", "text-component-editor"),
                new SimulationActor(UUID.randomUUID(), "文本玩家", true, false, Set.of()),
                SimulationWorld.overworld(),
                SimulationRunOptions.realTime(),
                0L
        ));
    }

    private static GraphDefinition messageGraph() {
        return new GraphDefinition(
                "text-component-editor",
                List.of(
                        node("manual-trigger", NodeType.MANUAL_TRIGGER, BuiltInBlockCatalog.TRIGGER_MANUAL_TEST, out("started"), Map.of()),
                        messageNode("chat", BuiltInBlockCatalog.ACTION_MESSAGE_CHAT),
                        messageNode("title", BuiltInBlockCatalog.ACTION_MESSAGE_TITLE),
                        messageNode("subtitle", BuiltInBlockCatalog.ACTION_MESSAGE_SUBTITLE),
                        messageNode("actionbar", BuiltInBlockCatalog.ACTION_MESSAGE_ACTIONBAR)
                ),
                List.of(
                        edge("e1", "manual-trigger", "started", "chat", "input"),
                        edge("e2", "chat", "done", "title", "input"),
                        edge("e3", "title", "done", "subtitle", "input"),
                        edge("e4", "subtitle", "done", "actionbar", "input")
                ),
                Map.of(TRIGGER_TYPE, "manual-trigger")
        );
    }

    private static NodeDefinition messageNode(String id, String blockId) {
        return node(id, NodeType.MESSAGE_ACTION, blockId, in("input"), out("done"), Map.of(
                "target", "CURRENT_PLAYER",
                "message", FORMATTED
        ));
    }

    private static NodeDefinition node(String id, NodeType type, String blockId, SlotDefinition slot, Map<String, String> config) {
        return new NodeDefinition(id, type, blockId, List.of(slot), config);
    }

    private static NodeDefinition node(String id, NodeType type, String blockId, SlotDefinition input, SlotDefinition output, Map<String, String> config) {
        return new NodeDefinition(id, type, blockId, List.of(input, output), config);
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
