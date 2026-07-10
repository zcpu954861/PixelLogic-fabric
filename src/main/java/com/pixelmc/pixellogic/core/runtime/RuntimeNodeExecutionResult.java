package com.pixelmc.pixellogic.core.runtime;

public record RuntimeNodeExecutionResult(
        String outputSlot,
        String traceMessage,
        RuntimeConditionResult conditionResult
) {
    public RuntimeNodeExecutionResult(String outputSlot, String traceMessage) {
        this(outputSlot, traceMessage, null);
    }
}
