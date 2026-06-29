import type { UiState } from '../model/graphTypes';

export const world = { width: 2160, height: 620 };

export const state: UiState = {
  apiStatus: 'checking',
  statusMessage: '正在连接 API...',
  demoActor: 'WebUI 模拟玩家',
  busyAction: null,
  lastAction: '尚未运行',
  error: '',
  latestTrace: null,
  graph: null,
  committedGraph: null,
  validation: null,
  hasDraft: false,
  dirty: false,
  selectedNodeId: 'condition-started',
  editorOpen: false,
  editorClosing: false,
  recentNodeId: null,
};