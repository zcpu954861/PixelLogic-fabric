import type { BlockCatalog, BlockDrag, BlockMetrics, GraphDocument, GraphEdge, GraphNode, GraphPosition, GraphSlot, InsertCandidate, SlotJoin } from '../../model/graphTypes';
import { edge } from '../../model/demoGraph';
import { blockMetrics, branchForNode, cloneGraph, connectedGraphEdges, containerBodyDropZone, containerBodyEntryAnchor, downstreamNodeIds, fallbackPosition, graphWithNodePositions, inputCenterOffset, nodePosition, outputCenterOffset } from '../../model/graphLayout';
import { normalBlockHeight, normalBlockWidth } from '../../model/containerGeometry';
import { conditionRackParent } from '../../model/conditionRack';
import { isBodyContainerNode } from '../../model/containerNodes';
import { activeOutputSlots } from '../../model/conditionOutputMode';
import { preferredMainOutput } from './activeOutput';
import { connectedOverlap, insertSnapX, insertSnapY, linkSnapX, linkSnapY, reconnectSnapX, reconnectSnapY } from './blockConstants';
import {
  canAssignContainerMembership,
  makeContainerBodyGap,
  makeContainerBodyStartGap,
  shiftForContainerSizeChanges,
  syncDraggedContainerMembership,
} from './containerPlacement';
import { applyConditionSlotDrop, findConditionSlotCandidate, snapConditionSlotDrag } from './conditionRackPlacement';

type DragGraphQueryContext = {
  graph: GraphDocument;
  nodeById: Map<string, GraphNode>;
  edgeById: Map<string, GraphEdge>;
  connectedEdges: GraphEdge[];
  metrics: Map<string, BlockMetrics>;
};

function dragGraphQueryContext(graph: GraphDocument): DragGraphQueryContext {
  return {
    graph,
    nodeById: new Map(graph.nodes.map((nodeItem) => [nodeItem.id, nodeItem])),
    edgeById: new Map(graph.edges.map((edgeItem) => [edgeItem.id, edgeItem])),
    connectedEdges: connectedGraphEdges(graph),
    metrics: new Map(),
  };
}

function metrics(context: DragGraphQueryContext, nodeItem: GraphNode): BlockMetrics {
  return blockMetrics(context.graph, nodeItem, context.metrics);
}

function inputOffset(context: DragGraphQueryContext, nodeItem: GraphNode): number | null {
  return nodeItem.slots.some((slot) => slot.direction === 'INPUT') ? metrics(context, nodeItem).inputY : null;
}

function outputOffset(context: DragGraphQueryContext, nodeItem: GraphNode, slotId: string): number {
  return metrics(context, nodeItem).outputOffsets[slotId] ?? normalBlockHeight / 2;
}

export function connectedActionText(candidate: InsertCandidate | null): string {
  if (candidate?.kind === 'container') {
    return '已放入容器内部，正在自动保存。';
  }
  if (candidate?.kind === 'condition-slot') {
    return '已放入结束条件槽，正在自动保存。';
  }
  if (candidate?.kind === 'append') {
    return '已连接到链尾，正在自动保存。';
  }
  if (candidate?.kind === 'attach') {
    return '已连接到后面的积木，正在自动保存。';
  }
  return '已插入到连接处，正在自动保存。';
}

export function findInsertCandidate(baseGraph: GraphDocument, drag: BlockDrag, catalog?: BlockCatalog): InsertCandidate | null {
  const graph = graphWithPreviewPositions(baseGraph, drag);
  const context = dragGraphQueryContext(graph);
  const rootNode = context.nodeById.get(drag.rootId);
  const rootPosition = drag.previewPositions.get(drag.rootId);
  if (!rootNode || !rootPosition) {
    return null;
  }

  const rootInputY = inputOffset(context, rootNode);
  const rootBounds = metrics(context, rootNode).visualBounds;
  const dragAnchor = {
    x: rootPosition.x + rootBounds.x + rootBounds.width / 2,
    y: rootPosition.y + rootBounds.y + rootBounds.height / 2,
  };
  const rootInputAnchor = rootInputY === null ? null : {
    x: rootPosition.x,
    y: rootPosition.y + rootInputY,
  };
  const group = new Set(drag.groupIds);
  const stableGraph = graphWithDragStartPositions(baseGraph, drag);
  const stableNodeById = new Map(stableGraph.nodes.map((nodeItem) => [nodeItem.id, nodeItem]));
  const stableMetrics = new Map<string, BlockMetrics>();
  const tailAnchor = draggedTailAnchorWithContext(context, drag);
  const stableRoot = stableNodeById.get(drag.rootId);
  const sourceVisualBounds = stableRoot ? blockMetrics(stableGraph, stableRoot, stableMetrics).visualBounds : null;
  const pointer = dragPointerPosition(drag);
  const conditionSlotCandidate = findConditionSlotCandidate(stableGraph, drag, {
    x: pointer.x,
    y: sourceVisualBounds
      ? rootPosition.y + sourceVisualBounds.y + sourceVisualBounds.height / 2
      : pointer.y,
  }, catalog);
  if (conditionSlotCandidate) {
    return conditionSlotCandidate;
  }
  const containerCandidate = findContainerCandidate(
    stableGraph,
    drag,
    dragAnchor,
  );
  if (containerCandidate) {
    if (!containerCandidate.valid) {
      return containerCandidate;
    }
    const containerId = containerCandidate.containerNodeId;
    const internalEdge = closestEdgeCandidate(context, drag, dragAnchor, (edgeItem) => edgeInScope(context, edgeItem, containerId));
    if (internalEdge?.valid) {
      return internalEdge;
    }
    if (rootInputAnchor) {
      const internalAppend = closestCandidate(
        appendCandidatesWithContext(context, drag).filter((candidate) => nodeInScope(context, candidate.sourceNodeId, containerId)),
        (candidate) => candidateSnapScoreWithContext(context, drag, candidate, rootInputAnchor),
      );
      if (internalAppend) {
        return internalAppend;
      }
    }
    if (tailAnchor) {
      const internalAttach = closestCandidate(
        attachCandidatesWithContext(context, drag).filter((candidate) =>
          nodeInScope(context, candidate.targetNodeId, containerId)
          && isContainerBodyHead(context, candidate.targetNodeId, containerId),
        ),
        (candidate) => bodyHeadDropScore(context, candidate, dragAnchor)
          ?? candidateSnapScoreWithContext(context, drag, candidate, tailAnchor),
      );
      if (internalAttach) {
        return internalAttach;
      }
    }
    const hasBodyContent = graph.nodes.some((nodeItem) =>
      !group.has(nodeItem.id)
      && (nodeItem.parentContainerId ?? '') === containerId
      && (nodeItem.parentSlot || 'body') === 'body',
    );
    if (!hasBodyContent) {
      return containerCandidate;
    }
    return internalEdge ?? {
      ...containerCandidate,
      valid: false,
      message: '请靠近容器内部的连接线或链尾再松手。',
    };
  }

  if (tailAnchor) {
    const bodyHeadAttach = closestCandidate(
      attachCandidatesWithContext(context, drag).filter((candidate) => {
        const target = context.nodeById.get(candidate.targetNodeId);
        const containerId = target?.parentContainerId ?? '';
        return Boolean(containerId) && isContainerBodyHead(context, candidate.targetNodeId, containerId);
      }),
      (candidate) => bodyHeadDropScore(context, candidate, dragAnchor)
        ?? candidateSnapScoreWithContext(context, drag, candidate, tailAnchor),
    );
    if (bodyHeadAttach) {
      const target = stableNodeById.get(bodyHeadAttach.targetNodeId);
      const check = canAssignContainerMembership(stableGraph, group, target?.parentContainerId ?? '');
      return check.valid ? bodyHeadAttach : { ...bodyHeadAttach, valid: false, message: check.message };
    }
  }

  const externalEdge = closestEdgeCandidate(context, drag, dragAnchor, (edgeItem) => edgeInScope(context, edgeItem, ''));
  if (externalEdge?.valid) {
    return externalEdge;
  }
  if (rootInputAnchor) {
    const appendCandidate = closestCandidate(
      appendCandidatesWithContext(context, drag).filter((candidate) => nodeInScope(context, candidate.sourceNodeId, '')),
      (candidate) => candidateSnapScoreWithContext(context, drag, candidate, rootInputAnchor),
    );
    if (appendCandidate) {
      return appendCandidate;
    }
  }

  if (tailAnchor) {
    const attachCandidate = closestCandidate(
      attachCandidatesWithContext(context, drag).filter((candidate) => nodeInScope(context, candidate.targetNodeId, '')),
      (candidate) => candidateSnapScoreWithContext(context, drag, candidate, tailAnchor),
    );
    if (attachCandidate) {
      return attachCandidate;
    }
  }

  return externalEdge;
}

function closestEdgeCandidate(
  context: DragGraphQueryContext,
  drag: BlockDrag,
  anchor: GraphPosition,
  include: (edgeItem: GraphEdge) => boolean,
): Extract<InsertCandidate, { kind: 'insert' }> | null {
  const group = new Set(drag.groupIds);
  let valid: { candidate: Extract<InsertCandidate, { kind: 'insert' }>; score: number } | null = null;
  let invalid: { candidate: Extract<InsertCandidate, { kind: 'insert' }>; score: number } | null = null;
  for (const join of drag.joins) {
    const edgeItem = context.edgeById.get(join.id);
    if (!edgeItem || !include(edgeItem)) {
      continue;
    }
    const score = insertSnapScore(context, edgeItem, anchor, join);
    if (score === null) {
      continue;
    }
    const check = canInsertIntoEdgeWithContext(context, edgeItem, drag);
    if (!check.valid && (group.has(edgeItem.sourceNodeId) || group.has(edgeItem.targetNodeId))) {
      continue;
    }
    const scored = {
      candidate: { kind: 'insert', edge: edgeItem, join, valid: check.valid, message: check.message } as const,
      score,
    };
    if (check.valid ? !valid || score < valid.score : !invalid || score < invalid.score) {
      if (check.valid) {
        valid = scored;
      } else {
        invalid = scored;
      }
    }
  }
  return valid?.candidate ?? invalid?.candidate ?? null;
}

function nodeInScope(context: DragGraphQueryContext, nodeId: string, containerId: string): boolean {
  const nodeItem = context.nodeById.get(nodeId);
  return (nodeItem?.parentContainerId ?? '') === containerId
    && (!containerId || (nodeItem?.parentSlot || 'body') === 'body');
}

function edgeInScope(context: DragGraphQueryContext, edgeItem: GraphEdge, containerId: string): boolean {
  return nodeInScope(context, edgeItem.sourceNodeId, containerId)
    && nodeInScope(context, edgeItem.targetNodeId, containerId);
}

function isContainerBodyHead(context: DragGraphQueryContext, nodeId: string, containerId: string): boolean {
  const nodeItem = context.nodeById.get(nodeId);
  if (!nodeItem || nodeItem.parentContainerId !== containerId || (nodeItem.parentSlot || 'body') !== 'body') {
    return false;
  }
  return !context.connectedEdges.some((graphEdge) =>
    graphEdge.targetNodeId === nodeId && nodeInScope(context, graphEdge.sourceNodeId, containerId),
  );
}

function bodyHeadDropScore(
  context: DragGraphQueryContext,
  candidate: Extract<InsertCandidate, { kind: 'attach' }>,
  dragCenter: GraphPosition,
): number | null {
  const target = context.nodeById.get(candidate.targetNodeId);
  const targetInputY = target ? inputOffset(context, target) : null;
  if (!target || targetInputY === null) {
    return null;
  }
  const targetPosition = nodePosition(context.graph, target.id);
  const dx = Math.abs(dragCenter.x - targetPosition.x);
  const dy = Math.abs(dragCenter.y - (targetPosition.y + targetInputY));
  return dx <= normalBlockWidth / 2 + linkSnapX && dy <= normalBlockHeight / 2 + linkSnapY
    ? dx + dy * 1.35
    : null;
}

function closestCandidate<T extends InsertCandidate>(candidates: T[], scoreFor: (candidate: T) => number | null): T | null {
  let closest: { candidate: T; score: number } | null = null;
  for (const candidate of candidates) {
    const score = scoreFor(candidate);
    if (score !== null && (!closest || score < closest.score)) {
      closest = { candidate, score };
    }
  }
  return closest?.candidate ?? null;
}

export function graphWithPreviewPositions(graph: GraphDocument, drag: BlockDrag): GraphDocument {
  return {
    ...graph,
    nodes: graph.nodes.map((nodeItem) => {
      const position = drag.previewPositions.get(nodeItem.id);
      if (!position) {
        return nodeItem;
      }
      return conditionRackParent(graph, nodeItem) && nodeItem.id === drag.rootId
        ? { ...nodeItem, position, parentContainerId: '', parentSlot: '' }
        : { ...nodeItem, position };
    }),
  };
}

function graphWithDragStartPositions(graph: GraphDocument, drag: BlockDrag): GraphDocument {
  return graphWithNodePositions(graph, drag.startPositions);
}

export function snapScore(anchor: GraphPosition, join: SlotJoin): number | null {
  const center = { x: join.x + join.width / 2, y: join.y + 15 };
  const dx = Math.abs(anchor.x - center.x);
  const dy = Math.abs(anchor.y - center.y);
  return dx <= insertSnapX && dy <= insertSnapY ? dx + dy * 1.35 : null;
}

function insertSnapScore(context: DragGraphQueryContext, edgeItem: GraphEdge, anchor: GraphPosition, join: SlotJoin): number | null {
  const source = context.nodeById.get(edgeItem.sourceNodeId);
  const target = context.nodeById.get(edgeItem.targetNodeId);
  if (source && target && edgeTouchesControlBoundary(source, target)) {
    return snapScoreWithin(anchor, join, linkSnapX + 24, linkSnapY + 14);
  }
  const snapY = source && target && nodesShareContainerBody(source, target)
    ? insertSnapY + normalBlockHeight
    : insertSnapY;
  return snapScoreWithin(anchor, join, insertSnapX, snapY);
}

function edgeTouchesControlBoundary(source: GraphNode, target: GraphNode): boolean {
  return isBodyContainerNode(source) || isBodyContainerNode(target);
}

function nodesShareContainerBody(source: GraphNode, target: GraphNode): boolean {
  return Boolean(
    source.parentContainerId
    && source.parentContainerId === target.parentContainerId
    && (source.parentSlot || 'body') === 'body'
    && (target.parentSlot || 'body') === 'body',
  );
}

export function candidateSnapScore(graph: GraphDocument, drag: BlockDrag, candidate: InsertCandidate, anchor: GraphPosition): number | null {
  return candidateSnapScoreWithContext(dragGraphQueryContext(graph), drag, candidate, anchor);
}

function candidateSnapScoreWithContext(context: DragGraphQueryContext, drag: BlockDrag, candidate: InsertCandidate, anchor: GraphPosition): number | null {
  if (candidate.kind === 'condition-slot') {
    return snapScore(anchor, candidate.join);
  }
  if (isReconnectCandidateWithContext(context, drag, candidate)) {
    return snapScoreWithin(anchor, candidate.join, reconnectSnapX, reconnectSnapY);
  }
  if (candidate.kind === 'append' && isContainerBodyAppend(context, candidate)) {
    return snapScoreWithin(anchor, candidate.join, linkSnapX + 72, linkSnapY + 36);
  }
  if (candidate.kind === 'append' || candidate.kind === 'attach') {
    return snapScoreWithin(anchor, candidate.join, linkSnapX, linkSnapY);
  }
  return snapScore(anchor, candidate.join);
}

function isContainerBodyAppend(context: DragGraphQueryContext, candidate: Extract<InsertCandidate, { kind: 'append' }>): boolean {
  const source = context.nodeById.get(candidate.sourceNodeId);
  return Boolean(source?.parentContainerId && (source.parentSlot || 'body') === 'body');
}

export function snapScoreWithin(anchor: GraphPosition, join: SlotJoin, snapX: number, snapY: number): number | null {
  const center = { x: join.x + join.width / 2, y: join.y + 15 };
  const dx = Math.abs(anchor.x - center.x);
  const dy = Math.abs(anchor.y - center.y);
  return dx <= snapX && dy <= snapY ? dx + dy * 1.35 : null;
}

export function isReconnectCandidate(graph: GraphDocument, drag: BlockDrag, candidate: InsertCandidate): boolean {
  return isReconnectCandidateWithContext(dragGraphQueryContext(graph), drag, candidate);
}

function isReconnectCandidateWithContext(context: DragGraphQueryContext, drag: BlockDrag, candidate: InsertCandidate): boolean {
  if (candidate.kind === 'container') {
    return false;
  }
  if (candidate.kind === 'append') {
    const root = context.nodeById.get(drag.rootId);
    const rootInput = root?.slots.find((slot) => slot.direction === 'INPUT');
    return Boolean(rootInput && context.graph.edges.some((graphEdge) =>
      graphEdge.sourceNodeId === candidate.sourceNodeId
      && graphEdge.sourceSlotId === candidate.sourceSlotId
      && graphEdge.targetNodeId === drag.rootId
      && graphEdge.targetSlotId === rootInput.id,
    ));
  }
  if (candidate.kind === 'attach') {
    return context.graph.edges.some((graphEdge) =>
      graphEdge.sourceNodeId === candidate.sourceNodeId
      && graphEdge.sourceSlotId === candidate.sourceSlotId
      && graphEdge.targetNodeId === candidate.targetNodeId
      && graphEdge.targetSlotId === candidate.targetSlotId,
    );
  }
  return false;
}

export function appendCandidates(graph: GraphDocument, drag: BlockDrag): Array<Extract<InsertCandidate, { kind: 'append' }>> {
  return appendCandidatesWithContext(dragGraphQueryContext(graph), drag);
}

function appendCandidatesWithContext(context: DragGraphQueryContext, drag: BlockDrag): Array<Extract<InsertCandidate, { kind: 'append' }>> {
  const graph = context.graph;
  const group = new Set(drag.groupIds);
  const root = context.nodeById.get(drag.rootId);
  const rootInput = root?.slots.find((slot) => slot.direction === 'INPUT');
  if (!root || !rootInput) {
    return [];
  }

  return graph.nodes.flatMap((source) => {
    if (group.has(source.id) || conditionRackParent(graph, source)) {
      return [];
    }
    const sourcePosition = source.position ?? fallbackPosition(source.id);
    const sourceSize = metrics(context, source);
    return activeOutputSlots(source)
      .filter((slot) => slot.edgeType === rootInput.edgeType)
      .filter((slot) => !context.connectedEdges.some((edgeItem) => edgeItem.sourceNodeId === source.id && edgeItem.sourceSlotId === slot.id))
      .map((slot): Extract<InsertCandidate, { kind: 'append' }> => ({
        kind: 'append',
        sourceNodeId: source.id,
        sourceSlotId: slot.id,
        join: {
          id: `append:${source.id}:${slot.id}`,
          from: source.id,
          to: drag.rootId,
          branch: slot.id === 'fail' ? 'fail' : slot.id === 'pass' ? 'pass' : branchForNode(graph, source),
          x: sourcePosition.x + sourceSize.width - connectedOverlap,
          y: sourcePosition.y + outputOffset(context, source, slot.id) - 15,
          width: 20,
          tone: slot.id === 'pass' ? 'pass' : slot.id === 'fail' ? 'fail' : 'normal',
        },
        valid: true,
        message: '松手即可自动吸附到链尾。',
      }));
  });
}

export function draggedTailAnchor(graph: GraphDocument, drag: BlockDrag): GraphPosition | null {
  return draggedTailAnchorWithContext(dragGraphQueryContext(graph), drag);
}

function draggedTailAnchorWithContext(context: DragGraphQueryContext, drag: BlockDrag): GraphPosition | null {
  const tail = draggedTailOutputWithContext(context, new Set(drag.groupIds));
  if (!tail) {
    return null;
  }
  const tailNode = context.nodeById.get(tail.nodeId);
  const tailPosition = drag.previewPositions.get(tail.nodeId);
  if (!tailNode || !tailPosition) {
    return null;
  }
  const tailSize = metrics(context, tailNode);
  return {
    x: tailPosition.x + tailSize.width - connectedOverlap,
    y: tailPosition.y + outputOffset(context, tailNode, tail.slot.id),
  };
}

export function attachCandidates(graph: GraphDocument, drag: BlockDrag): Array<Extract<InsertCandidate, { kind: 'attach' }>> {
  return attachCandidatesWithContext(dragGraphQueryContext(graph), drag);
}

function attachCandidatesWithContext(context: DragGraphQueryContext, drag: BlockDrag): Array<Extract<InsertCandidate, { kind: 'attach' }>> {
  const graph = context.graph;
  const group = new Set(drag.groupIds);
  const tail = draggedTailOutputWithContext(context, group);
  if (!tail) {
    return [];
  }

  return graph.nodes.flatMap((target): Array<Extract<InsertCandidate, { kind: 'attach' }>> => {
    if (group.has(target.id) || conditionRackParent(graph, target)) {
      return [];
    }
    const targetPosition = target.position ?? fallbackPosition(target.id);
    const targetBranch = branchForNode(graph, target);
    return target.slots
      .filter((slot) => slot.direction === 'INPUT')
      .filter((slot) => slot.edgeType === tail.slot.edgeType)
      .filter((slot) => !context.connectedEdges.some((edgeItem) => edgeItem.targetNodeId === target.id && edgeItem.targetSlotId === slot.id))
      .map((slot): Extract<InsertCandidate, { kind: 'attach' }> => ({
        kind: 'attach',
        sourceNodeId: tail.nodeId,
        sourceSlotId: tail.slot.id,
        targetNodeId: target.id,
        targetSlotId: slot.id,
        join: {
          id: `attach:${tail.nodeId}:${tail.slot.id}:${target.id}:${slot.id}`,
          from: tail.nodeId,
          to: target.id,
          branch: targetBranch,
          x: targetPosition.x - 10,
          y: targetPosition.y + (inputOffset(context, target) ?? normalBlockHeight / 2) - 15,
          width: 20,
          tone: targetBranch === 'fail' ? 'fail' : targetBranch === 'pass' ? 'pass' : 'normal',
        },
        valid: true,
        message: '松手即可连接到后面的积木。',
      }));
  });
}

export function snapDraggedGroupToCandidate(graph: GraphDocument, drag: BlockDrag): void {
  const candidate = drag.candidate;
  if (!candidate?.valid) {
    return;
  }
  if (candidate?.kind === 'container') {
    const container = graph.nodes.find((nodeItem) => nodeItem.id === candidate.containerNodeId);
    const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
    const rootPosition = drag.previewPositions.get(drag.rootId);
    const rootInputY = root ? inputCenterOffset(graph, root) : null;
    if (!container || !rootPosition || rootInputY === null) {
      return;
    }
    const entryAnchor = containerBodyEntryAnchor(graph, container);
    const dx = Math.round(entryAnchor.x) - rootPosition.x;
    const dy = Math.round(entryAnchor.y - rootInputY) - rootPosition.y;
    drag.previewPositions = new Map(
      Array.from(drag.previewPositions, ([nodeId, position]) => [nodeId, { x: position.x + dx, y: position.y + dy }]),
    );
    return;
  }
  if (candidate.kind === 'condition-slot') {
    snapConditionSlotDrag(graph, drag, candidate);
    return;
  }
  if (candidate?.kind === 'attach') {
    snapDraggedGroupTailToTarget(graph, drag, candidate);
    return;
  }

  const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const sourceNodeId = candidate?.kind === 'insert' ? candidate.edge.sourceNodeId : candidate?.kind === 'append' ? candidate.sourceNodeId : null;
  const sourceSlotId = candidate?.kind === 'insert' ? candidate.edge.sourceSlotId : candidate?.kind === 'append' ? candidate.sourceSlotId : null;
  const source = sourceNodeId ? graph.nodes.find((nodeItem) => nodeItem.id === sourceNodeId) : null;
  const rootPosition = drag.previewPositions.get(drag.rootId);
  const rootInputY = root ? inputCenterOffset(graph, root) : null;
  if (!candidate?.valid || !root || !source || !sourceSlotId || !rootPosition || rootInputY === null) {
    return;
  }

  const sourcePosition = source.position ?? fallbackPosition(source.id);
  const sourceSize = blockMetrics(graph, source);
  const snappedRoot = {
    x: Math.round(sourcePosition.x + sourceSize.width - connectedOverlap),
    y: Math.round(sourcePosition.y + outputCenterOffset(graph, source, sourceSlotId) - rootInputY),
  };
  const dx = snappedRoot.x - rootPosition.x;
  const dy = snappedRoot.y - rootPosition.y;
  drag.previewPositions = new Map(
    Array.from(drag.previewPositions, ([nodeId, position]) => [nodeId, { x: position.x + dx, y: position.y + dy }]),
  );
}

export function snapDraggedGroupTailToTarget(graph: GraphDocument, drag: BlockDrag, candidate: Extract<InsertCandidate, { kind: 'attach' }>): void {
  const tail = draggedTailOutput(graph, new Set(drag.groupIds));
  const tailNode = tail ? graph.nodes.find((nodeItem) => nodeItem.id === tail.nodeId) : null;
  const target = graph.nodes.find((nodeItem) => nodeItem.id === candidate.targetNodeId);
  const tailPosition = tail ? drag.previewPositions.get(tail.nodeId) : null;
  const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const rootPosition = drag.previewPositions.get(drag.rootId);
  const rootInputY = root ? inputCenterOffset(graph, root) : null;
  const targetInputY = target ? inputCenterOffset(graph, target) : null;
  if (!tail || !tailNode || !target || !tailPosition || !rootPosition || rootInputY === null || targetInputY === null) {
    return;
  }

  const targetPosition = target.position ?? fallbackPosition(target.id);
  const parent = target.parentContainerId ? graph.nodes.find((nodeItem) => nodeItem.id === target.parentContainerId) : null;
  if (parent && isContainerBodyHead(dragGraphQueryContext(graph), target.id, parent.id)) {
    const entryAnchor = containerBodyEntryAnchor(graph, parent);
    const dx = Math.round(entryAnchor.x) - rootPosition.x;
    const dy = Math.round(entryAnchor.y - rootInputY) - rootPosition.y;
    drag.previewPositions = new Map(
      Array.from(drag.previewPositions, ([nodeId, position]) => [nodeId, { x: position.x + dx, y: position.y + dy }]),
    );
    return;
  }

  const tailSize = blockMetrics(graph, tailNode);
  const snappedTail = {
    x: Math.round(targetPosition.x),
    y: Math.round(targetPosition.y + targetInputY),
  };
  const dx = snappedTail.x - (tailPosition.x + tailSize.width - connectedOverlap);
  const dy = snappedTail.y - (tailPosition.y + outputCenterOffset(graph, tailNode, tail.slot.id));
  drag.previewPositions = new Map(
    Array.from(drag.previewPositions, ([nodeId, position]) => [nodeId, { x: position.x + dx, y: position.y + dy }]),
  );
}

export function canInsertIntoEdge(graph: GraphDocument, edgeItem: GraphEdge, drag: BlockDrag): { valid: boolean; message: string } {
  return canInsertIntoEdgeWithContext(dragGraphQueryContext(graph), edgeItem, drag);
}

function canInsertIntoEdgeWithContext(context: DragGraphQueryContext, edgeItem: GraphEdge, drag: BlockDrag): { valid: boolean; message: string } {
  const group = new Set(drag.groupIds);
  if (group.has(edgeItem.sourceNodeId) || group.has(edgeItem.targetNodeId)) {
    return { valid: false, message: '不能插入到正在拖动的链条内部。' };
  }

  const source = context.nodeById.get(edgeItem.sourceNodeId);
  const target = context.nodeById.get(edgeItem.targetNodeId);
  const root = context.nodeById.get(drag.rootId);
  const sourceSlot = source?.slots.find((slot) => slot.id === edgeItem.sourceSlotId);
  const targetSlot = target?.slots.find((slot) => slot.id === edgeItem.targetSlotId);
  const rootInput = root?.slots.find((slot) => slot.direction === 'INPUT');
  const tail = draggedTailOutputWithContext(context, group);

  if (!source || !target || !sourceSlot || !targetSlot || !root) {
    return { valid: false, message: '连接信息不完整，不能插入。' };
  }
  if (!rootInput) {
    return { valid: false, message: '这个积木没有输入槽，不能插入到连接中。' };
  }
  if (!tail) {
    return { valid: false, message: '这个链条有多个出口，暂不支持直接插入。' };
  }
  if (sourceSlot.edgeType !== rootInput.edgeType || tail.slot.edgeType !== targetSlot.edgeType) {
    return { valid: false, message: '槽位类型不匹配，不能插入。' };
  }
  return { valid: true, message: '松手即可自动吸附到这里。' };
}

export function draggedTailOutput(graph: GraphDocument, group: Set<string>): { nodeId: string; slot: GraphSlot } | null {
  return draggedTailOutputWithContext(dragGraphQueryContext(graph), group);
}

function draggedTailOutputWithContext(context: DragGraphQueryContext, group: Set<string>): { nodeId: string; slot: GraphSlot } | null {
  const tails: Array<{ nodeId: string; slot: GraphSlot }> = [];
  context.graph.nodes.forEach((nodeItem) => {
    if (!group.has(nodeItem.id) || conditionRackParent(context.graph, nodeItem)) {
      return;
    }
    const slot = preferredMainOutput(nodeItem);
    if (!slot) {
      return;
    }
    const keepsGoingInsideGroup = context.connectedEdges.some((graphEdge) =>
      graphEdge.sourceNodeId === nodeItem.id
      && graphEdge.sourceSlotId === slot.id
      && group.has(graphEdge.targetNodeId),
    );
    if (!keepsGoingInsideGroup) {
      tails.push({ nodeId: nodeItem.id, slot });
    }
  });
  return tails.length === 1 ? tails[0] : null;
}

export function insertDraggedGroup(graph: GraphDocument, drag: BlockDrag): boolean {
  const candidate = drag.candidate;
  if (!candidate?.valid) {
    return false;
  }

  const group = new Set(drag.groupIds);
  if (candidate.kind === 'condition-slot') {
    return applyConditionSlotDrop(graph, drag, candidate);
  }
  if (candidate.kind === 'container') {
    assignContainerMembership(graph, group, candidate.containerNodeId, candidate.slotId);
    return true;
  }
  if (candidate.kind === 'attach') {
    const tail = draggedTailOutput(graph, group);
    const target = graph.nodes.find((nodeItem) => nodeItem.id === candidate.targetNodeId);
    const targetSlot = target?.slots.find((slot) => slot.id === candidate.targetSlotId);
    if (!tail || !target || !targetSlot || tail.slot.edgeType !== targetSlot.edgeType) {
      return false;
    }
    assignContainerMembership(
      graph,
      group,
      target.parentContainerId ?? '',
      target.parentContainerId ? target.parentSlot || 'body' : '',
    );
    graph.edges = graph.edges.filter((graphEdge) =>
      !(graphEdge.targetNodeId === candidate.targetNodeId && graphEdge.targetSlotId === candidate.targetSlotId && !group.has(graphEdge.sourceNodeId)),
    );
    graph.edges.push(edge(nextEdgeId(graph), tail.nodeId, tail.slot.id, candidate.targetNodeId, candidate.targetSlotId));
    return true;
  }

  const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const rootInput = root?.slots.find((slot) => slot.direction === 'INPUT');
  if (!rootInput) {
    return false;
  }

  if (candidate.kind === 'append') {
    const source = graph.nodes.find((nodeItem) => nodeItem.id === candidate.sourceNodeId);
    if (!source) {
      return false;
    }
    assignContainerMembership(
      graph,
      group,
      source.parentContainerId ?? '',
      source.parentContainerId ? source.parentSlot || 'body' : '',
    );
    graph.edges = graph.edges.filter((graphEdge) =>
      !(graphEdge.targetNodeId === drag.rootId && graphEdge.targetSlotId === rootInput.id && !group.has(graphEdge.sourceNodeId)),
    );
    graph.edges.push(edge(nextEdgeId(graph), candidate.sourceNodeId, candidate.sourceSlotId, drag.rootId, rootInput.id));
    return true;
  }

  const tail = draggedTailOutput(graph, group);
  const source = graph.nodes.find((nodeItem) => nodeItem.id === candidate.edge.sourceNodeId);
  const target = graph.nodes.find((nodeItem) => nodeItem.id === candidate.edge.targetNodeId);
  if (!tail || !source || !target || (source.parentContainerId ?? '') !== (target.parentContainerId ?? '')) {
    return false;
  }

  assignContainerMembership(
    graph,
    group,
    source.parentContainerId ?? '',
    source.parentContainerId ? source.parentSlot || 'body' : '',
  );

  graph.edges = graph.edges.filter((graphEdge) =>
    graphEdge.id !== candidate.edge.id
    && !(graphEdge.targetNodeId === drag.rootId && graphEdge.targetSlotId === rootInput.id && !group.has(graphEdge.sourceNodeId)),
  );
  graph.edges.push(edge(nextEdgeId(graph), candidate.edge.sourceNodeId, candidate.edge.sourceSlotId, drag.rootId, rootInput.id));
  graph.edges.push(edge(nextEdgeId(graph), tail.nodeId, tail.slot.id, candidate.edge.targetNodeId, candidate.edge.targetSlotId));
  return true;
}

export type DragDropResult = {
  graph: GraphDocument;
  inserted: boolean;
  movedOutOfContainer: boolean;
};

export function computeDragDrop(baseGraph: GraphDocument, drag: BlockDrag): DragDropResult {
  const placementDrag = { ...drag, previewPositions: new Map(drag.previewPositions) };
  snapDraggedGroupToCandidate(graphWithPreviewPositions(baseGraph, placementDrag), placementDrag);

  if (placementDrag.candidate?.kind === 'condition-slot' && placementDrag.candidate.valid) {
    const graph = cloneGraph(baseGraph);
    const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
    const rootPosition = placementDrag.previewPositions.get(drag.rootId);
    if (root && rootPosition) {
      root.position = { ...rootPosition };
    }
    const inserted = applyConditionSlotDrop(graph, placementDrag, placementDrag.candidate);
    return { graph, inserted, movedOutOfContainer: false };
  }

  const graph = cloneGraph(baseGraph);
  graph.nodes = graph.nodes.map((nodeItem) => {
    const position = placementDrag.previewPositions.get(nodeItem.id);
    return position ? { ...nodeItem, position } : nodeItem;
  });
  const baseRoot = baseGraph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const placedRoot = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  if (baseRoot && placedRoot && conditionRackParent(baseGraph, baseRoot) && placementDrag.candidate?.valid) {
    placedRoot.parentContainerId = '';
    placedRoot.parentSlot = '';
  }
  const group = new Set(drag.groupIds);
  const connectedEdgeIds = new Set(connectedGraphEdges(graph).map((graphEdge) => graphEdge.id));
  graph.edges = graph.edges.filter((graphEdge) =>
    (!group.has(graphEdge.sourceNodeId) && !group.has(graphEdge.targetNodeId))
    || connectedEdgeIds.has(graphEdge.id),
  );

  const inserted = placementDrag.candidate?.valid ? insertDraggedGroup(graph, placementDrag) : false;
  if (inserted) {
    applyCandidatePlacementGap(graph, placementDrag);
  }
  const movedOutOfContainer = !inserted && syncDraggedContainerMembership(graph, placementDrag);
  shiftForContainerSizeChanges(baseGraph, graph, group);
  return { graph, inserted, movedOutOfContainer };
}

function applyCandidatePlacementGap(graph: GraphDocument, drag: BlockDrag): void {
  const candidate = drag.candidate;
  if (candidate?.kind === 'container') {
    makeContainerBodyGap(graph, drag, candidate.containerNodeId);
  } else if (candidate?.kind === 'attach') {
    const target = graph.nodes.find((nodeItem) => nodeItem.id === candidate.targetNodeId);
    if (target?.parentContainerId) {
      makeContainerBodyStartGap(graph, drag, candidate.targetNodeId);
    }
  } else if (candidate?.kind === 'insert') {
    makeInsertionGap(graph, drag);
  }
}

function findContainerCandidate(
  graph: GraphDocument,
  drag: BlockDrag,
  anchor: GraphPosition,
): Extract<InsertCandidate, { kind: 'container' }> | null {
  const group = new Set(drag.groupIds);
  const container = smallestContainerAtPoint(graph, anchor, (nodeItem) =>
    !group.has(nodeItem.id) && isBodyContainerNode(nodeItem),
  );
  if (!container) {
    return null;
  }
  const rect = containerBodyDropZone(graph, container);
  const check = canAssignContainerMembership(graph, group, container.id);
  return {
    kind: 'container',
    containerNodeId: container.id,
    slotId: 'body',
    join: {
      id: `container:${container.id}:body`,
      from: container.id,
      to: drag.rootId,
      branch: 'main',
      x: rect.x,
      y: rect.y,
      width: rect.width,
      tone: 'normal',
    },
    valid: check.valid,
    message: check.message,
  };
}

function assignContainerMembership(graph: GraphDocument, group: Set<string>, containerNodeId: string, slotId: string): void {
  graph.edges = graph.edges.filter((graphEdge) =>
    !group.has(graphEdge.targetNodeId) || group.has(graphEdge.sourceNodeId),
  );
  graph.nodes = graph.nodes.map((nodeItem) => {
    if (!group.has(nodeItem.id) || (nodeItem.parentContainerId && group.has(nodeItem.parentContainerId))) {
      return nodeItem;
    }
    return { ...nodeItem, parentContainerId: containerNodeId, parentSlot: slotId };
  });
}

function pointInsideRect(point: GraphPosition, rect: { x: number; y: number; width: number; height: number }): boolean {
  return point.x >= rect.x && point.x <= rect.x + rect.width && point.y >= rect.y && point.y <= rect.y + rect.height;
}

function smallestContainerAtPoint(graph: GraphDocument, point: GraphPosition, include: (nodeItem: GraphNode) => boolean): GraphNode | null {
  return graph.nodes
    .filter((nodeItem) => include(nodeItem) && pointInsideRect(point, containerBodyDropZone(graph, nodeItem)))
    .sort((left, right) => rectArea(containerBodyDropZone(graph, left)) - rectArea(containerBodyDropZone(graph, right)))[0] ?? null;
}

function rectArea(rect: { width: number; height: number }): number {
  return rect.width * rect.height;
}

function dragPointerPosition(drag: BlockDrag): GraphPosition {
  const start = drag.startPositions.get(drag.rootId);
  const preview = drag.previewPositions.get(drag.rootId);
  return start && preview ? {
    x: drag.startWorld.x + preview.x - start.x,
    y: drag.startWorld.y + preview.y - start.y,
  } : drag.startWorld;
}

export function makeInsertionGap(graph: GraphDocument, drag: BlockDrag): void {
  const candidate = drag.candidate;
  if (!candidate || candidate.kind !== 'insert') {
    return;
  }

  const group = new Set(drag.groupIds);
  const groupRight = drag.groupIds.reduce((right, nodeId) => {
    const nodeItem = graph.nodes.find((item) => item.id === nodeId);
    if (!nodeItem) {
      return right;
    }
    const position = nodeItem.position ?? fallbackPosition(nodeId);
    const size = blockMetrics(graph, nodeItem);
    return Math.max(right, position.x + size.width);
  }, 0);
  const targetPosition = nodePosition(graph, candidate.edge.targetNodeId);
  const shiftX = Math.max(0, Math.round(groupRight - 14 - targetPosition.x));

  const shifted = new Set(downstreamNodeIds(graph, candidate.edge.targetNodeId).filter((nodeId) => !group.has(nodeId)));
  if (shiftX > 0) {
    graph.nodes = graph.nodes.map((nodeItem) => {
      if (!shifted.has(nodeItem.id)) {
        return nodeItem;
      }
      const position = nodeItem.position ?? fallbackPosition(nodeItem.id);
      return { ...nodeItem, position: { x: position.x + shiftX, y: position.y } };
    });
  }
  ensureLocalHorizontalGaps(graph, candidate.edge.targetNodeId, group);
}

export function ensureLocalHorizontalGaps(graph: GraphDocument, startNodeId: string, locked: Set<string>): void {
  const visited = new Set<string>();
  const queue = [startNodeId];
  while (queue.length > 0) {
    const sourceId = queue.shift();
    if (!sourceId || visited.has(sourceId)) {
      continue;
    }
    visited.add(sourceId);
    const source = graph.nodes.find((nodeItem) => nodeItem.id === sourceId);
    if (!source) {
      continue;
    }
    const sourcePosition = source.position ?? fallbackPosition(source.id);
    const minTargetX = sourcePosition.x + blockMetrics(graph, source).width - 14;
    connectedGraphEdges(graph)
      .filter((graphEdge) => graphEdge.sourceNodeId === sourceId)
      .forEach((graphEdge) => {
        if (locked.has(graphEdge.targetNodeId)) {
          return;
        }
        const target = graph.nodes.find((nodeItem) => nodeItem.id === graphEdge.targetNodeId);
        if (!target) {
          return;
        }
        const targetPosition = target.position ?? fallbackPosition(target.id);
        if (targetPosition.x < minTargetX) {
          target.position = { x: minTargetX, y: targetPosition.y };
        }
        queue.push(target.id);
      });
  }
}

export function nextEdgeId(graph: GraphDocument): string {
  let index = graph.edges.length + 1;
  while (graph.edges.some((graphEdge) => graphEdge.id === `e${index}`)) {
    index += 1;
  }
  return `e${index}`;
}
