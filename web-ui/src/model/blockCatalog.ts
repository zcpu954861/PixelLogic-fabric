import type { BlockCatalog, BlockKind, CatalogBlock, CatalogCategory, GraphNode, GraphPosition } from './graphTypes';

const category = (id: string, displayName: string, description: string, order: number): CatalogCategory => ({
  id,
  displayName,
  description,
  order,
  visibleByDefault: true,
});

export const fallbackCatalog: BlockCatalog = {
  categories: [
    category('offline', '积木库暂不可用', 'API 未连接，启动后会从后端加载完整积木目录。', 10),
  ],
  subcategories: [],
  blocks: [],
};

export function catalogCategories(catalog: BlockCatalog): CatalogCategory[] {
  return catalog.categories
    .filter((categoryItem) => categoryItem.visibleByDefault)
    .slice()
    .sort((left, right) => left.order - right.order || left.displayName.localeCompare(right.displayName, 'zh-CN'));
}

export function catalogBlocksForCategory(catalog: BlockCatalog, categoryId: string): CatalogBlock[] {
  const subcategoryOrder = new Map(catalog.subcategories.map((item) => [item.id, item.order]));
  return catalog.blocks
    .filter((blockItem) => !blockItem.hidden && !blockItem.deprecated && blockItem.categoryId === categoryId)
    .slice()
    .sort((left, right) => (subcategoryOrder.get(left.subcategoryId) ?? 0) - (subcategoryOrder.get(right.subcategoryId) ?? 0)
      || left.displayName.localeCompare(right.displayName, 'zh-CN'));
}

export function catalogBlock(catalog: BlockCatalog, blockId: string): CatalogBlock | null {
  return catalog.blocks.find((blockItem) => blockItem.id === blockId || blockItem.aliases.includes(blockId)) ?? null;
}

export function catalogCategory(catalog: BlockCatalog, categoryId: string): CatalogCategory | null {
  return catalog.categories.find((categoryItem) => categoryItem.id === categoryId) ?? null;
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

function isBlockKind(value: string): value is BlockKind {
  return ['trigger', 'condition', 'action', 'state', 'timer', 'debug', 'control'].includes(value);
}

function blockKindFromNodeType(type: string): BlockKind {
  if (type.includes('TRIGGER')) {
    return 'trigger';
  }
  if (type.includes('CONDITION')) {
    return 'condition';
  }
  if (type === 'STATE_SET_ACTION' || type === 'STATE_ADD_ACTION') {
    return 'state';
  }
  if (type === 'TIMER_START_ACTION') {
    return 'timer';
  }
  if (type === 'DEBUG_LOG_ACTION') {
    return 'debug';
  }
  if (type.startsWith('CONTROL_LOOP_') || type === 'CONTEXT_ENTITY_EXECUTE_AS') {
    return 'control';
  }
  return 'action';
}
