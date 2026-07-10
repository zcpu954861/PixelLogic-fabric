import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const app = read('src/ui/app.ts');
const polling = read('src/api/testRunPolling.ts');
const graphTypes = read('src/model/graphTypes.ts');
const resultPanel = read('src/ui/simulation/simulationTestContextPanel.ts');

assert.match(app, /stopTestRunPolling\(\);[\s\S]*\/api\/pixellogic\/test\/reset/, 'new runs must stop the old poll before reset');
assert.match(polling, /activeRunId/, 'polling must keep one active run identity');
assert.match(polling, /pollToken/, 'polling must reject stale responses');
assert.match(polling, /response\.runId !== runId/, 'a response for another run must be ignored');
assert.match(polling, /response\.terminal[\s\S]*stopTestRunPolling\(\)/, 'terminal runs must stop polling');
assert.match(polling, /failureLimit/, 'network retries must have a finite limit');
assert.match(polling, /window\.setTimeout/, 'polling must use a delayed one-shot timer');
assert.doesNotMatch(polling, /setInterval|applyGraphEdit|persistDraft|scheduleAutoSave|undoStack|redoStack/, 'polling must not busy-loop or mutate graph/history');
assert.match(app, /state\.latestTrace = update\.trace/, 'polling must replace the bounded trace snapshot instead of appending duplicates');
assert.match(app, /pagehide.*stopTestRunPolling/, 'page exit must clean up polling');
assert.match(app, /data-trace-scroll/, 'execution trace must have a stable scroll target');
assert.match(app, /captureTraceScrollSnapshot\(\)/, 'rerenders must capture execution trace scroll');
assert.match(app, /restoreTraceScrollSnapshot\(traceScroll\)/, 'rerenders must restore execution trace scroll');
assert.match(app, /stickToBottom.*scrollHeight/s, 'a trace already at the bottom must follow new entries');
assert.match(graphTypes, /runStatus\?: 'WAITING' \| 'COMPLETED' \| 'FAILED' \| 'CANCELLED'/, 'API types must expose run status');
assert.match(resultPanel, /等待后续执行/, 'suspended runs must not be displayed as completed');

console.log('control flow continuation WebUI self-check passed');
