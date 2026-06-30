package com.pixelmc.pixellogic.core.simulation.result;

import java.util.UUID;

public record SimulationMessageResult(String nodeId, UUID playerId, String message, String channel) {
    public SimulationMessageResult(String nodeId, UUID playerId, String message) {
        this(nodeId, playerId, message, "CHAT");
    }
}
