import type { BlockDrag, GraphDocument, GraphEdge, GraphNode, GraphPosition, GraphSlot, InsertCandidate, SlotJoin } from '../../model/graphTypes';
import { edge } from '../../model/demoGraph';
import { blockMetrics, branchForNode, cloneGraph, connectedGraphEdges, containerBodyDropZone, containerBodyEntryAnchor, downstreamNodeIds, fallbackPosition, inputCenterOffset, nodePosition, outputCenterOffset } from '../../model/graphLayout';
import { normalBlockHeight, normalBlockWidth } from '../../model/containerGeometry';
import { preferredMainOutput } from './activeOutput';
import { connectedOverlap, insertSnapX, insertSnapY, linkSnapX, linkSnapY, reconnectSnapX, reconnectSnapY } from './blockConstants';
import {
  canAssignContainerMembership,
  draggedGroupBounds,
  makeContainerBodyGap,
  makeContainerBodyStartGap,
  shiftForContainerSizeChanges,
  syncDraggedContainerMembership,
} from './containerPlacement';

export function connectedActionText(candidate: InsertCandidate | null): string {
  if (candidate?.kind === 'container') {
    return '已放入容器内部，正在自动保存。';
  }
  if (candidate?.kind === 'append') {
    return '已连接到链尾，正在自动保存。';
  }
  if (candidate?.kind === 'attach') {
    return '已连接到后面的积木，正在自动保存。';
  }
  return '已插入到连接处，正在自动保存。';
}

export function findInsertCandidate(baseGraph: GraphDocument, drag: BlockDrag): InsertCandidate | null {
  const graph = graphWithPreviewPositions(baseGraph, drag);
  const rootNode = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const rootPosition = drag.previewPositions.get(drag.rootId);
  if (!rootNode || !rootPosition) {
    return null;
  }

  const rootInputY = inputCenterOffset(graph, rootNode);
  const dragCenter = draggedGroupCenter(graph, drag) ?? {
    x: rootPosition.x + blockMetrics(graph, rootNode).width / 2,
    y: rootPosition.y + blockMetrics(graph, rootNode).height / 2,
  };
  const rootInputAnchor = rootInputY === null ? null : {
    x: rootPosition.x,
    y: rootPosition.y + rootInputY,
  };
  const group = new Set(drag.groupIds);
  const stableGraph = graphWithDragStartPositions(baseGraph, drag);
  const tailAnchor = draggedTailAnchor(graph, drag);
  const containerCandidate = findContainerCandidate(
    stableGraph,
    drag,
    dragCenter,
  );
  if (containerCandidate) {
    if (!containerCandidate.valid) {
      return containerCandidate;
    }
    const containerId = containerCandidate.containerNodeId;
    const internalEdge = closestEdgeCandidate(graph, drag, dragCenter, (edgeItem) => edgeInScope(graph, edgeItem, containerId));
    if (internalEdge?.valid) {
      return internalEdge;
    }
    if (rootInputAnchor) {
      const internalAppend = closestCandidate(
        appendCandidates(graph, drag).filter((candidate) => nodeInScope(graph, candidate.sourceNodeId, containerId)),
        (candidate) => candidateSnapScore(graph, drag, candidate, rootInputAnchor),
      );
      if (internalAppend) {
        return internalAppend;
      }
    }
    if (tailAnchor) {
      const internalAttach = closestCandidate(
        attachCandidates(graph, drag).filter((candidate) =>
          nodeInScope(graph, candidate.targetNodeId, containerId)
          && isContainerBodyHead(graph, candidate.targetNodeId, containerId),
        ),
        (candidate) => bodyHeadDropScore(graph, candidate, dragCenter)
          ?? candidateSnapScore(graph, drag, candidate, tailAnchor),
      );
      if (internalAttach) {
        return internalAttach;
      }
    }
    const hasBodyContent = graph.nodes.some((nodeItem) =>
      !group.has(nodeItem.id) && (nodeItem.parentContainerId ?? '') === containerId,
    );
    if (!hasBodyContent) {
      return containerCandidate;
    }
    return internalEdge ?? {
      ...containerCandidate,
      valid: false,
      message: '请靠近循环内部的连接线或链尾再松手。',
    };
  }

  if (tailAnchor) {
    const bodyHeadAttach = closestCandidate(
      attachCandidates(graph, drag).filter((candidate) => {
        const target = graph.nodes.find((nodeItem) => nodeItem.id === candidate.targetNodeId);
        const containerId = target?.parentContainerId ?? '';
        return Boolean(containerId) && isContainerBodyHead(graph, candidate.targetNodeId, containerId);
      }),
      (candidate) => bodyHeadDropScore(graph, candidate, dragCenter)
        ?? candidateSnapScore(graph, drag, candidate, tailAnchor),
    );
    if (bodyHeadAttach) {
      const target = stableGraph.nodes.find((nodeItem) => nodeItem.id === bodyHeadAttach.targetNodeId);
      const check = canAssignContainerMembership(stableGraph, group, target?.parentContainerId ?? '');
      return check.valid ? bodyHeadAttach : { ...bodyHeadAttach, valid: false, message: check.message };
    }
  }

  const externalEdge = closestEdgeCandidate(graph, drag, dragCenter, (edgeItem) => edgeInScope(graph, edgeItem, ''));
  if (externalEdge?.valid) {
    return externalEdge;
  }
  if (rootInputAnchor) {
    const appendCandidate = closestCandidate(
      appendCandidates(graph, drag).filter((candidate) => nodeInScope(graph, candidate.sourceNodeId, '')),
      (candidate) => candidateSnapScore(graph, drag, candidate, rootInputAnchor),
    );
    if (appendCandidate) {
      return appendCandidate;
    }
  }

  if (tailAnchor) {
    const attachCandidate = closestCandidate(
      attachCandidates(graph, drag).filter((candidate) => nodeInScope(graph, candidate.targetNodeId, '')),
      (candidate) => candidateSnapScore(graph, drag, candidate, tailAnchor),
    );
    if (attachCandidate) {
      return attachCandidate;
    }
  }

  return externalEdge;
}

function closestEdgeCandidate(
  graph: GraphDocument,
  drag: BlockDrag,
  anchor: GraphPosition,
  include: (edgeItem: GraphEdge) => boolean,
): Extract<InsertCandidate, { kind: 'insert' }> | null {
  const group = new Set(drag.groupIds);
  let valid: { candidate: Extract<InsertCandidate, { kind: 'insert' }>; score: number } | null = null;
  let invalid: { candidate: Extract<InsertCandidate, { kind: 'insert' }>; score: number } | null = null;
  for (const join of drag.joins) {
    const edgeItem = graph.edges.find((graphEdge) => graphEdge.id === join.id);
    if (!edgeItem || !include(edgeItem)) {
      continue;
    }
    const score = insertSnapScore(graph, edgeItem, anchor, join);
    if (score === null) {
      continue;
    }
    const check = canInsertIntoEdge(graph, edgeItem, drag);
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

function nodeInScope(graph: GraphDocument, nodeId: string, containerId: string): boolean {
  return (graph.nodes.find((nodeItem) => nodeItem.id === nodeId)?.parentContainerId ?? '') === containerId;
}

function edgeInScope(graph: GraphDocument, edgeItem: GraphEdge, containerId: string): boolean {
  return nodeInScope(graph, edgeItem.sourceNodeId, containerId)
    && nodeInScope(graph, edgeItem.targetNodeId, containerId);
}

function isContainerBodyHead(graph: GraphDocument, nodeId: string, containerId: string): boolean {
  return !connectedGraphEdges(graph).some((graphEdge) =>
    graphEdge.targetNodeId === nodeId && nodeInScope(graph, graphEdge.sourceNodeId, containerId),
  );
}

function bodyHeadDropScore(
  graph: GraphDocument,
  candidate: Extract<InsertCandidate, { kind: 'attach' }>,
  dragCenter: GraphPosition,
): number | null {
  const target = graph.nodes.find((nodeItem) => nodeItem.id === candidate.targetNodeId);
  const targetInputY = target ? inputCenterOffset(graph, target) : null;
  if (!target || targetInputY === null) {
    return null;
  }
  const targetPosition = nodePosition(graph, target.id);
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
      return position ? { ...nodeItem, position } : nodeItem;
    }),
  };
}

function graphWithDragStartPositions(graph: GraphDocument, drag: BlockDrag): GraphDocument {
  return {
    ...graph,
    nodes: graph.nodes.map((nodeItem) => {
      const position = drag.startPositions.get(nodeItem.id);
      return position ? { ...nodeItem, position } : nodeItem;
    }),
  };
}

export function snapScore(anchor: GraphPosition, join: SlotJoin): number | null {
  const center = { x: join.x + join.width / 2, y: join.y + 15 };
  const dx = Math.abs(anchor.x - center.x);
  const dy = Math.abs(anchor.y - center.y);
  return dx <= insertSnapX && dy <= insertSnapY ? dx + dy * 1.35 : null;
}

function insertSnapScore(graph: GraphDocument, edgeItem: GraphEdge, anchor: GraphPosition, join: SlotJoin): number | null {
  const source = graph.nodes.find((nodeItem) => nodeItem.id === edgeItem.sourceNodeId);
  const target = graph.nodes.find((nodeItem) => nodeItem.id === edgeItem.targetNodeId);
  if (source && target && edgeTouchesControlBoundary(source, target)) {
    return snapScoreWithin(anchor, join, linkSnapX + 24, linkSnapY + 14);
  }
  const snapY = source && target && nodesShareContainerBody(source, target)
    ? insertSnapY + normalBlockHeight
    : insertSnapY;
  return snapScoreWithin(anchor, join, insertSnapX, snapY);
}

function edgeTouchesControlBoundary(source: GraphNode, target: GraphNode): boolean {
  return isControlLoopNode(source) || isControlLoopNode(target);
}

function isControlLoopNode(nodeItem: GraphNode): boolean {
  return Boolean(nodeItem.blockId?.startsWith('control.loop.') || nodeItem.type.startsWith('CONTROL_LOOP_'));
}

function nodesShareContainerBody(source: GraphNode, target: GraphNode): boolean {
  return Boolean(source.parentContainerId && source.parentContainerId === target.parentContainerId);
}

export function candidateSnapScore(graph: GraphDocument, drag: BlockDrag, candidate: InsertCandidate, anchor: GraphPosition): number | null {
  if (isReconnectCandidate(graph, drag, candidate)) {
    return snapScoreWithin(anchor, candidate.join, reconnectSnapX, reconnectSnapY);
  }
  if (candidate.kind === 'append' && isContainerBodyAppend(graph, candidate)) {
    return snapScoreWithin(anchor, candidate.join, linkSnapX + 72, linkSnapY + 36);
  }
  if (candidate.kind === 'append' || candidate.kind === 'attach') {
    return snapScoreWithin(anchor, candidate.join, linkSnapX, linkSnapY);
  }
  return snapScore(anchor, candidate.join);
}

function isContainerBodyAppend(graph: GraphDocument, candidate: Extract<InsertCandidate, { kind: 'append' }>): boolean {
  const source = graph.nodes.find((nodeItem) => nodeItem.id === candidate.sourceNodeId);
  return Boolean(source?.parentContainerId);
}

export function snapScoreWithin(anchor: GraphPosition, join: SlotJoin, snapX: number, snapY: number): number | null {
  const center = { x: join.x + join.width / 2, y: join.y + 15 };
  const dx = Math.abs(anchor.x - center.x);
  const dy = Math.abs(anchor.y - center.y);
  return dx <= snapX && dy <= snapY ? dx + dy * 1.35 : null;
}

export function isReconnectCandidate(graph: GraphDocument, drag: BlockDrag, candidate: InsertCandidate): boolean {
  if (candidate.kind === 'container') {
    return false;
  }
  if (candidate.kind === 'append') {
    const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
    const rootInput = root?.slots.find((slot) => slot.direction === 'INPUT');
    return Boolean(rootInput && graph.edges.some((graphEdge) =>
      graphEdge.sourceNodeId === candidate.sourceNodeId
      && graphEdge.sourceSlotId === candidate.sourceSlotId
      && graphEdge.targetNodeId === drag.rootId
      && graphEdge.targetSlotId === rootInput.id,
    ));
  }
  if (candidate.kind === 'attach') {
    return graph.edges.some((graphEdge) =>
      graphEdge.sourceNodeId === candidate.sourceNodeId
      && graphEdge.sourceSlotId === candidate.sourceSlotId
      && graphEdge.targetNodeId === candidate.targetNodeId
      && graphEdge.targetSlotId === candidate.targetSlotId,
    );
  }
  return false;
}

export function appendCandidates(graph: GraphDocument, drag: BlockDrag): Array<Extract<InsertCandidate, { kind: 'append' }>> {
  const group = new Set(drag.groupIds);
  const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const rootInput = root?.slots.find((slot) => slot.direction === 'INPUT');
  if (!root || !rootInput) {
    return [];
  }

  const connectedEdges = connectedGraphEdges(graph);
  return graph.nodes.flatMap((source) => {
    if (group.has(source.id)) {
      return [];
    }
    const sourcePosition = source.position ?? fallbackPosition(source.id);
    const sourceSize = blockMetrics(graph, source);
    const output = preferredMainOutput(source);
    return (output ? [output] : [])
      .filter((slot) => slot.edgeType === rootInput.edgeType)
      .filter((slot) => !connectedEdges.some((edgeItem) => edgeItem.sourceNodeId === source.id && edgeItem.sourceSlotId === slot.id))
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
          y: sourcePosition.y + outputCenterOffset(graph, source, slot.id) - 15,
          width: 20,
          tone: slot.id === 'pass' ? 'pass' : slot.id === 'fail' ? 'fail' : 'normal',
        },
        valid: true,
        message: '松手即可自动吸附到链尾。',
      }));
  });
}

export function draggedTailAnchor(graph: GraphDocument, drag: BlockDrag): GraphPosition | null {
  const tail = draggedTailOutput(graph, new Set(drag.groupIds));
  if (!tail) {
    return null;
  }
  const tailNode = graph.nodes.find((nodeItem) => nodeItem.id === tail.nodeId);
  const tailPosition = drag.previewPositions.get(tail.nodeId);
  if (!tailNode || !tailPosition) {
    return null;
  }
  const tailSize = blockMetrics(graph, tailNode);
  return {
    x: tailPosition.x + tailSize.width - connectedOverlap,
    y: tailPosition.y + outputCenterOffset(graph, tailNode, tail.slot.id),
  };
}

export function attachCandidates(graph: GraphDocument, drag: BlockDrag): Array<Extract<InsertCandidate, { kind: 'attach' }>> {
  const group = new Set(drag.groupIds);
  const tail = draggedTailOutput(graph, group);
  if (!tail) {
    return [];
  }

  const connectedEdges = connectedGraphEdges(graph);
  return graph.nodes.flatMap((target): Array<Extract<InsertCandidate, { kind: 'attach' }>> => {
    if (group.has(target.id)) {
      return [];
    }
    const targetPosition = target.position ?? fallbackPosition(target.id);
    const targetBranch = branchForNode(graph, target);
    return target.slots
      .filter((slot) => slot.direction === 'INPUT')
      .filter((slot) => slot.edgeType === tail.slot.edgeType)
      .filter((slot) => !connectedEdges.some((edgeItem) => edgeItem.targetNodeId === target.id && edgeItem.targetSlotId === slot.id))
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
          y: targetPosition.y + (inputCenterOffset(graph, target) ?? normalBlockHeight / 2) - 15,
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
  if (parent && isContainerBodyHead(graph, target.id, parent.id)) {
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
  const group = new Set(drag.groupIds);
  if (group.has(edgeItem.sourceNodeId) || group.has(edgeItem.targetNodeId)) {
    return { valid: false, message: '不能插入到正在拖动的链条内部。' };
  }

  const source = graph.nodes.find((nodeItem) => nodeItem.id === edgeItem.sourceNodeId);
  const target = graph.nodes.find((nodeItem) => nodeItem.id === edgeItem.targetNodeId);
  const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const sourceSlot = source?.slots.find((slot) => slot.id === edgeItem.sourceSlotId);
  const targetSlot = target?.slots.find((slot) => slot.id === edgeItem.targetSlotId);
  const rootInput = root?.slots.find((slot) => slot.direction === 'INPUT');
  const tail = draggedTailOutput(graph, group);

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
  const tails: Array<{ nodeId: string; slot: GraphSlot }> = [];
  const edges = connectedGraphEdges(graph);
  graph.nodes.forEach((nodeItem) => {
    if (!group.has(nodeItem.id)) {
      return;
    }
    const slot = preferredMainOutput(nodeItem);
    if (!slot) {
      return;
    }
    const keepsGoingInsideGroup = edges.some((graphEdge) =>
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
  snapDraggedGroupToCandidate(baseGraph, placementDrag);

  const graph = cloneGraph(baseGraph);
  graph.nodes = graph.nodes.map((nodeItem) => {
    const position = placementDrag.previewPositions.get(nodeItem.id);
    return position ? { ...nodeItem, position } : nodeItem;
  });
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
    !group.has(nodeItem.id) && isControlLoopNode(nodeItem),
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

function draggedGroupCenter(graph: GraphDocument, drag: BlockDrag): GraphPosition | null {
  const bounds = draggedGroupBounds(graph, drag);
  return bounds ? { x: (bounds.left + bounds.right) / 2, y: (bounds.top + bounds.bottom) / 2 } : null;
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
