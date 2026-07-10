import type { SimulationTestContext, SimulationTestResult } from './simulationTestContext';

export type ApiStatus = 'checking' | 'online' | 'offline';
export type BlockKind = 'trigger' | 'condition' | 'action' | 'state' | 'timer' | 'debug' | 'control';
export type Branch = 'main' | 'pass' | 'fail';

export type ApiTraceStep = {
  timestamp: string;
  nodeId: string;
  message: string;
};

export type ApiTrace = {
  id: string;
  truncated: boolean;
  steps: ApiTraceStep[];
};

export type ValidationIssue = {
  severity: 'ERROR' | 'WARNING';
  code: string;
  message: string;
};

export type ValidationReport = {
  valid: boolean;
  issues: ValidationIssue[];
};

export type GraphPosition = {
  x: number;
  y: number;
};

export type GraphSlot = {
  id: string;
  direction: 'INPUT' | 'OUTPUT';
  edgeType: 'CONTROL';
};

export type ConditionSlotDefinition = {
  slotId: string;
  negated: boolean;
};

export type GraphNode = {
  id: string;
  type: string;
  blockId?: string;
  displayName: string;
  config: Record<string, string>;
  position?: GraphPosition;
  parentContainerId?: string;
  parentSlot?: string;
  conditionSlots?: ConditionSlotDefinition[];
  slots: GraphSlot[];
};

export type CatalogCategory = {
  id: string;
  displayName: string;
  description: string;
  order: number;
  visibleByDefault: boolean;
};

export type CatalogSubcategory = {
  id: string;
  categoryId: string;
  displayName: string;
  description: string;
  order: number;
};

export type CatalogFormField = {
  key: string;
  type: 'string' | 'textarea' | 'number' | 'integer' | 'boolean' | 'select' | 'segmented' | 'readonly' | 'hidden' | 'scope' | 'rich_text_component';
  label: string;
  description: string;
  defaultValue: string;
  placeholder: string;
  options: FieldOption[];
  required: boolean;
  min: string;
  max: string;
  step: string;
  ui: string;
  suffix: string;
};

export type CatalogBlock = {
  id: string;
  version: number;
  displayName: string;
  description: string;
  categoryId: string;
  subcategoryId: string;
  tags: string[];
  capabilities: string[];
  nodeKind: string;
  nodeType: string;
  defaultConfig: Record<string, string>;
  formSchema: CatalogFormField[];
  summaryTemplate: string;
  summaryFormatter: string;
  predicateSummaryTemplate?: string;
  predicateNegatedSummaryTemplate?: string;
  containerSlots: string[];
  inputSlots: GraphSlot[];
  outputSlots: GraphSlot[];
  simulationCapability: string;
  mcCapability: string;
  safetyFlags: string[];
  deprecated: boolean;
  hidden: boolean;
  aliases: string[];
};

export type BlockCatalog = {
  categories: CatalogCategory[];
  subcategories: CatalogSubcategory[];
  blocks: CatalogBlock[];
};

export type EditableField = {
  label: string;
  key: string;
  value: string;
  control: CatalogFormField['type'];
  description?: string;
  defaultValue?: string;
  placeholder?: string;
  required?: boolean;
  options?: FieldOption[];
  full?: boolean;
  min?: string;
  max?: string;
  step?: string;
  ui?: string;
  suffix?: string;
};

export type FieldOption = {
  value: string;
  label: string;
};

export type EditorSection = {
  title: string;
  fields: EditableField[];
};

export type GraphEdge = {
  id: string;
  sourceNodeId: string;
  sourceSlotId: string;
  targetNodeId: string;
  targetSlotId: string;
  type: 'CONTROL';
};

export type GraphDocument = {
  schemaVersion: 1;
  id: string;
  displayName: string;
  createdAt: string;
  updatedAt: string;
  fingerprint: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
  triggerEntries: Record<string, string>;
};

export type ApiResponse = {
  ok: boolean;
  message?: string;
  graph?: GraphDocument | null;
  graphs?: Array<{
    id: string;
    displayName: string;
    fingerprint: string;
    hasDraft: boolean;
  }>;
  validation?: ValidationReport;
  fingerprint?: string;
  hasDraft?: boolean;
  traceId?: string;
  runId?: string;
  runStatus?: 'WAITING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  terminal?: boolean;
  trace?: ApiTrace | null;
  traces?: ApiTrace[];
  simulation?: SimulationTestResult;
  catalog?: BlockCatalog;
  api?: string;
  demoActor?: {
    id: string;
    label: string;
  };
  error?: {
    code: string;
    message: string;
  };
};

export type UiState = {
  apiStatus: ApiStatus;
  statusMessage: string;
  demoActor: string;
  simulationTestContext: SimulationTestContext;
  simulationTestContextError: string;
  simulationResult: SimulationTestResult | null;
  simulationMenuOpen: boolean;
  simulationEditorOpen: boolean;
  simulationEditorClosing: boolean;
  simulationDraftContext: SimulationTestContext | null;
  simulationOriginalContext: SimulationTestContext | null;
  busyAction: string | null;
  lastAction: string;
  error: string;
  latestTrace: ApiTrace | null;
  catalog: BlockCatalog | null;
  catalogCategoryId: string | null;
  graph: GraphDocument | null;
  committedGraph: GraphDocument | null;
  validation: ValidationReport | null;
  hasDraft: boolean;
  dirty: boolean;
  selectedNodeId: string;
  editorOpen: boolean;
  editorClosing: boolean;
  editorDraftNode: GraphNode | null;
  editorOriginalNode: GraphNode | null;
  editorSaving: boolean;
  recentNodeId: string | null;
};

export type GraphHistoryEntry = {
  graph: GraphDocument;
  selectedNodeId: string;
  recentNodeId: string | null;
  lastAction: string;
};

export type SlotBlock = {
  id: string;
  kind: BlockKind;
  categoryId?: string;
  branch: Branch;
  type: string;
  title: string;
  summary: string;
  x: number;
  y: number;
  width: number;
  height: number;
  inputY: number | null;
  outputOffsets: Record<string, number>;
  bodyOffsetY?: number;
  presentation?: 'block' | 'predicate-capsule';
  embeddedParentId?: string;
  conditionRack?: ConditionRackView;
  selected?: boolean;
  hasChildren?: boolean;
};

export type ConditionRackRowView = {
  slotId: string;
  negated: boolean;
  index: number;
  x: number;
  y: number;
  width: number;
  height: number;
  slotRect: { x: number; y: number; width: number; height: number };
  toggleRect: { x: number; y: number; width: number; height: number };
  capsule?: SlotBlock;
};

export type ConditionRackView = {
  rows: ConditionRackRowView[];
};

export type BlockMetrics = {
  width: number;
  height: number;
  inputY: number | null;
  outputOffsets: Record<string, number>;
  visualBounds: { x: number; y: number; width: number; height: number };
};

export type LaneSpan = {
  above: number;
  below: number;
};

export type SlotJoin = {
  id: string;
  from: string;
  to: string;
  branch: Branch;
  x: number;
  y: number;
  width: number;
  tone?: 'normal' | 'pass' | 'fail';
};

export type InsertCandidate = {
  kind: 'insert';
  edge: GraphEdge;
  join: SlotJoin;
  valid: boolean;
  message: string;
} | {
  kind: 'container';
  containerNodeId: string;
  slotId: string;
  join: SlotJoin;
  valid: boolean;
  message: string;
} | {
  kind: 'condition-slot';
  containerNodeId: string;
  slotId: string;
  join: SlotJoin;
  valid: boolean;
  message: string;
} | {
  kind: 'append';
  sourceNodeId: string;
  sourceSlotId: string;
  join: SlotJoin;
  valid: boolean;
  message: string;
} | {
  kind: 'attach';
  sourceNodeId: string;
  sourceSlotId: string;
  targetNodeId: string;
  targetSlotId: string;
  join: SlotJoin;
  valid: boolean;
  message: string;
};

export type BlockDrag = {
  pointerId: number;
  rootId: string;
  groupIds: string[];
  started: boolean;
  startClient: GraphPosition;
  startWorld: GraphPosition;
  startPositions: Map<string, GraphPosition>;
  previewPositions: Map<string, GraphPosition>;
  joins: SlotJoin[];
  candidate: InsertCandidate | null;
};
