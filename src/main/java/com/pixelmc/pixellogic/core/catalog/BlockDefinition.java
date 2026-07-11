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
        List<String> aliases,
        List<String> searchKeywords,
        List<BlockCapability> capabilities,
        String nodeKind,
        NodeType nodeType,
        Map<String, String> defaultConfig,
        List<BlockFormFieldDefinition> formSchema,
        String summaryTemplate,
        String summaryFormatter,
        String predicateSummaryTemplate,
        String predicateNegatedSummaryTemplate,
        List<String> containerSlots,
        List<SlotDefinition> inputSlots,
        List<SlotDefinition> outputSlots,
        BlockCapabilityLevel simulationCapability,
        BlockCapabilityLevel mcCapability,
        List<BlockSafetyFlag> safetyFlags,
        boolean deprecated,
        boolean hidden,
        BlockLibraryVisibility visibility
) {
    public BlockDefinition {
        categoryId = categoryId == null ? "" : categoryId;
        subcategoryId = subcategoryId == null ? categoryId : subcategoryId;
        tags = tags == null ? List.of() : List.copyOf(tags);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        searchKeywords = searchKeywords == null ? List.of() : List.copyOf(searchKeywords);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        defaultConfig = defaultConfig == null ? Map.of() : Map.copyOf(defaultConfig);
        formSchema = formSchema == null ? List.of() : List.copyOf(formSchema);
        summaryTemplate = summaryTemplate == null ? "" : summaryTemplate;
        summaryFormatter = summaryFormatter == null ? "" : summaryFormatter;
        predicateSummaryTemplate = predicateSummaryTemplate == null ? "" : predicateSummaryTemplate;
        predicateNegatedSummaryTemplate = predicateNegatedSummaryTemplate == null ? "" : predicateNegatedSummaryTemplate;
        containerSlots = containerSlots == null ? List.of() : List.copyOf(containerSlots);
        inputSlots = inputSlots == null ? List.of() : List.copyOf(inputSlots);
        outputSlots = outputSlots == null ? List.of() : List.copyOf(outputSlots);
        safetyFlags = safetyFlags == null ? List.of() : List.copyOf(safetyFlags);
    }
}
