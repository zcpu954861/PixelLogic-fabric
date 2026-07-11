import {
  blockMetrics,
  branchForNode,
  connectedGraphEdges,
  fallbackPosition,
} from '../../model/graphLayout';
import { conditionRackFrame } from '../../model/containerGeometry';
import { catalogBlock, catalogPackForBlock } from '../../model/blockCatalog';
import { conditionRackParent, conditionSlotMember, conditionSlots, hasPredicateRack } from '../../model/conditionRack';
import type { BlockCatalog, BlockMetrics, GraphDocument, SlotBlock, SlotJoin } from '../../model/graphTypes';
import { nodeCategoryLabel, nodeSummary, blockKind, predicateNodeSummary } from '../humanize/labels';
import { normalBlockHeight } from './blockConstants';

export function buildBlocks(graph: GraphDocument, catalog: BlockCatalog, selectedNodeId: string): SlotBlock[] {
  const metricsCache = new Map<string, BlockMetrics>();
  const occupiedContainers = new Set(
    graph.nodes
      .filter((nodeItem) => (nodeItem.parentSlot || 'body') === 'body')
      .map((nodeItem) => nodeItem.parentContainerId)
      .filter(Boolean),
  );
  return graph.nodes.filter((nodeItem) => !conditionRackParent(graph, nodeItem)).map((nodeItem) => {
    const kind = blockKind(nodeItem.type);
    const blockItem = catalogBlock(catalog, nodeItem.blockId ?? '');
    const packId = blockItem ? catalogPackForBlock(catalog, blockItem)?.id : undefined;
    const position = nodeItem.position ?? fallbackPosition(nodeItem.id);
    const size = blockMetrics(graph, nodeItem, metricsCache);
    const visualX = position.x + size.visualBounds.x;
    const visualY = position.y + size.visualBounds.y;
    const bodyOffsetY = -size.visualBounds.y;
    const rack = hasPredicateRack(nodeItem, catalog)
      ? conditionRackFrame(size.width, size.height, conditionSlots(nodeItem))
      : null;
    return {
      id: nodeItem.id,
      kind,
      packId,
      branch: branchForNode(graph, nodeItem),
      type: nodeCategoryLabel(nodeItem, catalog),
      title: nodeItem.displayName || nodeItem.id,
      summary: nodeSummary(nodeItem, catalog),
      x: visualX,
      y: visualY,
      width: size.visualBounds.width,
      height: size.visualBounds.height,
      inputY: size.inputY === null ? null : size.inputY + bodyOffsetY,
      outputOffsets: Object.fromEntries(
        Object.entries(size.outputOffsets).map(([slotId, offset]) => [slotId, offset + bodyOffsetY]),
      ),
      bodyOffsetY,
      conditionRack: rack ? {
        rows: rack.rows.map((row) => {
          const member = conditionSlotMember(graph, nodeItem.id, row.slotId);
          const slot = conditionSlots(nodeItem).find((item) => item.slotId === row.slotId);
          return {
            slotId: row.slotId,
            negated: Boolean(slot?.negated),
            index: row.index,
            x: position.x + row.rowRect.x - visualX,
            y: position.y + row.rowRect.y - visualY,
            width: row.rowRect.width,
            height: row.rowRect.height,
            slotRect: {
              x: row.capsuleRect.x - row.rowRect.x,
              y: row.capsuleRect.y - row.rowRect.y,
              width: row.capsuleRect.width,
              height: row.capsuleRect.height,
            },
            toggleRect: {
              x: row.toggleRect.x - row.rowRect.x,
              y: row.toggleRect.y - row.rowRect.y,
              width: row.toggleRect.width,
              height: row.toggleRect.height,
            },
            capsule: member ? {
              id: member.id,
              kind: 'condition',
              branch: 'main',
              type: '结束条件',
              title: predicateNodeSummary(member, catalog, Boolean(slot?.negated)),
              summary: '',
              x: position.x + row.capsuleRect.x,
              y: position.y + row.capsuleRect.y,
              width: row.capsuleRect.width,
              height: row.capsuleRect.height,
              inputY: null,
              outputOffsets: {},
              presentation: 'predicate-capsule',
              embeddedParentId: nodeItem.id,
              selected: member.id === selectedNodeId,
            } : undefined,
          };
        }),
      } : undefined,
      selected: nodeItem.id === selectedNodeId,
      hasChildren: occupiedContainers.has(nodeItem.id),
    };
  });
}

export function buildJoins(graph: GraphDocument, blocks: SlotBlock[]): SlotJoin[] {
  const blockById = new Map(blocks.map((block) => [block.id, block]));
  return connectedGraphEdges(graph)
    .map((graphEdge): SlotJoin | null => {
      const source = blockById.get(graphEdge.sourceNodeId);
      const target = blockById.get(graphEdge.targetNodeId);
      if (!source || !target) {
        return null;
      }
      const tone = graphEdge.sourceSlotId === 'pass' ? 'pass' : graphEdge.sourceSlotId === 'fail' ? 'fail' : 'normal';
      return {
        id: graphEdge.id,
        from: source.id,
        to: target.id,
        branch: tone === 'fail' ? 'fail' : tone === 'pass' ? 'pass' : target.branch,
        x: target.x - 2,
        y: target.y + (target.inputY ?? normalBlockHeight / 2) - 15,
        width: 20,
        tone,
      };
    })
    .filter((join): join is SlotJoin => join !== null);
}

export type CanvasContentBounds = {
  minLeft: number;
  minTop: number;
  maxRight: number;
  maxBottom: number;
  width: number;
  height: number;
};

export function canvasContentBounds(blocks: SlotBlock[]): CanvasContentBounds {
  const visual = visualBlocks(blocks);
  if (visual.length === 0) {
    return { minLeft: 0, minTop: 0, maxRight: 0, maxBottom: 0, width: 0, height: 0 };
  }
  const minLeft = visual.reduce((left, block) => Math.min(left, block.x), Number.POSITIVE_INFINITY);
  const minTop = visual.reduce((top, block) => Math.min(top, block.y), Number.POSITIVE_INFINITY);
  const maxRight = visual.reduce((right, block) => Math.max(right, block.x + block.width), Number.NEGATIVE_INFINITY);
  const maxBottom = visual.reduce((bottom, block) => Math.max(bottom, block.y + block.height), Number.NEGATIVE_INFINITY);
  return {
    minLeft,
    minTop,
    maxRight,
    maxBottom,
    width: maxRight - minLeft,
    height: maxBottom - minTop,
  };
}

export function updateWorldSize(
  blocks: SlotBlock[],
  world: {
    width: number;
    height: number;
    minLeft: number;
    minTop: number;
    maxRight: number;
    maxBottom: number;
    contentWidth: number;
    contentHeight: number;
  },
): void {
  const bounds = canvasContentBounds(blocks);
  world.minLeft = bounds.minLeft;
  world.minTop = bounds.minTop;
  world.maxRight = bounds.maxRight;
  world.maxBottom = bounds.maxBottom;
  world.contentWidth = bounds.width;
  world.contentHeight = bounds.height;
  world.width = Math.max(2160, Math.ceil(bounds.width + 520));
  world.height = Math.max(620, Math.ceil(bounds.height + 240));
}

export function visualBlocks(blocks: SlotBlock[]): SlotBlock[] {
  return blocks.flatMap((block) => [
    block,
    ...(block.conditionRack?.rows.flatMap((row) => row.capsule ? [row.capsule] : []) ?? []),
  ]);
}

export function visualBlockById(blocks: SlotBlock[], nodeId: string): SlotBlock | null {
  return visualBlocks(blocks).find((block) => block.id === nodeId) ?? null;
}
