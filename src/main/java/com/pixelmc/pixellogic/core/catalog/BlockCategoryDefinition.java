package com.pixelmc.pixellogic.core.catalog;

public record BlockCategoryDefinition(
        String id,
        String displayName,
        String description,
        int order,
        boolean visibleByDefault
) {
}
