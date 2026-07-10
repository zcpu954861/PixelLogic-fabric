import { fallbackGraph } from './demoGraph';
import type { BlockKind, BlockMetrics, Branch, GraphDocument, GraphEdge, GraphNode, GraphPosition, LaneSpan } from './graphTypes';
import { activeOutputSlots, conditionOutputMode, isActiveOutputSlot } from './conditionOutputMode';
import {
  conditionRackFrame,
  containerFrame,
  containerHeightForChildBottom,
  containerMinimumHeight,
  containerMinimumWidth,
  containerWidthForChildRight,
  normalBlockHeight,
  normalBlockWidth,
} from './containerGeometry';
import { conditionRackParent, conditionSlots } from './conditionRack';
import { isBodyContainerNode } from './containerNodes';
import { blockKind } from '../ui/humanize/labels';
import {
  connectedOverlap,
  conditionBlockWidth,
  conditionBranchGap,
  visualConnectXTolerance,
  visualConnectYTolerance,
} from '../ui/canvas/blockConstants';
import { preferredMainOutput } from '../ui/canvas/activeOutput';
export function blockSize(kind: BlockKind): { width: number; height: number } {
  if (kind === 'control') {
    return { width: containerMinimumWidth, height: containerMinimumHeight };
  }
  return kind === 'condition' ? { width: conditionBlockWidth, height: normalBlockHeight * 2 + conditionBranchGap } : { width: normalBlockWidth, height: normalBlockHeight };
}

export function blockMetrics(graph: GraphDocument, nodeItem: GraphNode, cache = new Map<string, BlockMetrics>(), visiting = new Set<string>()): BlockMetrics {
  const cached = cache.get(nodeItem.id);
  if (cached) {
    return cached;
  }

  const rackParent = conditionRackParent(graph, nodeItem);
  if (rackParent) {
    const parentMetrics = blockMetrics(graph, rackParent, cache, visiting);
    const row = conditionRackFrame(parentMetrics.width, parentMetrics.height, conditionSlots(rackParent)).rows
      .find((item) => item.slotId === nodeItem.parentSlot);
    if (row) {
      const metrics = {
        width: row.capsuleRect.width,
        height: row.capsuleRect.height,
        inputY: null,
        outputOffsets: {},
        visualBounds: { x: 0, y: 0, width: row.capsuleRect.width, height: row.capsuleRect.height },
      };
      cache.set(nodeItem.id, metrics);
      return metrics;
    }
  }

  const kind = blockKind(nodeItem.type);
  if (kind === 'control') {
    const outerAnchorY = normalBlockHeight / 2;
    if (visiting.has(nodeItem.id)) {
      const rack = conditionRackFrame(containerMinimumWidth, containerMinimumHeight, conditionSlots(nodeItem));
      return {
        width: containerMinimumWidth,
        height: containerMinimumHeight,
        inputY: outerAnchorY,
        outputOffsets: Object.fromEntries(nodeItem.slots.filter((slot) => slot.direction === 'OUTPUT').map((slot) => [slot.id, outerAnchorY])),
        visualBounds: rack.fullBounds,
      };
    }
    visiting.add(nodeItem.id);
    const position = nodePosition(graph, nodeItem.id);
    const children = containerChildren(graph, nodeItem.id, 'body');
    let width = containerMinimumWidth;
    let height = containerMinimumHeight;
    let visualTop = 0;
    let visualBottom = height;
    children.forEach((child) => {
      const childPosition = nodePosition(graph, child.id);
      const childMetrics = blockMetrics(graph, child, cache, visiting);
      width = Math.max(width, containerWidthForChildRight(childPosition.x + childMetrics.width - position.x));
      height = Math.max(height, containerHeightForChildBottom(childPosition.y + childMetrics.height - position.y));
      visualTop = Math.min(visualTop, childPosition.y + childMetrics.visualBounds.y - position.y);
      visualBottom = Math.max(
        visualBottom,
        childPosition.y + childMetrics.visualBounds.y + childMetrics.visualBounds.height - position.y,
      );
    });
    visiting.delete(nodeItem.id);
    visualBottom = Math.max(visualBottom, height);
    const rack = conditionRackFrame(width, height, conditionSlots(nodeItem));
    visualTop = Math.min(visualTop, rack.fullBounds.y);
    const metrics = {
      width,
      height,
      inputY: outerAnchorY,
      outputOffsets: Object.fromEntries(nodeItem.slots.filter((slot) => slot.direction === 'OUTPUT').map((slot) => [slot.id, outerAnchorY])),
      visualBounds: { x: 0, y: visualTop, width, height: visualBottom - visualTop },
    };
    cache.set(nodeItem.id, metrics);
    return metrics;
  }
  if (kind !== 'condition' || conditionOutputMode(nodeItem) !== 'BRANCH') {
    const hasInput = nodeItem.slots.some((slot) => slot.direction === 'INPUT');
    const outputs = kind === 'condition' ? activeOutputSlots(nodeItem) : nodeItem.slots.filter((slot) => slot.direction === 'OUTPUT');
    const metrics = {
      width: normalBlockWidth,
      height: normalBlockHeight,
      inputY: hasInput ? normalBlockHeight / 2 : null,
      outputOffsets: Object.fromEntries(outputs.map((slot) => [slot.id, normalBlockHeight / 2])),
      visualBounds: { x: 0, y: 0, width: normalBlockWidth, height: normalBlockHeight },
    };
    cache.set(nodeItem.id, metrics);
    return metrics;
  }

  if (visiting.has(nodeItem.id)) {
    return {
      width: conditionBlockWidth,
      height: normalBlockHeight * 2 + conditionBranchGap,
      inputY: (normalBlockHeight * 2 + conditionBranchGap) / 2,
      outputOffsets: { pass: normalBlockHeight / 2, fail: normalBlockHeight + conditionBranchGap + normalBlockHeight / 2 },
      visualBounds: { x: 0, y: 0, width: conditionBlockWidth, height: normalBlockHeight * 2 + conditionBranchGap },
    };
  }

  visiting.add(nodeItem.id);
  const passSpan = branchLaneSpan(graph, nodeItem.id, 'pass', cache, visiting);
  const failSpan = branchLaneSpan(graph, nodeItem.id, 'fail', cache, visiting);
  visiting.delete(nodeItem.id);

  const height = passSpan.above + passSpan.below + conditionBranchGap + failSpan.above + failSpan.below;
  const passY = passSpan.above;
  const failY = passSpan.above + passSpan.below + conditionBranchGap + failSpan.above;
  const metrics = {
    width: conditionBlockWidth,
    height,
    inputY: (passY + failY) / 2,
    outputOffsets: { pass: passY, fail: failY },
    visualBounds: { x: 0, y: 0, width: conditionBlockWidth, height },
  };
  cache.set(nodeItem.id, metrics);
  return metrics;
}

export function branchLaneSpan(
  graph: GraphDocument,
  sourceNodeId: string,
  sourceSlotId: string,
  cache: Map<string, BlockMetrics>,
  visiting: Set<string>,
): LaneSpan {
  const edgeItem = activeGraphEdges(graph).find((graphEdge) => graphEdge.sourceNodeId === sourceNodeId && graphEdge.sourceSlotId === sourceSlotId);
  if (!edgeItem) {
    return { above: normalBlockHeight / 2, below: normalBlockHeight / 2 };
  }
  return subtreeLaneSpan(graph, edgeItem.targetNodeId, cache, visiting);
}

export function subtreeLaneSpan(graph: GraphDocument, nodeId: string, cache: Map<string, BlockMetrics>, visiting: Set<string>): LaneSpan {
  const nodeItem = graph.nodes.find((item) => item.id === nodeId);
  if (!nodeItem || visiting.has(nodeId)) {
    return { above: normalBlockHeight / 2, below: normalBlockHeight / 2 };
  }

  const metrics = blockMetrics(graph, nodeItem, cache, visiting);
  const inputY = metrics.inputY ?? metrics.height / 2;
  let above = inputY - metrics.visualBounds.y;
  let below = metrics.visualBounds.y + metrics.visualBounds.height - inputY;
  const outputSlot = preferredMainOutput(nodeItem);
  const nextEdge = outputSlot
    ? graph.edges.find((graphEdge) => graphEdge.sourceNodeId === nodeId && graphEdge.sourceSlotId === outputSlot.id)
    : null;
  const nextVisiting = new Set(visiting);
  nextVisiting.add(nodeId);
  if (nextEdge && outputSlot) {
    const nextSpan = subtreeLaneSpan(graph, nextEdge.targetNodeId, cache, nextVisiting);
    const outputY = outputCenterOffset(graph, nodeItem, outputSlot.id);
    above = Math.max(above, inputY - outputY + nextSpan.above);
    below = Math.max(below, outputY - inputY + nextSpan.below);
  }
  return { above, below };
}

export function nodePosition(graph: GraphDocument, nodeId: string): GraphPosition {
  const nodeItem = graph.nodes.find((item) => item.id === nodeId);
  if (!nodeItem) {
    return fallbackPosition(nodeId);
  }
  const parent = conditionRackParent(graph, nodeItem);
  if (parent) {
    const parentPosition = nodePosition(graph, parent.id);
    const parentMetrics = blockMetrics(graph, parent);
    const row = conditionRackFrame(parentMetrics.width, parentMetrics.height, conditionSlots(parent)).rows
      .find((item) => item.slotId === nodeItem.parentSlot);
    if (row) {
      return {
        x: parentPosition.x + row.capsuleRect.x,
        y: parentPosition.y + row.capsuleRect.y,
      };
    }
  }
  return nodeItem.position ?? fallbackPosition(nodeId);
}

export function blockVisualRect(graph: GraphDocument, nodeItem: GraphNode): { x: number; y: number; width: number; height: number } {
  const position = nodePosition(graph, nodeItem.id);
  const metrics = blockMetrics(graph, nodeItem);
  return {
    x: position.x + metrics.visualBounds.x,
    y: position.y + metrics.visualBounds.y,
    width: metrics.visualBounds.width,
    height: metrics.visualBounds.height,
  };
}

export function downstreamNodeIds(graph: GraphDocument, rootId: string): string[] {
  const outgoing = new Map<string, GraphEdge[]>();
  connectedGraphEdges(graph).forEach((graphEdge) => {
    const list = outgoing.get(graphEdge.sourceNodeId) ?? [];
    list.push(graphEdge);
    outgoing.set(graphEdge.sourceNodeId, list);
  });

  const visited = new Set<string>();
  const ordered: string[] = [];
  const stack = [rootId];
  while (stack.length > 0) {
    const nextId = stack.pop();
    if (!nextId || visited.has(nextId)) {
      continue;
    }
    visited.add(nextId);
    ordered.push(nextId);
    containerDescendantNodeIds(graph, nextId).forEach((childId) => {
      if (!visited.has(childId)) {
        stack.push(childId);
      }
    });
    for (const graphEdge of outgoing.get(nextId) ?? []) {
      if (!visited.has(graphEdge.targetNodeId)) {
        stack.push(graphEdge.targetNodeId);
      }
    }
  }
  return ordered;
}

export function containerChildren(graph: GraphDocument, containerNodeId: string, parentSlot = 'body'): GraphNode[] {
  return graph.nodes.filter((nodeItem) => nodeItem.parentContainerId === containerNodeId && (nodeItem.parentSlot || 'body') === parentSlot);
}

export function containerDescendantNodeIds(graph: GraphDocument, containerNodeId: string): string[] {
  const result: string[] = [];
  const visited = new Set<string>();
  const visit = (parentId: string) => {
    graph.nodes.filter((nodeItem) => nodeItem.parentContainerId === parentId).forEach((child) => {
      if (visited.has(child.id)) {
        return;
      }
      visited.add(child.id);
      result.push(child.id);
      visit(child.id);
    });
  };
  visit(containerNodeId);
  return result;
}

export function containerBodyRect(graph: GraphDocument, containerNode: GraphNode): { x: number; y: number; width: number; height: number } {
  const position = nodePosition(graph, containerNode.id);
  const metrics = blockMetrics(graph, containerNode);
  const rect = containerFrame(metrics.width, metrics.height).bodyRect;
  return {
    x: position.x + rect.x,
    y: position.y + rect.y,
    width: rect.width,
    height: rect.height,
  };
}

export function containerBodyDropZone(graph: GraphDocument, containerNode: GraphNode): { x: number; y: number; width: number; height: number } {
  const position = nodePosition(graph, containerNode.id);
  const metrics = blockMetrics(graph, containerNode);
  const rect = containerFrame(metrics.width, metrics.height).bodyDropZone;
  return { x: position.x + rect.x, y: position.y + rect.y, width: rect.width, height: rect.height };
}

export function containerBodyEntryAnchor(graph: GraphDocument, containerNode: GraphNode): GraphPosition {
  const position = nodePosition(graph, containerNode.id);
  const metrics = blockMetrics(graph, containerNode);
  const anchor = containerFrame(metrics.width, metrics.height).bodyEntryAnchor;
  return { x: position.x + anchor.x, y: position.y + anchor.y };
}

export function conditionSlotRects(
  graph: GraphDocument,
  containerNode: GraphNode,
  slotId: string,
): { row: { x: number; y: number; width: number; height: number }; capsule: { x: number; y: number; width: number; height: number } } | null {
  const position = nodePosition(graph, containerNode.id);
  const metrics = blockMetrics(graph, containerNode);
  const row = conditionRackFrame(metrics.width, metrics.height, conditionSlots(containerNode)).rows
    .find((item) => item.slotId === slotId);
  return row ? {
    row: {
      x: position.x + row.dropZone.x,
      y: position.y + row.dropZone.y,
      width: row.dropZone.width,
      height: row.dropZone.height,
    },
    capsule: {
      x: position.x + row.capsuleRect.x,
      y: position.y + row.capsuleRect.y,
      width: row.capsuleRect.width,
      height: row.capsuleRect.height,
    },
  } : null;
}

export function connectedComponentNodeIds(graph: GraphDocument, rootId: string, omittedEdgeId: string): string[] {
  const neighbors = new Map<string, string[]>();
  connectedGraphEdges(graph)
    .filter((graphEdge) => graphEdge.id !== omittedEdgeId)
    .forEach((graphEdge) => {
      neighbors.set(graphEdge.sourceNodeId, [...(neighbors.get(graphEdge.sourceNodeId) ?? []), graphEdge.targetNodeId]);
      neighbors.set(graphEdge.targetNodeId, [...(neighbors.get(graphEdge.targetNodeId) ?? []), graphEdge.sourceNodeId]);
    });
  const visited = new Set<string>();
  const ordered: string[] = [];
  const stack = [rootId];
  while (stack.length > 0) {
    const nextId = stack.pop();
    if (!nextId || visited.has(nextId)) {
      continue;
    }
    visited.add(nextId);
    ordered.push(nextId);
    for (const neighborId of neighbors.get(nextId) ?? []) {
      if (!visited.has(neighborId)) {
        stack.push(neighborId);
      }
    }
  }
  return ordered;
}

export function normalizeConditionBranchLayout(graph: GraphDocument): GraphDocument {
  const nextGraph = cloneGraph(graph);
  const nodeById = new Map(nextGraph.nodes.map((nodeItem) => [nodeItem.id, nodeItem]));
  const outgoing = new Map<string, GraphEdge[]>();
  const incoming = new Set<string>();
  activeGraphEdges(nextGraph).forEach((graphEdge) => {
    outgoing.set(graphEdge.sourceNodeId, [...(outgoing.get(graphEdge.sourceNodeId) ?? []), graphEdge]);
    incoming.add(graphEdge.targetNodeId);
  });

  const roots = [
    ...Object.values(nextGraph.triggerEntries),
    ...nextGraph.nodes.filter((nodeItem) => !incoming.has(nodeItem.id)).map((nodeItem) => nodeItem.id),
  ];
  const visitedEdges = new Set<string>();
  const alignDownstream = (sourceId: string, path: Set<string>) => {
    if (path.has(sourceId)) {
      return;
    }
    const source = nodeById.get(sourceId);
    if (!source) {
      return;
    }
    const sourcePosition = source.position ?? fallbackPosition(source.id);
    const nextPath = new Set(path);
    nextPath.add(sourceId);
    for (const graphEdge of outgoing.get(sourceId) ?? []) {
      if (visitedEdges.has(graphEdge.id)) {
        continue;
      }
      visitedEdges.add(graphEdge.id);
      const target = nodeById.get(graphEdge.targetNodeId);
      if (!target) {
        continue;
      }
      const targetInputY = inputCenterOffset(nextGraph, target);
      if (targetInputY === null) {
        continue;
      }
      const targetPosition = target.position ?? fallbackPosition(target.id);
      target.position = {
        ...targetPosition,
        y: Math.round(sourcePosition.y + outputCenterOffset(nextGraph, source, graphEdge.sourceSlotId) - targetInputY),
      };
      alignDownstream(target.id, nextPath);
    }
  };

  roots.forEach((rootId) => alignDownstream(rootId, new Set()));
  return nextGraph;
}

export function connectedGraphEdges(graph: GraphDocument): GraphEdge[] {
  return activeGraphEdges(graph).filter((graphEdge) => isVisuallyConnectedEdge(graph, graphEdge));
}

export function activeGraphEdges(graph: GraphDocument): GraphEdge[] {
  return graph.edges.filter((graphEdge) => {
    const source = graph.nodes.find((nodeItem) => nodeItem.id === graphEdge.sourceNodeId);
    return source ? isActiveOutputSlot(source, graphEdge.sourceSlotId) : false;
  });
}

export function isVisuallyConnectedEdge(graph: GraphDocument, graphEdge: GraphEdge): boolean {
  const source = graph.nodes.find((nodeItem) => nodeItem.id === graphEdge.sourceNodeId);
  const target = graph.nodes.find((nodeItem) => nodeItem.id === graphEdge.targetNodeId);
  if (!source || !target) {
    return false;
  }

  const targetInputY = inputCenterOffset(graph, target);
  if (targetInputY === null) {
    return false;
  }

  const sourcePosition = source.position ?? fallbackPosition(source.id);
  const targetPosition = target.position ?? fallbackPosition(target.id);
  const sourceSize = blockMetrics(graph, source);
  const expectedTargetX = sourcePosition.x + sourceSize.width - connectedOverlap;
  const expectedTargetInputY = sourcePosition.y + outputCenterOffset(graph, source, graphEdge.sourceSlotId);
  return Math.abs(targetPosition.x - expectedTargetX) <= visualConnectXTolerance
    && Math.abs(targetPosition.y + targetInputY - expectedTargetInputY) <= visualConnectYTolerance;
}

export function inputCenterOffset(graph: GraphDocument, nodeItem: GraphNode): number | null {
  if (!nodeItem.slots.some((slot) => slot.direction === 'INPUT')) {
    return null;
  }
  return blockMetrics(graph, nodeItem).inputY;
}

export function outputCenterOffset(graph: GraphDocument, sourceNode: GraphNode, sourceSlotId: string): number {
  return blockMetrics(graph, sourceNode).outputOffsets[sourceSlotId] ?? normalBlockHeight / 2;
}

export function fallbackPosition(nodeId: string): GraphPosition {
  return fallbackGraph.nodes.find((nodeItem) => nodeItem.id === nodeId)?.position ?? { x: 48, y: 78 };
}

export function cloneGraph(graph: GraphDocument): GraphDocument {
  return {
    ...graph,
    nodes: graph.nodes.map((nodeItem) => ({
      ...nodeItem,
      config: { ...nodeItem.config },
      conditionSlots: conditionSlots(nodeItem).map((slot) => ({ ...slot })),
      position: nodeItem.position ? { ...nodeItem.position } : undefined,
      parentContainerId: nodeItem.parentContainerId ?? '',
      parentSlot: nodeItem.parentSlot ?? '',
      slots: nodeItem.slots.map((slot) => ({ ...slot })),
    })),
    edges: graph.edges.map((graphEdge) => ({ ...graphEdge })),
    triggerEntries: { ...graph.triggerEntries },
  };
}

export function branchForNode(graph: GraphDocument, nodeItem: GraphNode): Branch {
  const incoming = connectedGraphEdges(graph).find((graphEdge) => graphEdge.targetNodeId === nodeItem.id);
  if (incoming?.sourceSlotId === 'fail') {
    return 'fail';
  }
  if (incoming?.sourceSlotId === 'pass') {
    return 'pass';
  }
  if (nodeItem.type.includes('TRIGGER') || nodeItem.type.includes('CONDITION') || isBodyContainerNode(nodeItem)) {
    return 'main';
  }
  return 'pass';
}
