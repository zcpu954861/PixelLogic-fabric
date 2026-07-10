package com.pixelmc.pixellogic.core.model;

import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record NodeDefinition(
        String id,
        NodeType type,
        String blockId,
        String parentContainerId,
        String parentSlot,
        List<ConditionSlotDefinition> conditionSlots,
        List<SlotDefinition> slots,
        Map<String, String> config
) {
    public NodeDefinition {
        blockId = BuiltInBlockCatalog.resolveBlockId(blockId, type);
        parentContainerId = parentContainerId == null ? "" : parentContainerId;
        parentSlot = parentSlot == null ? "" : parentSlot;
        conditionSlots = conditionSlots == null ? List.of() : List.copyOf(conditionSlots);
        slots = slots == null ? List.of() : List.copyOf(slots);
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    public NodeDefinition(
            String id,
            NodeType type,
            String blockId,
            String parentContainerId,
            String parentSlot,
            List<SlotDefinition> slots,
            Map<String, String> config
    ) {
        this(id, type, blockId, parentContainerId, parentSlot, List.of(), slots, config);
    }

    public NodeDefinition(String id, NodeType type, String blockId, List<SlotDefinition> slots, Map<String, String> config) {
        this(id, type, blockId, "", "", List.of(), slots, config);
    }

    public NodeDefinition(String id, NodeType type, List<SlotDefinition> slots, Map<String, String> config) {
        this(id, type, BuiltInBlockCatalog.blockIdFor(type), "", "", List.of(), slots, config);
    }

    public Optional<SlotDefinition> slot(String slotId) {
        return slots.stream().filter(slot -> slot.id().equals(slotId)).findFirst();
    }
}
