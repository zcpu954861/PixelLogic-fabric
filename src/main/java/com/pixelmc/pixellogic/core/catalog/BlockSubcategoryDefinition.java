package com.pixelmc.pixellogic.core.catalog;

public record BlockSubcategoryDefinition(
        String id,
        String categoryId,
        String displayName,
        String description,
        int order
) {
}
