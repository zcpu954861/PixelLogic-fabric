import { containerGeometry, normalBlockWidth } from '../../model/containerGeometry';
import {
  activeGraphEdges,
  blockMetrics,
  containerBodyRect,
  containerDescendantNodeIds,
  downstreamNodeIds,
  fallbackPosition,
  nodePosition,
} from '../../model/graphLayout';
import type { BlockDrag, GraphDocument, GraphNode } from '../../model/graphTypes';
import { connectedOverlap } from './blockConstants';

const maxContainerDepth = 4;

export function canAssignContainerMembership(
  graph: GraphDocument,
  group: Set<string>,
  containerNodeId: string,
): { valid: boolean; message: string } {
  const parents = new Map(graph.nodes.map((nodeItem) => [nodeItem.id, nodeItem.parentContainerId ?? '']));
  group.forEach((nodeId) => {
    const parentId = parents.get(nodeId) ?? '';
    if (!parentId || !group.has(parentId)) {
      parents.set(nodeId, containerNodeId);
    }
  });

  for (const nodeId of group) {
    let depth = 0;
    let parentId = parents.get(nodeId) ?? '';
    const visited = new Set([nodeId]);
    while (parentId) {
      if (visited.has(parentId)) {
        return { valid: false, message: '不能把容器放进自身或后代。' };
      }
      visited.add(parentId);
      depth += 1;
      if (depth > maxContainerDepth) {
        return { valid: false, message: `容器嵌套不能超过 ${maxContainerDepth} 层。` };
      }
      parentId = parents.get(parentId) ?? '';
    }
  }
  return { valid: true, message: '松手即可放入循环内部。' };
}

export function makeContainerBodyGap(graph: GraphDocument, drag: BlockDrag, containerNodeId: string): void {
  const group = new Set(drag.groupIds);
  const bounds = draggedGroupBounds(graph, drag);
  const dx = Math.round((bounds?.width ?? normalBlockWidth) - connectedOverlap + containerGeometry.childGap);
  const shifted = new Set<string>();
  graph.nodes
    .filter((nodeItem) => nodeItem.parentContainerId === containerNodeId && !group.has(nodeItem.id))
    .forEach((nodeItem) => {
      shifted.add(nodeItem.id);
      containerDescendantNodeIds(graph, nodeItem.id).forEach((nodeId) => shifted.add(nodeId));
    });
  shiftNodes(graph, shifted, dx);
}

export function makeContainerBodyStartGap(graph: GraphDocument, drag: BlockDrag, targetNodeId: string): void {
  const target = graph.nodes.find((nodeItem) => nodeItem.id === targetNodeId);
  if (!target?.parentContainerId) {
    return;
  }
  const bounds = draggedGroupBounds(graph, drag);
  const targetPosition = nodePosition(graph, target.id);
  const dx = Math.max(0, Math.round((bounds?.right ?? targetPosition.x) - connectedOverlap - targetPosition.x));
  shiftNodes(graph, new Set(downstreamNodeIds(graph, target.id).filter((nodeId) => !drag.groupIds.includes(nodeId))), dx);
}

export function shiftForContainerSizeChanges(beforeGraph: GraphDocument, graph: GraphDocument, locked: Set<string>): void {
  beforeGraph.nodes
    .filter(isControlLoopNode)
    .sort((left, right) => containerDepth(beforeGraph, right.id) - containerDepth(beforeGraph, left.id))
    .forEach((beforeContainer) => {
      if (locked.has(beforeContainer.id)) {
        return;
      }
      const afterContainer = graph.nodes.find((nodeItem) => nodeItem.id === beforeContainer.id);
      if (!afterContainer) {
        return;
      }
      const dx = Math.round(blockMetrics(graph, afterContainer).width - blockMetrics(beforeGraph, beforeContainer).width);
      if (dx === 0) {
        return;
      }
      const inside = new Set([beforeContainer.id, ...containerDescendantNodeIds(graph, beforeContainer.id)]);
      const shifted = new Set<string>();
      activeGraphEdges(beforeGraph)
        .filter((graphEdge) => graphEdge.sourceNodeId === beforeContainer.id && !inside.has(graphEdge.targetNodeId))
        .forEach((graphEdge) => downstreamNodeIds(beforeGraph, graphEdge.targetNodeId).forEach((nodeId) => {
          if (!locked.has(nodeId) && !inside.has(nodeId)) {
            shifted.add(nodeId);
          }
        }));
      shiftNodes(graph, shifted, dx);
    });
}

export function syncDraggedContainerMembership(graph: GraphDocument, drag: BlockDrag): boolean {
  const group = new Set(drag.groupIds);
  const containerGraph = graphWithDragStartPositions(graph, drag);
  const parentIdsToClear = new Set<string>();

  graph.nodes.forEach((nodeItem) => {
    if (!shouldCheckDraggedContainerMembership(nodeItem, group)) {
      return;
    }
    const parentId = nodeItem.parentContainerId;
    const parent = parentId ? containerGraph.nodes.find((item) => item.id === parentId) : null;
    const rect = nodeRect(graph, nodeItem);
    const center = { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 };
    if (!parent || !pointInsideRect(center, containerBodyRect(containerGraph, parent))) {
      parentIdsToClear.add(parentId ?? '');
    }
  });

  if (parentIdsToClear.size === 0) {
    return false;
  }
  graph.nodes = graph.nodes.map((nodeItem) => (
    shouldCheckDraggedContainerMembership(nodeItem, group) && parentIdsToClear.has(nodeItem.parentContainerId ?? '')
      ? { ...nodeItem, parentContainerId: '', parentSlot: '' }
      : nodeItem
  ));
  return true;
}

export function draggedGroupBounds(
  graph: GraphDocument,
  drag: BlockDrag,
): { left: number; right: number; top: number; bottom: number; width: number; height: number } | null {
  const previewGraph = {
    ...graph,
    nodes: graph.nodes.map((nodeItem) => {
      const position = drag.previewPositions.get(nodeItem.id);
      return position ? { ...nodeItem, position } : nodeItem;
    }),
  };
  return drag.groupIds.reduce<{ left: number; right: number; top: number; bottom: number; width: number; height: number } | null>((bounds, nodeId) => {
    const nodeItem = previewGraph.nodes.find((item) => item.id === nodeId);
    if (!nodeItem) {
      return bounds;
    }
    const position = nodePosition(previewGraph, nodeId);
    const size = blockMetrics(previewGraph, nodeItem);
    const left = bounds ? Math.min(bounds.left, position.x) : position.x;
    const right = bounds ? Math.max(bounds.right, position.x + size.width) : position.x + size.width;
    const top = bounds ? Math.min(bounds.top, position.y) : position.y;
    const bottom = bounds ? Math.max(bounds.bottom, position.y + size.height) : position.y + size.height;
    return { left, right, top, bottom, width: right - left, height: bottom - top };
  }, null);
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

function shouldCheckDraggedContainerMembership(nodeItem: GraphNode, group: Set<string>): boolean {
  return group.has(nodeItem.id) && Boolean(nodeItem.parentContainerId) && !group.has(nodeItem.parentContainerId ?? '');
}

function nodeRect(graph: GraphDocument, nodeItem: GraphNode): { x: number; y: number; width: number; height: number } {
  const position = nodePosition(graph, nodeItem.id);
  const size = blockMetrics(graph, nodeItem);
  return { x: position.x, y: position.y, width: size.width, height: size.height };
}

function pointInsideRect(point: { x: number; y: number }, rect: { x: number; y: number; width: number; height: number }): boolean {
  return point.x >= rect.x && point.x <= rect.x + rect.width && point.y >= rect.y && point.y <= rect.y + rect.height;
}

function shiftNodes(graph: GraphDocument, nodeIds: Set<string>, dx: number): void {
  if (dx === 0 || nodeIds.size === 0) {
    return;
  }
  graph.nodes = graph.nodes.map((nodeItem) => {
    if (!nodeIds.has(nodeItem.id)) {
      return nodeItem;
    }
    const position = nodeItem.position ?? fallbackPosition(nodeItem.id);
    return { ...nodeItem, position: { x: position.x + dx, y: position.y } };
  });
}

function containerDepth(graph: GraphDocument, nodeId: string): number {
  let depth = 0;
  let parentId = graph.nodes.find((nodeItem) => nodeItem.id === nodeId)?.parentContainerId;
  const visited = new Set<string>();
  while (parentId && !visited.has(parentId)) {
    visited.add(parentId);
    depth += 1;
    parentId = graph.nodes.find((nodeItem) => nodeItem.id === parentId)?.parentContainerId;
  }
  return depth;
}

function isControlLoopNode(nodeItem: GraphNode): boolean {
  return Boolean(nodeItem.blockId?.startsWith('control.loop.') || nodeItem.type.startsWith('CONTROL_LOOP_'));
}
