import './styles.css';

type Status = 'ok' | 'selected' | 'warning' | 'error';
type BlockKind = 'trigger' | 'condition' | 'action' | 'state' | 'timer' | 'debug';
type Branch = 'main' | 'pass' | 'fail';

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

const statusText: Record<Status, string> = {
  ok: '正常',
  selected: '已选中',
  warning: '警告',
  error: '错误',
};

const blocks: SlotBlock[] = [
  {
    id: 'trigger',
    kind: 'trigger',
    branch: 'main',
    type: '命令触发',
    title: '/startgame',
    summary: '玩家输入命令后开始流程',
    status: 'ok',
    x: 48,
    y: 86,
    width: 286,
    height: 148,
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
    x: 326,
    y: 74,
    width: 360,
    height: 432,
    meta: '双槽位',
  },
  {
    id: 'message',
    kind: 'action',
    branch: 'pass',
    type: '发送消息',
    title: '发送欢迎语',
    summary: '向玩家显示：欢迎开始游戏',
    status: 'ok',
    x: 696,
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
    x: 936,
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
    x: 1176,
    y: 78,
    width: 246,
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
    x: 1388,
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
    summary: '记录玩家已经开始过游戏',
    status: 'error',
    x: 746,
    y: 332,
    width: 300,
    height: 156,
  },
];

const joins: SlotJoin[] = [
  { id: 'j1', from: 'trigger', to: 'condition', branch: 'main', x: 318, y: 151, width: 44 },
  { id: 'j2', from: 'condition', to: 'message', branch: 'pass', x: 676, y: 139, width: 36, tone: 'pass' },
  { id: 'j3', from: 'message', to: 'state', branch: 'pass', x: 928, y: 139, width: 36, tone: 'pass' },
  { id: 'j4', from: 'state', to: 'timer', branch: 'pass', x: 1168, y: 139, width: 36, tone: 'pass' },
  { id: 'j5', from: 'timer', to: 'done-debug', branch: 'pass', x: 1380, y: 139, width: 36, tone: 'pass' },
  { id: 'j6', from: 'condition', to: 'fail-debug', branch: 'fail', x: 676, y: 397, width: 86, tone: 'fail' },
];

const app = document.querySelector<HTMLDivElement>('#app');
const world = { width: 1840, height: 620 };

let scale = 0.86;
let offsetX = 28;
let offsetY = 34;
let isDragging = false;
let dragStart = { x: 0, y: 0 };
let dragOffset = { x: 0, y: 0 };

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
  const errorChip = block.collapsedError
    ? `<span class="error-chip">${block.collapsedError}</span>`
    : '';
  const conditionSlots =
    block.kind === 'condition'
      ? `
        <div class="condition-slots" aria-label="条件分支槽位">
          <div class="branch-slot pass"><span>通过</span><b>继续开始流程</b></div>
          <div class="branch-slot fail"><span>失败</span><b>写入调试记录</b></div>
        </div>
      `
      : '';

  return `
    <article
      class="logic-block ${block.kind} ${block.status} ${block.branch}"
      data-block="${block.id}"
      data-branch="${block.branch}"
      style="left:${block.x}px; top:${block.y}px; width:${block.width}px; min-height:${block.height}px"
    >
      <div class="block-topline">
        <span>${block.type}</span>
        <b>${statusText[block.status]}</b>
      </div>
      <h3>${block.title}</h3>
      <p>${block.summary}</p>
      ${block.meta ? `<small>${block.meta}</small>` : ''}
      ${errorChip}
      ${conditionSlots}
    </article>
  `;
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
  document.querySelectorAll('.logic-block, .slot-join, .branch-lane').forEach((item) => {
    item.classList.remove('is-related');
  });
}

function markJoinFocus(joinEl: HTMLElement): void {
  clearFocus();
  joinEl.classList.add('is-related');
  document.querySelector(`[data-block="${joinEl.dataset.from}"]`)?.classList.add('is-related');
  document.querySelector(`[data-block="${joinEl.dataset.to}"]`)?.classList.add('is-related');
  document.querySelector(`[data-lane="${joinEl.dataset.branch}"]`)?.classList.add('is-related');
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
      document.querySelector(`[data-lane="${joinEl.dataset.branch}"]`)?.classList.add('is-related');
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

  document.querySelectorAll<HTMLElement>('.slot-join').forEach((joinEl) => {
    joinEl.addEventListener('mouseenter', () => markJoinFocus(joinEl));
    joinEl.addEventListener('mouseleave', () => {
      clearFocus();
      markSelectedFocus();
    });
  });

  markSelectedFocus();
}

if (app) {
  app.innerHTML = `
    <section class="workspace" aria-label="PixelLogic 槽位式横向积木流原型">
      <header class="topbar">
        <div class="brand">
          <span class="mark" aria-hidden="true"></span>
          <div>
            <strong>PixelLogic</strong>
            <small>当前流程：大厅开始流程</small>
          </div>
        </div>
        <nav class="top-actions" aria-label="工作台操作">
          <button type="button" class="ghost-button">已保存</button>
          <button type="button" class="run-button">测试运行</button>
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
            <button type="button" data-action="focus">聚焦选中</button>
          </div>
        </div>
        <section class="canvas-viewport" aria-label="可拖动画布">
          <div class="flow-world" style="width:${world.width}px; height:${world.height}px">
            <div class="branch-lane pass" data-lane="pass">
              <span>通过分支</span>
            </div>
            <div class="branch-lane fail" data-lane="fail">
              <span>失败分支</span>
            </div>
            ${joins.map(renderSlotJoin).join('')}
            ${blocks.map(renderBlock).join('')}
            <div class="drop-slot" style="left: 1788px; top: 112px">放入下一个积木</div>
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
          <b>摘要预览</b>
          <p>条件块右侧固定两个槽位：上方“通过”，下方“失败”。分支块直接贴到槽位后方，不需要追踪长线。</p>
        </section>
        <section class="validation-card">
          <b>验证提示</b>
          <p>动作积木默认只展示主路完成，错误处理收进小槽位，选中或悬停时再强调。</p>
        </section>
      </aside>

      <footer class="bottom-dock" aria-label="验证问题和执行记录">
        <section>
          <div class="panel-title"><span>验证问题</span><b>2</b></div>
          <ul class="issue-list">
            <li><span class="warn"></span>发送消息的错误处理槽位尚未接入</li>
            <li><span class="warn"></span>计时器取消路径保留为后续设计</li>
          </ul>
        </section>
        <section>
          <div class="panel-title"><span>执行记录</span><b>模拟</b></div>
          <ol class="trace-list">
            <li>[12:00:01] 命令触发：/startgame</li>
            <li>[12:00:01] 条件通过：PLAYER.started 等于 false</li>
            <li>[12:00:01] 发送消息：欢迎开始游戏</li>
            <li>[12:00:01] 状态写入：PLAYER.started = true</li>
            <li>[12:00:31] 调试记录：倒计时结束</li>
          </ol>
        </section>
      </footer>
    </section>
  `;

  bindInteractions();
  centerView();
}
