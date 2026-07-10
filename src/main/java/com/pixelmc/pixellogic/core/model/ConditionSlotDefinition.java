package com.pixelmc.pixellogic.core.model;

public record ConditionSlotDefinition(String slotId, boolean negated) {
    public ConditionSlotDefinition {
        slotId = slotId == null ? "" : slotId;
    }
}
