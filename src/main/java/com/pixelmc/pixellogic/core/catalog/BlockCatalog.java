package com.pixelmc.pixellogic.core.catalog;

import java.util.List;
import java.util.Optional;

public record BlockCatalog(
        List<BlockCategoryDefinition> categories,
        List<BlockSubcategoryDefinition> subcategories,
        List<BlockDefinition> blocks
) {
    public BlockCatalog {
        categories = categories == null ? List.of() : List.copyOf(categories);
        subcategories = subcategories == null ? List.of() : List.copyOf(subcategories);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public Optional<BlockDefinition> block(String blockId) {
        return blocks.stream().filter(block -> block.id().equals(blockId)).findFirst();
    }
}
