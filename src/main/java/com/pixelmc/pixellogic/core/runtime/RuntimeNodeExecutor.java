package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.catalog.RichTextComponentValue;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.StateScope;
import com.pixelmc.pixellogic.core.model.StateValueType;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.state.StateKey;
import com.pixelmc.pixellogic.core.state.StateValue;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;

import java.util.Optional;

final class RuntimeNodeExecutor {
    private final InMemoryStateStore stateStore;
    private final BoundedTraceBuffer traces;
    private final RuntimeServices services;

    RuntimeNodeExecutor(InMemoryStateStore stateStore, BoundedTraceBuffer traces, RuntimeServices services) {
        this.stateStore = stateStore;
        this.traces = traces;
        this.services = services;
    }

    RuntimeNodeExecutionResult execute(NodeDefinition node, ExecutionContext context) {
        Optional<RuntimeNodeExecutionResult> simulated = services.executeSimulationNode(
                node,
                context.snapshot()
        );
        if (simulated.isPresent()) {
            RuntimeNodeExecutionResult result = simulated.get();
            if (result.traceMessage() != null && !result.traceMessage().isBlank()) {
                traces.add(context.traceId(), node.id(), result.traceMessage());
            }
            return result;
        }
        String outputSlot = switch (node.type()) {
            case MANUAL_TRIGGER, COMMAND_TRIGGER -> "started";
            case STATE_COMPARE_CONDITION -> executeCondition(node, context);
            case MESSAGE_ACTION -> executeMessage(node, context);
            case STATE_SET_ACTION -> executeStateSet(node, context);
            case STATE_ADD_ACTION -> executeStateAdd(node, context);
            case DEBUG_LOG_ACTION -> executeDebug(node, context);
            case TIMER_START_ACTION, CONTROL_LOOP_COUNT, CONTROL_LOOP_FOREVER, CONTROL_LOOP_UNTIL,
                 CONTEXT_ENTITY_EXECUTE_AS ->
                    throw new IllegalStateException("控制流节点必须由 GraphRuntime 游标执行：" + node.type());
            case ENTITY_HAS_TAG_CONDITION,
                 PLAYER_IS_ADMIN_CONDITION,
                 PLAYER_DIMENSION_CONDITION,
                 PLAYER_IN_REGION_CONDITION,
                 PLAYER_Y_COMPARE_CONDITION,
                 TARGET_BLOCK_TYPE_CONDITION,
                 TARGET_BLOCK_IN_REGION_CONDITION,
                 TARGET_BLOCK_Y_COMPARE_CONDITION,
                 PLAYER_NEAR_TARGET_BLOCK_CONDITION,
                 ENTITY_ADD_TAG_ACTION,
                 ENTITY_REMOVE_TAG_ACTION,
                 ENTITY_DAMAGE_ACTION,
                 ENTITY_HEAL_ACTION,
                 ENTITY_SET_HEALTH_ACTION,
                 ENTITY_KILL_ACTION,
                 ENTITY_REMOVE_ACTION ->
                    throw new IllegalStateException("缺少模拟执行器：" + node.type());
        };
        return new RuntimeNodeExecutionResult(outputSlot, "");
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
