import './styles.css';

type Status = 'ok' | 'selected' | 'warning' | 'error';
type PortKind = 'input' | 'output';

type Port = {
  id: string;
  label: string;
  kind: PortKind;
  branch?: 'top' | 'bottom' | 'middle';
  connected?: boolean;
};

type FlowBlock = {
  id: string;
  type: string;
  name: string;
  summary: string;
  status: Status;
  x: number;
  y: number;
  width: number;
  height: number;
  inputs: Port[];
  outputs: Port[];
};

type FlowLink = {
  id: string;
  from: string;
  fromPort: string;
  to: string;
  toPort: string;
  label: string;
};

const statusText: Record<Status, string> = {
  ok: '正常',
  selected: '已选中',
  warning: '警告',
  error: '错误',
};

const blocks: FlowBlock[] = [
  {
    id: 'trigger',
    type: '命令触发',
    name: '/startgame',
    summary: '玩家输入命令后开始流程',
    status: 'ok',
    x: 70,
    y: 210,
    width: 260,
    height: 142,
    inputs: [],
    outputs: [{ id: 'start', label: '开始', kind: 'output', branch: 'middle', connected: true }],
  },
  {
    id: 'condition',
    type: '条件判断',
    name: '是否未开始',
    summary: 'PLAYER.started 等于 false',
    status: 'selected',
    x: 390,
    y: 150,
    width: 300,
    height: 250,
    inputs: [{ id: 'in', label: '输入', kind: 'input', branch: 'middle', connected: true }],
    outputs: [
      { id: 'pass', label: '通过：继续开始流程', kind: 'output', branch: 'top', connected: true },
      { id: 'fail', label: '失败：写入调试记录', kind: 'output', branch: 'bottom', connected: true },
    ],
  },
  {
    id: 'message',
    type: '发送消息',
    name: '发送欢迎语',
    summary: '向玩家显示：欢迎开始游戏',
    status: 'ok',
    x: 770,
    y: 70,
    width: 280,
    height: 150,
    inputs: [{ id: 'in', label: '输入', kind: 'input', branch: 'middle', connected: true }],
    outputs: [
      { id: 'done', label: '完成', kind: 'output', branch: 'top', connected: true },
      { id: 'error', label: '错误', kind: 'output', branch: 'bottom', connected: false },
    ],
  },
  {
    id: 'state',
    type: '状态动作',
    name: '记录开始状态',
    summary: '把 PLAYER.started 设置为 true',
    status: 'ok',
    x: 1120,
    y: 70,
    width: 280,
    height: 150,
    inputs: [{ id: 'in', label: '输入', kind: 'input', branch: 'middle', connected: true }],
    outputs: [
      { id: 'done', label: '完成', kind: 'output', branch: 'top', connected: true },
      { id: 'error', label: '错误', kind: 'output', branch: 'bottom', connected: false },
    ],
  },
  {
    id: 'timer',
    type: '计时器',
    name: '30 秒倒计时',
    summary: '等待 30 秒，完成后继续',
    status: 'warning',
    x: 1470,
    y: 70,
    width: 260,
    height: 150,
    inputs: [{ id: 'in', label: '输入', kind: 'input', branch: 'middle', connected: true }],
    outputs: [
      { id: 'cancel', label: '取消', kind: 'output', branch: 'top', connected: false },
      { id: 'done', label: '完成', kind: 'output', branch: 'bottom', connected: true },
    ],
  },
  {
    id: 'debug',
    type: '调试记录',
    name: '写入调试日志',
    summary: '记录玩家已经开始过游戏',
    status: 'error',
    x: 770,
    y: 390,
    width: 280,
    height: 150,
    inputs: [{ id: 'in', label: '输入', kind: 'input', branch: 'middle', connected: true }],
    outputs: [{ id: 'done', label: '完成', kind: 'output', branch: 'middle', connected: false }],
  },
];

const links: FlowLink[] = [
  { id: 'l1', from: 'trigger', fromPort: 'start', to: 'condition', toPort: 'in', label: '开始' },
  { id: 'l2', from: 'condition', fromPort: 'pass', to: 'message', toPort: 'in', label: '通过' },
  { id: 'l3', from: 'condition', fromPort: 'fail', to: 'debug', toPort: 'in', label: '失败' },
  { id: 'l4', from: 'message', fromPort: 'done', to: 'state', toPort: 'in', label: '完成' },
  { id: 'l5', from: 'state', fromPort: 'done', to: 'timer', toPort: 'in', label: '完成' },
  { id: 'l6', from: 'timer', fromPort: 'done', to: 'debug', toPort: 'in', label: '完成' },
];

const app = document.querySelector<HTMLDivElement>('#app');
const blockById = new Map(blocks.map((block) => [block.id, block]));
const world = { width: 1840, height: 620 };

let scale = 0.82;
let offsetX = 72;
let offsetY = 38;
let isDragging = false;
let dragStart = { x: 0, y: 0 };
let dragOffset = { x: 0, y: 0 };

function portY(block: FlowBlock, port: Port): number {
  if (port.branch === 'top') {
    return block.y + 58;
  }

  if (port.branch === 'bottom') {
    return block.y + block.height - 58;
  }

  return block.y + block.height / 2;
}

function portPoint(block: FlowBlock, port: Port): { x: number; y: number } {
  return {
    x: port.kind === 'input' ? block.x : block.x + block.width,
    y: portY(block, port),
  };
}

function getPort(block: FlowBlock, portId: string, kind: PortKind): Port {
  const port = [...block.inputs, ...block.outputs].find((item) => item.id === portId && item.kind === kind);

  if (!port) {
    throw new Error(`Missing port: ${block.id}.${portId}`);
  }

  return port;
}

function linkPath(link: FlowLink): string {
  const from = blockById.get(link.from);
  const to = blockById.get(link.to);

  if (!from || !to) {
    return '';
  }

  const start = portPoint(from, getPort(from, link.fromPort, 'output'));
  const end = portPoint(to, getPort(to, link.toPort, 'input'));
  const run = Math.max(36, Math.min(86, Math.abs(end.x - start.x) * 0.24));
  const midX = start.x + (end.x - start.x) / 2;

  return `M ${start.x} ${start.y} H ${start.x + run} C ${midX} ${start.y}, ${midX} ${end.y}, ${end.x - run} ${end.y} H ${end.x}`;
}

function renderPort(port: Port): string {
  const state = port.connected === false ? 'disconnected' : 'connected';

  return `
    <span class="block-port ${port.kind} ${state} ${port.branch ?? 'middle'}">
      <i aria-hidden="true"></i>
      <span>${port.label}</span>
    </span>
  `;
}

function renderBlock(block: FlowBlock): string {
  return `
    <article
      class="flow-block ${block.status}"
      data-block="${block.id}"
      style="left:${block.x}px; top:${block.y}px; width:${block.width}px; min-height:${block.height}px"
    >
      <div class="block-status">
        <span>${block.type}</span>
        <b>${statusText[block.status]}</b>
      </div>
      <h3>${block.name}</h3>
      <p>${block.summary}</p>
      <div class="block-ports">
        <div class="input-ports">${block.inputs.map(renderPort).join('')}</div>
        <div class="output-ports">${block.outputs.map(renderPort).join('')}</div>
      </div>
    </article>
  `;
}

function renderLink(link: FlowLink): string {
  const from = blockById.get(link.from);
  const to = blockById.get(link.to);

  if (!from || !to) {
    return '';
  }

  const start = portPoint(from, getPort(from, link.fromPort, 'output'));
  const end = portPoint(to, getPort(to, link.toPort, 'input'));
  const labelX = start.x + Math.min(96, Math.max(58, (end.x - start.x) * 0.35));
  const labelY = start.y + (end.y - start.y) * 0.42;

  return `
    <g class="flow-link" data-link="${link.id}" data-from="${link.from}" data-to="${link.to}">
      <path d="${linkPath(link)}"></path>
      <text x="${labelX}" y="${labelY}">${link.label}</text>
    </g>
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
  scale = Math.min(1, (rect.width - 80) / world.width, (rect.height - 60) / world.height);
  offsetX = Math.max(28, (rect.width - world.width * scale) / 2);
  offsetY = Math.max(20, (rect.height - world.height * scale) / 2);
  setTransform();
}

function centerView(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');

  if (!viewport) {
    return;
  }

  const rect = viewport.getBoundingClientRect();
  scale = 0.88;
  offsetX = Math.max(28, (rect.width - world.width * scale) / 2);
  offsetY = Math.max(22, (rect.height - world.height * scale) / 2);
  setTransform();
}

function focusSelectedBlock(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');
  const selected = blocks.find((block) => block.status === 'selected');

  if (!viewport || !selected) {
    return;
  }

  const rect = viewport.getBoundingClientRect();
  scale = Math.max(0.82, Math.min(1.05, scale));
  offsetX = rect.width / 2 - (selected.x + selected.width / 2) * scale;
  offsetY = rect.height / 2 - (selected.y + selected.height / 2) * scale;
  setTransform();
}

function clearLinkFocus(): void {
  document.querySelectorAll('.flow-link, .flow-block').forEach((item) => {
    item.classList.remove('is-related');
  });
}

function markSelectedLinks(): void {
  const selected = blocks.find((block) => block.status === 'selected');

  if (!selected) {
    return;
  }

  document.querySelector(`[data-block="${selected.id}"]`)?.classList.add('is-related');
  document.querySelectorAll<SVGGElement>('.flow-link').forEach((linkEl) => {
    if (linkEl.dataset.from === selected.id || linkEl.dataset.to === selected.id) {
      linkEl.classList.add('is-related');
    }
  });
}

function bindInteractions(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');

  if (!viewport) {
    return;
  }

  viewport.addEventListener('pointerdown', (event) => {
    if ((event.target as HTMLElement).closest('.flow-block, button')) {
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
      const next = Math.min(1.3, Math.max(0.55, scale + (event.deltaY > 0 ? -0.06 : 0.06)));
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

  document.querySelectorAll<SVGGElement>('.flow-link').forEach((linkEl) => {
    linkEl.addEventListener('mouseenter', () => {
      clearLinkFocus();
      linkEl.classList.add('is-related');
      document.querySelector(`[data-block="${linkEl.dataset.from}"]`)?.classList.add('is-related');
      document.querySelector(`[data-block="${linkEl.dataset.to}"]`)?.classList.add('is-related');
    });
    linkEl.addEventListener('mouseleave', () => {
      clearLinkFocus();
      markSelectedLinks();
    });
  });

  markSelectedLinks();
}

if (app) {
  app.innerHTML = `
    <section class="workspace" aria-label="PixelLogic 横向积木流原型">
      <header class="topbar">
        <div class="brand">
          <span class="mark" aria-hidden="true"></span>
          <div>
            <strong>PixelLogic</strong>
            <small>当前图：大厅开始流程</small>
          </div>
        </div>
        <nav class="top-actions" aria-label="工作台操作">
          <button type="button" class="ghost-button">已保存</button>
          <button type="button" class="run-button">测试运行</button>
          <button type="button" class="ghost-button" data-action="fit">适应视图</button>
          <button type="button" class="ghost-button" data-action="center">回到中心</button>
          <button type="button" class="icon-button" aria-label="设置">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 8.5a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7Zm7 3.5a6.8 6.8 0 0 0-.1-1.1l2-1.5-2-3.4-2.4 1a7.4 7.4 0 0 0-1.9-1.1L14.2 3h-4.4l-.4 2.6a7.4 7.4 0 0 0-1.9 1.1l-2.4-1-2 3.4 2 1.5A6.8 6.8 0 0 0 5 12c0 .4 0 .8.1 1.1l-2 1.5 2 3.4 2.4-1a7.4 7.4 0 0 0 1.9 1.1l.4 2.6h4.4l.4-2.6a7.4 7.4 0 0 0 1.9-1.1l2.4 1 2-3.4-2-1.5c.1-.3.1-.7.1-1.1Z"/></svg>
          </button>
        </nav>
      </header>

      <aside class="left-rail" aria-label="流程列表和节点库">
        <section>
          <div class="panel-title">
            <span>流程列表</span>
            <button type="button" class="tiny-button">新建</button>
          </div>
          <button type="button" class="graph-item active">大厅开始流程 <small>6 个积木</small></button>
          <button type="button" class="graph-item">宝箱门流程 <small>4 个积木</small></button>
          <button type="button" class="graph-item">竞技场重置 <small>草稿</small></button>
        </section>

        <section>
          <div class="panel-title"><span>节点库</span></div>
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
            <h1>横向积木流</h1>
          </div>
          <div class="canvas-tools" aria-label="画布状态">
            <span>缩放 <b data-zoom>82%</b></span>
            <button type="button" data-action="focus">聚焦选中</button>
          </div>
        </div>
        <section class="canvas-viewport" aria-label="可拖动画布">
          <div class="flow-world" style="width:${world.width}px; height:${world.height}px">
            <svg class="link-layer" viewBox="0 0 ${world.width} ${world.height}" aria-hidden="true">
              ${links.map(renderLink).join('')}
            </svg>
            ${blocks.map(renderBlock).join('')}
            <div class="drop-hint" style="left: 1770px; top: 84px">拖入下一个积木</div>
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
          <p>当 PLAYER.started 为 false，走上方“通过”；否则走下方“失败”。</p>
        </section>
        <section class="validation-card">
          <b>验证提示</b>
          <p>失败出口已经连接到调试记录。正式流程建议再加一个面向玩家的提示动作。</p>
        </section>
      </aside>

      <footer class="bottom-dock" aria-label="验证问题和执行记录">
        <section>
          <div class="panel-title"><span>验证问题</span><b>3</b></div>
          <ul class="issue-list">
            <li><span class="bad"></span>发送消息的“错误”出口未连接</li>
            <li><span class="warn"></span>计时器“取消”出口未连接</li>
            <li><span class="warn"></span>调试记录可以补充玩家名字段</li>
          </ul>
        </section>
        <section>
          <div class="panel-title"><span>执行记录</span><b>模拟</b></div>
          <ol class="trace-list">
            <li>[12:00:01] 命令触发：/startgame</li>
            <li>[12:00:01] 条件通过：PLAYER.started 等于 false</li>
            <li>[12:00:01] 发送消息：欢迎开始游戏</li>
            <li>[12:00:01] 状态写入：PLAYER.started = true</li>
            <li>[12:00:31] 计时器完成</li>
          </ol>
        </section>
      </footer>
    </section>
  `;

  bindInteractions();
  centerView();
}
