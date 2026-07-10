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
