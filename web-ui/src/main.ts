import './styles.css';

type ApiStatus = 'checking' | 'online' | 'offline';
type BlockKind = 'trigger' | 'condition' | 'action' | 'state' | 'timer' | 'debug';
type Branch = 'main' | 'pass' | 'fail';

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
  selected?: boolean;
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
};

let scale = 0.86;
let offsetX = 28;
let offsetY = 34;
let isDragging = false;
let dragStart = { x: 0, y: 0 };
let dragOffset = { x: 0, y: 0 };

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

function puzzlePath(kind: BlockKind, width: number, height: number): string {
  const tab = 18;
  const notchTop = 57;
  const notchBottom = 93;

  if (kind === 'trigger') {
    return `M0 0 H${width - tab} V${notchTop} H${width} V${notchBottom} H${width - tab} V${height} H0 Z`;
  }

  if (kind === 'condition') {
    const headHeight = 150;
    const headTop = (height - headHeight) / 2;
    const headBottom = headTop + headHeight;
    const inputTop = headTop + notchTop;
    const inputBottom = headTop + notchBottom;
    const branchInset = width - 106;

    return `M${branchInset} 0 H${width - tab} V${notchTop} H${width} V${notchBottom} H${width - tab} V311 H${width} V347 H${width - tab} V${height} H${branchInset} V${headBottom} H0 V${inputBottom} H${tab} V${inputTop} H0 V${headTop} H${branchInset} Z`;
  }

  return `M0 0 H${width - tab} V${notchTop} H${width} V${notchBottom} H${width - tab} V${height} H0 V${notchBottom} H${tab} V${notchTop} H0 Z`;
}

function conditionBranchTabs(width: number): string {
  return `
      <path class="branch-tab-fill pass" d="M${width - 20} ${57} H${width} V${93} H${width - 20} Z" />
      <path class="branch-tab pass" d="M${width - 18} ${57} H${width} V${93} H${width - 18}" />
      <path class="branch-tab-fill fail" d="M${width - 20} ${311} H${width} V${347} H${width - 20} Z" />
      <path class="branch-tab fail" d="M${width - 18} ${311} H${width} V${347} H${width - 18}" />
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

function buildBlocks(graph: GraphDocument): SlotBlock[] {
  return graph.nodes.map((nodeItem) => {
    const kind = blockKind(nodeItem.type);
    const position = nodeItem.position ?? fallbackPosition(nodeItem.id);
    const size = kind === 'condition' ? { width: 384, height: 404 } : { width: 260, height: 150 };
    return {
      id: nodeItem.id,
      kind,
      branch: branchForNode(nodeItem),
      type: nodeTypeLabel(nodeItem.type),
      title: nodeItem.displayName || nodeItem.id,
      summary: nodeSummary(nodeItem),
      x: position.x,
      y: position.y,
      width: size.width,
      height: size.height,
      selected: nodeItem.id === state.selectedNodeId,
    };
  });
}

function buildJoins(graph: GraphDocument, blocks: SlotBlock[]): SlotJoin[] {
  const blockById = new Map(blocks.map((block) => [block.id, block]));
  return graph.edges
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
        y: target.id === 'condition-started' ? source.y + 61 : target.y + 61,
        width: 20,
        tone,
      };
    })
    .filter((join): join is SlotJoin => join !== null);
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
  const branchTabs = block.kind === 'condition' ? conditionBranchTabs(block.width) : '';

  return `
    <article
      class="logic-block ${block.kind} ${block.branch}${block.selected ? ' selected' : ''}"
      data-block="${escapeAttr(block.id)}"
      data-branch="${block.branch}"
      style="left:${block.x}px; top:${block.y}px; width:${block.width}px; height:${block.height}px; z-index:${3000 - block.x + (block.selected ? 1000 : 0)}"
    >
      ${renderShape(puzzlePath(block.kind, block.width, block.height), block.width, block.height, branchTabs)}
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
    .map((step) => `<li>[${escapeHtml(formatTime(step.timestamp))}] ${escapeHtml(step.message)}</li>`)
    .join('');
}

function renderApp(): void {
  if (!app) {
    return;
  }

  const graph = currentGraph();
  const blocks = buildBlocks(graph);
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
          <span class="api-pill ${state.apiStatus}" aria-live="polite">${escapeHtml(apiStatusText())}</span>
          <button type="button" class="ghost-button" data-api-action="status" ${apiBusyAttr()}>刷新状态</button>
          <button type="button" class="run-button" data-api-action="start" ${apiBusyAttr()}>测试运行</button>
          <button type="button" class="ghost-button" data-api-action="reset" ${apiBusyAttr()}>重置测试状态</button>
          <button type="button" class="ghost-button" data-api-action="trace" ${apiBusyAttr()}>刷新执行记录</button>
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
            <button type="button">触发器</button>
            <button type="button">条件</button>
            <button type="button">动作</button>
            <button type="button">状态</button>
            <button type="button">计时器</button>
            <button type="button">调试</button>
          </div>
        </section>

        <section class="quick-start">
          <div class="panel-title"><span>当前版本</span></div>
          <button type="button">Committed ${escapeHtml(shortFingerprint(state.committedGraph?.fingerprint ?? graph.fingerprint))}</button>
          <button type="button" data-draft-status>${state.hasDraft ? '存在未提交草稿' : '无未提交草稿'}</button>
          <button type="button" data-dirty-status>${state.dirty ? '有未保存改动' : '无本地改动'}</button>
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
        </section>
      </main>

      <aside class="right-panel" aria-label="选中积木属性">
        <div class="panel-title">
          <span>选中积木属性</span>
          <b>${selectedNode ? escapeHtml(nodeTypeLabel(selectedNode.type)) : '未选中'}</b>
        </div>
        ${selectedNode ? renderNodeEditor(selectedNode) : '<section class="form-card">请选择一个积木。</section>'}
        <section class="draft-actions" aria-label="草稿操作">
          <button type="button" class="ghost-button" data-graph-action="save" ${apiBusyAttr()}>保存草稿</button>
          <button type="button" class="ghost-button" data-graph-action="validate" ${apiBusyAttr()}>校验草稿</button>
          <button type="button" class="run-button" data-graph-action="commit" ${apiBusyAttr()}>提交生效</button>
        </section>
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
          <div class="panel-title"><span>验证与草稿</span><b data-validation-title>${validationTitle()}</b></div>
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
    </section>
  `;

  bindInteractions();
  setTransform();
}

function renderNodeEditor(nodeItem: GraphNode): string {
  const fields = editableFields(nodeItem)
    .map(
      (field) => `
        <label>${escapeHtml(field.label)}
          <input value="${escapeAttr(field.value)}" data-config-key="${escapeAttr(field.key)}" />
        </label>
      `,
    )
    .join('');

  return `
    <section class="form-card">
      <label>名称<input value="${escapeAttr(nodeItem.displayName)}" data-node-field="displayName" /></label>
      <label>类型<input value="${escapeAttr(nodeTypeLabel(nodeItem.type))}" readonly /></label>
      ${fields || '<p class="field-hint">该积木当前只允许修改名称。</p>'}
    </section>
  `;
}

function editableFields(nodeItem: GraphNode): Array<{ label: string; key: string; value: string }> {
  const config = nodeItem.config;
  switch (nodeItem.type) {
    case 'STATE_COMPARE_CONDITION':
      return [
        { label: '状态范围', key: 'scope', value: config.scope ?? 'PLAYER' },
        { label: '字段', key: 'key', value: config.key ?? '' },
        { label: '目标值', key: 'expected', value: config.expected ?? 'false' },
        { label: '缺失时视为', key: 'missing', value: config.missing ?? 'false' },
      ];
    case 'MESSAGE_ACTION':
    case 'DEBUG_LOG_ACTION':
      return [{ label: '消息', key: 'message', value: config.message ?? '' }];
    case 'STATE_SET_ACTION':
      return [
        { label: '状态范围', key: 'scope', value: config.scope ?? 'PLAYER' },
        { label: '字段', key: 'key', value: config.key ?? '' },
        { label: '值类型', key: 'valueType', value: config.valueType ?? 'BOOLEAN' },
        { label: '写入值', key: 'value', value: config.value ?? '' },
      ];
    case 'STATE_ADD_ACTION':
      return [
        { label: '状态范围', key: 'scope', value: config.scope ?? 'PLAYER' },
        { label: '字段', key: 'key', value: config.key ?? '' },
        { label: '累加数值', key: 'amount', value: config.amount ?? '1' },
      ];
    case 'TIMER_START_ACTION':
      return [{ label: '秒数', key: 'durationSeconds', value: config.durationSeconds ?? '30' }];
    default:
      return [];
  }
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
  offsetY = Math.max(24, (rect.height - 620 * scale) / 2);
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
    const blockEl = (event.target as HTMLElement).closest<HTMLElement>('.logic-block');
    if (blockEl?.dataset.block) {
      state.selectedNodeId = blockEl.dataset.block;
      renderApp();
      return;
    }

    if ((event.target as HTMLElement).closest('.slot-join, button, input')) {
      return;
    }

    isDragging = true;
    dragStart = { x: event.clientX, y: event.clientY };
    dragOffset = { x: offsetX, y: offsetY };
    viewport.classList.add('is-dragging');
    viewport.setPointerCapture(event.pointerId);
  });

  viewport.addEventListener('pointermove', (event) => {
    if (!isDragging) {
      return;
    }

    offsetX = dragOffset.x + event.clientX - dragStart.x;
    offsetY = dragOffset.y + event.clientY - dragStart.y;
    setTransform();
  });

  viewport.addEventListener('pointerup', (event) => {
    isDragging = false;
    viewport.classList.remove('is-dragging');
    viewport.releasePointerCapture(event.pointerId);
  });

  viewport.addEventListener('pointercancel', () => {
    isDragging = false;
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
  document.querySelector('[data-api-action="status"]')?.addEventListener('click', () => void refreshStatus());
  document.querySelector('[data-api-action="start"]')?.addEventListener('click', () => void startTest());
  document.querySelector('[data-api-action="reset"]')?.addEventListener('click', () => void resetTest());
  document.querySelector('[data-api-action="trace"]')?.addEventListener('click', () => void refreshLatestTrace());
  document.querySelector('[data-graph-action="save"]')?.addEventListener('click', () => void saveDraft());
  document.querySelector('[data-graph-action="validate"]')?.addEventListener('click', () => void validateDraft());
  document.querySelector('[data-graph-action="commit"]')?.addEventListener('click', () => void commitDraft());

  document.querySelectorAll<HTMLInputElement>('[data-node-field], [data-config-key]').forEach((inputEl) => {
    inputEl.addEventListener('input', () => updateSelectedNode(inputEl));
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

function updateSelectedNode(inputEl: HTMLInputElement): void {
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

  if (inputEl.dataset.nodeField === 'displayName') {
    nextNode.displayName = inputEl.value;
  }
  if (inputEl.dataset.configKey) {
    nextNode.config[inputEl.dataset.configKey] = inputEl.value;
  }
  state.graph = nextGraph;
  state.dirty = true;
  state.validation = null;
  state.lastAction = '草稿已修改，尚未保存。';
  refreshDraftIndicators();
}

function refreshDraftIndicators(): void {
  const draftStatus = document.querySelector<HTMLElement>('[data-draft-status]');
  const dirtyStatus = document.querySelector<HTMLElement>('[data-dirty-status]');
  const lastAction = document.querySelector<HTMLElement>('[data-last-action]');
  const validationTitleEl = document.querySelector<HTMLElement>('[data-validation-title]');
  const issueList = document.querySelector<HTMLElement>('[data-issue-list]');

  if (draftStatus) {
    draftStatus.textContent = state.hasDraft ? '存在未提交草稿' : '无未提交草稿';
  }
  if (dirtyStatus) {
    dirtyStatus.textContent = state.dirty ? '有未保存改动' : '无本地改动';
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
}

async function loadGraph(): Promise<void> {
  await runAction('加载图', async () => {
    const graphResponse = await api(`/api/pixellogic/graphs/${graphId}`);
    if (!graphResponse.graph) {
      throw new Error('API 未返回 graph。');
    }
    state.committedGraph = graphResponse.graph;
    state.graph = graphResponse.graph;
    state.validation = graphResponse.validation ?? null;
    state.hasDraft = graphResponse.hasDraft ?? false;

    const draftResponse = await api(`/api/pixellogic/graphs/${graphId}/draft`);
    if (draftResponse.graph) {
      state.graph = draftResponse.graph;
      state.hasDraft = true;
    }

    ensureSelectedNode();
    state.apiStatus = 'online';
    state.statusMessage = 'Graph 已从 API 加载';
    state.lastAction = state.hasDraft ? '已加载未提交草稿' : '已加载已提交版本';
  });
}

async function refreshStatus(): Promise<void> {
  await runAction('刷新状态', async () => {
    const data = await api('/api/pixellogic/status');
    state.apiStatus = 'online';
    state.statusMessage = data.message ?? 'API 已连接';
    state.demoActor = data.demoActor?.label ?? 'WebUI 模拟玩家';
    state.lastAction = 'API 状态已刷新';
  });
}

async function saveDraft(): Promise<void> {
  await runAction('保存草稿', async () => {
    const data = await persistDraft();
    state.lastAction = data.message ?? '草稿已保存';
  });
}

async function validateDraft(): Promise<void> {
  await runAction('校验草稿', async () => {
    if (state.dirty) {
      await persistDraft();
    }
    const data = await api(`/api/pixellogic/graphs/${graphId}/validate`, { method: 'POST' });
    state.validation = data.validation ?? null;
    state.lastAction = state.validation?.valid ? '草稿校验通过' : '草稿校验失败';
  });
}

async function commitDraft(): Promise<void> {
  await runAction('提交生效', async () => {
    if (state.dirty) {
      await persistDraft();
    }
    const data = await api(`/api/pixellogic/graphs/${graphId}/commit`, { method: 'POST' });
    if (!data.graph) {
      throw new Error('API 未返回已提交 graph。');
    }
    state.graph = data.graph;
    state.committedGraph = data.graph;
    state.validation = data.validation ?? null;
    state.hasDraft = false;
    state.dirty = false;
    state.lastAction = data.message ?? '图已提交生效';
  });
}

async function persistDraft(): Promise<ApiResponse> {
  const data = await api(`/api/pixellogic/graphs/${graphId}/draft`, {
    method: 'PUT',
    body: JSON.stringify({ graph: currentGraph() }),
  });
  if (!data.graph) {
    throw new Error('API 未返回已保存草稿。');
  }
  state.graph = data.graph;
  state.hasDraft = true;
  state.dirty = false;
  state.validation = null;
  return data;
}

async function startTest(): Promise<void> {
  await runAction('测试运行', async () => {
    const warning = uncommittedNotice();
    const data = await api('/api/pixellogic/test/start', { method: 'POST' });
    state.apiStatus = 'online';
    state.latestTrace = data.trace ?? null;
    state.lastAction = warning || data.message || '测试运行已执行';
  });
}

async function resetTest(): Promise<void> {
  await runAction('重置测试状态', async () => {
    const data = await api('/api/pixellogic/test/reset', { method: 'POST' });
    state.apiStatus = 'online';
    await refreshLatestTrace(false);
    state.lastAction = data.message ?? '测试状态已重置';
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
    await runAction('刷新执行记录', action);
  } else {
    await action();
  }
}

async function runAction(label: string, action: () => Promise<void>): Promise<void> {
  state.busyAction = label;
  state.error = '';
  renderApp();

  try {
    await action();
  } catch (error) {
    const connected = error instanceof PixelLogicApiError ? error.connected : false;
    state.apiStatus = connected ? 'online' : 'offline';
    state.statusMessage = connected ? 'API 已连接' : 'API 未连接';
    state.error = error instanceof Error ? error.message : 'API 未连接';
  } finally {
    state.busyAction = null;
    renderApp();
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

function branchForNode(nodeItem: GraphNode): Branch {
  if (nodeItem.id === 'debug-already-started') {
    return 'fail';
  }
  if (nodeItem.id === 'manual-trigger' || nodeItem.id === 'condition-started') {
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
      return `${config.scope ?? 'PLAYER'}.${config.key ?? 'key'} 等于 ${config.expected ?? 'false'}`;
    case 'MESSAGE_ACTION':
      return `向模拟玩家显示：${config.message ?? ''}`;
    case 'STATE_SET_ACTION':
      return `把 ${config.scope ?? 'PLAYER'}.${config.key ?? 'key'} 设置为 ${config.value ?? ''}`;
    case 'STATE_ADD_ACTION':
      return `把 ${config.scope ?? 'PLAYER'}.${config.key ?? 'key'} 增加 ${config.amount ?? '1'}`;
    case 'TIMER_START_ACTION':
      return `倒计时 ${config.durationSeconds ?? '30'} 秒后继续`;
    case 'DEBUG_LOG_ACTION':
      return `记录：${config.message ?? ''}`;
    default:
      return nodeItem.id;
  }
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
  if (!validation) {
    return '<li><span class="warn"></span>草稿尚未校验</li>';
  }
  if (validation.valid) {
    return '<li><span class="ok"></span>草稿校验通过，可以提交生效</li>';
  }
  return validation.issues
    .map((issue) => `<li><span class="warn"></span>${escapeHtml(issue.message)}</li>`)
    .join('');
}

function validationTitle(): string {
  if (state.dirty) {
    return '未保存';
  }
  if (state.validation?.valid) {
    return '通过';
  }
  if (state.validation && !state.validation.valid) {
    return '失败';
  }
  return state.hasDraft ? '有草稿' : '已提交';
}

function uncommittedNotice(): string {
  if (state.dirty) {
    return '当前有未提交草稿/未保存改动，测试运行仍使用已提交版本。';
  }
  if (state.hasDraft) {
    return '当前有未提交草稿，测试运行仍使用已提交版本。';
  }
  return '';
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
