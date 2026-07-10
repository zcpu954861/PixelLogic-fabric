import { conditionSlotMember, conditionSlots, hasPredicateRack, isPredicateNode } from '../../model/conditionRack';
import { conditionSlotRects } from '../../model/graphLayout';
import type { BlockCatalog, BlockDrag, CatalogBlock, GraphDocument, GraphNode, GraphPosition, InsertCandidate } from '../../model/graphTypes';

type ConditionSlotHit = {
  container: GraphNode;
  slotId: string;
  row: { x: number; y: number; width: number; height: number };
  capsule: { x: number; y: number; width: number; height: number };
};

export function findConditionSlotCandidate(
  graph: GraphDocument,
  drag: BlockDrag,
  point: GraphPosition,
  catalog?: BlockCatalog,
): Extract<InsertCandidate, { kind: 'condition-slot' }> | null {
  const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  if (!root || !catalog || !isPredicateNode(root, catalog)) {
    return null;
  }
  const hit = conditionSlotAtPoint(graph, point, catalog, root.id);
  if (!hit) {
    return null;
  }
  return {
    kind: 'condition-slot',
    containerNodeId: hit.container.id,
    slotId: hit.slotId,
    join: {
      id: `condition-slot:${hit.container.id}:${hit.slotId}`,
      from: hit.container.id,
      to: root.id,
      branch: 'main',
      x: hit.row.x,
      y: hit.row.y,
      width: hit.row.width,
      tone: 'normal',
    },
    valid: true,
    message: '松手即可放入结束条件槽。',
  };
}

export function catalogConditionSlotAtPoint(
  graph: GraphDocument,
  point: GraphPosition,
  blockItem: CatalogBlock,
  catalog: BlockCatalog,
): ConditionSlotHit | null {
  return blockItem.capabilities?.includes('PREDICATE')
    ? conditionSlotAtPoint(graph, point, catalog, '')
    : null;
}

export function snapConditionSlotDrag(graph: GraphDocument, drag: BlockDrag, candidate: Extract<InsertCandidate, { kind: 'condition-slot' }>): void {
  const container = graph.nodes.find((nodeItem) => nodeItem.id === candidate.containerNodeId);
  const rects = container ? conditionSlotRects(graph, container, candidate.slotId) : null;
  if (rects) {
    drag.previewPositions.set(drag.rootId, { x: rects.capsule.x, y: rects.capsule.y });
  }
}

export function applyConditionSlotDrop(
  graph: GraphDocument,
  drag: BlockDrag,
  candidate: Extract<InsertCandidate, { kind: 'condition-slot' }>,
): boolean {
  const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const container = graph.nodes.find((nodeItem) => nodeItem.id === candidate.containerNodeId);
  const rects = container ? conditionSlotRects(graph, container, candidate.slotId) : null;
  if (!root || !container || !rects) {
    return false;
  }
  const occupied = conditionSlotMember(graph, container.id, candidate.slotId);
  if (occupied && occupied.id !== root.id) {
    return false;
  }
  root.parentContainerId = container.id;
  root.parentSlot = candidate.slotId;
  root.position = { x: rects.capsule.x, y: rects.capsule.y };
  graph.edges = graph.edges.filter((graphEdge) =>
    graphEdge.sourceNodeId !== root.id && graphEdge.targetNodeId !== root.id,
  );
  return true;
}

export function placeCatalogNodeInConditionSlot(graph: GraphDocument, nodeItem: GraphNode, hit: ConditionSlotHit): void {
  nodeItem.parentContainerId = hit.container.id;
  nodeItem.parentSlot = hit.slotId;
  nodeItem.position = { x: hit.capsule.x, y: hit.capsule.y };
}

function conditionSlotAtPoint(
  graph: GraphDocument,
  point: GraphPosition,
  catalog: BlockCatalog,
  allowedOccupiedNodeId: string,
): ConditionSlotHit | null {
  for (const container of graph.nodes.filter((nodeItem) => hasPredicateRack(nodeItem, catalog))) {
    for (const slot of conditionSlots(container)) {
      const rects = conditionSlotRects(graph, container, slot.slotId);
      if (!rects || !pointInside(point, rects.row)) {
        continue;
      }
      const occupied = conditionSlotMember(graph, container.id, slot.slotId);
      if (occupied && occupied.id !== allowedOccupiedNodeId) {
        return null;
      }
      return { container, slotId: slot.slotId, ...rects };
    }
  }
  return null;
}

function pointInside(point: GraphPosition, rect: { x: number; y: number; width: number; height: number }): boolean {
  return point.x >= rect.x && point.x <= rect.x + rect.width
    && point.y >= rect.y && point.y <= rect.y + rect.height;
}
