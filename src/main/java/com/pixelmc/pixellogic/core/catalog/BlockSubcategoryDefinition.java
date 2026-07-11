package com.pixelmc.pixellogic.core.catalog;

/** Wire compatibility projection derived from the formal block categories. */
public record BlockSubcategoryDefinition(
        String id,
        String categoryId,
        String displayName,
        String description,
        int order
) {
}
