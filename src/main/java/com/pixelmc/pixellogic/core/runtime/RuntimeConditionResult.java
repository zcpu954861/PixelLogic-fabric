package com.pixelmc.pixellogic.core.runtime;

public record RuntimeConditionResult(
        String conditionNodeId,
        String blockId,
        RuntimeSubjectReference subject,
        boolean rawResult,
        String factSummary
) {
    public RuntimeConditionResult {
        conditionNodeId = conditionNodeId == null ? "" : conditionNodeId;
        blockId = blockId == null ? "" : blockId;
        factSummary = factSummary == null ? "" : factSummary;
    }
}
