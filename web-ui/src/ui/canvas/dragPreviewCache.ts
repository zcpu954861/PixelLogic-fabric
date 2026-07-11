import type { BlockCatalog, BlockDrag, GraphDocument, InsertCandidate, SlotBlock } from '../../model/graphTypes';

export type DragPreviewComputation = {
  placementGraph: GraphDocument;
  baseBlocks: SlotBlock[];
  placementBlocks: SlotBlock[];
  draggedNodeIds: Set<string>;
};

type CacheEntry = {
  graph: GraphDocument;
  catalog: BlockCatalog;
  key: string;
  value: DragPreviewComputation;
};

export class ActiveDragPreviewCache {
  private entry: CacheEntry | null = null;

  getOrCompute(
    graph: GraphDocument,
    graphVersion: number,
    catalog: BlockCatalog,
    selectedNodeId: string,
    drag: BlockDrag,
    compute: () => DragPreviewComputation,
  ): DragPreviewComputation {
    const key = dragPreviewKey(graphVersion, selectedNodeId, drag);
    if (!key) {
      this.clear();
      return compute();
    }
    if (this.entry?.graph === graph && this.entry.catalog === catalog && this.entry.key === key) {
      return this.entry.value;
    }
    const value = compute();
    this.entry = { graph, catalog, key, value };
    return value;
  }

  clear(): void {
    this.entry = null;
  }
}

export function dragPreviewKey(graphVersion: number, selectedNodeId: string, drag: BlockDrag): string | null {
  const candidate = drag.candidate;
  if (!candidate?.valid) {
    return null;
  }
  const rootPreview = drag.previewPositions.get(drag.rootId);
  if (!rootPreview) {
    return null;
  }
  const group = drag.groupIds.map((nodeId) => {
    const start = drag.startPositions.get(nodeId);
    const preview = drag.previewPositions.get(nodeId);
    return [nodeId, start?.x, start?.y, preview ? preview.x - rootPreview.x : null, preview ? preview.y - rootPreview.y : null];
  });
  return JSON.stringify([
    graphVersion,
    selectedNodeId,
    drag.rootId,
    group,
    candidateKey(candidate),
  ]);
}

function candidateKey(candidate: InsertCandidate): unknown[] {
  const join = candidate.join;
  const common = [candidate.kind, candidate.valid, join.id, join.from, join.to, join.branch, join.x, join.y, join.width, join.tone ?? ''];
  if (candidate.kind === 'insert') {
    const edge = candidate.edge;
    return [...common, edge.id, edge.sourceNodeId, edge.sourceSlotId, edge.targetNodeId, edge.targetSlotId, edge.type];
  }
  if (candidate.kind === 'container' || candidate.kind === 'condition-slot') {
    return [...common, candidate.containerNodeId, candidate.slotId];
  }
  if (candidate.kind === 'append') {
    return [...common, candidate.sourceNodeId, candidate.sourceSlotId];
  }
  return [...common, candidate.sourceNodeId, candidate.sourceSlotId, candidate.targetNodeId, candidate.targetSlotId];
}
