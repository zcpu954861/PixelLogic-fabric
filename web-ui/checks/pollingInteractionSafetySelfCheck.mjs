import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const app = read('src/ui/app.ts');
const polling = read('src/api/testRunPolling.ts');

const startTest = app.slice(app.indexOf('async function startTest'), app.indexOf('async function refreshLatestTrace'));
const localRefresh = app.slice(app.indexOf('function refreshTestExecutionView'), app.indexOf('function apiBusyAttr'));
const pointerMove = app.slice(app.indexOf('function moveBlockDrag'), app.indexOf('function endBlockDrag'));

assert.equal((startTest.match(/refreshTestExecutionView\(\)/g) ?? []).length, 2,
  'poll success and terminal failure must both use the local execution refresh');
assert.doesNotMatch(startTest, /onUpdate:[\s\S]*?renderApp\(\)[\s\S]*?onFailure:/,
  'poll updates must not rebuild the application');
assert.doesNotMatch(startTest, /onFailure:[\s\S]*?renderApp\(\)/,
  'poll failures must not rebuild the application');

for (const selector of [
  'data-test-result-view',
  'data-api-status',
  'data-api-status-message',
  'data-api-validation-status',
  'data-last-action',
  'data-api-error-view',
  'data-trace-id',
  'data-trace-list',
]) {
  assert.match(localRefresh, new RegExp(selector), `local refresh must update ${selector}`);
}
assert.match(localRefresh, /captureTraceScrollSnapshot\(\)[\s\S]*restoreTraceScrollSnapshot\(traceScroll\)/,
  'local trace updates must preserve stick-to-bottom or the current reading position');
assert.doesNotMatch(localRefresh,
  /app\.innerHTML|renderApp\(|canvas-viewport|data-modal-overlay|data-sim-modal-overlay|cancelInteractionAnimations|clearPlacementPreview|clearCatalogDragGhost|applyGraphEdit|scheduleAutoSave/,
  'local polling refresh must not touch canvas, modal, drag, graph, history, or autosave state');

assert.match(polling, /activeRunId/);
assert.match(polling, /pollToken/);
assert.match(polling, /response\.runId !== runId/);
assert.match(polling, /response\.terminal[\s\S]*stopTestRunPolling\(\)/);
assert.match(app, /pagehide.*stopTestRunPolling/);
assert.doesNotMatch(pointerMove, /applyGraphEdit\(/,
  'pointer move must remain preview-only after the polling hotfix');

console.log('polling interaction safety WebUI self-check passed');
