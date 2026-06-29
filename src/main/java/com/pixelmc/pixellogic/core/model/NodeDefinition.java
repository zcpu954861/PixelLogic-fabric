package com.pixelmc.pixellogic.core.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record NodeDefinition(
        String id,
        NodeType type,
        List<SlotDefinition> slots,
        Map<String, String> config
) {
    public Optional<SlotDefinition> slot(String slotId) {
        return slots.stream().filter(slot -> slot.id().equals(slotId)).findFirst();
    }
}
