package com.pixelmc.pixellogic.core.graph;

import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class DemoGraphFactory {
    public static final String TRIGGER_TYPE = "manual.test.start";

    private DemoGraphFactory() {
    }

    public static GraphDefinition create(Duration timerDuration) {
        int seconds = Math.toIntExact(timerDuration.toSeconds());
        return new GraphDefinition(
                "demo-start-flow",
                List.of(
                        node("manual-trigger", NodeType.MANUAL_TRIGGER, out("started"), Map.of()),
                        node("condition-started", NodeType.STATE_COMPARE_CONDITION, in("input"), out("pass"), out("fail"), Map.of(
                                "outputMode", ConditionOutputMode.BRANCH.name(),
                                "scope", "PLAYER",
                                "key", "started",
                                "valueType", "BOOLEAN",
                                "expected", "false",
                                "missing", "false"
                        )),
                        node("welcome-message", NodeType.MESSAGE_ACTION, in("input"), out("done"), Map.of(
                                "message", "欢迎开始游戏"
                        )),
                        node("set-started", NodeType.STATE_SET_ACTION, in("input"), out("done"), Map.of(
                                "scope", "PLAYER",
                                "key", "started",
                                "valueType", "BOOLEAN",
                                "value", "true"
                        )),
                        node("add-start-count", NodeType.STATE_ADD_ACTION, in("input"), out("done"), Map.of(
                                "scope", "PLAYER",
                                "key", "start_count",
                                "valueType", "INTEGER",
                                "amount", "1"
                        )),
                        node("timer-start", NodeType.TIMER_START_ACTION, in("input"), out("timer_completed"), Map.of(
                                "durationSeconds", String.valueOf(seconds)
                        )),
                        node("debug-finished", NodeType.DEBUG_LOG_ACTION, in("input"), out("done"), Map.of(
                                "message", "倒计时结束"
                        )),
                        node("debug-already-started", NodeType.DEBUG_LOG_ACTION, in("input"), out("done"), Map.of(
                                "message", "玩家已经开始过游戏"
                        ))
                ),
                List.of(
                        edge("e1", "manual-trigger", "started", "condition-started", "input"),
                        edge("e2", "condition-started", "pass", "welcome-message", "input"),
                        edge("e3", "welcome-message", "done", "set-started", "input"),
                        edge("e4", "set-started", "done", "add-start-count", "input"),
                        edge("e5", "add-start-count", "done", "timer-start", "input"),
                        edge("e6", "timer-start", "timer_completed", "debug-finished", "input"),
                        edge("e7", "condition-started", "fail", "debug-already-started", "input")
                ),
                Map.of(TRIGGER_TYPE, "manual-trigger")
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
