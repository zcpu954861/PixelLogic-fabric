import { cpus } from 'node:os';
import { performance } from 'node:perf_hooks';
import { createServer } from 'vite';

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' });
try {
  const { ActiveDragPreviewCache } = await server.ssrLoadModule('/src/ui/canvas/dragPreviewCache.ts');
  const { fallbackCatalog } = await server.ssrLoadModule('/src/model/blockCatalog.ts');
  const { buildBlocks, buildJoins } = await server.ssrLoadModule('/src/ui/canvas/slotFlowViewModel.ts');
  const { computeDragDrop, findInsertCandidate } = await server.ssrLoadModule('/src/ui/canvas/dragInsert.ts');
  const input = { id: 'input', direction: 'INPUT', edgeType: 'CONTROL' };
  const output = (id = 'done') => ({ id, direction: 'OUTPUT', edgeType: 'CONTROL' });
  const normal = (id, x, y = 100, parentContainerId = '', parentSlot = '') => ({
    id, type: 'DEBUG_LOG_ACTION', blockId: 'debug.log', displayName: id, config: { message: id },
    position: { x, y }, parentContainerId, parentSlot, slots: [input, output()],
  });
  const graphFor = (size) => {
    const nodes = [];
    const edges = [];
    const chainCount = size - 6;
    for (let index = 0; index < chainCount; index += 1) nodes.push(normal(`n${index}`, index * 246));
    for (let index = 0; index < chainCount - 1; index += 1) {
      edges.push({ id: `e${index}`, sourceNodeId: `n${index}`, sourceSlotId: 'done', targetNodeId: `n${index + 1}`, targetSlotId: 'input', type: 'CONTROL' });
    }
    nodes.push(normal('dragged', 4000));
    nodes.push({ id: 'branch', type: 'STATE_COMPARE_CONDITION', blockId: 'condition.state.equals', displayName: 'branch', config: { outputMode: 'BRANCH' }, position: { x: 0, y: 420 }, slots: [input, output('pass'), output('fail')] });
    nodes.push({ id: 'loop', type: 'CONTROL_LOOP_COUNT', blockId: 'control.loop.count', displayName: 'loop', config: {}, position: { x: 300, y: 620 }, slots: [input, output()] });
    nodes.push({ id: 'until', type: 'CONTROL_LOOP_UNTIL', blockId: 'control.loop.until', displayName: 'until', config: {}, conditionSlots: [{ slotId: 'condition-a', negated: false }], position: { x: 700, y: 620 }, slots: [input, output()] });
    nodes.push({ id: 'predicate', type: 'STATE_COMPARE_CONDITION', blockId: 'condition.state.equals', displayName: 'predicate', config: { outputMode: 'PASS_ONLY' }, position: { x: 0, y: 0 }, parentContainerId: 'until', parentSlot: 'condition-a', slots: [input, output('pass'), output('fail')] });
    nodes.push({ id: 'context', type: 'CONTEXT_ENTITY_EXECUTE_AS', blockId: 'context.entity.execute_as', displayName: 'context', config: {}, position: { x: 1100, y: 620 }, parentContainerId: 'loop', parentSlot: 'body', slots: [input, output()] });
    return { schemaVersion: 1, id: `bench-${size}`, displayName: 'bench', createdAt: '', updatedAt: '', fingerprint: '', nodes, edges, triggerEntries: {} };
  };
  const percentile = (samples, ratio) => samples[Math.min(samples.length - 1, Math.floor(samples.length * ratio))];
  const measure = (run, rounds = 30) => {
    for (let index = 0; index < 5; index += 1) run();
    const samples = Array.from({ length: rounds }, () => {
      const started = performance.now();
      run();
      return performance.now() - started;
    }).sort((left, right) => left - right);
    return { p50: percentile(samples, 0.5), p95: percentile(samples, 0.95) };
  };

  console.log(`Node ${process.version}; ${process.platform}/${process.arch}; ${cpus()[0]?.model ?? 'unknown CPU'}`);
  console.log('Non-gating benchmark; times are milliseconds.');
  console.log('nodes\tfirst p50/p95\tstable hit p50/p95\tswitch p50/p95\tfull builds/request first/hit/switch');
  for (const size of [9, 65, 129]) {
    const graph = graphFor(size);
    const joins = buildJoins(graph, buildBlocks(graph, fallbackCatalog, 'dragged'));
    const drag = {
      pointerId: 1, rootId: 'dragged', groupIds: ['dragged'], started: true,
      startClient: { x: 0, y: 0 }, startWorld: { x: 4000, y: 100 },
      startPositions: new Map([['dragged', { x: 4000, y: 100 }]]),
      previewPositions: new Map([['dragged', { x: 254, y: 100 }]]), joins, candidate: null,
    };
    let fullBuilds = 0;
    const compute = () => {
      fullBuilds += 1;
      const placementGraph = computeDragDrop(graph, drag).graph;
      return {
        placementGraph,
        baseBlocks: buildBlocks(graph, fallbackCatalog, 'dragged'),
        placementBlocks: buildBlocks(placementGraph, fallbackCatalog, 'dragged'),
        draggedNodeIds: new Set(drag.groupIds),
      };
    };
    const request = (cache, switched = false) => {
      const x = 254;
      drag.previewPositions.set('dragged', { x, y: 100 });
      drag.candidate = findInsertCandidate(graph, drag, fallbackCatalog);
      if (switched && drag.candidate) {
        drag.candidate = { ...drag.candidate, join: { ...drag.candidate.join, id: `${drag.candidate.join.id}:switched`, x: drag.candidate.join.x + 1 } };
      }
      return cache.getOrCompute(graph, 0, fallbackCatalog, 'dragged', drag, compute);
    };
    const firstBefore = fullBuilds;
    const first = measure(() => { const cache = new ActiveDragPreviewCache(); request(cache); });
    const firstBuilds = fullBuilds - firstBefore;
    const stableCache = new ActiveDragPreviewCache();
    request(stableCache);
    const hitBefore = fullBuilds;
    const hit = measure(() => request(stableCache));
    const hitBuilds = fullBuilds - hitBefore;
    const switchCache = new ActiveDragPreviewCache();
    let toggle = false;
    const switchBefore = fullBuilds;
    const switched = measure(() => { toggle = !toggle; request(switchCache, toggle); });
    const switchBuilds = fullBuilds - switchBefore;
    const fmt = ({ p50, p95 }) => `${p50.toFixed(3)}/${p95.toFixed(3)}`;
    console.log(`${size}\t${fmt(first)}\t${fmt(hit)}\t${fmt(switched)}\t${(firstBuilds / 35).toFixed(0)}/${(hitBuilds / 30).toFixed(0)}/${(switchBuilds / 35).toFixed(0)}`);
  }
} finally {
  await server.close();
}
