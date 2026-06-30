export type ApiStatus = 'checking' | 'online' | 'offline';
export type BlockKind = 'trigger' | 'condition' | 'action' | 'state' | 'timer' | 'debug';
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

export type GraphNode = {
  id: string;
  type: string;
  blockId?: string;
  displayName: string;
  config: Record<string, string>;
  position?: GraphPosition;
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
  label: string;
  control: 'text' | 'number' | 'select' | 'boolean';
  options: FieldOption[];
  required: boolean;
  full: boolean;
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
  nodeKind: string;
  nodeType: string;
  defaultConfig: Record<string, string>;
  formFields: CatalogFormField[];
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
  control: 'text' | 'number' | 'select' | 'boolean';
  options?: FieldOption[];
  full?: boolean;
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
  trace?: ApiTrace | null;
  traces?: ApiTrace[];
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
  selected?: boolean;
};

export type BlockMetrics = {
  width: number;
  height: number;
  inputY: number | null;
  outputOffsets: Record<string, number>;
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
