import './styles.css';

type NodeStatus = 'normal' | 'warning' | 'error' | 'selected';

type LogicNode = {
  id: string;
  type: string;
  name: string;
  summary: string;
  status: NodeStatus;
  inputs: string[];
  outputs: string[];
  x: number;
  y: number;
};

type Edge = {
  from: string;
  to: string;
  label: string;
};

const nodes: LogicNode[] = [
  {
    id: 'command',
    type: 'Command Trigger',
    name: '/startgame',
    summary: '玩家输入开始命令后进入流程。',
    status: 'normal',
    inputs: [],
    outputs: ['start'],
    x: 7,
    y: 20,
  },
  {
    id: 'condition',
    type: 'Condition',
    name: '是否未开始',
    summary: 'PLAYER.started 等于 false 时通过。',
    status: 'selected',
    inputs: ['in'],
    outputs: ['pass', 'fail'],
    x: 35,
    y: 20,
  },
  {
    id: 'message',
    type: 'Message Action',
    name: '发送欢迎消息',
    summary: '向玩家发送“欢迎开始游戏”。',
    status: 'normal',
    inputs: ['in'],
    outputs: ['done', 'error'],
    x: 63,
    y: 10,
  },
  {
    id: 'state',
    type: 'State Action',
    name: '记录开始状态',
    summary: '把 PLAYER.started 设置为 true。',
    status: 'normal',
    inputs: ['in'],
    outputs: ['done', 'error'],
    x: 63,
    y: 41,
  },
  {
    id: 'timer',
    type: 'Timer',
    name: '30 秒倒计时',
    summary: '等待 30 秒后继续执行。',
    status: 'warning',
    inputs: ['in'],
    outputs: ['completed', 'error'],
    x: 35,
    y: 62,
  },
  {
    id: 'debug',
    type: 'Debug Log',
    name: '写入调试记录',
    summary: '输出流程结果，方便服主定位问题。',
    status: 'error',
    inputs: ['in'],
    outputs: ['done'],
    x: 7,
    y: 62,
  },
];

const edges: Edge[] = [
  { from: 'command', to: 'condition', label: 'Trigger' },
  { from: 'condition', to: 'message', label: 'pass' },
  { from: 'condition', to: 'debug', label: 'fail' },
  { from: 'message', to: 'state', label: 'done' },
  { from: 'state', to: 'timer', label: 'done' },
  { from: 'timer', to: 'debug', label: 'completed' },
];

const app = document.querySelector<HTMLDivElement>('#app');
const nodeById = new Map(nodes.map((node) => [node.id, node]));

function renderPorts(items: string[], side: 'input' | 'output'): string {
  return items
    .map((item) => `<span class="port port-${side}"><i aria-hidden="true"></i>${item}</span>`)
    .join('');
}

function renderNode(node: LogicNode): string {
  return `
    <article class="logic-node ${node.status}" style="left:${node.x}%; top:${node.y}%">
      <div class="node-topline">
        <span>${node.type}</span>
        <b>${node.status}</b>
      </div>
      <h3>${node.name}</h3>
      <p>${node.summary}</p>
      <div class="ports">
        <div>${renderPorts(node.inputs, 'input')}</div>
        <div>${renderPorts(node.outputs, 'output')}</div>
      </div>
    </article>
  `;
}

function renderEdge(edge: Edge): string {
  const from = nodeById.get(edge.from);
  const to = nodeById.get(edge.to);

  if (!from || !to) {
    return '';
  }

  const x1 = from.x + 10;
  const y1 = from.y + 10;
  const x2 = to.x + 10;
  const y2 = to.y + 10;
  const midX = (x1 + x2) / 2;
  const midY = (y1 + y2) / 2;
  const curve = Math.max(8, Math.abs(x2 - x1) * 0.25);

  return `
    <path class="edge-line" d="M ${x1} ${y1} C ${x1 + curve} ${y1}, ${x2 - curve} ${y2}, ${x2} ${y2}" />
    <text class="edge-label" x="${midX}" y="${midY}">${edge.label}</text>
  `;
}

if (app) {
  app.innerHTML = `
    <section class="workspace" aria-label="PixelLogic visual concept prototype">
      <header class="topbar">
        <div class="brand">
          <span class="mark" aria-hidden="true"></span>
          <div>
            <strong>PixelLogic</strong>
            <small>小游戏流程 / Lobby Start</small>
          </div>
        </div>
        <nav class="top-actions" aria-label="Workspace actions">
          <button type="button" class="ghost-button">已保存</button>
          <button type="button" class="run-button">测试运行</button>
          <button type="button" class="icon-button" aria-label="设置">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 8.5a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7Zm7 3.5a6.8 6.8 0 0 0-.1-1.1l2-1.5-2-3.4-2.4 1a7.4 7.4 0 0 0-1.9-1.1L14.2 3h-4.4l-.4 2.6a7.4 7.4 0 0 0-1.9 1.1l-2.4-1-2 3.4 2 1.5A6.8 6.8 0 0 0 5 12c0 .4 0 .8.1 1.1l-2 1.5 2 3.4 2.4-1a7.4 7.4 0 0 0 1.9 1.1l.4 2.6h4.4l.4-2.6a7.4 7.4 0 0 0 1.9-1.1l2.4 1 2-3.4-2-1.5c.1-.3.1-.7.1-1.1Z"/></svg>
          </button>
        </nav>
      </header>

      <aside class="left-rail" aria-label="Graphs and node library">
        <section>
          <div class="panel-title">
            <span>Graphs</span>
            <button type="button" class="tiny-button">新建</button>
          </div>
          <button type="button" class="graph-item active">Lobby Start <small>6 cards</small></button>
          <button type="button" class="graph-item">Treasure Door <small>4 cards</small></button>
          <button type="button" class="graph-item">Arena Reset <small>draft</small></button>
        </section>

        <section>
          <div class="panel-title"><span>节点库</span></div>
          <div class="library-grid">
            <button type="button">Trigger</button>
            <button type="button">Condition</button>
            <button type="button">Action</button>
            <button type="button">State</button>
            <button type="button">Timer</button>
            <button type="button">Debug</button>
          </div>
        </section>

        <section class="first-use">
          <div>
            <b>首次使用</b>
            <p>从一个清楚的小流程开始。</p>
          </div>
          <button type="button">创建第一个 Graph</button>
          <button type="button">从模板开始</button>
          <button type="button">手动创建 Trigger</button>
          <button type="button">查看示例</button>
        </section>
      </aside>

      <main class="graph-stage" aria-label="Graph Canvas">
        <div class="stage-head">
          <div>
            <p class="eyebrow">Graph Canvas</p>
            <h1>Lobby Start Flow</h1>
          </div>
          <div class="status-strip" aria-label="Validation status">
            <span class="ok">4 正常</span>
            <span class="warn">1 警告</span>
            <span class="bad">1 错误</span>
          </div>
        </div>
        <section class="canvas-board" aria-label="Mock logic graph">
          <svg class="edge-layer" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
            ${edges.map(renderEdge).join('')}
          </svg>
          ${nodes.map(renderNode).join('')}
        </section>
      </main>

      <aside class="right-panel" aria-label="Selected card properties">
        <div class="panel-title">
          <span>选中卡片属性</span>
          <b>Condition</b>
        </div>
        <section class="form-card">
          <label>名称<input value="是否未开始" readonly /></label>
          <label>条件类型<input value="变量比较" readonly /></label>
          <label>状态 scope<input value="PLAYER" readonly /></label>
          <label>变量名<input value="started" readonly /></label>
          <label>比较方式<input value="equals" readonly /></label>
          <label>目标值<input value="false" readonly /></label>
        </section>
        <section class="preview-card">
          <b>摘要预览</b>
          <p>当玩家的 started 状态为 false 时，流程走 pass；否则走 fail。</p>
        </section>
        <section class="validation-card">
          <b>验证提示</b>
          <p>fail 输出未连接到面向玩家的反馈。当前已连到 Debug Log，适合调试，正式流程建议补用户提示。</p>
        </section>
      </aside>

      <footer class="bottom-dock" aria-label="Validation and trace">
        <section>
          <div class="panel-title"><span>验证错误</span><b>4</b></div>
          <ul class="issue-list">
            <li><span class="bad"></span>未连接 fail 输出到玩家反馈</li>
            <li><span class="bad"></span>变量名为空：备用条件卡片</li>
            <li><span class="warn"></span>Timer 缺少时长说明</li>
            <li><span class="warn"></span>Action 缺少 message 内容：草稿节点</li>
          </ul>
        </section>
        <section>
          <div class="panel-title"><span>执行 trace</span><b>mock</b></div>
          <ol class="trace-list">
            <li>[12:00:01] Command trigger started</li>
            <li>[12:00:01] Condition passed: player.started == false</li>
            <li>[12:00:01] Message sent: 欢迎开始游戏</li>
            <li>[12:00:01] PLAYER.started set to true</li>
            <li>[12:00:31] Timer completed</li>
          </ol>
        </section>
      </footer>
    </section>
  `;
}
