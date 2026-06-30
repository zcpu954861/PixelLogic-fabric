import { fallbackGraph } from './demoGraph';
import type { BlockKind, BlockMetrics, Branch, GraphDocument, GraphEdge, GraphNode, GraphPosition, GraphSlot, LaneSpan } from './graphTypes';
import { activeOutputSlots, conditionOutputMode, isActiveOutputSlot } from './conditionOutputMode';
import { blockKind } from '../ui/humanize/labels';
import { connectedOverlap, conditionBlockWidth, conditionBranchGap, normalBlockHeight, normalBlockWidth, visualConnectXTolerance, visualConnectYTolerance } from '../ui/canvas/blockConstants';
export function blockSize(kind: BlockKind): { width: number; height: number } {
  return kind === 'condition' ? { width: conditionBlockWidth, height: normalBlockHeight * 2 + conditionBranchGap } : { width: normalBlockWidth, height: normalBlockHeight };
}

export function blockMetrics(graph: GraphDocument, nodeItem: GraphNode, cache = new Map<string, BlockMetrics>(), visiting = new Set<string>()): BlockMetrics {
  const cached = cache.get(nodeItem.id);
  if (cached) {
    return cached;
  }

  const kind = blockKind(nodeItem.type);
  if (kind !== 'condition' || conditionOutputMode(nodeItem) !== 'BRANCH') {
    const hasInput = nodeItem.slots.some((slot) => slot.direction === 'INPUT');
    const outputs = kind === 'condition' ? activeOutputSlots(nodeItem) : nodeItem.slots.filter((slot) => slot.direction === 'OUTPUT');
    const metrics = {
      width: normalBlockWidth,
      height: normalBlockHeight,
      inputY: hasInput ? normalBlockHeight / 2 : null,
      outputOffsets: Object.fromEntries(outputs.map((slot) => [slot.id, normalBlockHeight / 2])),
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
  let above = inputY;
  let below = metrics.height - inputY;
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
  return graph.nodes.find((nodeItem) => nodeItem.id === nodeId)?.position ?? fallbackPosition(nodeId);
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
    for (const graphEdge of outgoing.get(nextId) ?? []) {
      if (!visited.has(graphEdge.targetNodeId)) {
        stack.push(graphEdge.targetNodeId);
      }
    }
  }
  return ordered;
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

export function preferredMainOutput(nodeItem: GraphNode): GraphSlot | null {
  const outputs = activeOutputSlots(nodeItem);
  for (const slotId of ['done', 'timer_completed', 'started']) {
    const slot = outputs.find((item) => item.id === slotId);
    if (slot) {
      return slot;
    }
  }
  return outputs.length === 1 && !['pass', 'fail'].includes(outputs[0].id) ? outputs[0] : null;
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
      position: nodeItem.position ? { ...nodeItem.position } : undefined,
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
  if (nodeItem.type.includes('TRIGGER') || nodeItem.type.includes('CONDITION')) {
    return 'main';
  }
  return 'pass';
}
