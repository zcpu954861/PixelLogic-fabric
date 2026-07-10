import type { GraphNode } from './graphTypes';

export function isBodyContainerNode(nodeItem: GraphNode): boolean {
  return Boolean(
    nodeItem.blockId?.startsWith('control.loop.')
    || nodeItem.blockId === 'context.entity.execute_as'
    || nodeItem.type.startsWith('CONTROL_LOOP_')
    || nodeItem.type === 'CONTEXT_ENTITY_EXECUTE_AS'
  );
}
