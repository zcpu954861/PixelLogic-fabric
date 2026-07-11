import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createServer } from 'vite';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const server = await createServer({ appType: 'custom', logLevel: 'silent', server: { middlewareMode: true } });

try {
  const viewport = await server.ssrLoadModule('/src/ui/canvas/viewportReadability.ts');
  const modal = await server.ssrLoadModule('/src/ui/editor/blockEditorModal.ts');
  const simulation = await server.ssrLoadModule('/src/ui/simulation/simulationTestContextPanel.ts');
  const contextModel = await server.ssrLoadModule('/src/model/simulationTestContext.ts');
  const app = read('src/ui/app.ts');
  const blocks = read('src/ui/canvas/blockView.ts');
  const blockCss = read('src/styles/blocks.css');
  const baseCss = read('src/styles/base.css');
  const responsiveCss = read('src/styles/responsive.css');
  const traceCss = read('src/styles/trace.css');

  assert.equal(viewport.readableZoomThreshold, 0.72, 'readability threshold must be centralized');
  const transform = viewport.initialReadableTransform(
    { width: 900, height: 560 },
    [{ id: 'trigger', x: 120, y: 80, width: 280, height: 112 }, { id: 'next', x: 430, y: 80, width: 280, height: 112 }],
    ['trigger'],
  );
  assert.ok(transform && transform.scale >= viewport.readableZoomThreshold, 'initial focus must remain readable');
  assert.match(app, /viewportTouched[\s\S]*autoFocusedGraphId[\s\S]*focusInitialGraphIfNeeded/,
    'initial viewport must be one-shot and respect user interaction');
  assert.match(app, /classList\.toggle\('is-overview', scale < readableZoomThreshold\)/,
    'low zoom must only switch a visual overview class');
  assert.match(blockCss, /\.flow-world\.is-overview \.logic-block > p[\s\S]*visibility: hidden/,
    'overview must visually hide secondary text without changing geometry');

  const catalog = { packs: [], categories: [], subcategories: [], blocks: [{ id: 'condition.test', displayName: '玩家是否拥有标签', aliases: [] }] };
  const node = { blockId: 'condition.test', displayName: '玩家是否拥有标签', type: 'CONDITION', config: {}, slots: [] };
  assert.equal(modal.blockEditorTitle(node, catalog), '未命名(玩家是否拥有标签)');
  assert.equal(modal.blockEditorTitle({ ...node, displayName: '守卫检查' }, catalog), '守卫检查(玩家是否拥有标签)');
  const modalSource = read('src/ui/editor/blockEditorModal.ts');
  assert.match(modalSource, /保存修改/);
  assert.match(modalSource, /拖拽和连接会自动保存；此处字段修改需点击“保存修改”/);

  const context = contextModel.defaultSimulationTestContext();
  simulation.prepareSimulationDisclosures(context);
  const contextHtml = simulation.renderSimulationTestContextModal(context, { closing: false, error: '', steady: true });
  assert.match(contextHtml, /data-sim-disclosure="player" aria-expanded="true" aria-controls=/);
  assert.match(contextHtml, /data-sim-disclosure="entity" aria-expanded="false" aria-controls=/);
  assert.match(contextHtml, /data-sim-disclosure="block" aria-expanded="false" aria-controls=/);
  assert.match(contextHtml, /data-sim-disclosure="regions" aria-expanded="false" aria-controls=/);
  assert.match(contextHtml, /测试目标实体[\s\S]*未启用/);
  assert.equal(simulation.buildRunConclusion({ status: 'WAITING', message: '正在等待 1 秒。' }, null), '运行等待中：正在等待 1 秒。');
  assert.equal(simulation.buildRunConclusion({ status: 'CANCELLED', message: '新的运行替换旧运行。' }, null), '运行已取消：新的运行替换旧运行。');
  assert.match(simulation.buildRunConclusion({ status: 'COMPLETED', success: true, message: '' }, { steps: [{ message: '流程自然结束。' }] }), /流程自然结束/);

  assert.match(blocks, /tabindex="0"[\s\S]*role="button"[\s\S]*aria-label="编辑积木/,
    'cards must expose a keyboard focus and edit affordance');
  assert.match(app, /event\.key === 'Enter' \|\| event\.key === ' '/);
  assert.match(app, /event\.key === 'Delete' \|\| event\.key === 'Backspace'/);
  assert.match(app, /if \(!nodeId \|\| event\.target !== blockEl\) return/,
    'nested controls must keep native keyboard behavior');
  assert.match(blockCss, /\.logic-block:focus-visible/);
  assert.match(baseCss, /:focus-visible/);
  assert.match(responsiveCss, /prefers-reduced-motion: reduce/);
  assert.match(traceCss, /\.bottom-dock[\s\S]*overflow: hidden[\s\S]*\.trace-details[\s\S]*position: relative[\s\S]*overflow: hidden[\s\S]*\.trace-details > section[\s\S]*position: absolute[\s\S]*inset: 42px 0 0[\s\S]*overflow: auto/,
    'trace details must stay inside the fixed dock row');
  assert.match(app, /refreshTestExecutionView[\s\S]*resultView\.innerHTML[\s\S]*traceList\.innerHTML/,
    'polling must keep using the local result/trace patch boundary');

  console.log('UI workbench polish self-check passed');
} finally {
  await server.close();
}
