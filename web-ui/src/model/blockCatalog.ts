import type {
  BlockCatalog,
  BlockKind,
  CatalogBlock,
  CatalogCategory,
  CatalogPack,
  GraphNode,
  GraphPosition,
  LibraryFilter,
  LibraryLocation,
} from './graphTypes';

type SearchDocument = {
  block: CatalogBlock;
  pack: CatalogPack;
  category: CatalogCategory;
  text: string;
  name: string;
  aliases: string[];
};

export type CatalogIndex = {
  packById: ReadonlyMap<string, CatalogPack>;
  categoryById: ReadonlyMap<string, CatalogCategory>;
  categoriesByPackId: ReadonlyMap<string, CatalogCategory[]>;
  blocksByCategoryId: ReadonlyMap<string, CatalogBlock[]>;
  blockById: ReadonlyMap<string, CatalogBlock>;
  searchDocumentByBlockId: ReadonlyMap<string, SearchDocument>;
  packs: CatalogPack[];
};

export type CatalogSearchResult = {
  block: CatalogBlock;
  pack: CatalogPack;
  category: CatalogCategory;
  path: string;
};

const indexCache = new WeakMap<BlockCatalog, CatalogIndex>();

export const fallbackCatalog: BlockCatalog = { packs: [], categories: [], subcategories: [], blocks: [] };

export function catalogIndex(catalog: BlockCatalog): CatalogIndex {
  const cached = indexCache.get(catalog);
  if (cached) {
    return cached;
  }
  const packs = [...(catalog.packs ?? [])].sort(orderThenId);
  const categories = [...(catalog.categories ?? [])].sort(orderThenId);
  const packById = new Map(packs.map((item) => [item.id, item]));
  const categoryById = new Map(categories.map((item) => [item.id, item]));
  const categoriesByPackId = groupBy(categories, (item) => item.packId);
  const blocks = [...(catalog.blocks ?? [])].sort((left, right) => {
    const leftCategory = categoryById.get(left.categoryId);
    const rightCategory = categoryById.get(right.categoryId);
    return (packById.get(leftCategory?.packId ?? '')?.order ?? 0) - (packById.get(rightCategory?.packId ?? '')?.order ?? 0)
      || (leftCategory?.packId ?? '').localeCompare(rightCategory?.packId ?? '')
      || (leftCategory?.order ?? 0) - (rightCategory?.order ?? 0)
      || (leftCategory?.id ?? '').localeCompare(rightCategory?.id ?? '')
      || left.id.localeCompare(right.id);
  });
  const blocksByCategoryId = groupBy(blocks, (item) => item.categoryId);
  const blockById = new Map(blocks.map((item) => [item.id, item]));
  const searchDocumentByBlockId = new Map<string, SearchDocument>();
  for (const block of blocks) {
    const category = categoryById.get(block.categoryId);
    const pack = category ? packById.get(category.packId) : null;
    if (!category || !pack) {
      continue;
    }
    const aliases = (block.aliases ?? []).map(normalizeSearchText);
    searchDocumentByBlockId.set(block.id, {
      block,
      pack,
      category,
      name: normalizeSearchText(block.displayName),
      aliases,
      text: normalizeSearchText([
        block.displayName,
        block.description,
        block.id,
        ...(block.aliases ?? []),
        ...(block.searchKeywords ?? []),
        category.displayName,
        pack.displayName,
      ].join(' ')),
    });
  }
  const index = { packById, categoryById, categoriesByPackId, blocksByCategoryId, blockById, searchDocumentByBlockId, packs };
  indexCache.set(catalog, index);
  return index;
}

export function catalogPacks(catalog: BlockCatalog, filter: LibraryFilter | null = null): Array<CatalogPack & { count: number }> {
  const index = catalogIndex(catalog);
  const counts = visibleCounts(index, filter);
  return index.packs.flatMap((pack) => counts.pack.get(pack.id) ? [{ ...pack, count: counts.pack.get(pack.id) ?? 0 }] : []);
}

export function catalogCategoriesForPack(
  catalog: BlockCatalog,
  packId: string,
  filter: LibraryFilter | null = null,
): Array<CatalogCategory & { count: number }> {
  const index = catalogIndex(catalog);
  const counts = visibleCounts(index, filter);
  return (index.categoriesByPackId.get(packId) ?? [])
    .flatMap((category) => counts.category.get(category.id) ? [{ ...category, count: counts.category.get(category.id) ?? 0 }] : []);
}

export function catalogBlocksForCategory(
  catalog: BlockCatalog,
  categoryId: string,
  filter: LibraryFilter | null = null,
): CatalogBlock[] {
  const index = catalogIndex(catalog);
  return (index.blocksByCategoryId.get(categoryId) ?? []).filter((block) => visibleInLibrary(block, filter));
}

export function searchCatalog(
  catalog: BlockCatalog,
  query: string,
  filter: LibraryFilter | null = null,
): CatalogSearchResult[] {
  const normalized = normalizeSearchText(query);
  if (!normalized) {
    return [];
  }
  const terms = normalized.split(' ');
  return [...catalogIndex(catalog).searchDocumentByBlockId.values()]
    .filter((document) => visibleInLibrary(document.block, filter) && terms.every((term) => document.text.includes(term)))
    .sort((left, right) => searchRank(left, normalized) - searchRank(right, normalized)
      || left.pack.order - right.pack.order
      || left.pack.id.localeCompare(right.pack.id)
      || left.category.order - right.category.order
      || left.category.id.localeCompare(right.category.id)
      || left.block.id.localeCompare(right.block.id))
    .map(({ block, pack, category }) => ({ block, pack, category, path: `${pack.displayName} > ${category.displayName}` }));
}

export function sanitizeLibraryLocation(
  catalog: BlockCatalog,
  location: LibraryLocation,
  filter: LibraryFilter | null = null,
): LibraryLocation {
  if (location.level === 'packs') {
    return location;
  }
  const packs = new Set(catalogPacks(catalog, filter).map((item) => item.id));
  if (!packs.has(location.packId)) {
    return { level: 'packs' };
  }
  if (location.level === 'categories') {
    return location;
  }
  const category = catalogIndex(catalog).categoryById.get(location.categoryId);
  return category?.packId === location.packId && catalogBlocksForCategory(catalog, category.id, filter).length > 0
    ? location
    : { level: 'categories', packId: location.packId };
}

export function catalogBlock(catalog: BlockCatalog, blockId: string): CatalogBlock | null {
  const index = catalogIndex(catalog);
  return index.blockById.get(blockId)
    ?? catalog.blocks.find((blockItem) => (blockItem.aliases ?? []).includes(blockId))
    ?? null;
}

export function catalogCategory(catalog: BlockCatalog, categoryId: string): CatalogCategory | null {
  return catalogIndex(catalog).categoryById.get(categoryId) ?? null;
}

export function catalogPack(catalog: BlockCatalog, packId: string): CatalogPack | null {
  return catalogIndex(catalog).packById.get(packId) ?? null;
}

export function catalogPackForBlock(catalog: BlockCatalog, blockItem: CatalogBlock): CatalogPack | null {
  const category = catalogCategory(catalog, blockItem.categoryId);
  return category ? catalogPack(catalog, category.packId) : null;
}

export function catalogPath(catalog: BlockCatalog, blockItem: CatalogBlock): string {
  const category = catalogCategory(catalog, blockItem.categoryId);
  const pack = category ? catalogPack(catalog, category.packId) : null;
  return pack && category ? `${pack.displayName} > ${category.displayName}` : '';
}

export function blockKindFromCatalogBlock(blockItem: CatalogBlock): BlockKind {
  return isBlockKind(blockItem.nodeKind) ? blockItem.nodeKind : blockKindFromNodeType(blockItem.nodeType);
}

export function createCatalogNode(blockItem: CatalogBlock, id: string, position: GraphPosition): GraphNode {
  return {
    id,
    type: blockItem.nodeType,
    blockId: blockItem.id,
    displayName: blockItem.displayName,
    config: { ...blockItem.defaultConfig },
    conditionSlots: [],
    position,
    parentContainerId: '',
    parentSlot: '',
    slots: [...blockItem.inputSlots, ...blockItem.outputSlots].map((slot) => ({ ...slot })),
  };
}

export function catalogNodeIdPrefix(blockItem: CatalogBlock): string {
  return blockItem.id.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '') || 'block';
}

function visibleCounts(index: CatalogIndex, filter: LibraryFilter | null): { pack: Map<string, number>; category: Map<string, number> } {
  const pack = new Map<string, number>();
  const category = new Map<string, number>();
  for (const block of index.blockById.values()) {
    const categoryItem = index.categoryById.get(block.categoryId);
    if (!categoryItem || !visibleInLibrary(block, filter)) {
      continue;
    }
    category.set(categoryItem.id, (category.get(categoryItem.id) ?? 0) + 1);
    pack.set(categoryItem.packId, (pack.get(categoryItem.packId) ?? 0) + 1);
  }
  return { pack, category };
}

function visibleInLibrary(block: CatalogBlock, filter: LibraryFilter | null): boolean {
  if (block.deprecated || block.visibility === 'HIDDEN') {
    return false;
  }
  if (!filter) {
    return block.visibility === 'BROWSE';
  }
  return block.capabilities?.includes(filter.capability) ?? false;
}

function searchRank(document: SearchDocument, query: string): number {
  if (document.name === query || document.aliases.includes(query)) {
    return 0;
  }
  return document.name.startsWith(query) ? 1 : 2;
}

function normalizeSearchText(value: string): string {
  return value.trim().toLocaleLowerCase('zh-CN').replace(/\s+/g, ' ');
}

function orderThenId<T extends { order: number; id: string }>(left: T, right: T): number {
  return left.order - right.order || left.id.localeCompare(right.id);
}

function groupBy<T>(items: T[], key: (item: T) => string): Map<string, T[]> {
  const result = new Map<string, T[]>();
  for (const item of items) {
    const id = key(item);
    const bucket = result.get(id);
    if (bucket) {
      bucket.push(item);
    } else {
      result.set(id, [item]);
    }
  }
  return result;
}

function isBlockKind(value: string): value is BlockKind {
  return ['trigger', 'condition', 'action', 'state', 'timer', 'debug', 'control'].includes(value);
}

function blockKindFromNodeType(type: string): BlockKind {
  if (type.includes('TRIGGER')) return 'trigger';
  if (type.includes('CONDITION')) return 'condition';
  if (type === 'STATE_SET_ACTION' || type === 'STATE_ADD_ACTION') return 'state';
  if (type === 'TIMER_START_ACTION') return 'timer';
  if (type === 'DEBUG_LOG_ACTION') return 'debug';
  if (type.startsWith('CONTROL_LOOP_') || type === 'CONTEXT_ENTITY_EXECUTE_AS') return 'control';
  return 'action';
}
