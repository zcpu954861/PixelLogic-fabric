import type { GraphDocument, GraphEdge, GraphNode, GraphPosition, GraphSlot } from './graphTypes';

export const graphId = 'demo-start-flow';
export const fallbackGraph: GraphDocument = {
  schemaVersion: 1,
  id: graphId,
  displayName: 'Demo 开始流程',
  createdAt: '',
  updatedAt: '',
  fingerprint: '',
  triggerEntries: { 'manual.test.start': 'manual-trigger' },
  nodes: [
    node('manual-trigger', 'MANUAL_TRIGGER', 'WebUI 测试运行', {}, { x: 48, y: 205 }, [out('started')]),
    node(
      'condition-started',
      'STATE_COMPARE_CONDITION',
      '是否未开始',
      { scope: 'PLAYER', key: 'started', valueType: 'BOOLEAN', expected: 'false', missing: 'false' },
      { x: 294, y: 78 },
      [input('input'), out('pass'), out('fail')],
    ),
    node('welcome-message', 'MESSAGE_ACTION', '发送欢迎语', { message: '欢迎开始游戏' }, { x: 664, y: 78 }, [
      input('input'),
      out('done'),
    ]),
    node(
      'set-started',
      'STATE_SET_ACTION',
      '记录开始状态',
      { scope: 'PLAYER', key: 'started', valueType: 'BOOLEAN', value: 'true' },
      { x: 910, y: 78 },
      [input('input'), out('done')],
    ),
    node(
      'add-start-count',
      'STATE_ADD_ACTION',
      '累计开始次数',
      { scope: 'PLAYER', key: 'start_count', valueType: 'INTEGER', amount: '1' },
      { x: 1156, y: 78 },
      [input('input'), out('done')],
    ),
    node('timer-start', 'TIMER_START_ACTION', '等待倒计时', { durationSeconds: '30' }, { x: 1402, y: 78 }, [
      input('input'),
      out('timer_completed'),
    ]),
    node('debug-finished', 'DEBUG_LOG_ACTION', '倒计时结束', { message: '倒计时结束' }, { x: 1648, y: 78 }, [
      input('input'),
      out('done'),
    ]),
    node(
      'debug-already-started',
      'DEBUG_LOG_ACTION',
      '已经开始过',
      { message: '玩家已经开始过游戏' },
      { x: 664, y: 332 },
      [input('input'), out('done')],
    ),
  ],
  edges: [
    edge('e1', 'manual-trigger', 'started', 'condition-started', 'input'),
    edge('e2', 'condition-started', 'pass', 'welcome-message', 'input'),
    edge('e3', 'welcome-message', 'done', 'set-started', 'input'),
    edge('e4', 'set-started', 'done', 'add-start-count', 'input'),
    edge('e5', 'add-start-count', 'done', 'timer-start', 'input'),
    edge('e6', 'timer-start', 'timer_completed', 'debug-finished', 'input'),
    edge('e7', 'condition-started', 'fail', 'debug-already-started', 'input'),
  ],
};


export function node(
  id: string,
  type: string,
  displayName: string,
  config: Record<string, string>,
  position: GraphPosition,
  slots: GraphSlot[],
): GraphNode {
  return { id, type, displayName, config, position, slots };
}
export function input(id: string): GraphSlot {
  return { id, direction: 'INPUT', edgeType: 'CONTROL' };
}
export function out(id: string): GraphSlot {
  return { id, direction: 'OUTPUT', edgeType: 'CONTROL' };
}
export function edge(id: string, sourceNodeId: string, sourceSlotId: string, targetNodeId: string, targetSlotId: string): GraphEdge {
  return { id, sourceNodeId, sourceSlotId, targetNodeId, targetSlotId, type: 'CONTROL' };
}