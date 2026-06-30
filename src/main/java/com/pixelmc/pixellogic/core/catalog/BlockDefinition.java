package com.pixelmc.pixellogic.core.catalog;

import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;

import java.util.List;
import java.util.Map;

public record BlockDefinition(
        String id,
        int version,
        String displayName,
        String description,
        String categoryId,
        String subcategoryId,
        List<String> tags,
        String nodeKind,
        NodeType nodeType,
        Map<String, String> defaultConfig,
        List<BlockFormFieldDefinition> formFields,
        List<SlotDefinition> inputSlots,
        List<SlotDefinition> outputSlots,
        BlockCapabilityLevel simulationCapability,
        BlockCapabilityLevel mcCapability,
        List<BlockSafetyFlag> safetyFlags,
        boolean deprecated,
        boolean hidden,
        List<String> aliases
) {
    public BlockDefinition {
        tags = tags == null ? List.of() : List.copyOf(tags);
        defaultConfig = defaultConfig == null ? Map.of() : Map.copyOf(defaultConfig);
        formFields = formFields == null ? List.of() : List.copyOf(formFields);
        inputSlots = inputSlots == null ? List.of() : List.copyOf(inputSlots);
        outputSlots = outputSlots == null ? List.of() : List.copyOf(outputSlots);
        safetyFlags = safetyFlags == null ? List.of() : List.copyOf(safetyFlags);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        subcategoryId = subcategoryId == null ? "" : subcategoryId;
    }
}
