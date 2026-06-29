import './styles.css';

type ApiStatus = 'checking' | 'online' | 'offline';
type BlockKind = 'trigger' | 'condition' | 'action' | 'state' | 'timer' | 'debug';
type Branch = 'main' | 'pass' | 'fail';
type LibraryKind = 'trigger' | 'condition' | 'action' | 'state' | 'timer' | 'debug';

type ApiTraceStep = {
  timestamp: string;
  nodeId: string;
  message: string;
};

type ApiTrace = {
  id: string;
  truncated: boolean;
  steps: ApiTraceStep[];
};

type ValidationIssue = {
  severity: 'ERROR' | 'WARNING';
  code: string;
  message: string;
};

type ValidationReport = {
  valid: boolean;
  issues: ValidationIssue[];
};

type GraphPosition = {
  x: number;
  y: number;
};

type GraphSlot = {
  id: string;
  direction: 'INPUT' | 'OUTPUT';
  edgeType: 'CONTROL';
};

type GraphNode = {
  id: string;
  type: string;
  displayName: string;
  config: Record<string, string>;
  position?: GraphPosition;
  slots: GraphSlot[];
};

type EditableField = {
  label: string;
  key: string;
  value: string;
  control: 'text' | 'number' | 'select' | 'boolean';
  options?: FieldOption[];
  full?: boolean;
  suffix?: string;
};

type FieldOption = {
  value: string;
  label: string;
};

type EditorSection = {
  title: string;
  fields: EditableField[];
};

type GraphEdge = {
  id: string;
  sourceNodeId: string;
  sourceSlotId: string;
  targetNodeId: string;
  targetSlotId: string;
  type: 'CONTROL';
};

type GraphDocument = {
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

type ApiResponse = {
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

type UiState = {
  apiStatus: ApiStatus;
  statusMessage: string;
  demoActor: string;
  busyAction: string | null;
  lastAction: string;
  error: string;
  latestTrace: ApiTrace | null;
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

type GraphHistoryEntry = {
  graph: GraphDocument;
  selectedNodeId: string;
  recentNodeId: string | null;
  lastAction: string;
};

type SlotBlock = {
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

type BlockMetrics = {
  width: number;
  height: number;
  inputY: number | null;
  outputOffsets: Record<string, number>;
};

type LaneSpan = {
  above: number;
  below: number;
};

type SlotJoin = {
  id: string;
  from: string;
  to: string;
  branch: Branch;
  x: number;
  y: number;
  width: number;
  tone?: 'normal' | 'pass' | 'fail';
};

type InsertCandidate = {
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

type BlockDrag = {
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

class PixelLogicApiError extends Error {
  constructor(
    message: string,
    readonly connected: boolean,
  ) {
    super(message);
  }
}

const graphId = 'demo-start-flow';
const app = document.querySelector<HTMLDivElement>('#app');
const world = { width: 2160, height: 620 };

const fallbackGraph: GraphDocument = {
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

const state: UiState = {
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

let scale = 0.86;
let offsetX = 28;
let offsetY = 34;
let isPanning = false;
let panStart = { x: 0, y: 0 };
let panOffset = { x: 0, y: 0 };
let activeBlockDrag: BlockDrag | null = null;
let lastBlockClick: { nodeId: string; time: number } | null = null;
let graphVersion = 0;
let autoSaveTimer: number | null = null;
let autoSaveInFlight = false;
let autoSaveAgain = false;
let autoSavePromise: Promise<void> | null = null;
const undoStack: GraphHistoryEntry[] = [];
const redoStack: GraphHistoryEntry[] = [];
const dragThreshold = 6;
const doubleClickMs = 340;
const autoSaveDelayMs = 650;
const historyLimit = 80;
const normalBlockWidth = 260;
const normalBlockHeight = 150;
const conditionBlockWidth = 384;
const conditionBranchGap = 104;
const puzzleMouthHalfHeight = 18;
const insertSnapX = 188;
const insertSnapY = 118;
const reconnectSnapX = 24;
const reconnectSnapY = 18;
const connectedOverlap = 14;
const visualConnectXTolerance = 8;
const visualConnectYTolerance = 6;

function node(
  id: string,
  type: string,
  displayName: string,
  config: Record<string, string>,
  position: GraphPosition,
  slots: GraphSlot[],
): GraphNode {
  return { id, type, displayName, config, position, slots };
}

function input(id: string): GraphSlot {
  return { id, direction: 'INPUT', edgeType: 'CONTROL' };
}

function out(id: string): GraphSlot {
  return { id, direction: 'OUTPUT', edgeType: 'CONTROL' };
}

function edge(id: string, sourceNodeId: string, sourceSlotId: string, targetNodeId: string, targetSlotId: string): GraphEdge {
  return { id, sourceNodeId, sourceSlotId, targetNodeId, targetSlotId, type: 'CONTROL' };
}

function currentGraph(): GraphDocument {
  return state.graph ?? fallbackGraph;
}

function puzzlePath(block: SlotBlock): string {
  const { kind, width, height } = block;
  const tab = 18;
  const notchTop = 57;
  const notchBottom = 93;

  if (kind === 'trigger') {
    return `M0 0 H${width - tab} V${notchTop} H${width} V${notchBottom} H${width - tab} V${height} H0 Z`;
  }

  if (kind === 'condition') {
    const headHeight = 150;
    const inputY = block.inputY ?? height / 2;
    const passY = block.outputOffsets.pass ?? 75;
    const failY = block.outputOffsets.fail ?? 329;
    const headTop = inputY - headHeight / 2;
    const headBottom = headTop + headHeight;
    const inputTop = inputY - puzzleMouthHalfHeight;
    const inputBottom = inputY + puzzleMouthHalfHeight;
    const passTop = passY - puzzleMouthHalfHeight;
    const passBottom = passY + puzzleMouthHalfHeight;
    const failTop = failY - puzzleMouthHalfHeight;
    const failBottom = failY + puzzleMouthHalfHeight;
    const capTop = Math.max(0, Math.min(headTop, passY - normalBlockHeight / 2));
    const capBottom = Math.min(height, Math.max(headBottom, failY + normalBlockHeight / 2));
    const branchInset = width - 106;

    return `M${branchInset} ${capTop} H${width - tab} V${passTop} H${width} V${passBottom} H${width - tab} V${failTop} H${width} V${failBottom} H${width - tab} V${capBottom} H${branchInset} V${headBottom} H0 V${inputBottom} H${tab} V${inputTop} H0 V${headTop} H${branchInset} Z`;
  }

  return `M0 0 H${width - tab} V${notchTop} H${width} V${notchBottom} H${width - tab} V${height} H0 V${notchBottom} H${tab} V${notchTop} H0 Z`;
}

function conditionBranchTabs(block: SlotBlock): string {
  const width = block.width;
  const passY = block.outputOffsets.pass ?? 75;
  const failY = block.outputOffsets.fail ?? 329;
  const passTop = passY - puzzleMouthHalfHeight;
  const passBottom = passY + puzzleMouthHalfHeight;
  const failTop = failY - puzzleMouthHalfHeight;
  const failBottom = failY + puzzleMouthHalfHeight;
  return `
      <path class="branch-tab-fill pass" d="M${width - 20} ${passTop} H${width} V${passBottom} H${width - 20} Z" />
      <path class="branch-tab pass" d="M${width - 18} ${passTop} H${width} V${passBottom} H${width - 18}" />
      <path class="branch-tab-fill fail" d="M${width - 20} ${failTop} H${width} V${failBottom} H${width - 20} Z" />
      <path class="branch-tab fail" d="M${width - 18} ${failTop} H${width} V${failBottom} H${width - 18}" />
    `;
}

function renderShape(path: string, width: number, height: number, extraPaths = ''): string {
  return `
    <svg class="puzzle-shape" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" aria-hidden="true">
      <path class="block-body" d="${path}" />
      ${extraPaths}
    </svg>
  `;
}

function blockSize(kind: BlockKind): { width: number; height: number } {
  return kind === 'condition' ? { width: conditionBlockWidth, height: normalBlockHeight * 2 + conditionBranchGap } : { width: normalBlockWidth, height: normalBlockHeight };
}

function blockMetrics(graph: GraphDocument, nodeItem: GraphNode, cache = new Map<string, BlockMetrics>(), visiting = new Set<string>()): BlockMetrics {
  const cached = cache.get(nodeItem.id);
  if (cached) {
    return cached;
  }

  const kind = blockKind(nodeItem.type);
  if (kind !== 'condition') {
    const hasInput = nodeItem.slots.some((slot) => slot.direction === 'INPUT');
    const metrics = {
      width: normalBlockWidth,
      height: normalBlockHeight,
      inputY: hasInput ? normalBlockHeight / 2 : null,
      outputOffsets: Object.fromEntries(nodeItem.slots.filter((slot) => slot.direction === 'OUTPUT').map((slot) => [slot.id, normalBlockHeight / 2])),
    };
    cache.set(nodeItem.id, metrics);
    return metrics;
  }

  if (visiting.has(nodeItem.id)) {
    return {
      width: conditionBlockWidth,
      height: normalBlockHeight * 2 + conditionBranchGap,
      inputY: (normalBlockHeight * 2 + conditionBranchGap) / 2,
      outputOffsets: { pass: normalBlockHeight / 2, fail: normalBlockHeight + conditionBranchGap + normalBlockHeight / 2 },
    };
  }

  visiting.add(nodeItem.id);
  const passSpan = branchLaneSpan(graph, nodeItem.id, 'pass', cache, visiting);
  const failSpan = branchLaneSpan(graph, nodeItem.id, 'fail', cache, visiting);
  visiting.delete(nodeItem.id);

  const height = passSpan.above + passSpan.below + conditionBranchGap + failSpan.above + failSpan.below;
  const passY = passSpan.above;
  const failY = passSpan.above + passSpan.below + conditionBranchGap + failSpan.above;
  const metrics = {
    width: conditionBlockWidth,
    height,
    inputY: (passY + failY) / 2,
    outputOffsets: { pass: passY, fail: failY },
  };
  cache.set(nodeItem.id, metrics);
  return metrics;
}

function branchLaneSpan(
  graph: GraphDocument,
  sourceNodeId: string,
  sourceSlotId: string,
  cache: Map<string, BlockMetrics>,
  visiting: Set<string>,
): LaneSpan {
  const edgeItem = graph.edges.find((graphEdge) => graphEdge.sourceNodeId === sourceNodeId && graphEdge.sourceSlotId === sourceSlotId);
  if (!edgeItem) {
    return { above: normalBlockHeight / 2, below: normalBlockHeight / 2 };
  }
  return subtreeLaneSpan(graph, edgeItem.targetNodeId, cache, visiting);
}

function subtreeLaneSpan(graph: GraphDocument, nodeId: string, cache: Map<string, BlockMetrics>, visiting: Set<string>): LaneSpan {
  const nodeItem = graph.nodes.find((item) => item.id === nodeId);
  if (!nodeItem || visiting.has(nodeId)) {
    return { above: normalBlockHeight / 2, below: normalBlockHeight / 2 };
  }

  const metrics = blockMetrics(graph, nodeItem, cache, visiting);
  const inputY = metrics.inputY ?? metrics.height / 2;
  let above = inputY;
  let below = metrics.height - inputY;
  const outputSlot = preferredMainOutput(nodeItem);
  const nextEdge = outputSlot
    ? graph.edges.find((graphEdge) => graphEdge.sourceNodeId === nodeId && graphEdge.sourceSlotId === outputSlot.id)
    : null;
  const nextVisiting = new Set(visiting);
  nextVisiting.add(nodeId);
  if (nextEdge && outputSlot) {
    const nextSpan = subtreeLaneSpan(graph, nextEdge.targetNodeId, cache, nextVisiting);
    const outputY = outputCenterOffset(graph, nodeItem, outputSlot.id);
    above = Math.max(above, inputY - outputY + nextSpan.above);
    below = Math.max(below, outputY - inputY + nextSpan.below);
  }
  return { above, below };
}

function buildBlocks(graph: GraphDocument): SlotBlock[] {
  const metricsCache = new Map<string, BlockMetrics>();
  return graph.nodes.map((nodeItem) => {
    const kind = blockKind(nodeItem.type);
    const position = nodeItem.position ?? fallbackPosition(nodeItem.id);
    const size = blockMetrics(graph, nodeItem, metricsCache);
    return {
      id: nodeItem.id,
      kind,
      branch: branchForNode(graph, nodeItem),
      type: nodeTypeLabel(nodeItem.type),
      title: nodeItem.displayName || nodeItem.id,
      summary: nodeSummary(nodeItem),
      x: position.x,
      y: position.y,
      width: size.width,
      height: size.height,
      inputY: size.inputY,
      outputOffsets: size.outputOffsets,
      selected: nodeItem.id === state.selectedNodeId,
    };
  });
}

function buildJoins(graph: GraphDocument, blocks: SlotBlock[]): SlotJoin[] {
  const blockById = new Map(blocks.map((block) => [block.id, block]));
  return connectedGraphEdges(graph)
    .map((graphEdge): SlotJoin | null => {
      const source = blockById.get(graphEdge.sourceNodeId);
      const target = blockById.get(graphEdge.targetNodeId);
      if (!source || !target) {
        return null;
      }
      const tone = graphEdge.sourceSlotId === 'pass' ? 'pass' : graphEdge.sourceSlotId === 'fail' ? 'fail' : 'normal';
      return {
        id: graphEdge.id,
        from: source.id,
        to: target.id,
        branch: tone === 'fail' ? 'fail' : tone === 'pass' ? 'pass' : target.branch,
        x: target.x - 2,
        y: target.y + (target.inputY ?? normalBlockHeight / 2) - 15,
        width: 20,
        tone,
      };
    })
    .filter((join): join is SlotJoin => join !== null);
}

function updateWorldSize(blocks: SlotBlock[]): void {
  const maxRight = blocks.reduce((right, block) => Math.max(right, block.x + block.width), 0);
  const maxBottom = blocks.reduce((bottom, block) => Math.max(bottom, block.y + block.height), 0);
  world.width = Math.max(2160, Math.ceil(maxRight + 260));
  world.height = Math.max(620, Math.ceil(maxBottom + 120));
}

function renderSlotJoin(join: SlotJoin): string {
  return `
    <button
      type="button"
      class="slot-join ${join.tone ?? 'normal'}"
      data-join="${escapeAttr(join.id)}"
      data-from="${escapeAttr(join.from)}"
      data-to="${escapeAttr(join.to)}"
      data-branch="${join.branch}"
      aria-label="积木拼接"
      style="left:${join.x}px; top:${join.y}px; width:${join.width}px"
    ></button>
  `;
}

function renderBlock(block: SlotBlock): string {
  const branchTabs = block.kind === 'condition' ? conditionBranchTabs(block) : '';

  return `
    <article
      class="logic-block ${block.kind} ${block.branch}${block.selected ? ' selected' : ''}${state.recentNodeId === block.id ? ' newly-added' : ''}"
      data-block="${escapeAttr(block.id)}"
      data-branch="${block.branch}"
      style="left:${block.x}px; top:${block.y}px; width:${block.width}px; height:${block.height}px; --condition-content-top:${Math.max(18, (block.inputY ?? 202) - 57)}px; z-index:${3000 - block.x + (block.selected ? 1000 : 0)}"
    >
      ${renderShape(puzzlePath(block), block.width, block.height, branchTabs)}
      <div class="block-topline">
        <span>${escapeHtml(block.type)}</span>
      </div>
      <h3>${escapeHtml(block.title)}</h3>
      <p>${escapeHtml(block.summary)}</p>
    </article>
  `;
}

function renderTrace(trace: ApiTrace | null): string {
  if (!trace) {
    return '<li class="trace-empty">暂无执行记录。启动 API 后点击“测试运行”。</li>';
  }

  return trace.steps
    .map((step) => `<li>[${escapeHtml(formatTime(step.timestamp))}] ${escapeHtml(humanizeTraceMessage(step.message))}</li>`)
    .join('');
}

function renderApp(): void {
  if (!app) {
    return;
  }

  const graph = currentGraph();
  const blocks = buildBlocks(graph);
  updateWorldSize(blocks);
  const joins = buildJoins(graph, blocks);
  const selectedNode = selectedNodeFrom(graph);
  const validationItems = validationList();
  const graphCount = graph.nodes.length;

  app.innerHTML = `
    <section class="workspace" aria-label="PixelLogic 槽位式横向积木流">
      <header class="topbar">
        <div class="brand">
          <span class="mark" aria-hidden="true"></span>
          <div>
            <strong>PixelLogic</strong>
            <small>当前流程：${escapeHtml(graph.displayName)}</small>
          </div>
        </div>
        <nav class="top-actions" aria-label="工作台操作">
          <span class="api-pill ${state.apiStatus}" data-api-status aria-live="polite">${escapeHtml(apiStatusText())}</span>
          <button type="button" class="ghost-button" data-history-action="undo" title="Ctrl+Z" ${canUndo() ? '' : 'disabled'}>上一步</button>
          <button type="button" class="ghost-button" data-history-action="redo" title="Ctrl+Y / Ctrl+Shift+Z" ${canRedo() ? '' : 'disabled'}>下一步</button>
          <button type="button" class="run-button" data-api-action="start" ${apiBusyAttr()}>测试运行</button>
          <button type="button" class="ghost-button" data-action="fit">适应视图</button>
          <button type="button" class="ghost-button" data-action="center">回到中心</button>
        </nav>
      </header>

      <aside class="left-rail" aria-label="流程列表和积木库">
        <section>
          <div class="panel-title">
            <span>流程列表</span>
            <button type="button" class="tiny-button">新建</button>
          </div>
          <button type="button" class="graph-item active">${escapeHtml(graph.displayName)} <small>${graphCount} 个积木</small></button>
        </section>

        <section>
          <div class="panel-title"><span>积木库</span></div>
          <div class="library-grid">
            <button type="button" data-library-kind="trigger">触发器</button>
            <button type="button" data-library-kind="condition">条件</button>
            <button type="button" data-library-kind="action">动作</button>
            <button type="button" data-library-kind="state">状态</button>
            <button type="button" data-library-kind="timer">计时器</button>
            <button type="button" data-library-kind="debug">调试</button>
          </div>
        </section>

        <section class="quick-start">
          <div class="panel-title"><span>当前版本</span></div>
          <button type="button">Committed ${escapeHtml(shortFingerprint(state.committedGraph?.fingerprint ?? graph.fingerprint))}</button>
          <button type="button" data-draft-status>${draftStatusText()}</button>
          <button type="button" data-dirty-status>${state.dirty ? '等待自动保存' : '无本地改动'}</button>
        </section>
      </aside>

      <main class="graph-stage" aria-label="逻辑画布">
        <div class="stage-head">
          <div>
            <p class="eyebrow">逻辑画布</p>
            <h1>槽位式横向积木流</h1>
          </div>
          <div class="canvas-tools" aria-label="画布状态">
            <span>缩放 <b data-zoom>92%</b></span>
            <span class="actor-chip">${escapeHtml(state.demoActor)}</span>
            <button type="button" data-action="focus">聚焦选中</button>
          </div>
        </div>
        <section class="canvas-viewport" aria-label="可拖动画布">
          <div class="flow-world" style="width:${world.width}px; height:${world.height}px">
            ${joins.map(renderSlotJoin).join('')}
            ${blocks.map(renderBlock).join('')}
          </div>
          <div class="drag-hint" data-drag-hint aria-live="polite"></div>
        </section>
      </main>

      <aside class="right-panel" aria-label="选中积木信息">
        <div class="panel-title">
          <span>选中积木</span>
          <b>${selectedNode ? escapeHtml(nodeTypeLabel(selectedNode.type)) : '未选中'}</b>
        </div>
        ${selectedNode ? renderNodeInfo(selectedNode) : '<section class="info-card">单击积木选中，拖动积木移动，双击积木编辑。</section>'}
        <section class="preview-card">
          <b>API 状态</b>
          <p>${escapeHtml(state.statusMessage)}</p>
        </section>
        <section class="validation-card">
          <b>最后动作</b>
          <p data-last-action>${escapeHtml(state.lastAction)}</p>
        </section>
        ${state.error ? `<section class="api-error" role="alert">${escapeHtml(state.error)}</section>` : ''}
      </aside>

      <footer class="bottom-dock" aria-label="验证问题和执行记录">
        <section>
          <div class="panel-title"><span>自动检查</span><b data-validation-title>${validationTitle()}</b></div>
          <ul class="issue-list" data-issue-list>
            <li><span class="${state.apiStatus === 'online' ? 'ok' : 'warn'}"></span>${escapeHtml(state.statusMessage)}</li>
            ${uncommittedNotice() ? `<li><span class="warn"></span>${escapeHtml(uncommittedNotice())}</li>` : ''}
            ${validationItems}
          </ul>
        </section>
        <section>
          <div class="panel-title"><span>执行记录</span><b>${state.latestTrace ? escapeHtml(shortTraceId(state.latestTrace.id)) : '无'}</b></div>
          <ol class="trace-list">
            ${renderTrace(state.latestTrace)}
          </ol>
        </section>
      </footer>
      ${state.editorOpen && selectedNode ? renderEditorModal(selectedNode) : ''}
    </section>
  `;

  bindInteractions();
  setTransform();
  focusEditor();
}

function renderNodeInfo(nodeItem: GraphNode): string {
  const configItems = nodeConfigItems(nodeItem);
  return `
    <section class="info-card">
      <b>${escapeHtml(nodeItem.displayName || nodeItem.id)}</b>
      <p>${escapeHtml(nodeSummary(nodeItem))}</p>
    </section>
    <section class="info-card">
      <b>配置摘要</b>
      ${configItems.length > 0 ? `
        <dl class="config-list">
          ${configItems
            .map((item) => `<div><dt>${escapeHtml(item.label)}</dt><dd>${escapeHtml(item.value)}</dd></div>`)
            .join('')}
        </dl>
      ` : '<p>该积木当前没有额外配置。</p>'}
    </section>
    ${renderConnectionInfo(nodeItem)}
    <section class="info-card">
      <b>提示</b>
      <p>单击选中，拖动移动积木和后续链条，双击打开编辑窗口。</p>
    </section>
  `;
}

function renderConnectionInfo(nodeItem: GraphNode): string {
  const graph = currentGraph();
  const edges = connectedGraphEdges(graph);
  const incoming = edges.filter((graphEdge) => graphEdge.targetNodeId === nodeItem.id);
  const outgoing = edges.filter((graphEdge) => graphEdge.sourceNodeId === nodeItem.id);
  const canDelete = !Object.values(graph.triggerEntries).includes(nodeItem.id);

  return `
    <section class="info-card connection-card">
      <b>连接</b>
      <dl class="config-list">
        <div><dt>输入</dt><dd>${escapeHtml(incoming.length > 0 ? incoming.map(edgeSummary).join('；') : '未连接')}</dd></div>
        <div><dt>输出</dt><dd>${escapeHtml(outgoing.length > 0 ? outgoing.map(edgeSummary).join('；') : '未连接')}</dd></div>
      </dl>
      <div class="info-actions">
        <button type="button" class="ghost-button" data-graph-action="disconnect-input" ${incoming.length === 0 ? 'disabled' : ''}>断开输入</button>
        <button type="button" class="ghost-button danger" data-graph-action="delete-selected" ${canDelete ? '' : 'disabled'}>删除积木</button>
      </div>
    </section>
  `;
}

function edgeSummary(graphEdge: GraphEdge): string {
  const graph = currentGraph();
  const source = graph.nodes.find((nodeItem) => nodeItem.id === graphEdge.sourceNodeId);
  const target = graph.nodes.find((nodeItem) => nodeItem.id === graphEdge.targetNodeId);
  return `${source?.displayName ?? graphEdge.sourceNodeId}.${slotLabel(graphEdge.sourceSlotId)} -> ${target?.displayName ?? graphEdge.targetNodeId}`;
}

function renderEditorModal(nodeItem: GraphNode): string {
  const title = `${nodeTypeLabel(nodeItem.type)}：${nodeItem.displayName || nodeItem.id}`;
  const modalIssue = state.error || validationSummaryText();

  return `
    <div class="editor-overlay${state.editorClosing ? ' is-closing' : ''}" data-modal-overlay>
      <section class="editor-dialog" role="dialog" aria-modal="true" aria-labelledby="block-editor-title">
        <header class="editor-head">
          <div>
            <p class="eyebrow">积木编辑</p>
            <h2 id="block-editor-title" tabindex="-1">${escapeHtml(title)}</h2>
          </div>
          <button type="button" class="modal-close" data-modal-action="close" aria-label="关闭编辑窗口">×</button>
        </header>
        <div class="editor-body">
          <section class="editor-summary">
            <b>当前摘要</b>
            <p data-modal-summary>${escapeHtml(nodeSummary(nodeItem))}</p>
          </section>
          ${renderNodeEditor(nodeItem)}
          ${state.error || state.validation ? `
            <section class="editor-issues" role="${state.error ? 'alert' : 'status'}">
              <b>${state.error ? '自动保存提示' : '检查结果'}</b>
              <p data-modal-error>${escapeHtml(modalIssue)}</p>
            </section>
          ` : ''}
        </div>
        <footer class="editor-actions">
          <button type="button" class="ghost-button" data-modal-action="cancel">关闭</button>
        </footer>
      </section>
    </div>
  `;
}

function renderNodeEditor(nodeItem: GraphNode): string {
  const section = editorSection(nodeItem);

  return `
    <section class="form-card editor-section">
      <b>基础信息</b>
      <div class="field-grid">
        <label class="field-row is-full">名称<input value="${escapeAttr(nodeItem.displayName)}" data-node-field="displayName" /></label>
        <div class="readonly-field"><span>积木类型</span><b>${escapeHtml(nodeTypeLabel(nodeItem.type))}</b></div>
      </div>
    </section>
    <section class="form-card editor-section">
      <b>${escapeHtml(section.title)}</b>
      ${section.fields.length > 0 ? `
        <div class="field-grid">
          ${section.fields.map(renderEditableField).join('')}
        </div>
      ` : '<p class="field-hint">这个积木当前只需要修改名称。</p>'}
    </section>
  `;
}

function renderEditableField(field: EditableField): string {
  const inputType = field.control === 'number' ? 'number' : 'text';
  const control = field.control === 'select'
    ? `
      <select data-config-key="${escapeAttr(field.key)}">
        ${(field.options ?? [])
          .map((option) => `<option value="${escapeAttr(option.value)}"${option.value === field.value ? ' selected' : ''}>${escapeHtml(option.label)}</option>`)
          .join('')}
      </select>
    `
    : field.control === 'boolean'
      ? `
        <div class="segmented-control" role="group" aria-label="${escapeAttr(field.label)}">
          ${booleanOptions()
            .map((option) => `
              <button type="button" data-config-key="${escapeAttr(field.key)}" data-config-value="${escapeAttr(option.value)}" aria-pressed="${option.value === field.value}">
                ${escapeHtml(option.label)}
              </button>
            `)
            .join('')}
        </div>
      `
      : `
        <span class="input-with-suffix">
          <input type="${inputType}" value="${escapeAttr(field.value)}" data-config-key="${escapeAttr(field.key)}" />
          ${field.suffix ? `<span>${escapeHtml(field.suffix)}</span>` : ''}
        </span>
      `;

  return `
    <label class="field-row${field.full ? ' is-full' : ''}">${escapeHtml(field.label)}
      ${control}
    </label>
  `;
}

function editorSection(nodeItem: GraphNode): EditorSection {
  switch (nodeItem.type) {
    case 'STATE_COMPARE_CONDITION':
      return { title: '条件设置', fields: editableFields(nodeItem) };
    case 'STATE_SET_ACTION':
    case 'STATE_ADD_ACTION':
      return { title: '状态设置', fields: editableFields(nodeItem) };
    case 'MESSAGE_ACTION':
      return { title: '消息内容', fields: editableFields(nodeItem) };
    case 'TIMER_START_ACTION':
      return { title: '计时设置', fields: editableFields(nodeItem) };
    case 'DEBUG_LOG_ACTION':
      return { title: '记录内容', fields: editableFields(nodeItem) };
    default:
      return { title: '配置内容', fields: editableFields(nodeItem) };
  }
}

function editableFields(nodeItem: GraphNode): EditableField[] {
  const config = nodeItem.config;
  switch (nodeItem.type) {
    case 'STATE_COMPARE_CONDITION':
      return [
        { label: '作用对象', key: 'scope', value: config.scope ?? 'PLAYER', control: 'select', options: stateScopeOptions() },
        { label: '状态名', key: 'key', value: config.key ?? '', control: 'text' },
        { label: '目标值', key: 'expected', value: config.expected ?? 'false', control: 'boolean' },
        { label: '缺失时视为', key: 'missing', value: config.missing ?? 'false', control: 'boolean' },
      ];
    case 'MESSAGE_ACTION':
      return [{ label: '消息', key: 'message', value: config.message ?? '', control: 'text', full: true }];
    case 'DEBUG_LOG_ACTION':
      return [{ label: '内容', key: 'message', value: config.message ?? '', control: 'text', full: true }];
    case 'STATE_SET_ACTION':
      return [
        { label: '作用对象', key: 'scope', value: config.scope ?? 'PLAYER', control: 'select', options: stateScopeOptions() },
        { label: '状态名', key: 'key', value: config.key ?? '', control: 'text' },
        { label: '数据类型', key: 'valueType', value: config.valueType ?? 'BOOLEAN', control: 'select', options: valueTypeOptions() },
        { label: '设置为', key: 'value', value: config.value ?? '', control: config.valueType === 'BOOLEAN' ? 'boolean' : 'text' },
      ];
    case 'STATE_ADD_ACTION':
      return [
        { label: '作用对象', key: 'scope', value: config.scope ?? 'PLAYER', control: 'select', options: stateScopeOptions() },
        { label: '状态名', key: 'key', value: config.key ?? '', control: 'text' },
        { label: '增加数值', key: 'amount', value: config.amount ?? '1', control: 'number' },
      ];
    case 'TIMER_START_ACTION':
      return [{ label: '等待时间', key: 'durationSeconds', value: config.durationSeconds ?? '30', control: 'number', suffix: '秒' }];
    default:
      return [];
  }
}

function nodeConfigItems(nodeItem: GraphNode): Array<{ label: string; value: string }> {
  return editableFields(nodeItem).map((field) => ({ label: field.label, value: displayFieldValue(field) }));
}

function displayFieldValue(field: EditableField): string {
  if (!field.value) {
    return '未填写';
  }
  if (field.control === 'boolean') {
    return booleanLabel(field.value);
  }
  if (field.control === 'select') {
    return field.options?.find((option) => option.value === field.value)?.label ?? field.value;
  }
  return field.suffix ? `${field.value} ${field.suffix}` : field.value;
}

function booleanOptions(): FieldOption[] {
  return [
    { value: 'true', label: '是' },
    { value: 'false', label: '否' },
  ];
}

function stateScopeOptions(): FieldOption[] {
  return [
    { value: 'PLAYER', label: '玩家' },
    { value: 'GLOBAL', label: '全局' },
    { value: 'SESSION', label: '当前会话' },
  ];
}

function valueTypeOptions(): FieldOption[] {
  return [
    { value: 'BOOLEAN', label: '是或否' },
    { value: 'INTEGER', label: '数字' },
    { value: 'STRING', label: '文本' },
  ];
}

function setTransform(): void {
  const worldEl = document.querySelector<HTMLDivElement>('.flow-world');
  const zoomText = document.querySelector<HTMLElement>('[data-zoom]');

  if (!worldEl) {
    return;
  }

  worldEl.style.transform = `translate(${offsetX}px, ${offsetY}px) scale(${scale})`;

  if (zoomText) {
    zoomText.textContent = `${Math.round(scale * 100)}%`;
  }
}

function fitView(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');

  if (!viewport) {
    return;
  }

  const rect = viewport.getBoundingClientRect();
  scale = Math.min(1, (rect.width - 72) / world.width, (rect.height - 56) / world.height);
  offsetX = Math.max(26, (rect.width - world.width * scale) / 2);
  offsetY = Math.max(22, (rect.height - world.height * scale) / 2);
  setTransform();
}

function centerView(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');

  if (!viewport) {
    return;
  }

  const rect = viewport.getBoundingClientRect();
  scale = 0.78;
  offsetX = rect.width > 1200 ? -28 : 28;
  offsetY = Math.max(24, (rect.height - world.height * scale) / 2);
  setTransform();
}

function focusSelectedBlock(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');
  const selected = buildBlocks(currentGraph()).find((block) => block.selected);

  if (!viewport || !selected) {
    return;
  }

  const rect = viewport.getBoundingClientRect();
  scale = Math.max(0.76, Math.min(1.02, scale));
  offsetX = rect.width / 2 - (selected.x + selected.width / 2) * scale;
  offsetY = rect.height / 2 - (selected.y + selected.height / 2) * scale;
  setTransform();
}

function clearFocus(): void {
  document.querySelectorAll('.logic-block, .slot-join').forEach((item) => {
    item.classList.remove('is-related');
  });
}

function markJoinFocus(joinEl: HTMLElement): void {
  clearFocus();
  joinEl.classList.add('is-related');
  document.querySelector(`[data-block="${joinEl.dataset.from}"]`)?.classList.add('is-related');
  document.querySelector(`[data-block="${joinEl.dataset.to}"]`)?.classList.add('is-related');
}

function markSelectedFocus(): void {
  const selected = state.selectedNodeId;

  document.querySelector(`[data-block="${selected}"]`)?.classList.add('is-related');
  document.querySelectorAll<HTMLElement>('.slot-join').forEach((joinEl) => {
    if (joinEl.dataset.from === selected || joinEl.dataset.to === selected) {
      joinEl.classList.add('is-related');
    }
  });
}

function bindInteractions(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');

  if (!viewport) {
    return;
  }

  viewport.addEventListener('pointerdown', (event) => {
    const target = event.target as HTMLElement;
    const blockEl = target.closest<HTMLElement>('.logic-block');
    if (blockEl?.dataset.block) {
      event.preventDefault();
      beginBlockPointerDown(event, blockEl.dataset.block, viewport);
      return;
    }

    if (target.closest('.slot-join, button, input, select')) {
      return;
    }

    isPanning = true;
    panStart = { x: event.clientX, y: event.clientY };
    panOffset = { x: offsetX, y: offsetY };
    viewport.classList.add('is-dragging');
    viewport.setPointerCapture(event.pointerId);
  });

  viewport.addEventListener('pointermove', (event) => {
    if (activeBlockDrag?.pointerId === event.pointerId) {
      moveBlockDrag(event);
      return;
    }

    if (!isPanning) {
      return;
    }

    offsetX = panOffset.x + event.clientX - panStart.x;
    offsetY = panOffset.y + event.clientY - panStart.y;
    setTransform();
  });

  viewport.addEventListener('pointerup', (event) => {
    if (activeBlockDrag?.pointerId === event.pointerId) {
      endBlockDrag(event, viewport);
      return;
    }

    isPanning = false;
    viewport.classList.remove('is-dragging');
    if (viewport.hasPointerCapture(event.pointerId)) {
      viewport.releasePointerCapture(event.pointerId);
    }
  });

  viewport.addEventListener('pointercancel', (event) => {
    if (activeBlockDrag?.pointerId === event.pointerId) {
      cancelBlockDrag(viewport);
      return;
    }
    isPanning = false;
    viewport.classList.remove('is-dragging');
  });

  viewport.addEventListener(
    'wheel',
    (event) => {
      event.preventDefault();
      const previous = scale;
      const next = Math.min(1.22, Math.max(0.58, scale + (event.deltaY > 0 ? -0.06 : 0.06)));
      const rect = viewport.getBoundingClientRect();
      const pointerX = event.clientX - rect.left;
      const pointerY = event.clientY - rect.top;
      const worldX = (pointerX - offsetX) / previous;
      const worldY = (pointerY - offsetY) / previous;
      scale = next;
      offsetX = pointerX - worldX * scale;
      offsetY = pointerY - worldY * scale;
      setTransform();
    },
    { passive: false },
  );

  document.querySelector('[data-action="fit"]')?.addEventListener('click', fitView);
  document.querySelector('[data-action="center"]')?.addEventListener('click', centerView);
  document.querySelector('[data-action="focus"]')?.addEventListener('click', focusSelectedBlock);
  document.querySelector('[data-api-action="start"]')?.addEventListener('click', () => void startTest());
  document.querySelector('[data-history-action="undo"]')?.addEventListener('click', undoGraphEdit);
  document.querySelector('[data-history-action="redo"]')?.addEventListener('click', redoGraphEdit);
  document.querySelector('[data-graph-action="disconnect-input"]')?.addEventListener('click', disconnectSelectedInput);
  document.querySelector('[data-graph-action="delete-selected"]')?.addEventListener('click', deleteSelectedNode);
  document.querySelector('[data-modal-action="close"]')?.addEventListener('click', requestCloseEditor);
  document.querySelector('[data-modal-action="cancel"]')?.addEventListener('click', requestCloseEditor);
  document.querySelectorAll<HTMLButtonElement>('[data-library-kind]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      const kind = buttonEl.dataset.libraryKind as LibraryKind | undefined;
      if (kind) {
        addLibraryBlock(kind);
      }
    });
  });
  document.querySelector('.editor-overlay')?.addEventListener('pointerdown', (event) => {
    if ((event.target as HTMLElement).hasAttribute('data-modal-overlay')) {
      requestCloseEditor();
    }
  });

  document.onkeydown = (event) => {
    if (isUndoShortcut(event)) {
      event.preventDefault();
      undoGraphEdit();
      return;
    }
    if (isRedoShortcut(event)) {
      event.preventDefault();
      redoGraphEdit();
      return;
    }
    if (event.key === 'Escape' && state.editorOpen) {
      event.preventDefault();
      requestCloseEditor();
    }
    if (event.key === 'Tab' && state.editorOpen) {
      trapEditorFocus(event);
    }
  };
  window.onbeforeunload = state.dirty || state.hasDraft || autoSaveInFlight ? () => '还有修改正在自动保存，确定要离开吗？' : null;

  document.querySelectorAll<HTMLInputElement | HTMLSelectElement>('[data-node-field], [data-config-key]').forEach((inputEl) => {
    if (inputEl instanceof HTMLSelectElement) {
      inputEl.addEventListener('change', () => updateSelectedNode(inputEl));
      return;
    }
    inputEl.addEventListener('input', () => updateSelectedNode(inputEl));
  });
  document.querySelectorAll<HTMLButtonElement>('[data-config-value]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      if (buttonEl.dataset.configKey && buttonEl.dataset.configValue) {
        updateSelectedNodeValue(buttonEl.dataset.configKey, buttonEl.dataset.configValue);
        document.querySelectorAll<HTMLButtonElement>(`[data-config-key="${buttonEl.dataset.configKey}"][data-config-value]`).forEach((item) => {
          item.setAttribute('aria-pressed', String(item === buttonEl));
        });
      }
    });
  });

  document.querySelectorAll<HTMLElement>('.slot-join').forEach((joinEl) => {
    joinEl.addEventListener('mouseenter', () => markJoinFocus(joinEl));
    joinEl.addEventListener('mouseleave', () => {
      clearFocus();
      markSelectedFocus();
    });
  });

  markSelectedFocus();
}

function beginBlockPointerDown(event: PointerEvent, nodeId: string, viewport: HTMLElement): void {
  const graph = currentGraph();
  if (!graph.nodes.some((nodeItem) => nodeItem.id === nodeId)) {
    return;
  }

  if (event.detail >= 2) {
    lastBlockClick = null;
    openEditor(nodeId);
    return;
  }

  state.selectedNodeId = nodeId;
  state.recentNodeId = null;
  const groupIds = downstreamNodeIds(graph, nodeId);
  const startPositions = new Map<string, GraphPosition>();
  groupIds.forEach((id) => startPositions.set(id, nodePosition(graph, id)));
  activeBlockDrag = {
    pointerId: event.pointerId,
    rootId: nodeId,
    groupIds,
    started: false,
    startClient: { x: event.clientX, y: event.clientY },
    startWorld: pointerToWorld(event),
    startPositions,
    previewPositions: new Map(startPositions),
    joins: buildJoins(graph, buildBlocks(graph)),
    candidate: null,
  };
  viewport.setPointerCapture(event.pointerId);
}

function moveBlockDrag(event: PointerEvent): void {
  const drag = activeBlockDrag;
  if (!drag) {
    return;
  }

  const clientDx = event.clientX - drag.startClient.x;
  const clientDy = event.clientY - drag.startClient.y;
  if (!drag.started && Math.hypot(clientDx, clientDy) < dragThreshold) {
    return;
  }

  if (!drag.started) {
    drag.started = true;
    lastBlockClick = null;
    state.selectedNodeId = drag.rootId;
    state.recentNodeId = null;
    document.querySelector('.canvas-viewport')?.classList.add('is-block-dragging');
    drag.groupIds.forEach((id) => document.querySelector<HTMLElement>(`[data-block="${id}"]`)?.classList.add('is-chain-dragging'));
    document.querySelector<HTMLElement>(`[data-block="${drag.rootId}"]`)?.classList.add('is-drag-root', 'selected');
    clearFocus();
    setDragHint('拖动整链中，靠近两个积木之间会自动吸附插入。', 'active');
  }

  const current = pointerToWorld(event);
  const worldDx = current.x - drag.startWorld.x;
  const worldDy = current.y - drag.startWorld.y;
  drag.previewPositions = new Map();
  drag.groupIds.forEach((id) => {
    const start = drag.startPositions.get(id);
    if (!start) {
      return;
    }
    const next = {
      x: Math.round(start.x + worldDx),
      y: Math.round(start.y + worldDy),
    };
    drag.previewPositions.set(id, next);
    const blockEl = document.querySelector<HTMLElement>(`[data-block="${id}"]`);
    if (blockEl) {
      blockEl.style.left = `${next.x}px`;
      blockEl.style.top = `${next.y}px`;
    }
  });

  drag.candidate = findInsertCandidate(drag);
  renderInsertPreview(drag);
}

function endBlockDrag(event: PointerEvent, viewport: HTMLElement): void {
  const drag = activeBlockDrag;
  if (!drag) {
    return;
  }

  activeBlockDrag = null;
  clearInsertPreview();
  setDragHint('', '');
  viewport.classList.remove('is-block-dragging');
  if (viewport.hasPointerCapture(event.pointerId)) {
    viewport.releasePointerCapture(event.pointerId);
  }

  if (!drag.started) {
    selectOrOpenBlock(drag.rootId);
    return;
  }

  snapDraggedGroupToCandidate(currentGraph(), drag);
  const nextGraph = cloneGraph(currentGraph());
  nextGraph.nodes = nextGraph.nodes.map((nodeItem) => {
    const nextPosition = drag.previewPositions.get(nodeItem.id);
    return nextPosition ? { ...nodeItem, position: nextPosition } : nodeItem;
  });
  nextGraph.edges = connectedGraphEdges(nextGraph);
  const inserted = drag.candidate?.valid ? insertDraggedGroup(nextGraph, drag) : false;
  if (inserted) {
    makeInsertionGap(nextGraph, drag);
  }
  const actionText = inserted
    ? connectedActionText(drag.candidate)
    : '位置已更新，正在自动保存。';
  applyGraphEdit(nextGraph, actionText, { selectedNodeId: drag.rootId, recentNodeId: null });
}

function connectedActionText(candidate: InsertCandidate | null): string {
  if (candidate?.kind === 'append') {
    return '已连接到链尾，正在自动保存。';
  }
  if (candidate?.kind === 'attach') {
    return '已连接到后面的积木，正在自动保存。';
  }
  return '已插入到连接处，正在自动保存。';
}

function selectOrOpenBlock(nodeId: string): void {
  const now = window.performance.now();
  const isDoubleClick = lastBlockClick?.nodeId === nodeId && now - lastBlockClick.time <= doubleClickMs;

  if (isDoubleClick) {
    lastBlockClick = null;
    openEditor(nodeId);
    return;
  }

  lastBlockClick = { nodeId, time: now };
  state.selectedNodeId = nodeId;
  state.recentNodeId = null;
  state.error = '';
  renderApp();
}

function cancelBlockDrag(viewport: HTMLElement): void {
  activeBlockDrag = null;
  clearInsertPreview();
  setDragHint('', '');
  viewport.classList.remove('is-block-dragging');
  renderApp();
}

function pointerToWorld(event: PointerEvent): GraphPosition {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');
  if (!viewport) {
    return { x: 0, y: 0 };
  }
  const rect = viewport.getBoundingClientRect();
  return {
    x: (event.clientX - rect.left - offsetX) / scale,
    y: (event.clientY - rect.top - offsetY) / scale,
  };
}

function downstreamNodeIds(graph: GraphDocument, rootId: string): string[] {
  const outgoing = new Map<string, GraphEdge[]>();
  connectedGraphEdges(graph).forEach((graphEdge) => {
    const list = outgoing.get(graphEdge.sourceNodeId) ?? [];
    list.push(graphEdge);
    outgoing.set(graphEdge.sourceNodeId, list);
  });

  const visited = new Set<string>();
  const ordered: string[] = [];
  const stack = [rootId];
  while (stack.length > 0) {
    const nextId = stack.pop();
    if (!nextId || visited.has(nextId)) {
      continue;
    }
    visited.add(nextId);
    ordered.push(nextId);
    for (const graphEdge of outgoing.get(nextId) ?? []) {
      if (!visited.has(graphEdge.targetNodeId)) {
        stack.push(graphEdge.targetNodeId);
      }
    }
  }
  return ordered;
}

function connectedComponentNodeIds(graph: GraphDocument, rootId: string, omittedEdgeId: string): string[] {
  const neighbors = new Map<string, string[]>();
  connectedGraphEdges(graph)
    .filter((graphEdge) => graphEdge.id !== omittedEdgeId)
    .forEach((graphEdge) => {
      neighbors.set(graphEdge.sourceNodeId, [...(neighbors.get(graphEdge.sourceNodeId) ?? []), graphEdge.targetNodeId]);
      neighbors.set(graphEdge.targetNodeId, [...(neighbors.get(graphEdge.targetNodeId) ?? []), graphEdge.sourceNodeId]);
    });
  const visited = new Set<string>();
  const ordered: string[] = [];
  const stack = [rootId];
  while (stack.length > 0) {
    const nextId = stack.pop();
    if (!nextId || visited.has(nextId)) {
      continue;
    }
    visited.add(nextId);
    ordered.push(nextId);
    for (const neighborId of neighbors.get(nextId) ?? []) {
      if (!visited.has(neighborId)) {
        stack.push(neighborId);
      }
    }
  }
  return ordered;
}

function nodePosition(graph: GraphDocument, nodeId: string): GraphPosition {
  return graph.nodes.find((nodeItem) => nodeItem.id === nodeId)?.position ?? fallbackPosition(nodeId);
}

function canUndo(): boolean {
  return undoStack.length > 0;
}

function canRedo(): boolean {
  return redoStack.length > 0;
}

function graphHistorySnapshot(): GraphHistoryEntry {
  return {
    graph: cloneGraph(currentGraph()),
    selectedNodeId: state.selectedNodeId,
    recentNodeId: state.recentNodeId,
    lastAction: state.lastAction,
  };
}

function rememberGraphState(): void {
  undoStack.push(graphHistorySnapshot());
  if (undoStack.length > historyLimit) {
    undoStack.shift();
  }
  redoStack.length = 0;
}

function resetGraphHistory(): void {
  undoStack.length = 0;
  redoStack.length = 0;
  graphVersion = 0;
  if (autoSaveTimer !== null) {
    window.clearTimeout(autoSaveTimer);
    autoSaveTimer = null;
  }
}

function applyGraphEdit(
  graph: GraphDocument,
  lastAction: string,
  options: { selectedNodeId?: string; recentNodeId?: string | null; refreshOnly?: boolean } = {},
): void {
  const nextGraph = normalizeConditionBranchLayout(graph);
  rememberGraphState();
  graphVersion += 1;
  state.graph = nextGraph;
  state.dirty = true;
  state.validation = null;
  state.error = '';
  state.lastAction = lastAction;
  if (options.selectedNodeId !== undefined) {
    state.selectedNodeId = options.selectedNodeId;
  }
  if ('recentNodeId' in options) {
    state.recentNodeId = options.recentNodeId ?? null;
  }
  scheduleAutoSave();
  if (options.refreshOnly) {
    refreshDraftIndicators();
  } else {
    renderApp();
  }
}

function restoreGraphHistory(entry: GraphHistoryEntry, lastAction: string): void {
  graphVersion += 1;
  state.graph = normalizeConditionBranchLayout(entry.graph);
  state.selectedNodeId = entry.selectedNodeId;
  state.recentNodeId = entry.recentNodeId;
  ensureSelectedNode();
  state.dirty = true;
  state.validation = null;
  state.error = '';
  state.lastAction = lastAction;
  scheduleAutoSave();
  renderApp();
}

function undoGraphEdit(): void {
  const previous = undoStack.pop();
  if (!previous) {
    return;
  }
  redoStack.push(graphHistorySnapshot());
  restoreGraphHistory(previous, `已撤销：${previous.lastAction}`);
}

function redoGraphEdit(): void {
  const next = redoStack.pop();
  if (!next) {
    return;
  }
  undoStack.push(graphHistorySnapshot());
  restoreGraphHistory(next, `已重做：${next.lastAction}`);
}

function normalizeConditionBranchLayout(graph: GraphDocument): GraphDocument {
  const nextGraph = cloneGraph(graph);
  const nodeById = new Map(nextGraph.nodes.map((nodeItem) => [nodeItem.id, nodeItem]));
  const outgoing = new Map<string, GraphEdge[]>();
  const incoming = new Set<string>();
  nextGraph.edges.forEach((graphEdge) => {
    outgoing.set(graphEdge.sourceNodeId, [...(outgoing.get(graphEdge.sourceNodeId) ?? []), graphEdge]);
    incoming.add(graphEdge.targetNodeId);
  });

  const roots = [
    ...Object.values(nextGraph.triggerEntries),
    ...nextGraph.nodes.filter((nodeItem) => !incoming.has(nodeItem.id)).map((nodeItem) => nodeItem.id),
  ];
  const visitedEdges = new Set<string>();
  const alignDownstream = (sourceId: string, path: Set<string>) => {
    if (path.has(sourceId)) {
      return;
    }
    const source = nodeById.get(sourceId);
    if (!source) {
      return;
    }
    const sourcePosition = source.position ?? fallbackPosition(source.id);
    const nextPath = new Set(path);
    nextPath.add(sourceId);
    for (const graphEdge of outgoing.get(sourceId) ?? []) {
      if (visitedEdges.has(graphEdge.id)) {
        continue;
      }
      visitedEdges.add(graphEdge.id);
      const target = nodeById.get(graphEdge.targetNodeId);
      if (!target) {
        continue;
      }
      const targetInputY = inputCenterOffset(nextGraph, target);
      if (targetInputY === null) {
        continue;
      }
      const targetPosition = target.position ?? fallbackPosition(target.id);
      target.position = {
        ...targetPosition,
        y: Math.round(sourcePosition.y + outputCenterOffset(nextGraph, source, graphEdge.sourceSlotId) - targetInputY),
      };
      alignDownstream(target.id, nextPath);
    }
  };

  roots.forEach((rootId) => alignDownstream(rootId, new Set()));
  return nextGraph;
}

function isUndoShortcut(event: KeyboardEvent): boolean {
  return !event.isComposing
    && (event.ctrlKey || event.metaKey)
    && !event.altKey
    && !event.shiftKey
    && event.key.toLowerCase() === 'z';
}

function isRedoShortcut(event: KeyboardEvent): boolean {
  const key = event.key.toLowerCase();
  return !event.isComposing
    && (event.ctrlKey || event.metaKey)
    && !event.altKey
    && (key === 'y' || (event.shiftKey && key === 'z'));
}

function connectedGraphEdges(graph: GraphDocument): GraphEdge[] {
  return graph.edges.filter((graphEdge) => isVisuallyConnectedEdge(graph, graphEdge));
}

function isVisuallyConnectedEdge(graph: GraphDocument, graphEdge: GraphEdge): boolean {
  const source = graph.nodes.find((nodeItem) => nodeItem.id === graphEdge.sourceNodeId);
  const target = graph.nodes.find((nodeItem) => nodeItem.id === graphEdge.targetNodeId);
  if (!source || !target) {
    return false;
  }

  const targetInputY = inputCenterOffset(graph, target);
  if (targetInputY === null) {
    return false;
  }

  const sourcePosition = source.position ?? fallbackPosition(source.id);
  const targetPosition = target.position ?? fallbackPosition(target.id);
  const sourceSize = blockMetrics(graph, source);
  const expectedTargetX = sourcePosition.x + sourceSize.width - connectedOverlap;
  const expectedTargetInputY = sourcePosition.y + outputCenterOffset(graph, source, graphEdge.sourceSlotId);
  return Math.abs(targetPosition.x - expectedTargetX) <= visualConnectXTolerance
    && Math.abs(targetPosition.y + targetInputY - expectedTargetInputY) <= visualConnectYTolerance;
}

function findInsertCandidate(drag: BlockDrag): InsertCandidate | null {
  const graph = graphWithPreviewPositions(currentGraph(), drag);
  const rootNode = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const rootPosition = drag.previewPositions.get(drag.rootId);
  if (!rootNode || !rootPosition) {
    return null;
  }

  const rootInputY = inputCenterOffset(graph, rootNode);
  const group = new Set(drag.groupIds);
  let best: { candidate: InsertCandidate; score: number } | null = null;

  if (rootInputY !== null) {
    const rootAnchor = { x: rootPosition.x, y: rootPosition.y + rootInputY };
    for (const join of drag.joins) {
      const edgeItem = graph.edges.find((graphEdge) => graphEdge.id === join.id);
      if (!edgeItem) {
        continue;
      }
      const score = snapScore(rootAnchor, join);
      if (score === null) {
        continue;
      }
      if (!best || score < best.score) {
        const check = canInsertIntoEdge(graph, edgeItem, drag);
        if (!check.valid && (group.has(edgeItem.sourceNodeId) || group.has(edgeItem.targetNodeId))) {
          continue;
        }
        best = { candidate: { kind: 'insert', edge: edgeItem, join, valid: check.valid, message: check.message }, score };
      }
    }

    for (const candidate of appendCandidates(graph, drag)) {
      const score = candidateSnapScore(graph, drag, candidate, rootAnchor);
      if (score === null) {
        continue;
      }
      if (!best || score < best.score) {
        best = { candidate, score };
      }
    }
  }

  const tailAnchor = draggedTailAnchor(graph, drag);
  if (tailAnchor) {
    for (const candidate of attachCandidates(graph, drag)) {
      const score = candidateSnapScore(graph, drag, candidate, tailAnchor);
      if (score === null) {
        continue;
      }
      if (!best || score < best.score) {
        best = { candidate, score };
      }
    }
  }

  return best?.candidate ?? null;
}

function graphWithPreviewPositions(graph: GraphDocument, drag: BlockDrag): GraphDocument {
  return {
    ...graph,
    nodes: graph.nodes.map((nodeItem) => {
      const position = drag.previewPositions.get(nodeItem.id);
      return position ? { ...nodeItem, position } : nodeItem;
    }),
  };
}

function snapScore(anchor: GraphPosition, join: SlotJoin): number | null {
  const center = { x: join.x + join.width / 2, y: join.y + 15 };
  const dx = Math.abs(anchor.x - center.x);
  const dy = Math.abs(anchor.y - center.y);
  return dx <= insertSnapX && dy <= insertSnapY ? dx + dy * 1.35 : null;
}

function candidateSnapScore(graph: GraphDocument, drag: BlockDrag, candidate: InsertCandidate, anchor: GraphPosition): number | null {
  return isReconnectCandidate(graph, drag, candidate)
    ? snapScoreWithin(anchor, candidate.join, reconnectSnapX, reconnectSnapY)
    : snapScore(anchor, candidate.join);
}

function snapScoreWithin(anchor: GraphPosition, join: SlotJoin, snapX: number, snapY: number): number | null {
  const center = { x: join.x + join.width / 2, y: join.y + 15 };
  const dx = Math.abs(anchor.x - center.x);
  const dy = Math.abs(anchor.y - center.y);
  return dx <= snapX && dy <= snapY ? dx + dy * 1.35 : null;
}

function isReconnectCandidate(graph: GraphDocument, drag: BlockDrag, candidate: InsertCandidate): boolean {
  if (candidate.kind === 'append') {
    const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
    const rootInput = root?.slots.find((slot) => slot.direction === 'INPUT');
    return Boolean(rootInput && graph.edges.some((graphEdge) =>
      graphEdge.sourceNodeId === candidate.sourceNodeId
      && graphEdge.sourceSlotId === candidate.sourceSlotId
      && graphEdge.targetNodeId === drag.rootId
      && graphEdge.targetSlotId === rootInput.id,
    ));
  }
  if (candidate.kind === 'attach') {
    return graph.edges.some((graphEdge) =>
      graphEdge.sourceNodeId === candidate.sourceNodeId
      && graphEdge.sourceSlotId === candidate.sourceSlotId
      && graphEdge.targetNodeId === candidate.targetNodeId
      && graphEdge.targetSlotId === candidate.targetSlotId,
    );
  }
  return false;
}

function inputCenterOffset(graph: GraphDocument, nodeItem: GraphNode): number | null {
  if (!nodeItem.slots.some((slot) => slot.direction === 'INPUT')) {
    return null;
  }
  return blockMetrics(graph, nodeItem).inputY;
}

function outputCenterOffset(graph: GraphDocument, sourceNode: GraphNode, sourceSlotId: string): number {
  return blockMetrics(graph, sourceNode).outputOffsets[sourceSlotId] ?? normalBlockHeight / 2;
}

function appendCandidates(graph: GraphDocument, drag: BlockDrag): InsertCandidate[] {
  const group = new Set(drag.groupIds);
  const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const rootInput = root?.slots.find((slot) => slot.direction === 'INPUT');
  if (!root || !rootInput) {
    return [];
  }

  const connectedEdges = connectedGraphEdges(graph);
  return graph.nodes.flatMap((source) => {
    if (group.has(source.id)) {
      return [];
    }
    const sourcePosition = source.position ?? fallbackPosition(source.id);
    const sourceSize = blockMetrics(graph, source);
    return source.slots
      .filter((slot) => slot.direction === 'OUTPUT')
      .filter((slot) => slot.edgeType === rootInput.edgeType)
      .filter((slot) => !connectedEdges.some((edgeItem) => edgeItem.sourceNodeId === source.id && edgeItem.sourceSlotId === slot.id))
      .map((slot): InsertCandidate => ({
        kind: 'append',
        sourceNodeId: source.id,
        sourceSlotId: slot.id,
        join: {
          id: `append:${source.id}:${slot.id}`,
          from: source.id,
          to: drag.rootId,
          branch: slot.id === 'fail' ? 'fail' : slot.id === 'pass' ? 'pass' : branchForNode(graph, source),
          x: sourcePosition.x + sourceSize.width - connectedOverlap,
          y: sourcePosition.y + outputCenterOffset(graph, source, slot.id) - 15,
          width: 20,
          tone: slot.id === 'pass' ? 'pass' : slot.id === 'fail' ? 'fail' : 'normal',
        },
        valid: true,
        message: '松手即可自动吸附到链尾。',
      }));
  });
}

function draggedTailAnchor(graph: GraphDocument, drag: BlockDrag): GraphPosition | null {
  const tail = draggedTailOutput(graph, new Set(drag.groupIds));
  if (!tail) {
    return null;
  }
  const tailNode = graph.nodes.find((nodeItem) => nodeItem.id === tail.nodeId);
  const tailPosition = drag.previewPositions.get(tail.nodeId);
  if (!tailNode || !tailPosition) {
    return null;
  }
  const tailSize = blockMetrics(graph, tailNode);
  return {
    x: tailPosition.x + tailSize.width - connectedOverlap,
    y: tailPosition.y + outputCenterOffset(graph, tailNode, tail.slot.id),
  };
}

function attachCandidates(graph: GraphDocument, drag: BlockDrag): InsertCandidate[] {
  const group = new Set(drag.groupIds);
  const tail = draggedTailOutput(graph, group);
  if (!tail) {
    return [];
  }

  const connectedEdges = connectedGraphEdges(graph);
  return graph.nodes.flatMap((target) => {
    if (group.has(target.id)) {
      return [];
    }
    const targetPosition = target.position ?? fallbackPosition(target.id);
    const targetBranch = branchForNode(graph, target);
    return target.slots
      .filter((slot) => slot.direction === 'INPUT')
      .filter((slot) => slot.edgeType === tail.slot.edgeType)
      .filter((slot) => !connectedEdges.some((edgeItem) => edgeItem.targetNodeId === target.id && edgeItem.targetSlotId === slot.id))
      .map((slot): InsertCandidate => ({
        kind: 'attach',
        sourceNodeId: tail.nodeId,
        sourceSlotId: tail.slot.id,
        targetNodeId: target.id,
        targetSlotId: slot.id,
        join: {
          id: `attach:${tail.nodeId}:${tail.slot.id}:${target.id}:${slot.id}`,
          from: tail.nodeId,
          to: target.id,
          branch: targetBranch,
          x: targetPosition.x - 10,
          y: targetPosition.y + (inputCenterOffset(graph, target) ?? normalBlockHeight / 2) - 15,
          width: 20,
          tone: targetBranch === 'fail' ? 'fail' : targetBranch === 'pass' ? 'pass' : 'normal',
        },
        valid: true,
        message: '松手即可连接到后面的积木。',
      }));
  });
}

function snapDraggedGroupToCandidate(graph: GraphDocument, drag: BlockDrag): void {
  const candidate = drag.candidate;
  if (candidate?.kind === 'attach') {
    snapDraggedGroupTailToTarget(graph, drag, candidate);
    return;
  }

  const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const sourceNodeId = candidate?.kind === 'insert' ? candidate.edge.sourceNodeId : candidate?.kind === 'append' ? candidate.sourceNodeId : null;
  const sourceSlotId = candidate?.kind === 'insert' ? candidate.edge.sourceSlotId : candidate?.kind === 'append' ? candidate.sourceSlotId : null;
  const source = sourceNodeId ? graph.nodes.find((nodeItem) => nodeItem.id === sourceNodeId) : null;
  const rootPosition = drag.previewPositions.get(drag.rootId);
  const rootInputY = root ? inputCenterOffset(graph, root) : null;
  if (!candidate?.valid || !root || !source || !sourceSlotId || !rootPosition || rootInputY === null) {
    return;
  }

  const sourcePosition = source.position ?? fallbackPosition(source.id);
  const sourceSize = blockMetrics(graph, source);
  const snappedRoot = {
    x: Math.round(sourcePosition.x + sourceSize.width - connectedOverlap),
    y: Math.round(sourcePosition.y + outputCenterOffset(graph, source, sourceSlotId) - rootInputY),
  };
  const dx = snappedRoot.x - rootPosition.x;
  const dy = snappedRoot.y - rootPosition.y;
  drag.previewPositions = new Map(
    Array.from(drag.previewPositions, ([nodeId, position]) => [nodeId, { x: position.x + dx, y: position.y + dy }]),
  );
}

function snapDraggedGroupTailToTarget(graph: GraphDocument, drag: BlockDrag, candidate: Extract<InsertCandidate, { kind: 'attach' }>): void {
  const tail = draggedTailOutput(graph, new Set(drag.groupIds));
  const tailNode = tail ? graph.nodes.find((nodeItem) => nodeItem.id === tail.nodeId) : null;
  const target = graph.nodes.find((nodeItem) => nodeItem.id === candidate.targetNodeId);
  const tailPosition = tail ? drag.previewPositions.get(tail.nodeId) : null;
  const targetInputY = target ? inputCenterOffset(graph, target) : null;
  if (!tail || !tailNode || !target || !tailPosition || targetInputY === null) {
    return;
  }

  const targetPosition = target.position ?? fallbackPosition(target.id);
  const tailSize = blockMetrics(graph, tailNode);
  const snappedTail = {
    x: Math.round(targetPosition.x),
    y: Math.round(targetPosition.y + targetInputY),
  };
  const dx = snappedTail.x - (tailPosition.x + tailSize.width - connectedOverlap);
  const dy = snappedTail.y - (tailPosition.y + outputCenterOffset(graph, tailNode, tail.slot.id));
  drag.previewPositions = new Map(
    Array.from(drag.previewPositions, ([nodeId, position]) => [nodeId, { x: position.x + dx, y: position.y + dy }]),
  );
}

function canInsertIntoEdge(graph: GraphDocument, edgeItem: GraphEdge, drag: BlockDrag): { valid: boolean; message: string } {
  const group = new Set(drag.groupIds);
  if (group.has(edgeItem.sourceNodeId) || group.has(edgeItem.targetNodeId)) {
    return { valid: false, message: '不能插入到正在拖动的链条内部。' };
  }

  const source = graph.nodes.find((nodeItem) => nodeItem.id === edgeItem.sourceNodeId);
  const target = graph.nodes.find((nodeItem) => nodeItem.id === edgeItem.targetNodeId);
  const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const sourceSlot = source?.slots.find((slot) => slot.id === edgeItem.sourceSlotId);
  const targetSlot = target?.slots.find((slot) => slot.id === edgeItem.targetSlotId);
  const rootInput = root?.slots.find((slot) => slot.direction === 'INPUT');
  const tail = draggedTailOutput(graph, group);

  if (!source || !target || !sourceSlot || !targetSlot || !root) {
    return { valid: false, message: '连接信息不完整，不能插入。' };
  }
  if (!rootInput) {
    return { valid: false, message: '这个积木没有输入槽，不能插入到连接中。' };
  }
  if (!tail) {
    return { valid: false, message: '这个链条有多个出口，暂不支持直接插入。' };
  }
  if (sourceSlot.edgeType !== rootInput.edgeType || tail.slot.edgeType !== targetSlot.edgeType) {
    return { valid: false, message: '槽位类型不匹配，不能插入。' };
  }
  return { valid: true, message: '松手即可自动吸附到这里。' };
}

function draggedTailOutput(graph: GraphDocument, group: Set<string>): { nodeId: string; slot: GraphSlot } | null {
  const tails: Array<{ nodeId: string; slot: GraphSlot }> = [];
  const edges = connectedGraphEdges(graph);
  graph.nodes.forEach((nodeItem) => {
    if (!group.has(nodeItem.id)) {
      return;
    }
    const slot = preferredMainOutput(nodeItem);
    if (!slot) {
      return;
    }
    const keepsGoingInsideGroup = edges.some((graphEdge) =>
      graphEdge.sourceNodeId === nodeItem.id
      && graphEdge.sourceSlotId === slot.id
      && group.has(graphEdge.targetNodeId),
    );
    if (!keepsGoingInsideGroup) {
      tails.push({ nodeId: nodeItem.id, slot });
    }
  });
  return tails.length === 1 ? tails[0] : null;
}

function preferredMainOutput(nodeItem: GraphNode): GraphSlot | null {
  const outputs = nodeItem.slots.filter((slot) => slot.direction === 'OUTPUT');
  for (const slotId of ['done', 'timer_completed', 'started']) {
    const slot = outputs.find((item) => item.id === slotId);
    if (slot) {
      return slot;
    }
  }
  return outputs.length === 1 && !['pass', 'fail'].includes(outputs[0].id) ? outputs[0] : null;
}

function renderInsertPreview(drag: BlockDrag): void {
  const candidate = drag.candidate;
  clearInsertPreview();
  if (!candidate) {
    setDragHint('靠近两个积木之间会自动吸附插入。', 'active');
    return;
  }

  if (candidate.kind === 'insert') {
    const joinEl = document.querySelector<HTMLElement>(`[data-join="${candidate.join.id}"]`);
    joinEl?.classList.add(candidate.valid ? 'insert-target' : 'insert-invalid');
    if (candidate.valid) {
      markInsertSplitGroups(graphWithPreviewPositions(currentGraph(), drag), candidate);
    }
  }
  document.querySelector<HTMLElement>(`[data-block="${candidate.join.from}"]`)?.classList.add('is-related');
  document.querySelector<HTMLElement>(`[data-block="${candidate.join.to}"]`)?.classList.add('is-related');
  setDragHint(candidate.message, candidate.valid ? 'valid' : 'invalid');
}

function markInsertSplitGroups(graph: GraphDocument, candidate: Extract<InsertCandidate, { kind: 'insert' }>): void {
  const leftSide = new Set(connectedComponentNodeIds(graph, candidate.edge.sourceNodeId, candidate.edge.id));
  const rightSide = new Set(connectedComponentNodeIds(graph, candidate.edge.targetNodeId, candidate.edge.id));
  const overlap = new Set(Array.from(leftSide).filter((nodeId) => rightSide.has(nodeId)));

  leftSide.forEach((nodeId) => {
    if (!overlap.has(nodeId)) {
      document.querySelector<HTMLElement>(`[data-block="${nodeId}"]`)?.classList.add('is-insert-split-left');
    }
  });
  rightSide.forEach((nodeId) => {
    if (!overlap.has(nodeId)) {
      document.querySelector<HTMLElement>(`[data-block="${nodeId}"]`)?.classList.add('is-insert-split-right');
    }
  });
}

function clearInsertPreview(): void {
  document.querySelectorAll('.slot-join.insert-target, .slot-join.insert-invalid').forEach((item) => {
    item.classList.remove('insert-target', 'insert-invalid');
  });
  document.querySelectorAll('.logic-block.is-insert-split-left, .logic-block.is-insert-split-right').forEach((item) => {
    item.classList.remove('is-insert-split-left', 'is-insert-split-right');
  });
  clearFocus();
}

function setDragHint(message: string, tone: string): void {
  const hint = document.querySelector<HTMLElement>('[data-drag-hint]');
  if (!hint) {
    return;
  }
  hint.textContent = message;
  hint.className = `drag-hint${tone ? ` ${tone}` : ''}`;
}

function insertDraggedGroup(graph: GraphDocument, drag: BlockDrag): boolean {
  const candidate = drag.candidate;
  if (!candidate?.valid) {
    return false;
  }

  const group = new Set(drag.groupIds);
  if (candidate.kind === 'attach') {
    const tail = draggedTailOutput(graph, group);
    const target = graph.nodes.find((nodeItem) => nodeItem.id === candidate.targetNodeId);
    const targetSlot = target?.slots.find((slot) => slot.id === candidate.targetSlotId);
    if (!tail || !target || !targetSlot || tail.slot.edgeType !== targetSlot.edgeType) {
      return false;
    }
    graph.edges = graph.edges.filter((graphEdge) =>
      !(graphEdge.targetNodeId === candidate.targetNodeId && graphEdge.targetSlotId === candidate.targetSlotId && !group.has(graphEdge.sourceNodeId)),
    );
    graph.edges.push(edge(nextEdgeId(graph), tail.nodeId, tail.slot.id, candidate.targetNodeId, candidate.targetSlotId));
    return true;
  }

  const root = graph.nodes.find((nodeItem) => nodeItem.id === drag.rootId);
  const rootInput = root?.slots.find((slot) => slot.direction === 'INPUT');
  if (!rootInput) {
    return false;
  }

  if (candidate.kind === 'append') {
    graph.edges = graph.edges.filter((graphEdge) =>
      !(graphEdge.targetNodeId === drag.rootId && graphEdge.targetSlotId === rootInput.id && !group.has(graphEdge.sourceNodeId)),
    );
    graph.edges.push(edge(nextEdgeId(graph), candidate.sourceNodeId, candidate.sourceSlotId, drag.rootId, rootInput.id));
    return true;
  }

  const tail = draggedTailOutput(graph, group);
  if (!tail) {
    return false;
  }

  graph.edges = graph.edges.filter((graphEdge) =>
    graphEdge.id !== candidate.edge.id
    && !(graphEdge.targetNodeId === drag.rootId && graphEdge.targetSlotId === rootInput.id && !group.has(graphEdge.sourceNodeId)),
  );
  graph.edges.push(edge(nextEdgeId(graph), candidate.edge.sourceNodeId, candidate.edge.sourceSlotId, drag.rootId, rootInput.id));
  graph.edges.push(edge(nextEdgeId(graph), tail.nodeId, tail.slot.id, candidate.edge.targetNodeId, candidate.edge.targetSlotId));
  return true;
}

function makeInsertionGap(graph: GraphDocument, drag: BlockDrag): void {
  const candidate = drag.candidate;
  if (!candidate || candidate.kind !== 'insert') {
    return;
  }

  const group = new Set(drag.groupIds);
  const groupRight = drag.groupIds.reduce((right, nodeId) => {
    const nodeItem = graph.nodes.find((item) => item.id === nodeId);
    if (!nodeItem) {
      return right;
    }
    const position = nodeItem.position ?? fallbackPosition(nodeId);
    const size = blockMetrics(graph, nodeItem);
    return Math.max(right, position.x + size.width);
  }, 0);
  const targetPosition = nodePosition(graph, candidate.edge.targetNodeId);
  const shiftX = Math.max(0, Math.round(groupRight - 14 - targetPosition.x));

  const shifted = new Set(downstreamNodeIds(graph, candidate.edge.targetNodeId).filter((nodeId) => !group.has(nodeId)));
  if (shiftX > 0) {
    graph.nodes = graph.nodes.map((nodeItem) => {
      if (!shifted.has(nodeItem.id)) {
        return nodeItem;
      }
      const position = nodeItem.position ?? fallbackPosition(nodeItem.id);
      return { ...nodeItem, position: { x: position.x + shiftX, y: position.y } };
    });
  }
  ensureLocalHorizontalGaps(graph, candidate.edge.targetNodeId, group);
}

function ensureLocalHorizontalGaps(graph: GraphDocument, startNodeId: string, locked: Set<string>): void {
  const visited = new Set<string>();
  const queue = [startNodeId];
  while (queue.length > 0) {
    const sourceId = queue.shift();
    if (!sourceId || visited.has(sourceId)) {
      continue;
    }
    visited.add(sourceId);
    const source = graph.nodes.find((nodeItem) => nodeItem.id === sourceId);
    if (!source) {
      continue;
    }
    const sourcePosition = source.position ?? fallbackPosition(source.id);
    const minTargetX = sourcePosition.x + blockMetrics(graph, source).width - 14;
    connectedGraphEdges(graph)
      .filter((graphEdge) => graphEdge.sourceNodeId === sourceId)
      .forEach((graphEdge) => {
        if (locked.has(graphEdge.targetNodeId)) {
          return;
        }
        const target = graph.nodes.find((nodeItem) => nodeItem.id === graphEdge.targetNodeId);
        if (!target) {
          return;
        }
        const targetPosition = target.position ?? fallbackPosition(target.id);
        if (targetPosition.x < minTargetX) {
          target.position = { x: minTargetX, y: targetPosition.y };
        }
        queue.push(target.id);
      });
  }
}

function nextEdgeId(graph: GraphDocument): string {
  let index = graph.edges.length + 1;
  while (graph.edges.some((graphEdge) => graphEdge.id === `e${index}`)) {
    index += 1;
  }
  return `e${index}`;
}

function addLibraryBlock(kind: LibraryKind): void {
  const graph = cloneGraph(currentGraph());
  const nodeItem = createLibraryNode(kind, visibleDropPosition(kind));
  graph.nodes.push(nodeItem);
  applyGraphEdit(graph, `已新增“${nodeTypeLabel(nodeItem.type)}”，正在自动保存。`, {
    selectedNodeId: nodeItem.id,
    recentNodeId: nodeItem.id,
  });
}

function createLibraryNode(kind: LibraryKind, position: GraphPosition): GraphNode {
  const graph = currentGraph();
  switch (kind) {
    case 'trigger':
      return node(uniqueNodeId('manual-trigger', graph), 'MANUAL_TRIGGER', '手动触发', {}, position, [out('started')]);
    case 'condition':
      return node(
        uniqueNodeId('condition', graph),
        'STATE_COMPARE_CONDITION',
        '条件判断',
        { scope: 'PLAYER', key: 'started', valueType: 'BOOLEAN', expected: 'false', missing: 'false' },
        position,
        [input('input'), out('pass'), out('fail')],
      );
    case 'state':
      return node(
        uniqueNodeId('state-set', graph),
        'STATE_SET_ACTION',
        '状态写入',
        { scope: 'PLAYER', key: 'started', valueType: 'BOOLEAN', value: 'true' },
        position,
        [input('input'), out('done')],
      );
    case 'timer':
      return node(uniqueNodeId('timer', graph), 'TIMER_START_ACTION', '计时器', { durationSeconds: '30' }, position, [
        input('input'),
        out('timer_completed'),
      ]);
    case 'debug':
      return node(uniqueNodeId('debug', graph), 'DEBUG_LOG_ACTION', '调试记录', { message: '调试记录' }, position, [
        input('input'),
        out('done'),
      ]);
    case 'action':
    default:
      return node(uniqueNodeId('message', graph), 'MESSAGE_ACTION', '发送消息', { message: '新消息' }, position, [
        input('input'),
        out('done'),
      ]);
  }
}

function uniqueNodeId(prefix: string, graph: GraphDocument): string {
  let index = graph.nodes.length + 1;
  let id = `${prefix}-${index}`;
  while (graph.nodes.some((nodeItem) => nodeItem.id === id)) {
    index += 1;
    id = `${prefix}-${index}`;
  }
  return id;
}

function visibleDropPosition(kind: LibraryKind): GraphPosition {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');
  const size = blockSize(kind);
  if (!viewport) {
    return { x: 84, y: 88 };
  }
  const rect = viewport.getBoundingClientRect();
  const x = (rect.width / 2 - offsetX) / scale - size.width / 2;
  const y = (rect.height / 2 - offsetY) / scale - size.height / 2;
  return {
    x: Math.max(28, Math.round(x)),
    y: Math.max(28, Math.round(y)),
  };
}

function disconnectSelectedInput(): void {
  const graph = cloneGraph(currentGraph());
  const before = graph.edges.length;
  graph.edges = graph.edges.filter((graphEdge) => graphEdge.targetNodeId !== state.selectedNodeId);
  if (graph.edges.length === before) {
    return;
  }
  applyGraphEdit(graph, '已断开该积木的输入连接，正在自动保存。');
}

function deleteSelectedNode(): void {
  const graph = cloneGraph(currentGraph());
  if (Object.values(graph.triggerEntries).includes(state.selectedNodeId)) {
    state.error = '入口积木不能删除。';
    refreshDraftIndicators();
    return;
  }
  const before = graph.nodes.length;
  graph.nodes = graph.nodes.filter((nodeItem) => nodeItem.id !== state.selectedNodeId);
  if (graph.nodes.length === before) {
    return;
  }
  graph.edges = graph.edges.filter((graphEdge) => graphEdge.sourceNodeId !== state.selectedNodeId && graphEdge.targetNodeId !== state.selectedNodeId);
  applyGraphEdit(graph, '已删除积木和相关连接，正在自动保存。', {
    selectedNodeId: graph.nodes[0]?.id ?? '',
    recentNodeId: null,
  });
}

function openEditor(nodeId: string): void {
  state.selectedNodeId = nodeId;
  state.editorOpen = true;
  state.editorClosing = false;
  renderApp();
}

function requestCloseEditor(): void {
  closeEditor();
}

function closeEditor(): void {
  state.editorClosing = true;
  const overlayEl = document.querySelector<HTMLElement>('.editor-overlay');
  if (overlayEl) {
    overlayEl.classList.add('is-closing');
  }
  window.setTimeout(() => {
    state.editorOpen = false;
    state.editorClosing = false;
    renderApp();
  }, 160);
}

function focusEditor(): void {
  if (!state.editorOpen) {
    return;
  }
  window.setTimeout(() => {
    const target = document.querySelector<HTMLElement>('.editor-dialog input, .editor-dialog select, #block-editor-title');
    target?.focus();
  }, 0);
}

function trapEditorFocus(event: KeyboardEvent): void {
  const focusSelector = '.editor-dialog button:not([disabled]), .editor-dialog input:not([disabled]), .editor-dialog select:not([disabled]), #block-editor-title';
  const focusables = Array.from(
    document.querySelectorAll<HTMLElement>(focusSelector),
  ).filter((item) => item.offsetParent !== null);
  if (focusables.length === 0) {
    return;
  }
  const first = focusables[0];
  const last = focusables[focusables.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function updateSelectedNode(inputEl: HTMLInputElement | HTMLSelectElement): void {
  if (inputEl.dataset.nodeField === 'displayName') {
    updateSelectedNodeValue('displayName', inputEl.value, 'node');
  }
  if (inputEl.dataset.configKey) {
    updateSelectedNodeValue(inputEl.dataset.configKey, inputEl.value);
  }
}

function updateSelectedNodeValue(key: string, value: string, target: 'config' | 'node' = 'config'): void {
  const graph = currentGraph();
  const selected = selectedNodeFrom(graph);
  if (!selected) {
    return;
  }
  const nextGraph = cloneGraph(graph);
  const nextNode = nextGraph.nodes.find((item) => item.id === selected.id);
  if (!nextNode) {
    return;
  }

  if (target === 'node' && key === 'displayName') {
    if (nextNode.displayName === value) {
      return;
    }
    nextNode.displayName = value;
  } else {
    if (nextNode.config[key] === value) {
      return;
    }
    nextNode.config[key] = value;
  }
  applyGraphEdit(nextGraph, '内容已修改，正在自动保存。', { refreshOnly: true });
}

function refreshDraftIndicators(): void {
  const apiStatus = document.querySelector<HTMLElement>('[data-api-status]');
  const draftStatus = document.querySelector<HTMLElement>('[data-draft-status]');
  const dirtyStatus = document.querySelector<HTMLElement>('[data-dirty-status]');
  const lastAction = document.querySelector<HTMLElement>('[data-last-action]');
  const validationTitleEl = document.querySelector<HTMLElement>('[data-validation-title]');
  const issueList = document.querySelector<HTMLElement>('[data-issue-list]');
  const modalSummary = document.querySelector<HTMLElement>('[data-modal-summary]');
  const modalError = document.querySelector<HTMLElement>('[data-modal-error]');
  const undoButton = document.querySelector<HTMLButtonElement>('[data-history-action="undo"]');
  const redoButton = document.querySelector<HTMLButtonElement>('[data-history-action="redo"]');

  if (apiStatus) {
    apiStatus.textContent = apiStatusText();
    apiStatus.className = `api-pill ${state.apiStatus}`;
  }
  if (draftStatus) {
    draftStatus.textContent = draftStatusText();
  }
  if (dirtyStatus) {
    dirtyStatus.textContent = state.dirty ? '等待自动保存' : '无本地改动';
  }
  if (lastAction) {
    lastAction.textContent = state.lastAction;
  }
  if (validationTitleEl) {
    validationTitleEl.textContent = validationTitle();
  }
  if (issueList) {
    issueList.innerHTML = `
      <li><span class="${state.apiStatus === 'online' ? 'ok' : 'warn'}"></span>${escapeHtml(state.statusMessage)}</li>
      ${uncommittedNotice() ? `<li><span class="warn"></span>${escapeHtml(uncommittedNotice())}</li>` : ''}
      ${validationList()}
    `;
  }
  if (modalSummary) {
    const selected = selectedNodeFrom(currentGraph());
    modalSummary.textContent = selected ? nodeSummary(selected) : '';
  }
  if (modalError) {
    modalError.textContent = state.error || validationSummaryText();
  }
  undoButton?.toggleAttribute('disabled', !canUndo());
  redoButton?.toggleAttribute('disabled', !canRedo());
  window.onbeforeunload = state.dirty || state.hasDraft || autoSaveInFlight ? () => '还有修改正在自动保存，确定要离开吗？' : null;
}

function scheduleAutoSave(): void {
  if (autoSaveTimer !== null) {
    window.clearTimeout(autoSaveTimer);
  }
  autoSaveTimer = window.setTimeout(() => {
    autoSaveTimer = null;
    void startAutoSave();
  }, autoSaveDelayMs);
}

function startAutoSave(): Promise<void> {
  if (autoSaveInFlight && autoSavePromise) {
    autoSaveAgain = true;
    return autoSavePromise;
  }
  const promise = performAutoSave();
  autoSavePromise = promise;
  void promise.finally(() => {
    if (autoSavePromise === promise) {
      autoSavePromise = null;
    }
  });
  return promise;
}

async function performAutoSave(): Promise<void> {
  if (autoSaveInFlight) {
    autoSaveAgain = true;
    return;
  }
  if (!state.dirty && !state.hasDraft) {
    return;
  }

  const saveVersion = graphVersion;
  autoSaveInFlight = true;
  state.busyAction = '自动保存';
  refreshDraftIndicators();

  try {
    await saveAndCommit({ auto: true, version: saveVersion });
    state.apiStatus = 'online';
    state.statusMessage = 'API 已连接';
  } catch (error) {
    const connected = error instanceof PixelLogicApiError ? error.connected : false;
    state.apiStatus = connected ? 'online' : 'offline';
    state.statusMessage = connected ? 'API 已连接' : 'API 未连接';
    state.error = error instanceof Error ? error.message : 'API 未连接';
    state.lastAction = '自动保存失败，请确认 API 连接或修复检查问题。';
  } finally {
    const changedDuringSave = autoSaveAgain || graphVersion !== saveVersion;
    autoSaveAgain = false;
    autoSaveInFlight = false;
    state.busyAction = null;
    if (changedDuringSave) {
      scheduleAutoSave();
    }
    if (state.editorOpen) {
      refreshDraftIndicators();
    } else {
      renderApp();
    }
  }
}

async function waitForPendingAutoSave(): Promise<void> {
  if (autoSaveTimer !== null) {
    window.clearTimeout(autoSaveTimer);
    autoSaveTimer = null;
  }
  if (autoSavePromise) {
    await autoSavePromise;
  }
}

async function loadGraph(): Promise<void> {
  await runAction('加载图', async () => {
    const graphResponse = await api(`/api/pixellogic/graphs/${graphId}`);
    if (!graphResponse.graph) {
      throw new Error('API 未返回 graph。');
    }
    state.committedGraph = normalizeConditionBranchLayout(graphResponse.graph);
    state.graph = state.committedGraph;
    state.validation = graphResponse.validation ?? null;
    state.hasDraft = graphResponse.hasDraft ?? false;

    const draftResponse = await api(`/api/pixellogic/graphs/${graphId}/draft`);
    if (draftResponse.graph) {
      state.graph = normalizeConditionBranchLayout(draftResponse.graph);
      state.hasDraft = true;
    }

    ensureSelectedNode();
    resetGraphHistory();
    state.apiStatus = 'online';
    state.statusMessage = 'Graph 已从 API 加载';
    state.lastAction = state.hasDraft ? '已加载上次未保存完成的修改' : '已加载已保存版本';
  });
}

async function saveAndCommit(options: { auto?: boolean; version?: number } = {}): Promise<boolean> {
  const saveVersion = options.version ?? graphVersion;
  syncGraphConnectionsToVisual();

  if (!state.dirty && !state.hasDraft) {
    state.lastAction = options.auto ? '已自动保存。' : '已保存。';
    return true;
  }

  if (state.dirty) {
    await persistDraft(saveVersion);
    if (graphVersion !== saveVersion) {
      return false;
    }
  }

  const validationData = await api(`/api/pixellogic/graphs/${graphId}/validate`, { method: 'POST' });
  if (graphVersion !== saveVersion) {
    return false;
  }
  state.validation = validationData.validation ?? null;
  if (!state.validation?.valid) {
    state.lastAction = options.auto ? '自动保存未生效：请修复检查问题。' : '保存失败：请修复验证问题。';
    state.error = validationErrorText();
    return false;
  }

  const data = await api(`/api/pixellogic/graphs/${graphId}/commit`, { method: 'POST' });
  if (graphVersion !== saveVersion) {
    return false;
  }
  if (!data.graph) {
    throw new Error('API 未返回已保存 graph。');
  }
  state.graph = data.graph;
  state.committedGraph = data.graph;
  state.validation = data.validation ?? null;
  state.hasDraft = false;
  state.dirty = false;
  state.lastAction = options.auto ? '已自动保存并生效。' : '已保存并生效。';
  return true;
}

function syncGraphConnectionsToVisual(): void {
  const current = currentGraph();
  const graph = normalizeConditionBranchLayout(current);
  const connectedEdges = connectedGraphEdges(graph);
  const samePositions = graph.nodes.every((nodeItem) => {
    const currentNode = current.nodes.find((item) => item.id === nodeItem.id);
    const currentPosition = currentNode?.position ?? (currentNode ? fallbackPosition(currentNode.id) : null);
    const nextPosition = nodeItem.position ?? fallbackPosition(nodeItem.id);
    return currentPosition !== null && currentPosition.x === nextPosition.x && currentPosition.y === nextPosition.y;
  });
  if (connectedEdges.length === graph.edges.length && samePositions) {
    return;
  }

  state.graph = { ...cloneGraph(graph), edges: connectedEdges.map((graphEdge) => ({ ...graphEdge })) };
  state.dirty = true;
  state.validation = null;
}

async function persistDraft(saveVersion = graphVersion): Promise<ApiResponse> {
  const graphToSave = cloneGraph(currentGraph());
  const data = await api(`/api/pixellogic/graphs/${graphId}/draft`, {
    method: 'PUT',
    body: JSON.stringify({ graph: graphToSave }),
  });
  if (!data.graph) {
    throw new Error('API 未返回已保存内容。');
  }
  state.hasDraft = true;
  if (graphVersion === saveVersion) {
    state.graph = data.graph;
    state.dirty = false;
    state.validation = null;
  }
  return data;
}

async function startTest(): Promise<void> {
  await runAction('测试运行', async () => {
    await waitForPendingAutoSave();
    if (state.dirty || state.hasDraft) {
      const saved = await saveAndCommit({ version: graphVersion });
      if (!saved) {
        return;
      }
    }
    await api('/api/pixellogic/test/reset', { method: 'POST' });
    const data = await api('/api/pixellogic/test/start', { method: 'POST' });
    state.apiStatus = 'online';
    state.latestTrace = data.trace ?? null;
    state.lastAction = data.message || '测试运行已执行';
  });
}

async function refreshLatestTrace(showBusy = true): Promise<void> {
  const action = async () => {
    const data = await api('/api/pixellogic/traces/latest');
    state.apiStatus = 'online';
    state.latestTrace = data.trace ?? null;
    state.lastAction = state.latestTrace ? '执行记录已刷新' : state.lastAction;
  };

  if (showBusy) {
    await runAction('刷新记录', action);
  } else {
    await action();
  }
}

async function runAction(label: string, action: () => Promise<void>, options: { renderBusy?: boolean } = {}): Promise<void> {
  state.busyAction = label;
  state.error = '';
  if (options.renderBusy ?? true) {
    renderApp();
  }

  try {
    await action();
  } catch (error) {
    const connected = error instanceof PixelLogicApiError ? error.connected : false;
    state.apiStatus = connected ? 'online' : 'offline';
    state.statusMessage = connected ? 'API 已连接' : 'API 未连接';
    state.error = error instanceof Error ? error.message : 'API 未连接';
  } finally {
    state.busyAction = null;
    if (!state.editorClosing) {
      renderApp();
    }
  }
}

async function api(path: string, init?: RequestInit): Promise<ApiResponse> {
  let response: Response;
  const headers = new Headers(init?.headers);
  headers.set('Accept', 'application/json');
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  try {
    response = await fetch(path, {
      ...init,
      headers,
    });
  } catch {
    throw new PixelLogicApiError('API 未连接，请确认 PixelLogic API server 已启动。', false);
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.toLowerCase().includes('application/json')) {
    throw new PixelLogicApiError('API 未连接：当前 /api 返回的不是 JSON。', false);
  }

  let data: ApiResponse;
  try {
    data = (await response.json()) as ApiResponse;
  } catch {
    throw new PixelLogicApiError('API 未连接：当前 /api 返回的 JSON 无法解析。', false);
  }

  if (!response.ok || !data.ok) {
    throw new PixelLogicApiError(data.error?.message ?? 'PixelLogic API 返回错误。', true);
  }
  return data;
}

function blockKind(type: string): BlockKind {
  if (type.includes('TRIGGER')) {
    return 'trigger';
  }
  if (type.includes('CONDITION')) {
    return 'condition';
  }
  if (type === 'STATE_SET_ACTION' || type === 'STATE_ADD_ACTION') {
    return 'state';
  }
  if (type === 'TIMER_START_ACTION') {
    return 'timer';
  }
  if (type === 'DEBUG_LOG_ACTION') {
    return 'debug';
  }
  return 'action';
}

function branchForNode(graph: GraphDocument, nodeItem: GraphNode): Branch {
  const incoming = connectedGraphEdges(graph).find((graphEdge) => graphEdge.targetNodeId === nodeItem.id);
  if (incoming?.sourceSlotId === 'fail') {
    return 'fail';
  }
  if (incoming?.sourceSlotId === 'pass') {
    return 'pass';
  }
  if (nodeItem.type.includes('TRIGGER') || nodeItem.type.includes('CONDITION')) {
    return 'main';
  }
  return 'pass';
}

function nodeTypeLabel(type: string): string {
  switch (type) {
    case 'MANUAL_TRIGGER':
      return '测试触发';
    case 'COMMAND_TRIGGER':
      return '命令触发';
    case 'STATE_COMPARE_CONDITION':
      return '条件判断';
    case 'MESSAGE_ACTION':
      return '发送消息';
    case 'STATE_SET_ACTION':
      return '状态写入';
    case 'STATE_ADD_ACTION':
      return '状态累加';
    case 'TIMER_START_ACTION':
      return '计时器';
    case 'DEBUG_LOG_ACTION':
      return '调试记录';
    default:
      return '积木';
  }
}

function nodeSummary(nodeItem: GraphNode): string {
  const config = nodeItem.config;
  switch (nodeItem.type) {
    case 'MANUAL_TRIGGER':
      return 'WebUI 点击后调用真实后端 API';
    case 'STATE_COMPARE_CONDITION':
      return `当“${scopeLabel(config.scope)}”的 ${config.key ?? '状态名'} 等于“${booleanLabel(config.expected ?? 'false')}”时，走“通过”分支。`;
    case 'MESSAGE_ACTION':
      return `向模拟玩家显示：${config.message ?? ''}`;
    case 'STATE_SET_ACTION':
      return `把“${scopeLabel(config.scope)}”的 ${config.key ?? '状态名'} 设置为“${stateValueLabel(config.value ?? '', config.valueType ?? 'BOOLEAN')}”。`;
    case 'STATE_ADD_ACTION':
      return `把“${scopeLabel(config.scope)}”的 ${config.key ?? '状态名'} 增加 ${config.amount ?? '1'}。`;
    case 'TIMER_START_ACTION':
      return `等待 ${config.durationSeconds ?? '30'} 秒后继续。`;
    case 'DEBUG_LOG_ACTION':
      return `记录：${config.message ?? ''}`;
    default:
      return nodeItem.id;
  }
}

function scopeLabel(value = 'PLAYER'): string {
  return stateScopeOptions().find((option) => option.value === value)?.label ?? value;
}

function valueTypeLabel(value = 'BOOLEAN'): string {
  return valueTypeOptions().find((option) => option.value === value)?.label ?? value;
}

function booleanLabel(value = 'false'): string {
  return booleanOptions().find((option) => option.value === value)?.label ?? value;
}

function slotLabel(value: string): string {
  switch (value) {
    case 'input':
      return '输入';
    case 'started':
      return '开始';
    case 'pass':
      return '通过';
    case 'fail':
      return '失败';
    case 'done':
      return '完成';
    case 'timer_completed':
      return '计时完成';
    default:
      return value;
  }
}

function stateValueLabel(value: string, valueType: string): string {
  return valueType === 'BOOLEAN' ? booleanLabel(value || 'false') : value || '未填写';
}

function humanizeTraceMessage(message: string): string {
  return message
    .replace(/\bPLAYER\.([A-Za-z0-9_]+)/g, '玩家状态 $1')
    .replace(/\bGLOBAL\.([A-Za-z0-9_]+)/g, '全局状态 $1')
    .replace(/\bSESSION\.([A-Za-z0-9_]+)/g, '当前会话状态 $1')
    .replace(/\bBOOLEAN\b/g, '是或否')
    .replace(/\bINTEGER\b/g, '数字')
    .replace(/\bSTRING\b/g, '文本')
    .replace(/\btrue\b/g, '是')
    .replace(/\bfalse\b/g, '否');
}

function fallbackPosition(nodeId: string): GraphPosition {
  return fallbackGraph.nodes.find((nodeItem) => nodeItem.id === nodeId)?.position ?? { x: 48, y: 78 };
}

function selectedNodeFrom(graph: GraphDocument): GraphNode | null {
  return graph.nodes.find((nodeItem) => nodeItem.id === state.selectedNodeId) ?? graph.nodes[0] ?? null;
}

function ensureSelectedNode(): void {
  const graph = currentGraph();
  if (!graph.nodes.some((nodeItem) => nodeItem.id === state.selectedNodeId)) {
    state.selectedNodeId = graph.nodes[0]?.id ?? 'condition-started';
  }
}

function cloneGraph(graph: GraphDocument): GraphDocument {
  return {
    ...graph,
    nodes: graph.nodes.map((nodeItem) => ({
      ...nodeItem,
      config: { ...nodeItem.config },
      position: nodeItem.position ? { ...nodeItem.position } : undefined,
      slots: nodeItem.slots.map((slot) => ({ ...slot })),
    })),
    edges: graph.edges.map((graphEdge) => ({ ...graphEdge })),
    triggerEntries: { ...graph.triggerEntries },
  };
}

function validationList(): string {
  const validation = state.validation;
  if (autoSaveInFlight) {
    return '<li><span class="warn"></span>正在自动保存并检查</li>';
  }
  if (!validation) {
    return '<li><span class="warn"></span>修改后会自动保存并检查</li>';
  }
  if (validation.valid) {
    return '<li><span class="ok"></span>自动检查通过</li>';
  }
  return validation.issues
    .map((issue) => `<li><span class="warn"></span>${escapeHtml(issue.message)}</li>`)
    .join('');
}

function validationErrorText(): string {
  const issues = state.validation?.issues ?? [];
  if (issues.length === 0) {
    return '保存失败：请修复验证问题。';
  }
  return `保存失败：${issues.map((issue) => issue.message).join('；')}`;
}

function validationSummaryText(): string {
  if (!state.validation) {
    return state.dirty ? '修改后会自动保存并检查。' : '尚未产生新的检查结果。';
  }
  if (state.validation.valid) {
    return '自动检查通过。';
  }
  return validationErrorText();
}

function validationTitle(): string {
  if (autoSaveInFlight) {
    return '保存中';
  }
  if (state.dirty) {
    return '待保存';
  }
  if (state.validation?.valid) {
    return '通过';
  }
  if (state.validation && !state.validation.valid) {
    return '失败';
  }
  return state.hasDraft ? '未完成' : '已保存';
}

function uncommittedNotice(): string {
  if (state.dirty) {
    return '当前改动正在等待自动保存。';
  }
  if (state.hasDraft) {
    return '当前有未生效草稿，测试运行前会自动检查。';
  }
  return '';
}

function draftStatusText(): string {
  if (autoSaveInFlight) {
    return '自动保存中';
  }
  if (state.dirty) {
    return '等待自动保存';
  }
  if (state.hasDraft) {
    return '有待修复草稿';
  }
  return '已自动保存';
}

function apiStatusText(): string {
  if (state.busyAction) {
    return `${state.busyAction}中`;
  }
  if (state.apiStatus === 'online') {
    return 'API 已连接';
  }
  if (state.apiStatus === 'offline') {
    return 'API 未连接';
  }
  return '连接中';
}

function apiBusyAttr(): string {
  return state.busyAction ? 'disabled' : '';
}

function shortTraceId(traceId: string): string {
  return traceId.length > 8 ? traceId.slice(0, 8) : traceId;
}

function shortFingerprint(fingerprint: string): string {
  return fingerprint ? fingerprint.slice(0, 8) : '未加载';
}

function formatTime(raw: string): string {
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) {
    return '--:--:--';
  }
  return date.toLocaleTimeString('zh-CN', { hour12: false });
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function escapeAttr(value: string): string {
  return escapeHtml(value);
}

if (app) {
  renderApp();
  centerView();
  void loadGraph().then(() => {
    if (state.apiStatus === 'online') {
      void refreshLatestTrace(false).catch(() => {
        state.apiStatus = 'offline';
        state.statusMessage = 'API 未连接';
        state.error = 'API 未连接，请确认 PixelLogic API server 已启动。';
        renderApp();
      });
    }
  });
}
