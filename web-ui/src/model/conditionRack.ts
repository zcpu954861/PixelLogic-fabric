import { catalogBlock } from './blockCatalog';
import type { BlockCatalog, ConditionSlotDefinition, GraphDocument, GraphNode } from './graphTypes';

export const predicateCapability = 'PREDICATE';
export const predicateRackCapability = 'PREDICATE_RACK';

export function conditionSlots(nodeItem: GraphNode): ConditionSlotDefinition[] {
  return nodeItem.conditionSlots ?? [];
}

export function hasBlockCapability(nodeItem: GraphNode, catalog: BlockCatalog, capability: string): boolean {
  const blockItem = catalogBlock(catalog, nodeItem.blockId ?? '')
    ?? catalog.blocks.find((item) => item.nodeType === nodeItem.type);
  return Boolean(blockItem?.capabilities?.includes(capability));
}

export function isPredicateNode(nodeItem: GraphNode, catalog: BlockCatalog): boolean {
  return hasBlockCapability(nodeItem, catalog, predicateCapability);
}

export function hasPredicateRack(nodeItem: GraphNode, catalog?: BlockCatalog): boolean {
  return catalog
    ? hasBlockCapability(nodeItem, catalog, predicateRackCapability)
    : nodeItem.blockId === 'control.loop.until' || nodeItem.type === 'CONTROL_LOOP_UNTIL';
}

export function conditionSlotMember(
  graph: GraphDocument,
  containerNodeId: string,
  slotId: string,
): GraphNode | null {
  return graph.nodes.find((nodeItem) =>
    nodeItem.parentContainerId === containerNodeId && nodeItem.parentSlot === slotId,
  ) ?? null;
}

export function conditionRackParent(graph: GraphDocument, nodeItem: GraphNode): GraphNode | null {
  if (!nodeItem.parentContainerId || !nodeItem.parentSlot) {
    return null;
  }
  const parent = graph.nodes.find((item) => item.id === nodeItem.parentContainerId);
  return parent && conditionSlots(parent).some((slot) => slot.slotId === nodeItem.parentSlot) ? parent : null;
}

export function nextConditionSlotId(nodeItem: GraphNode): string {
  const used = new Set(conditionSlots(nodeItem).map((slot) => slot.slotId));
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    let candidate = `condition-${crypto.randomUUID()}`;
    while (used.has(candidate)) {
      candidate = `condition-${crypto.randomUUID()}`;
    }
    return candidate;
  }
  let index = used.size + 1;
  while (used.has(`condition-${index}`)) {
    index += 1;
  }
  return `condition-${index}`;
}
