package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.state.StateKey;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface RuntimeServices {
    default Optional<RuntimeNodeExecutionResult> executeSimulationNode(NodeDefinition node, UUID playerId, String sessionId) {
        return Optional.empty();
    }

    default Optional<RuntimeNodeExecutionResult> executeSimulationNode(
            NodeDefinition node,
            RuntimeExecutionContext context
    ) {
        return executeSimulationNode(node, context.playerId(), context.sessionId());
    }

    default Optional<RuntimePredicateResult> evaluatePredicate(NodeDefinition node, UUID playerId, String sessionId) {
        return Optional.empty();
    }

    default Optional<RuntimePredicateResult> evaluatePredicate(
            NodeDefinition node,
            RuntimeExecutionContext context
    ) {
        return evaluatePredicate(node, context.playerId(), context.sessionId());
    }

    default Optional<RuntimeSubjectReference> runEntity(UUID playerId, String sessionId) {
        return playerId == null
                ? Optional.empty()
                : Optional.of(new RuntimeSubjectReference(
                        playerId.toString(),
                        RuntimeSubjectReference.Kind.PLAYER,
                        "运行实体"
                ));
    }

    default Optional<RuntimeSubjectReference> targetEntity(UUID playerId, String sessionId) {
        return Optional.empty();
    }

    default boolean entityResolvable(RuntimeSubjectReference entity, UUID playerId, String sessionId) {
        return entity != null && entity.isEntity() && !entity.id().isBlank();
    }

    void sendPlayerMessage(UUID playerId, String message);

    void debug(String message);

    void scheduleTimer(Duration delay, TimerContinuation continuation);

    default void recordActionResult(String nodeId, String kind, String message) {
    }

    default void recordMessageResult(String nodeId, UUID playerId, String message) {
    }

    default void recordMessageResult(String nodeId, UUID playerId, String message, String channel) {
        recordMessageResult(nodeId, playerId, message);
    }

    default void recordStateChange(String nodeId, StateKey key, String value) {
    }

    default void recordTimerScheduled(String nodeId, Duration delay, TimerContinuation continuation) {
    }

    default void recordRuntimeResult(RuntimeResult result) {
    }

}
