import type { GraphEdge, GraphNode, GraphSlot } from './graphTypes';
import { graphConfigString } from './entityTargetReference';

export type ConditionOutputMode = 'PASS_ONLY' | 'FAIL_ONLY' | 'BRANCH';

export const conditionOutputModeKey = 'outputMode';

export function conditionOutputMode(nodeItem: GraphNode): ConditionOutputMode {
  const value = graphConfigString(nodeItem.config, conditionOutputModeKey);
  return value === 'PASS_ONLY' || value === 'FAIL_ONLY' || value === 'BRANCH' ? value : 'BRANCH';
}

export function conditionOutputModeLabel(value: string | undefined): string {
  switch (value) {
    case 'PASS_ONLY':
      return '满足时继续';
    case 'FAIL_ONLY':
      return '不满足时继续';
    case 'BRANCH':
    case undefined:
    case '':
      return '分成两路';
    default:
      return value;
  }
}

export function activeConditionOutputSlots(nodeItem: GraphNode): string[] {
  if (!nodeItem.type.includes('CONDITION')) {
    return nodeItem.slots.filter((slot) => slot.direction === 'OUTPUT').map((slot) => slot.id);
  }
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return ['pass'];
    case 'FAIL_ONLY':
      return ['fail'];
    case 'BRANCH':
      return ['pass', 'fail'];
  }
}

export function activeOutputSlots(nodeItem: GraphNode): GraphSlot[] {
  const active = new Set(activeConditionOutputSlots(nodeItem));
  return nodeItem.slots.filter((slot) => slot.direction === 'OUTPUT' && active.has(slot.id));
}

export function isActiveOutputSlot(nodeItem: GraphNode, slotId: string): boolean {
  return activeConditionOutputSlots(nodeItem).includes(slotId);
}

export function reconcileConditionOutputEdges(
  edges: GraphEdge[],
  originalNode: GraphNode,
  nextNode: GraphNode,
): GraphEdge[] {
  const originalMode = conditionOutputMode(originalNode);
  const nextMode = conditionOutputMode(nextNode);
  if (originalMode !== 'BRANCH' && nextMode !== 'BRANCH') {
    const [originalSlot] = activeConditionOutputSlots(originalNode);
    const [nextSlot] = activeConditionOutputSlots(nextNode);
    return edges.flatMap((graphEdge) => {
      if (graphEdge.sourceNodeId !== nextNode.id) {
        return [graphEdge];
      }
      return graphEdge.sourceSlotId === originalSlot
        ? [{ ...graphEdge, sourceSlotId: nextSlot }]
        : [];
    });
  }
  const activeSlots = new Set(activeConditionOutputSlots(nextNode));
  return edges.filter((graphEdge) =>
    graphEdge.sourceNodeId !== nextNode.id || activeSlots.has(graphEdge.sourceSlotId),
  );
}
