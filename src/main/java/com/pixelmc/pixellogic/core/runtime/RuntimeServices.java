package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.state.StateKey;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RuntimeServices {
    default Optional<RuntimeNodeExecutionResult> executeSimulationNode(
            NodeDefinition node,
            RuntimeExecutionContext context
    ) {
        return EntityTagExecution.execute(node, context, this)
                .or(() -> EntityHealthExecution.execute(node, context, this))
                .or(() -> EntityStatusExecution.execute(node, context, this));
    }

    default Optional<RuntimePredicateResult> evaluatePredicate(
            NodeDefinition node,
            RuntimeExecutionContext context
    ) {
        return EntityTagExecution.evaluatePredicate(node, context, this);
    }

    default Optional<RuntimeSubjectReference> initialCurrentEntity(UUID playerId, String sessionId) {
        return Optional.empty();
    }

    default Optional<RuntimeSubjectReference> targetEntity(UUID playerId, String sessionId) {
        return Optional.empty();
    }

    default RuntimeEntityProvider entityProvider() {
        return RuntimeEntityProvider.UNAVAILABLE;
    }

    void sendPlayerMessage(UUID playerId, String message);

    void debug(String message);

    void scheduleTimer(Duration delay, TimerContinuation continuation);

    default void recordActionResult(String nodeId, String kind, String message) {
    }

    default void recordActionOutcome(String nodeId, RuntimeActionOutcome outcome) {
        recordActionResult(nodeId, outcome.kind(), outcome.message());
    }

    default void recordEntityTagState(
            String nodeId,
            RuntimeSubjectReference target,
            Set<String> tags
    ) {
    }

    default void recordMessageResult(String nodeId, UUID playerId, String message, String channel) {
    }

    default void recordStateChange(String nodeId, StateKey key, String value) {
    }

    default void recordTimerScheduled(String nodeId, Duration delay, TimerContinuation continuation) {
    }

    default void recordRuntimeResult(RuntimeResult result) {
    }

}
