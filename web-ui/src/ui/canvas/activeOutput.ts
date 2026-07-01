import { activeOutputSlots } from '../../model/conditionOutputMode';
import type { GraphNode, GraphSlot } from '../../model/graphTypes';

export function preferredMainOutput(nodeItem: GraphNode): GraphSlot | null {
  const outputs = activeOutputSlots(nodeItem);
  for (const slotId of ['done', 'timer_completed', 'started']) {
    const slot = outputs.find((item) => item.id === slotId);
    if (slot) {
      return slot;
    }
  }
  return outputs.length === 1 ? outputs[0] : null;
}
