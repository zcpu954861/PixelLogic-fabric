package com.pixelmc.pixellogic.core.runtime;

import java.util.UUID;

public record TriggerEvent(String triggerType, String commandText, UUID playerId, String sessionId) {
}
