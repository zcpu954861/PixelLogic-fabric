import type { UiState } from '../model/graphTypes';
import { fallbackCatalog } from '../model/blockCatalog';
import { defaultSimulationTestContext } from '../model/simulationTestContext';

export const world = { width: 2160, height: 620 };

export const state: UiState = {
  apiStatus: 'checking',
  statusMessage: '正在连接 API...',
  demoActor: 'WebUI 模拟玩家',
  simulationTestContext: defaultSimulationTestContext(),
  simulationTestContextError: '',
  simulationResult: null,
  simulationMenuOpen: false,
  simulationEditorOpen: false,
  simulationEditorClosing: false,
  simulationDraftContext: null,
  simulationOriginalContext: null,
  busyAction: null,
  lastAction: '尚未运行',
  error: '',
  latestTrace: null,
  catalog: fallbackCatalog,
  catalogCategoryId: null,
  graph: null,
  committedGraph: null,
  validation: null,
  hasDraft: false,
  dirty: false,
  selectedNodeId: 'condition-started',
  editorOpen: false,
  editorClosing: false,
  editorDraftNode: null,
  editorOriginalNode: null,
  editorSaving: false,
  recentNodeId: null,
};
