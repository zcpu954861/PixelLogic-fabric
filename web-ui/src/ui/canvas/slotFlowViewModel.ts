import {
  blockMetrics,
  branchForNode,
  connectedGraphEdges,
  fallbackPosition,
} from '../../model/graphLayout';
import type { BlockCatalog, BlockMetrics, GraphDocument, SlotBlock, SlotJoin } from '../../model/graphTypes';
import { nodeCategoryLabel, nodeSummary, blockKind } from '../humanize/labels';
import { normalBlockHeight } from './blockConstants';

export function buildBlocks(graph: GraphDocument, catalog: BlockCatalog, selectedNodeId: string): SlotBlock[] {
  const metricsCache = new Map<string, BlockMetrics>();
  return graph.nodes.map((nodeItem) => {
    const kind = blockKind(nodeItem.type);
    const position = nodeItem.position ?? fallbackPosition(nodeItem.id);
    const size = blockMetrics(graph, nodeItem, metricsCache);
    return {
      id: nodeItem.id,
      kind,
      branch: branchForNode(graph, nodeItem),
      type: nodeCategoryLabel(nodeItem, catalog),
      title: nodeItem.displayName || nodeItem.id,
      summary: nodeSummary(nodeItem, catalog),
      x: position.x,
      y: position.y,
      width: size.width,
      height: size.height,
      inputY: size.inputY,
      outputOffsets: size.outputOffsets,
      selected: nodeItem.id === selectedNodeId,
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

export function updateWorldSize(blocks: SlotBlock[], world: { width: number; height: number }): void {
  const maxRight = blocks.reduce((right, block) => Math.max(right, block.x + block.width), 0);
  const maxBottom = blocks.reduce((bottom, block) => Math.max(bottom, block.y + block.height), 0);
  world.width = Math.max(2160, Math.ceil(maxRight + 260));
  world.height = Math.max(620, Math.ceil(maxBottom + 120));
}
