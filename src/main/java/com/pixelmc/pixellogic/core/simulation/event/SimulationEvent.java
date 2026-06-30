package com.pixelmc.pixellogic.core.simulation.event;

public record SimulationEvent(
        SimulationEventType type,
        String triggerType,
        String commandText,
        String sessionId
) {
    public SimulationEvent {
        type = type == null ? SimulationEventType.MANUAL_TRIGGER : type;
        triggerType = triggerType == null || triggerType.isBlank() ? "manual.test.start" : triggerType;
        commandText = commandText == null ? "" : commandText;
        sessionId = sessionId == null || sessionId.isBlank() ? "manual-session" : sessionId;
    }

    public static SimulationEvent manual(String triggerType, String commandText, String sessionId) {
        return new SimulationEvent(SimulationEventType.MANUAL_TRIGGER, triggerType, commandText, sessionId);
    }
}
