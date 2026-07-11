package com.pixelmc.pixellogic.core.catalog;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record BlockCatalog(
        List<BlockPackDefinition> packs,
        List<BlockCategoryDefinition> categories,
        List<BlockSubcategoryDefinition> subcategories,
        List<BlockDefinition> blocks
) {
    public BlockCatalog(
            List<BlockPackDefinition> packs,
            List<BlockCategoryDefinition> categories,
            List<BlockDefinition> blocks
    ) {
        this(packs, categories, compatibilitySubcategories(categories), blocks);
    }

    public BlockCatalog {
        packs = packs == null ? List.of() : List.copyOf(packs);
        categories = categories == null ? List.of() : List.copyOf(categories);
        subcategories = subcategories == null ? List.of() : List.copyOf(subcategories);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);

        Map<String, BlockPackDefinition> packsById = new HashMap<>();
        for (BlockPackDefinition pack : packs) {
            require(pack != null, "pack must not be null");
            requireText(pack.id(), "pack id");
            requireText(pack.displayName(), "pack displayName: " + pack.id());
            requireText(pack.description(), "pack description: " + pack.id());
            requireText(pack.icon(), "pack icon: " + pack.id());
            require(packsById.putIfAbsent(pack.id(), pack) == null, "duplicate pack id: " + pack.id());
        }

        Map<String, BlockCategoryDefinition> categoriesById = new HashMap<>();
        for (BlockCategoryDefinition category : categories) {
            require(category != null, "category must not be null");
            requireText(category.id(), "category id");
            requireText(category.packId(), "category packId: " + category.id());
            requireText(category.displayName(), "category displayName: " + category.id());
            requireText(category.description(), "category description: " + category.id());
            requireText(category.icon(), "category icon: " + category.id());
            require(category.visibleByDefault(), "category visibleByDefault: " + category.id());
            require(packsById.containsKey(category.packId()), "unknown category packId: " + category.id());
            require(categoriesById.putIfAbsent(category.id(), category) == null,
                    "duplicate category id: " + category.id());
        }
        require(subcategories.equals(compatibilitySubcategories(categories)),
                "subcategories must be the derived category compatibility projection");

        Set<String> blockIds = new HashSet<>();
        Set<String> aliases = new HashSet<>();
        for (BlockDefinition block : blocks) {
            require(block != null, "block must not be null");
            requireText(block.id(), "block id");
            requireText(block.displayName(), "block displayName: " + block.id());
            requireText(block.description(), "block description: " + block.id());
            requireText(block.nodeKind(), "block nodeKind: " + block.id());
            require(block.nodeType() != null, "block nodeType: " + block.id());
            require(block.visibility() != null, "block visibility: " + block.id());
            require(block.hidden() == (block.visibility() != BlockLibraryVisibility.BROWSE),
                    "block hidden compatibility: " + block.id());
            require(block.subcategoryId().equals(block.categoryId()),
                    "block subcategory compatibility: " + block.id());
            if (block.visibility() == BlockLibraryVisibility.CONTEXT_ONLY) {
                require(block.capabilities().contains(BlockCapability.PREDICATE),
                        "context-only block has no supported library context: " + block.id());
            }
            if (block.visibility() != BlockLibraryVisibility.HIDDEN) {
                requireText(block.categoryId(), "block categoryId: " + block.id());
                require(categoriesById.containsKey(block.categoryId()), "unknown block categoryId: " + block.id());
            } else if (block.categoryId() != null && !block.categoryId().isBlank()) {
                require(categoriesById.containsKey(block.categoryId()), "unknown hidden block categoryId: " + block.id());
            }
            require(blockIds.add(block.id()), "duplicate block id: " + block.id());
            requireUniqueTerms(block.tags(), "tags", block.id());
            requireUniqueTerms(block.aliases(), "aliases", block.id());
            requireUniqueTerms(block.searchKeywords(), "searchKeywords", block.id());
            for (String alias : block.aliases()) {
                require(aliases.add(normalize(alias)), "duplicate block alias: " + alias);
            }
        }
        for (String blockId : blockIds) {
            require(!aliases.contains(normalize(blockId)), "block alias collides with block id: " + blockId);
        }

        Set<String> visibleCategoryIds = new HashSet<>();
        Set<String> visiblePackIds = new HashSet<>();
        for (BlockDefinition block : blocks) {
            if (!block.deprecated() && block.visibility() != BlockLibraryVisibility.HIDDEN) {
                BlockCategoryDefinition category = categoriesById.get(block.categoryId());
                visibleCategoryIds.add(category.id());
                visiblePackIds.add(category.packId());
            }
        }
        for (BlockCategoryDefinition category : categories) {
            require(visibleCategoryIds.contains(category.id()), "empty visible category: " + category.id());
        }
        for (BlockPackDefinition pack : packs) {
            require(visiblePackIds.contains(pack.id()), "empty visible pack: " + pack.id());
        }

        packs = packs.stream()
                .sorted(Comparator.comparingInt(BlockPackDefinition::order).thenComparing(BlockPackDefinition::id))
                .toList();
        categories = categories.stream()
                .sorted(Comparator.comparingInt(BlockCategoryDefinition::order).thenComparing(BlockCategoryDefinition::id))
                .toList();
        blocks = blocks.stream()
                .sorted(Comparator
                        .comparingInt((BlockDefinition block) -> {
                            BlockCategoryDefinition category = categoriesById.get(block.categoryId());
                            return category == null ? Integer.MAX_VALUE : packsById.get(category.packId()).order();
                        })
                        .thenComparing(block -> {
                            BlockCategoryDefinition category = categoriesById.get(block.categoryId());
                            return category == null ? "" : category.packId();
                        })
                        .thenComparingInt(block -> {
                            BlockCategoryDefinition category = categoriesById.get(block.categoryId());
                            return category == null ? Integer.MAX_VALUE : category.order();
                        })
                        .thenComparing(BlockDefinition::categoryId)
                        .thenComparing(BlockDefinition::id))
                .toList();
    }

    private static List<BlockSubcategoryDefinition> compatibilitySubcategories(
            List<BlockCategoryDefinition> categories
    ) {
        if (categories == null) {
            return List.of();
        }
        return categories.stream()
                .filter(category -> category != null)
                .sorted(Comparator.comparingInt(BlockCategoryDefinition::order).thenComparing(BlockCategoryDefinition::id))
                .map(category -> new BlockSubcategoryDefinition(
                        category.id(),
                        category.id(),
                        category.displayName(),
                        category.description(),
                        category.order()
                ))
                .toList();
    }

    private static void requireUniqueTerms(List<String> terms, String field, String blockId) {
        Set<String> normalized = new HashSet<>();
        for (String term : terms) {
            requireText(term, "block " + field + ": " + blockId);
            require(normalized.add(normalize(term)),
                    "duplicate block " + field + ": " + blockId + " -> " + term);
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " must not be blank");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
