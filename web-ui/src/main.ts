import './styles.css';

type ApiStatus = 'checking' | 'online' | 'offline';
type Status = 'ok' | 'selected' | 'warning' | 'error';
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

type ApiResponse = {
  ok: boolean;
  message?: string;
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
};

type SlotBlock = {
  id: string;
  kind: BlockKind;
  branch: Branch;
  type: string;
  title: string;
  summary: string;
  status: Status;
  x: number;
  y: number;
  width: number;
  height: number;
  selected?: boolean;
  meta?: string;
  collapsedError?: string;
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

const state: UiState = {
  apiStatus: 'checking',
  statusMessage: '正在连接 API...',
  demoActor: 'WebUI 模拟玩家',
  busyAction: null,
  lastAction: '尚未运行',
  error: '',
  latestTrace: null,
};

const blocks: SlotBlock[] = [
  {
    id: 'trigger',
    kind: 'trigger',
    branch: 'main',
    type: '测试触发',
    title: '/pixellogic test start',
    summary: 'WebUI 点击后调用真实后端 API',
    status: 'ok',
    x: 48,
    y: 205,
    width: 260,
    height: 150,
    meta: '起点',
  },
  {
    id: 'condition',
    kind: 'condition',
    branch: 'main',
    type: '条件判断',
    title: '是否未开始',
    summary: 'PLAYER.started 等于 false',
    status: 'selected',
    selected: true,
    x: 294,
    y: 78,
    width: 384,
    height: 404,
    meta: '双槽位',
  },
  {
    id: 'message',
    kind: 'action',
    branch: 'pass',
    type: '发送消息',
    title: '发送欢迎语',
    summary: '向模拟玩家显示：欢迎开始游戏',
    status: 'ok',
    x: 664,
    y: 78,
    width: 260,
    height: 150,
    collapsedError: '错误处理',
  },
  {
    id: 'state',
    kind: 'state',
    branch: 'pass',
    type: '状态动作',
    title: '记录开始状态',
    summary: '把 PLAYER.started 设置为 true',
    status: 'ok',
    x: 910,
    y: 78,
    width: 260,
    height: 150,
    collapsedError: '错误处理',
  },
  {
    id: 'timer',
    kind: 'timer',
    branch: 'pass',
    type: '计时器',
    title: '等待 30 秒',
    summary: '倒计时结束后继续',
    status: 'warning',
    x: 1156,
    y: 78,
    width: 260,
    height: 150,
    meta: '主路完成',
  },
  {
    id: 'done-debug',
    kind: 'debug',
    branch: 'pass',
    type: '调试记录',
    title: '倒计时结束',
    summary: '记录本轮开始流程完成',
    status: 'ok',
    x: 1402,
    y: 78,
    width: 260,
    height: 150,
  },
  {
    id: 'fail-debug',
    kind: 'debug',
    branch: 'fail',
    type: '调试记录',
    title: '已经开始过',
    summary: '第二次运行进入失败分支',
    status: 'error',
    x: 664,
    y: 332,
    width: 260,
    height: 150,
  },
];

const joins: SlotJoin[] = [
  { id: 'j1', from: 'trigger', to: 'condition', branch: 'main', x: 292, y: 266, width: 20 },
  { id: 'j2', from: 'condition', to: 'message', branch: 'pass', x: 662, y: 139, width: 20, tone: 'pass' },
  { id: 'j3', from: 'message', to: 'state', branch: 'pass', x: 908, y: 139, width: 20, tone: 'pass' },
  { id: 'j4', from: 'state', to: 'timer', branch: 'pass', x: 1154, y: 139, width: 20, tone: 'pass' },
  { id: 'j5', from: 'timer', to: 'done-debug', branch: 'pass', x: 1400, y: 139, width: 20, tone: 'pass' },
  { id: 'j6', from: 'condition', to: 'fail-debug', branch: 'fail', x: 662, y: 393, width: 20, tone: 'fail' },
];

const app = document.querySelector<HTMLDivElement>('#app');
const world = { width: 1840, height: 620 };

let scale = 0.86;
let offsetX = 28;
let offsetY = 34;
let isDragging = false;
let dragStart = { x: 0, y: 0 };
let dragOffset = { x: 0, y: 0 };

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

function renderSlotJoin(join: SlotJoin): string {
  return `
    <button
      type="button"
      class="slot-join ${join.tone ?? 'normal'}"
      data-join="${join.id}"
      data-from="${join.from}"
      data-to="${join.to}"
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
      data-block="${block.id}"
      data-branch="${block.branch}"
      style="left:${block.x}px; top:${block.y}px; width:${block.width}px; height:${block.height}px; z-index:${3000 - block.x + (block.selected ? 1000 : 0)}"
    >
      ${renderShape(puzzlePath(block.kind, block.width, block.height), block.width, block.height, branchTabs)}
      <div class="block-topline">
        <span>${block.type}</span>
      </div>
      <h3>${block.title}</h3>
      <p>${block.summary}</p>
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

  app.innerHTML = `
    <section class="workspace" aria-label="PixelLogic 槽位式横向积木流">
      <header class="topbar">
        <div class="brand">
          <span class="mark" aria-hidden="true"></span>
          <div>
            <strong>PixelLogic</strong>
            <small>当前流程：大厅开始流程</small>
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
          <button type="button" class="icon-button" aria-label="设置">
            <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M12 8.5a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7Zm7 3.5a6.8 6.8 0 0 0-.1-1.1l2-1.5-2-3.4-2.4 1a7.4 7.4 0 0 0-1.9-1.1L14.2 3h-4.4l-.4 2.6a7.4 7.4 0 0 0-1.9 1.1l-2.4-1-2 3.4 2 1.5A6.8 6.8 0 0 0 5 12c0 .4 0 .8.1 1.1l-2 1.5 2 3.4 2.4-1a7.4 7.4 0 0 0 1.9 1.1l.4 2.6h4.4l.4-2.6a7.4 7.4 0 0 0 1.9-1.1l2.4 1 2-3.4-2-1.5c.1-.3.1-.7.1-1.1Z"/></svg>
          </button>
        </nav>
      </header>

      <aside class="left-rail" aria-label="流程列表和积木库">
        <section>
          <div class="panel-title">
            <span>流程列表</span>
            <button type="button" class="tiny-button">新建</button>
          </div>
          <button type="button" class="graph-item active">大厅开始流程 <small>7 个积木</small></button>
          <button type="button" class="graph-item">宝箱门流程 <small>4 个积木</small></button>
          <button type="button" class="graph-item">竞技场重置 <small>草稿</small></button>
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
          <div class="panel-title"><span>快捷创建</span></div>
          <button type="button">创建第一个流程</button>
          <button type="button">从模板开始</button>
          <button type="button">手动创建触发器</button>
          <button type="button">查看示例</button>
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
          <b>条件判断</b>
        </div>
        <section class="form-card">
          <label>名称<input value="是否未开始" readonly /></label>
          <label>类型<input value="变量比较" readonly /></label>
          <label>状态范围<input value="PLAYER" readonly /></label>
          <label>字段<input value="started" readonly /></label>
          <label>比较方式<input value="等于" readonly /></label>
          <label>目标值<input value="false" readonly /></label>
        </section>
        <section class="preview-card">
          <b>API 状态</b>
          <p>${escapeHtml(state.statusMessage)}</p>
        </section>
        <section class="validation-card">
          <b>最后动作</b>
          <p>${escapeHtml(state.lastAction)}</p>
        </section>
        ${state.error ? `<section class="api-error" role="alert">${escapeHtml(state.error)}</section>` : ''}
      </aside>

      <footer class="bottom-dock" aria-label="验证问题和执行记录">
        <section>
          <div class="panel-title"><span>测试运行</span><b>${escapeHtml(apiStatusText())}</b></div>
          <ul class="issue-list">
            <li><span class="${state.apiStatus === 'online' ? 'ok' : 'warn'}"></span>${escapeHtml(state.statusMessage)}</li>
            <li><span class="warn"></span>当前只接入测试运行 API，不包含保存或发布</li>
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
  scale = 0.86;
  offsetX = rect.width > 1200 ? -40 : 28;
  offsetY = Math.max(24, (rect.height - 620 * scale) / 2);
  setTransform();
}

function focusSelectedBlock(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');
  const selected = blocks.find((block) => block.selected);

  if (!viewport || !selected) {
    return;
  }

  const rect = viewport.getBoundingClientRect();
  scale = Math.max(0.84, Math.min(1.02, scale));
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
  const selected = blocks.find((block) => block.selected);

  if (!selected) {
    return;
  }

  document.querySelector(`[data-block="${selected.id}"]`)?.classList.add('is-related');
  document.querySelectorAll<HTMLElement>('.slot-join').forEach((joinEl) => {
    if (joinEl.dataset.from === selected.id || joinEl.dataset.to === selected.id) {
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
    if ((event.target as HTMLElement).closest('.logic-block, .slot-join, button, input')) {
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
      const next = Math.min(1.22, Math.max(0.62, scale + (event.deltaY > 0 ? -0.06 : 0.06)));
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

  document.querySelectorAll<HTMLElement>('.slot-join').forEach((joinEl) => {
    joinEl.addEventListener('mouseenter', () => markJoinFocus(joinEl));
    joinEl.addEventListener('mouseleave', () => {
      clearFocus();
      markSelectedFocus();
    });
  });

  markSelectedFocus();
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

async function startTest(): Promise<void> {
  await runAction('测试运行', async () => {
    const data = await api('/api/pixellogic/test/start', { method: 'POST' });
    state.apiStatus = 'online';
    state.latestTrace = data.trace ?? null;
    state.lastAction = data.message ?? '测试运行已执行';
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
    state.apiStatus = 'offline';
    state.statusMessage = 'API 未连接';
    state.error = error instanceof Error ? error.message : 'API 未连接';
  } finally {
    state.busyAction = null;
    renderApp();
  }
}

async function api(path: string, init?: RequestInit): Promise<ApiResponse> {
  let response: Response;

  try {
    response = await fetch(path, {
      headers: {
        Accept: 'application/json',
      },
      ...init,
    });
  } catch {
    throw new Error('API 未连接，请确认 PixelLogic API server 已启动。');
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.toLowerCase().includes('application/json')) {
    throw new Error('API 未连接：当前 /api 返回的不是 JSON。');
  }

  let data: ApiResponse;
  try {
    data = (await response.json()) as ApiResponse;
  } catch {
    throw new Error('API 未连接：当前 /api 返回的 JSON 无法解析。');
  }

  if (!response.ok || !data.ok) {
    throw new Error(data.error?.message ?? 'PixelLogic API 返回错误。');
  }
  return data;
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

if (app) {
  renderApp();
  centerView();
  void refreshStatus().then(() => {
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
