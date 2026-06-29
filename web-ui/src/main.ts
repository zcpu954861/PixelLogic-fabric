import './styles.css';

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
  apiStatus: 'checking' | 'online' | 'offline';
  statusMessage: string;
  demoActor: string;
  busyAction: string | null;
  lastAction: string;
  error: string;
  latestTrace: ApiTrace | null;
  traces: ApiTrace[];
};

const state: UiState = {
  apiStatus: 'checking',
  statusMessage: '正在连接 PixelLogic API...',
  demoActor: 'WebUI 模拟玩家',
  busyAction: null,
  lastAction: '尚未运行',
  error: '',
  latestTrace: null,
  traces: [],
};

const app = document.querySelector<HTMLDivElement>('#app');

if (app) {
  render();
  void bootstrap();
}

async function bootstrap() {
  await refreshStatus();
  await refreshLatestTrace();
}

function render() {
  if (!app) {
    return;
  }

  app.innerHTML = `
    <section class="app-shell" aria-label="PixelLogic WebUI">
      <header class="topbar">
        <div>
          <p class="eyebrow">PixelLogic v1</p>
          <h1>测试运行工作台</h1>
        </div>
        <div class="topbar-status" aria-live="polite">
          <span class="status-dot ${state.apiStatus}"></span>
          <span>${escapeHtml(statusLabel())}</span>
        </div>
        <div class="toolbar" aria-label="测试运行操作">
          ${button('refresh-status', '刷新状态', 'secondary')}
          ${button('start-test', '测试运行', 'primary')}
          ${button('reset-test', '重置测试状态', 'secondary')}
          ${button('refresh-trace', '刷新执行记录', 'secondary')}
        </div>
      </header>

      <aside class="left-rail" aria-label="流程与积木库">
        <section>
          <h2>流程</h2>
          <button class="flow-item selected" type="button">Demo 开始流程</button>
        </section>
        <section>
          <h2>积木库</h2>
          <ul class="library-list">
            <li>手动触发</li>
            <li>状态判断</li>
            <li>发送消息</li>
            <li>状态写入</li>
            <li>计时器</li>
            <li>调试记录</li>
          </ul>
        </section>
      </aside>

      <main class="workspace" aria-label="槽位式横向积木流">
        <section class="runtime-strip" aria-live="polite">
          <div>
            <span class="label">API 状态</span>
            <strong>${escapeHtml(state.statusMessage)}</strong>
          </div>
          <div>
            <span class="label">测试上下文</span>
            <strong>${escapeHtml(state.demoActor)}</strong>
          </div>
          <div>
            <span class="label">最后动作</span>
            <strong>${escapeHtml(state.lastAction)}</strong>
          </div>
        </section>

        <section class="flow-board" aria-label="当前测试流程">
          <article class="block-card trigger-card" tabindex="0">
            <span class="block-type">触发</span>
            <h2>WebUI 测试运行</h2>
            <p>点击测试运行后，后端以模拟玩家执行内置 demo graph。</p>
            <span class="out-slot">started</span>
          </article>

          <article class="block-card condition-card" tabindex="0">
            <span class="block-type">条件</span>
            <h2>PLAYER.started == false</h2>
            <p>独立条件块选择通过或失败分支。</p>
            <div class="branch-slot pass">通过</div>
            <div class="branch-slot fail">失败</div>
          </article>

          <section class="branch-stack" aria-label="条件分支">
            <div class="branch-row">
              <article class="block-card action-card success" tabindex="0">
                <span class="block-type">动作</span>
                <h2>欢迎 + 状态 + 计时器</h2>
                <p>发送欢迎消息，写入 started，累加 start_count，并启动 30 秒计时器。</p>
              </article>
              <article class="block-card action-card success" tabindex="0">
                <span class="block-type">完成</span>
                <h2>倒计时结束</h2>
                <p>计时器完成后写入调试记录。</p>
              </article>
            </div>
            <div class="branch-row">
              <article class="block-card action-card danger" tabindex="0">
                <span class="block-type">失败</span>
                <h2>已经开始过</h2>
                <p>第二次运行会进入失败分支，并显示最近 trace。</p>
              </article>
            </div>
          </section>
        </section>
      </main>

      <aside class="right-rail" aria-label="选中积木属性">
        <section>
          <h2>属性</h2>
          <dl class="property-list">
            <div>
              <dt>图</dt>
              <dd>demo-start-flow</dd>
            </div>
            <div>
              <dt>运行方式</dt>
              <dd>真实后端 API</dd>
            </div>
            <div>
              <dt>模拟玩家</dt>
              <dd>${escapeHtml(state.demoActor)}</dd>
            </div>
          </dl>
        </section>
        <section class="validation-card">
          <h2>验证</h2>
          <p>当前 demo graph 已由后端校验后执行。</p>
        </section>
      </aside>

      <footer class="trace-dock" aria-label="执行记录">
        <div class="dock-header">
          <div>
            <p class="eyebrow">Execution Trace</p>
            <h2>${state.latestTrace ? `最近记录 ${escapeHtml(shortTraceId(state.latestTrace.id))}` : '最近执行记录'}</h2>
          </div>
          <span class="trace-count">${state.latestTrace ? `${state.latestTrace.steps.length} 步` : '无记录'}</span>
        </div>
        ${state.error ? `<p class="error-banner" role="alert">${escapeHtml(state.error)}</p>` : ''}
        ${traceHtml(state.latestTrace)}
      </footer>
    </section>
  `;

  bindActions();
}

function bindActions() {
  document.querySelector<HTMLButtonElement>('[data-action="refresh-status"]')?.addEventListener('click', () => {
    void refreshStatus();
  });
  document.querySelector<HTMLButtonElement>('[data-action="start-test"]')?.addEventListener('click', () => {
    void startTest();
  });
  document.querySelector<HTMLButtonElement>('[data-action="reset-test"]')?.addEventListener('click', () => {
    void resetTest();
  });
  document.querySelector<HTMLButtonElement>('[data-action="refresh-trace"]')?.addEventListener('click', () => {
    void refreshLatestTrace();
  });
}

async function refreshStatus() {
  await runAction('刷新状态', async () => {
    const data = await api('/api/pixellogic/status');
    state.apiStatus = 'online';
    state.statusMessage = data.message ?? 'API 已连接';
    state.demoActor = data.demoActor?.label ?? 'WebUI 模拟玩家';
    state.lastAction = 'API 状态已刷新';
  });
}

async function startTest() {
  await runAction('测试运行', async () => {
    const data = await api('/api/pixellogic/test/start', { method: 'POST' });
    state.latestTrace = data.trace ?? null;
    state.lastAction = data.message ?? '测试运行已执行';
    await refreshTraces(false);
  });
}

async function resetTest() {
  await runAction('重置测试状态', async () => {
    const data = await api('/api/pixellogic/test/reset', { method: 'POST' });
    await refreshLatestTrace(false);
    state.lastAction = data.message ?? '测试状态已重置';
  });
}

async function refreshLatestTrace(showBusy = true) {
  const action = async () => {
    const data = await api('/api/pixellogic/traces/latest');
    state.latestTrace = data.trace ?? null;
    state.lastAction = state.latestTrace ? '执行记录已刷新' : state.lastAction;
  };
  if (showBusy) {
    await runAction('刷新执行记录', action);
  } else {
    await action();
    render();
  }
}

async function refreshTraces(showBusy = true) {
  const action = async () => {
    const data = await api('/api/pixellogic/traces');
    state.traces = data.traces ?? [];
  };
  if (showBusy) {
    await runAction('刷新记录列表', action);
  } else {
    await action();
  }
}

async function runAction(label: string, action: () => Promise<void>) {
  state.busyAction = label;
  state.error = '';
  render();
  try {
    await action();
    if (state.apiStatus !== 'online') {
      state.apiStatus = 'online';
    }
  } catch (error) {
    state.apiStatus = 'offline';
    state.error = error instanceof Error ? error.message : '请求失败，请确认 PixelLogic API 已启动。';
  } finally {
    state.busyAction = null;
    render();
  }
}

async function api(path: string, init?: RequestInit): Promise<ApiResponse> {
  const response = await fetch(path, init);
  const data = (await response.json()) as ApiResponse;
  if (!response.ok || !data.ok) {
    throw new Error(data.error?.message ?? 'PixelLogic API 返回错误。');
  }
  return data;
}

function button(action: string, label: string, tone: 'primary' | 'secondary') {
  const busy = state.busyAction !== null;
  const text = state.busyAction === label ? `${label}中...` : label;
  return `<button class="tool-button ${tone}" type="button" data-action="${action}" ${busy ? 'disabled' : ''}>${escapeHtml(text)}</button>`;
}

function traceHtml(trace: ApiTrace | null) {
  if (!trace) {
    return `<div class="empty-trace">暂无执行记录。启动 API 后点击“测试运行”。</div>`;
  }
  return `
    <ol class="trace-list">
      ${trace.steps
        .map(
          (step) => `
            <li>
              <time>${escapeHtml(formatTime(step.timestamp))}</time>
              <span>${escapeHtml(step.message)}</span>
            </li>
          `,
        )
        .join('')}
    </ol>
    ${trace.truncated ? '<p class="trace-warning">记录已截断。</p>' : ''}
  `;
}

function statusLabel() {
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

function shortTraceId(traceId: string) {
  return traceId.length > 8 ? traceId.slice(0, 8) : traceId;
}

function formatTime(raw: string) {
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) {
    return '--:--:--';
  }
  return date.toLocaleTimeString('zh-CN', { hour12: false });
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}
