package com.pixelmc.pixellogic.core.simulation.result;

import com.pixelmc.pixellogic.core.catalog.RichTextComponentValue;

import java.util.UUID;

public record SimulationMessageResult(String nodeId, UUID playerId, String message, String channel, String component) {
    public SimulationMessageResult(String nodeId, UUID playerId, String message) {
        this(nodeId, playerId, message, "CHAT");
    }

    public SimulationMessageResult(String nodeId, UUID playerId, String message, String channel) {
        this(nodeId, playerId, message, channel, RichTextComponentValue.fromPlainText(message));
    }
}
