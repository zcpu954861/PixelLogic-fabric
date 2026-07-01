package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.catalog.RichTextComponentValue;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.StateScope;
import com.pixelmc.pixellogic.core.model.StateValueType;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.state.StateKey;
import com.pixelmc.pixellogic.core.state.StateValue;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public final class GraphRuntime {
    private final CompiledGraph graph;
    private final InMemoryStateStore stateStore;
    private final BoundedTraceBuffer traces;
    private final RuntimeServices services;
    private final RuntimeLimits limits;
    private final long generation;

    public GraphRuntime(
            CompiledGraph graph,
            InMemoryStateStore stateStore,
            BoundedTraceBuffer traces,
            RuntimeServices services,
            RuntimeLimits limits
    ) {
        this(graph, stateStore, traces, services, limits, 0L);
    }

    public GraphRuntime(
            CompiledGraph graph,
            InMemoryStateStore stateStore,
            BoundedTraceBuffer traces,
            RuntimeServices services,
            RuntimeLimits limits,
            long generation
    ) {
        this.graph = graph;
        this.stateStore = stateStore;
        this.traces = traces;
        this.services = services;
        this.limits = limits;
        this.generation = generation;
    }

    public RuntimeResult start(TriggerEvent event) {
        String traceId = UUID.randomUUID().toString();
        traces.startTrace(traceId);
        traces.add(traceId, "trigger", "手动触发：" + event.commandText());

        Optional<NodeDefinition> entry = graph.entryForTrigger(event.triggerType());
        if (entry.isEmpty()) {
            traces.add(traceId, "trigger", "执行失败：找不到触发入口。");
            return new RuntimeResult(false, traceId, "找不到触发入口。");
        }

        ExecutionContext context = new ExecutionContext(traceId, event.playerId(), event.sessionId(), 0);
        return runFrom(entry.get(), context);
    }

    public RuntimeResult resumeTimer(TimerContinuation continuation) {
        if (continuation.generation() != generation) {
            return new RuntimeResult(false, continuation.traceId(), "计时器已失效。");
        }
        if (continuation.depth() > limits.maxContinuationDepth()) {
            traces.add(continuation.traceId(), continuation.targetNodeId(), "执行失败：计时器 continuation 深度超限。");
            return new RuntimeResult(false, continuation.traceId(), "计时器 continuation 深度超限。");
        }

        Optional<NodeDefinition> target = graph.node(continuation.targetNodeId());
        if (target.isEmpty()) {
            traces.add(continuation.traceId(), continuation.targetNodeId(), "执行失败：计时器目标节点不存在。");
            return new RuntimeResult(false, continuation.traceId(), "计时器目标节点不存在。");
        }

        traces.add(continuation.traceId(), continuation.targetNodeId(), "计时器完成：继续执行");
        ExecutionContext context = new ExecutionContext(
                continuation.traceId(),
                continuation.playerId(),
                continuation.sessionId(),
                continuation.depth()
        );
        return runFrom(target.get(), context);
    }

    private RuntimeResult runFrom(NodeDefinition startNode, ExecutionContext context) {
        NodeDefinition current = startNode;
        while (current != null) {
            if (context.nextStep() > limits.maxStepsPerExecution()) {
                traces.add(context.traceId(), current.id(), "执行失败：超过最大执行步数。");
                return new RuntimeResult(false, context.traceId(), "超过最大执行步数。");
            }

            String outputSlot;
            try {
                outputSlot = executeNode(current, context);
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null ? "运行时错误。" : exception.getMessage();
                traces.add(context.traceId(), current.id(), "执行失败：" + message);
                return new RuntimeResult(false, context.traceId(), message);
            }
            if (outputSlot == null) {
                return new RuntimeResult(true, context.traceId(), "执行完成。");
            }

            Optional<NodeDefinition> next;
            try {
                next = graph.firstTarget(current.id(), outputSlot);
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null ? "连接解析失败。" : exception.getMessage();
                traces.add(context.traceId(), current.id(), "执行失败：" + message);
                return new RuntimeResult(false, context.traceId(), message);
            }
            if (next.isEmpty()) {
                traces.add(context.traceId(), current.id(), "未连接后续积木，流程在此结束。");
                return new RuntimeResult(true, context.traceId(), "执行完成。");
            }
            current = next.get();
        }
        return new RuntimeResult(true, context.traceId(), "执行完成。");
    }

    private String executeNode(NodeDefinition node, ExecutionContext context) {
        Optional<RuntimeNodeExecutionResult> simulated = services.executeSimulationNode(
                node,
                context.playerId(),
                context.sessionId()
        );
        if (simulated.isPresent()) {
            RuntimeNodeExecutionResult result = simulated.get();
            if (result.traceMessage() != null && !result.traceMessage().isBlank()) {
                traces.add(context.traceId(), node.id(), result.traceMessage());
            }
            return result.outputSlot();
        }
        return switch (node.type()) {
            case MANUAL_TRIGGER, COMMAND_TRIGGER -> "started";
            case STATE_COMPARE_CONDITION -> executeCondition(node, context);
            case MESSAGE_ACTION -> executeMessage(node, context);
            case STATE_SET_ACTION -> executeStateSet(node, context);
            case STATE_ADD_ACTION -> executeStateAdd(node, context);
            case TIMER_START_ACTION -> executeTimer(node, context);
            case DEBUG_LOG_ACTION -> executeDebug(node, context);
            case PLAYER_HAS_TAG_CONDITION,
                 PLAYER_IS_ADMIN_CONDITION,
                 PLAYER_DIMENSION_CONDITION,
                 PLAYER_IN_REGION_CONDITION,
                 TARGET_BLOCK_TYPE_CONDITION,
                 TARGET_BLOCK_IN_REGION_CONDITION,
                 PLAYER_ADD_TAG_ACTION,
                 PLAYER_REMOVE_TAG_ACTION ->
                    throw new IllegalStateException("缺少模拟执行器：" + node.type());
        };
    }

    private String executeCondition(NodeDefinition node, ExecutionContext context) {
        StateKey key = stateKey(node, context);
        boolean missingValue = parseBooleanConfig(node.config().get("missing"), "missing");
        boolean expected = parseBooleanConfig(node.config().get("expected"), "expected");
        Optional<StateValue> stored = stateStore.get(key);
        if (stored.isPresent() && stored.get().type() != StateValueType.BOOLEAN) {
            throw new IllegalStateException("状态类型不匹配：" + displayStateKey(key));
        }
        boolean actual = stored.map(value -> value.asBoolean(missingValue)).orElse(missingValue);
        boolean passed = actual == expected;
        traces.add(context.traceId(), node.id(), "条件" + (passed ? "通过" : "失败") + "：" + displayStateKey(key) + " == " + expected);
        ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
        traces.add(context.traceId(), node.id(), mode.traceMessage(passed));
        return mode.outputSlot(passed);
    }

    private String executeMessage(NodeDefinition node, ExecutionContext context) {
        String message = RichTextComponentValue.plainText(node.config().getOrDefault("message", ""));
        String channel = messageResultKind(node.blockId());
        if ("CHAT".equals(channel)) {
            services.sendPlayerMessage(context.playerId(), message);
        }
        String traceMessage = switch (channel) {
            case "TITLE" -> "显示标题：" + message;
            case "SUBTITLE" -> "显示副标题：" + message;
            case "ACTIONBAR" -> "显示快捷栏消息：" + message;
            default -> "发送消息：" + message;
        };
        traces.add(context.traceId(), node.id(), traceMessage);
        services.recordMessageResult(node.id(), context.playerId(), message, channel);
        services.recordActionResult(node.id(), "message", traceMessage);
        return "done";
    }

    private String executeStateSet(NodeDefinition node, ExecutionContext context) {
        StateKey key = stateKey(node, context);
        StateValue value = parseStateValue(node.config().get("valueType"), node.config().get("value"));
        Optional<StateValue> stored = stateStore.get(key);
        if (stored.isPresent() && stored.get().type() != value.type()) {
            throw new IllegalStateException("状态类型不匹配：" + displayStateKey(key));
        }
        stateStore.set(key, value);
        traces.add(context.traceId(), node.id(), "状态写入：" + displayStateKey(key) + " = " + value.displayValue());
        services.recordStateChange(node.id(), key, value.displayValue());
        services.recordActionResult(node.id(), "state", "状态写入：" + displayStateKey(key) + " = " + value.displayValue());
        return "done";
    }

    private String executeStateAdd(NodeDefinition node, ExecutionContext context) {
        StateKey key = stateKey(node, context);
        int amount = Integer.parseInt(node.config().getOrDefault("amount", "0"));
        StateValue value = stateStore.addInteger(key, amount);
        traces.add(context.traceId(), node.id(), "状态累加：" + displayStateKey(key) + " = " + value.displayValue());
        services.recordStateChange(node.id(), key, value.displayValue());
        services.recordActionResult(node.id(), "state", "状态累加：" + displayStateKey(key) + " = " + value.displayValue());
        return "done";
    }

    private String executeTimer(NodeDefinition node, ExecutionContext context) {
        int seconds = Integer.parseInt(node.config().getOrDefault("durationSeconds", "30"));
        Optional<NodeDefinition> target = graph.firstTarget(node.id(), "timer_completed");
        if (target.isEmpty()) {
            throw new IllegalStateException("计时器缺少完成后的目标。");
        }
        TimerContinuation continuation = new TimerContinuation(
                graph.graphId(),
                target.get().id(),
                context.traceId(),
                context.playerId(),
                context.sessionId(),
                context.continuationDepth() + 1,
                generation
        );
        traces.add(context.traceId(), node.id(), "计时器启动：" + seconds + " 秒");
        services.recordActionResult(node.id(), "timer", "计时器启动：" + seconds + " 秒");
        services.recordTimerScheduled(node.id(), Duration.ofSeconds(seconds), continuation);
        services.scheduleTimer(Duration.ofSeconds(seconds), continuation);
        return null;
    }

    private String executeDebug(NodeDefinition node, ExecutionContext context) {
        String message = node.config().getOrDefault("message", "");
        services.debug(message);
        traces.add(context.traceId(), node.id(), "调试记录：" + message);
        services.recordActionResult(node.id(), "debug", "调试记录：" + message);
        return "done";
    }

    private StateKey stateKey(NodeDefinition node, ExecutionContext context) {
        StateScope scope = StateScope.valueOf(node.config().getOrDefault("scope", "PLAYER"));
        String owner = switch (scope) {
            case GLOBAL -> null;
            case PLAYER -> context.playerId() == null ? null : context.playerId().toString();
            case SESSION -> context.sessionId();
        };
        return StateKey.of(scope, owner, node.config().get("key"));
    }

    private StateValue parseStateValue(String type, String raw) {
        StateValueType valueType = StateValueType.valueOf(type);
        return switch (valueType) {
            case BOOLEAN -> {
                if (!"true".equals(raw) && !"false".equals(raw)) {
                    throw new IllegalArgumentException("BOOLEAN 值必须是 true 或 false。");
                }
                yield StateValue.bool("true".equals(raw));
            }
            case INTEGER -> StateValue.integer(Integer.parseInt(raw));
            case STRING -> StateValue.string(raw);
        };
    }

    private boolean parseBooleanConfig(String raw, String configKey) {
        if ("true".equals(raw)) {
            return true;
        }
        if ("false".equals(raw)) {
            return false;
        }
        throw new IllegalArgumentException("布尔配置无效：" + configKey);
    }

    private String displayStateKey(StateKey key) {
        return key.scope() + "." + key.key();
    }

    private String messageResultKind(String blockId) {
        return switch (blockId) {
            case BuiltInBlockCatalog.ACTION_MESSAGE_TITLE -> "TITLE";
            case BuiltInBlockCatalog.ACTION_MESSAGE_SUBTITLE -> "SUBTITLE";
            case BuiltInBlockCatalog.ACTION_MESSAGE_ACTIONBAR -> "ACTIONBAR";
            default -> "CHAT";
        };
    }
}
