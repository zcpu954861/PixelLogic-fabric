package com.pixelmc.pixellogic.core.catalog;

import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.selfcheck.SelfCheckSupport;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;

public final class BlockLibraryTaxonomySelfCheck {
    private BlockLibraryTaxonomySelfCheck() {
    }

    public static void main(String[] args) {
        SelfCheckSupport.run("blockLibraryTaxonomySelfCheck", () -> {
            verifyBuiltInTaxonomy();
            verifySearchAndLegacyIds();
            verifyVisibilitySemantics();
            verifyStableSorting();
            verifyInvalidCatalogsFailFast();
        });
    }

    private static void verifyBuiltInTaxonomy() {
        BlockCatalog catalog = BuiltInBlockCatalog.catalog();
        Map<String, String> expectedCategories = Map.ofEntries(
                Map.entry(BuiltInBlockCatalog.TRIGGER_MANUAL_TEST, "events-triggers.test-entry"),
                Map.entry(BuiltInBlockCatalog.CONDITION_STATE_EQUALS, "state-data.conditions"),
                Map.entry(BuiltInBlockCatalog.ACTION_MESSAGE_CHAT, "presentation-feedback.player-messages"),
                Map.entry(BuiltInBlockCatalog.ACTION_MESSAGE_TITLE, "presentation-feedback.screen-prompts"),
                Map.entry(BuiltInBlockCatalog.ACTION_MESSAGE_SUBTITLE, "presentation-feedback.screen-prompts"),
                Map.entry(BuiltInBlockCatalog.ACTION_MESSAGE_ACTIONBAR, "presentation-feedback.screen-prompts"),
                Map.entry(BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG, "player-entity.tags"),
                Map.entry(BuiltInBlockCatalog.CONDITION_PLAYER_IS_ADMIN, "player-entity.identity-permissions"),
                Map.entry(BuiltInBlockCatalog.CONDITION_PLAYER_DIMENSION_IS, "location-region.dimensions-heights"),
                Map.entry(BuiltInBlockCatalog.CONDITION_PLAYER_IN_REGION, "location-region.regions"),
                Map.entry(BuiltInBlockCatalog.CONDITION_PLAYER_Y_COMPARE, "location-region.dimensions-heights"),
                Map.entry(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IS_TYPE, "block-world.target-block"),
                Map.entry(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_IN_REGION, "location-region.regions"),
                Map.entry(BuiltInBlockCatalog.CONDITION_TARGET_BLOCK_Y_COMPARE, "location-region.dimensions-heights"),
                Map.entry(BuiltInBlockCatalog.CONDITION_PLAYER_NEAR_TARGET_BLOCK, "location-region.spatial-relations"),
                Map.entry(BuiltInBlockCatalog.ACTION_ENTITY_ADD_TAG, "player-entity.tags"),
                Map.entry(BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_TAG, "player-entity.tags"),
                Map.entry(BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE, "player-entity.health-attributes"),
                Map.entry(BuiltInBlockCatalog.ACTION_ENTITY_HEAL, "player-entity.health-attributes"),
                Map.entry(BuiltInBlockCatalog.ACTION_ENTITY_SET_HEALTH, "player-entity.health-attributes"),
                Map.entry(BuiltInBlockCatalog.ACTION_ENTITY_KILL, "player-entity.entity-management"),
                Map.entry(BuiltInBlockCatalog.ACTION_ENTITY_REMOVE, "player-entity.entity-management"),
                Map.entry(BuiltInBlockCatalog.CONTROL_LOOP_COUNT, "logic-flow.loops"),
                Map.entry(BuiltInBlockCatalog.CONTROL_LOOP_FOREVER, "logic-flow.loops"),
                Map.entry(BuiltInBlockCatalog.CONTROL_LOOP_UNTIL, "logic-flow.loops"),
                Map.entry(BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS, "player-entity.execution-context"),
                Map.entry(BuiltInBlockCatalog.STATE_SET, "state-data.mutations"),
                Map.entry(BuiltInBlockCatalog.STATE_ADD, "state-data.mutations"),
                Map.entry(BuiltInBlockCatalog.TIMER_WAIT, "logic-flow.timing"),
                Map.entry(BuiltInBlockCatalog.DEBUG_LOG, "presentation-feedback.diagnostics")
        );
        Map<String, String> expectedCategoryNames = Map.ofEntries(
                Map.entry("events-triggers.test-entry", "测试入口"),
                Map.entry("logic-flow.loops", "循环"),
                Map.entry("logic-flow.timing", "等待与时序"),
                Map.entry("player-entity.tags", "标签"),
                Map.entry("player-entity.identity-permissions", "身份与权限"),
                Map.entry("player-entity.execution-context", "实体上下文"),
                Map.entry("player-entity.health-attributes", "生命与属性"),
                Map.entry("player-entity.entity-management", "实体管理"),
                Map.entry("location-region.dimensions-heights", "维度与高度"),
                Map.entry("location-region.regions", "区域"),
                Map.entry("location-region.spatial-relations", "空间关系"),
                Map.entry("block-world.target-block", "目标方块"),
                Map.entry("presentation-feedback.player-messages", "玩家消息"),
                Map.entry("presentation-feedback.screen-prompts", "屏幕提示"),
                Map.entry("presentation-feedback.diagnostics", "调试诊断"),
                Map.entry("state-data.conditions", "状态条件"),
                Map.entry("state-data.mutations", "状态写入")
        );

        require(catalog.packs().size() == 7, "built-in taxonomy should expose seven non-empty packs");
        require(catalog.categories().size() == 17, "built-in taxonomy should expose seventeen non-empty categories");
        require(catalog.subcategories().size() == catalog.categories().size(),
                "legacy subcategories should be a one-to-one compatibility projection");
        require(catalog.subcategories().stream().allMatch(subcategory -> catalog.categories().stream()
                        .anyMatch(category -> subcategory.id().equals(category.id())
                                && subcategory.categoryId().equals(category.id())
                                && subcategory.displayName().equals(category.displayName())
                                && subcategory.order() == category.order())),
                "legacy subcategory metadata should be derived from formal categories");
        require(catalog.blocks().size() == 30, "built-in taxonomy should explicitly classify all 30 blocks");
        require(catalog.categories().stream().allMatch(category ->
                        category.displayName().equals(expectedCategoryNames.get(category.id()))),
                "formal category display names should match the taxonomy specification");
        require(catalog.blocks().stream().map(BlockDefinition::id).collect(java.util.stream.Collectors.toSet())
                        .equals(expectedCategories.keySet()),
                "built-in taxonomy matrix should contain every and only current block id");
        catalog.blocks().forEach(block -> {
            require(block.categoryId().equals(expectedCategories.get(block.id())),
                    "unexpected formal category for " + block.id());
            require(block.subcategoryId().equals(block.categoryId()) && block.tags().isEmpty() && !block.hidden(),
                    "built-in compatibility metadata should be derived from the formal taxonomy: " + block.id());
            require(block.visibility() == BlockLibraryVisibility.BROWSE,
                    "all current built-in blocks should remain normally browsable: " + block.id());
        });

        Set<String> visibleCategories = new HashSet<>();
        Set<String> visiblePacks = new HashSet<>();
        Map<String, BlockCategoryDefinition> categoriesById = catalog.categories().stream()
                .collect(java.util.stream.Collectors.toMap(BlockCategoryDefinition::id, item -> item));
        catalog.blocks().stream()
                .filter(block -> !block.deprecated() && block.visibility() != BlockLibraryVisibility.HIDDEN)
                .forEach(block -> {
                    BlockCategoryDefinition category = categoriesById.get(block.categoryId());
                    visibleCategories.add(category.id());
                    visiblePacks.add(category.packId());
                });
        require(visibleCategories.size() == catalog.categories().size(), "every built-in category should be reachable");
        require(visiblePacks.size() == catalog.packs().size(), "every built-in pack should be reachable");

        require(isSorted(catalog.packs(), Comparator.comparingInt(BlockPackDefinition::order)
                        .thenComparing(BlockPackDefinition::id)),
                "packs should be sorted by order then id");
        require(isSorted(catalog.categories(), Comparator.comparingInt(BlockCategoryDefinition::order)
                        .thenComparing(BlockCategoryDefinition::id)),
                "categories should be sorted by order then id");
        require(isSorted(catalog.blocks(), Comparator
                        .comparingInt((BlockDefinition block) -> catalog.packs().stream()
                                .filter(pack -> pack.id().equals(categoriesById.get(block.categoryId()).packId()))
                                .findFirst().orElseThrow().order())
                        .thenComparing(block -> categoriesById.get(block.categoryId()).packId())
                        .thenComparingInt(block -> categoriesById.get(block.categoryId()).order())
                        .thenComparing(BlockDefinition::categoryId)
                        .thenComparing(BlockDefinition::id)),
                "blocks should be sorted by category order then stable ids");
    }

    private static void verifySearchAndLegacyIds() {
        BlockDefinition wait = block(BuiltInBlockCatalog.TIMER_WAIT);
        require(wait.aliases().isEmpty()
                        && wait.searchKeywords().containsAll(List.of("等待", "延迟", "timer", "delay")),
                "wait should expose real search keywords without expanding graph aliases");
        require(block(BuiltInBlockCatalog.CONTROL_LOOP_COUNT).searchKeywords().contains("repeat")
                        && block(BuiltInBlockCatalog.CONTROL_LOOP_FOREVER).searchKeywords().contains("while")
                        && block(BuiltInBlockCatalog.CONTROL_LOOP_UNTIL).searchKeywords().contains("until"),
                "loop blocks should expose their real command-language search terms");
        require(block(BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS).searchKeywords().contains("as")
                        && block(BuiltInBlockCatalog.CONTEXT_ENTITY_EXECUTE_AS).searchKeywords().contains("实体上下文"),
                "entity context should expose as/entity-context search metadata");
        require(block(BuiltInBlockCatalog.STATE_SET).searchKeywords().containsAll(List.of("变量", "状态", "score", "计分")),
                "state blocks should expose state/score search terms");
        require(block(BuiltInBlockCatalog.CONDITION_ENTITY_HAS_TAG).searchKeywords().contains("实体标签")
                        && BuiltInBlockCatalog.block(String.join(".", "condition", "player", "has_tag")).isEmpty()
                        && BuiltInBlockCatalog.block(String.join(".", "action", "context_entity", "add_tag")).isEmpty(),
                "generic entity tags should replace removed tag ids without aliases");

        BlockDefinition manual = block(BuiltInBlockCatalog.TRIGGER_MANUAL_TEST);
        require(manual.aliases().equals(List.of("manual.test.start")),
                "manual trigger should retain its legacy block id");
        require(manual.searchKeywords().contains("manual"),
                "manual should remain searchable without becoming a graph alias");
        require(BuiltInBlockCatalog.block("manual.test.start").orElseThrow().id().equals(manual.id()),
                "legacy block id should still canonicalize to the current block");
        require(BuiltInBlockCatalog.block("manual").isEmpty(),
                "user search terms must never canonicalize graph block ids");
    }

    private static void verifyVisibilitySemantics() {
        BlockPackDefinition pack = pack("visibility", 10);
        BlockCategoryDefinition category = category("visibility.blocks", pack.id(), 10);
        BlockDefinition browse = syntheticBlock(
                "visibility.browse", category.id(), BlockLibraryVisibility.BROWSE,
                List.of(BlockCapability.PREDICATE), List.of(), List.of()
        );
        BlockDefinition contextOnly = syntheticBlock(
                "visibility.context", category.id(), BlockLibraryVisibility.CONTEXT_ONLY,
                List.of(BlockCapability.PREDICATE), List.of(), List.of()
        );
        BlockDefinition hidden = syntheticBlock(
                "visibility.hidden", category.id(), BlockLibraryVisibility.HIDDEN,
                List.of(BlockCapability.PREDICATE), List.of(), List.of()
        );
        BlockCatalog catalog = new BlockCatalog(List.of(pack), List.of(category), List.of(hidden, contextOnly, browse));
        BlockCatalog unclassifiedHidden = new BlockCatalog(
                List.of(pack), List.of(category),
                List.of(browse, syntheticBlock("visibility.internal", "", BlockLibraryVisibility.HIDDEN))
        );
        require(unclassifiedHidden.blocks().stream().anyMatch(block -> block.id().equals("visibility.internal")),
                "a never-visible internal block may remain outside the library taxonomy");

        List<String> ordinary = catalog.blocks().stream()
                .filter(block -> !block.deprecated() && block.visibility() == BlockLibraryVisibility.BROWSE)
                .map(BlockDefinition::id)
                .toList();
        List<String> predicateContext = catalog.blocks().stream()
                .filter(block -> !block.deprecated() && block.visibility() != BlockLibraryVisibility.HIDDEN)
                .filter(block -> block.capabilities().contains(BlockCapability.PREDICATE))
                .map(BlockDefinition::id)
                .toList();
        require(ordinary.equals(List.of("visibility.browse")),
                "ordinary browsing should exclude context-only and hidden blocks");
        require(predicateContext.equals(List.of("visibility.browse", "visibility.context")),
                "compatible context should reach browse and context-only predicates but never hidden blocks");
        require(!browse.hidden() && contextOnly.hidden() && hidden.hidden(),
                "legacy hidden metadata should hide every non-browse block from old clients");
    }

    private static void verifyStableSorting() {
        BlockPackDefinition zPack = pack("z-pack", 10);
        BlockPackDefinition aPack = pack("a-pack", 10);
        BlockCategoryDefinition zCategory = category("z-pack.category", zPack.id(), 10);
        BlockCategoryDefinition aCategory = category("a-pack.category", aPack.id(), 10);
        BlockCatalog catalog = new BlockCatalog(
                List.of(zPack, aPack),
                List.of(zCategory, aCategory),
                List.of(
                        syntheticBlock("z-pack.second", zCategory.id(), BlockLibraryVisibility.BROWSE),
                        syntheticBlock("a-pack.second", aCategory.id(), BlockLibraryVisibility.BROWSE),
                        syntheticBlock("a-pack.first", aCategory.id(), BlockLibraryVisibility.BROWSE)
                )
        );
        require(catalog.packs().stream().map(BlockPackDefinition::id).toList().equals(List.of("a-pack", "z-pack")),
                "equal-order packs should use id as stable tie-breaker");
        require(catalog.categories().stream().map(BlockCategoryDefinition::id).toList()
                        .equals(List.of("a-pack.category", "z-pack.category")),
                "equal-order categories should use id as stable tie-breaker");
        require(catalog.blocks().stream().map(BlockDefinition::id).toList()
                        .equals(List.of("a-pack.first", "a-pack.second", "z-pack.second")),
                "blocks should use category and block ids as stable tie-breakers");
    }

    private static void verifyInvalidCatalogsFailFast() {
        BlockPackDefinition pack = pack("valid", 10);
        BlockCategoryDefinition category = category("valid.category", pack.id(), 10);
        BlockDefinition block = syntheticBlock("valid.block", category.id(), BlockLibraryVisibility.BROWSE);

        expectInvalid("duplicate pack id", () -> new BlockCatalog(List.of(pack, pack), List.of(), List.of()));
        expectInvalid("duplicate category id", () -> new BlockCatalog(
                List.of(pack), List.of(category, category), List.of(block)
        ));
        expectInvalid("unknown category packId", () -> new BlockCatalog(
                List.of(pack), List.of(category("orphan.category", "missing", 10)), List.of()
        ));
        expectInvalid("unknown block categoryId", () -> new BlockCatalog(
                List.of(pack), List.of(category),
                List.of(syntheticBlock("orphan.block", "missing.category", BlockLibraryVisibility.BROWSE))
        ));
        expectInvalid("block categoryId: unclassified.block", () -> new BlockCatalog(
                List.of(pack), List.of(category),
                List.of(syntheticBlock("unclassified.block", "", BlockLibraryVisibility.BROWSE))
        ));
        expectInvalid("duplicate block id", () -> new BlockCatalog(
                List.of(pack), List.of(category), List.of(block, block)
        ));
        expectInvalid("duplicate block aliases", () -> new BlockCatalog(
                List.of(pack), List.of(category),
                List.of(syntheticBlock("alias.block", category.id(), BlockLibraryVisibility.BROWSE,
                        List.of(), List.of("Delay", "delay"), List.of()))
        ));
        expectInvalid("duplicate block alias", () -> new BlockCatalog(
                List.of(pack), List.of(category),
                List.of(
                        syntheticBlock("alias.first", category.id(), BlockLibraryVisibility.BROWSE,
                                List.of(), List.of("Delay"), List.of()),
                        syntheticBlock("alias.second", category.id(), BlockLibraryVisibility.BROWSE,
                                List.of(), List.of("delay"), List.of())
                )
        ));
        expectInvalid("duplicate block searchKeywords", () -> new BlockCatalog(
                List.of(pack), List.of(category),
                List.of(syntheticBlock("keyword.block", category.id(), BlockLibraryVisibility.BROWSE,
                        List.of(), List.of(), List.of("State", "state")))
        ));
        expectInvalid("must not be blank", () -> new BlockCatalog(
                List.of(pack), List.of(category),
                List.of(syntheticBlock("blank-term.block", category.id(), BlockLibraryVisibility.BROWSE,
                        List.of(), List.of(" "), List.of()))
        ));
        expectInvalid("block alias collides with block id", () -> new BlockCatalog(
                List.of(pack), List.of(category),
                List.of(
                        syntheticBlock("alias.target", category.id(), BlockLibraryVisibility.BROWSE),
                        syntheticBlock("alias.source", category.id(), BlockLibraryVisibility.BROWSE,
                                List.of(), List.of("alias.target"), List.of())
                )
        ));
        expectInvalid("context-only block has no supported library context", () -> new BlockCatalog(
                List.of(pack), List.of(category),
                List.of(syntheticBlock("context.unreachable", category.id(), BlockLibraryVisibility.CONTEXT_ONLY))
        ));
        expectInvalid("empty visible category", () -> new BlockCatalog(
                List.of(pack), List.of(category),
                List.of(syntheticBlock("hidden.block", category.id(), BlockLibraryVisibility.HIDDEN))
        ));
        BlockCategoryDefinition emptyCategory = category("valid.empty", pack.id(), 20);
        expectInvalid("empty visible category", () -> new BlockCatalog(
                List.of(pack), List.of(category, emptyCategory), List.of(block)
        ));
        BlockPackDefinition emptyPack = pack("empty", 20);
        expectInvalid("empty visible pack", () -> new BlockCatalog(
                List.of(pack, emptyPack), List.of(category), List.of(block)
        ));
    }

    private static BlockDefinition block(String blockId) {
        return BuiltInBlockCatalog.block(blockId).orElseThrow();
    }

    private static BlockPackDefinition pack(String id, int order) {
        return new BlockPackDefinition(id, id, "Synthetic pack " + id, "#", order);
    }

    private static BlockCategoryDefinition category(String id, String packId, int order) {
        return new BlockCategoryDefinition(id, packId, id, "Synthetic category " + id, "#", order, true);
    }

    private static BlockDefinition syntheticBlock(String id, String categoryId, BlockLibraryVisibility visibility) {
        return syntheticBlock(id, categoryId, visibility, List.of(), List.of(), List.of());
    }

    private static BlockDefinition syntheticBlock(
            String id,
            String categoryId,
            BlockLibraryVisibility visibility,
            List<BlockCapability> capabilities,
            List<String> aliases,
            List<String> searchKeywords
    ) {
        return new BlockDefinition(
                id,
                1,
                id,
                "Synthetic block " + id,
                categoryId,
                categoryId,
                List.of(),
                aliases,
                searchKeywords,
                capabilities,
                "action",
                NodeType.DEBUG_LOG_ACTION,
                Map.of(),
                List.of(),
                "Synthetic summary",
                "",
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                BlockCapabilityLevel.FULLY_SIMULATABLE,
                BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                List.of(BlockSafetyFlag.READ_ONLY),
                false,
                visibility != BlockLibraryVisibility.BROWSE,
                visibility,
                null
        );
    }

    private static <T> boolean isSorted(List<T> values, Comparator<T> comparator) {
        for (int index = 1; index < values.size(); index += 1) {
            if (comparator.compare(values.get(index - 1), values.get(index)) > 0) {
                return false;
            }
        }
        return true;
    }

    private static void expectInvalid(String message, Runnable action) {
        try {
            action.run();
            throw new IllegalStateException("expected invalid catalog: " + message);
        } catch (IllegalArgumentException exception) {
            require(exception.getMessage().contains(message),
                    "unexpected invalid catalog message: " + exception.getMessage());
        }
    }
}
