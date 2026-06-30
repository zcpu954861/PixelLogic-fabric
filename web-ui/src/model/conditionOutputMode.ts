import type { GraphNode, GraphSlot } from './graphTypes';

export type ConditionOutputMode = 'PASS_ONLY' | 'FAIL_ONLY' | 'BRANCH';

export const conditionOutputModeKey = 'outputMode';

export function conditionOutputMode(nodeItem: GraphNode): ConditionOutputMode {
  const value = nodeItem.config[conditionOutputModeKey];
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
