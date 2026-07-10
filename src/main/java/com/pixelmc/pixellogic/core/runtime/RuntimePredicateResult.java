package com.pixelmc.pixellogic.core.runtime;

public record RuntimePredicateResult(boolean value, String traceMessage) {
    public RuntimePredicateResult {
        traceMessage = traceMessage == null ? "" : traceMessage;
    }
}
