package com.pixelmc.pixellogic.core.runtime;

public record RuntimeNodeExecutionResult(
        String outputSlot,
        String traceMessage,
        RuntimeConditionResult conditionResult,
        RuntimeActionOutcome actionOutcome
) {
    public RuntimeNodeExecutionResult(String outputSlot, String traceMessage) {
        this(outputSlot, traceMessage, null, null);
    }

    public RuntimeNodeExecutionResult(
            String outputSlot,
            String traceMessage,
            RuntimeConditionResult conditionResult
    ) {
        this(outputSlot, traceMessage, conditionResult, null);
    }
}
