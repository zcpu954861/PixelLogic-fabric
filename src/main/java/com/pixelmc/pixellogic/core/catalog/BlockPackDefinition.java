package com.pixelmc.pixellogic.core.catalog;

public record BlockPackDefinition(
        String id,
        String displayName,
        String description,
        String icon,
        int order
) {
}
