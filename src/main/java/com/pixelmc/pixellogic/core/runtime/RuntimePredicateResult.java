package com.pixelmc.pixellogic.core.runtime;

public record RuntimePredicateResult(
        boolean value,
        String traceMessage,
        RuntimeConditionResult conditionResult
) {
    public RuntimePredicateResult(boolean value, String traceMessage) {
        this(value, traceMessage, null);
    }

    public RuntimePredicateResult {
        traceMessage = traceMessage == null ? "" : traceMessage;
    }
}
