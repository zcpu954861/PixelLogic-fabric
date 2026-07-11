import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createServer } from 'vite';

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' });
try {
  const { ActiveDragPreviewCache } = await server.ssrLoadModule('/src/ui/canvas/dragPreviewCache.ts');
  const { fallbackCatalog } = await server.ssrLoadModule('/src/model/blockCatalog.ts');
  const { buildBlocks, buildJoins } = await server.ssrLoadModule('/src/ui/canvas/slotFlowViewModel.ts');
  const { computeDragDrop, findInsertCandidate } = await server.ssrLoadModule('/src/ui/canvas/dragInsert.ts');

  const input = { id: 'input', direction: 'INPUT', edgeType: 'CONTROL' };
  const output = { id: 'done', direction: 'OUTPUT', edgeType: 'CONTROL' };
  const node = (id, x, parentContainerId = '', parentSlot = '') => ({
    id, type: 'DEBUG_LOG_ACTION', blockId: 'debug.log', displayName: id, config: {},
    position: { x, y: 100 }, parentContainerId, parentSlot, slots: [input, output],
  });
  const graph = {
    schemaVersion: 1, id: 'drag-performance', displayName: 'drag performance', createdAt: '', updatedAt: '', fingerprint: '',
    nodes: [node('source', 0), node('target', 246), node('dragged', 800)],
    edges: [{ id: 'edge', sourceNodeId: 'source', sourceSlotId: 'done', targetNodeId: 'target', targetSlotId: 'input', type: 'CONTROL' }],
    triggerEntries: {},
  };
  const joins = buildJoins(graph, buildBlocks(graph, fallbackCatalog, 'dragged'));
  const drag = {
    pointerId: 1, rootId: 'dragged', groupIds: ['dragged'], started: true,
    startClient: { x: 0, y: 0 }, startWorld: { x: 800, y: 100 },
    startPositions: new Map([['dragged', { x: 800, y: 100 }]]),
    previewPositions: new Map([['dragged', { x: 254, y: 100 }]]), joins, candidate: null,
  };
  drag.candidate = findInsertCandidate(graph, drag, fallbackCatalog);
  assert.equal(drag.candidate?.kind, 'insert');
  assert.equal(drag.candidate?.valid, true);

  const cache = new ActiveDragPreviewCache();
  let builds = 0;
  const compute = (sourceGraph = graph) => {
    builds += 1;
    const placementGraph = computeDragDrop(sourceGraph, drag).graph;
    return {
      placementGraph,
      baseBlocks: buildBlocks(sourceGraph, fallbackCatalog, 'dragged'),
      placementBlocks: buildBlocks(placementGraph, fallbackCatalog, 'dragged'),
      draggedNodeIds: new Set(drag.groupIds),
    };
  };
  const first = cache.getOrCompute(graph, 0, fallbackCatalog, 'dragged', drag, compute);
  drag.previewPositions.set('dragged', { x: 258, y: 104 });
  const stable = cache.getOrCompute(graph, 0, fallbackCatalog, 'dragged', drag, compute);
  assert.equal(stable, first, 'uniform pointer motion on one snapped candidate must reuse the full preview');
  assert.equal(builds, 1, 'stable candidate must compute/build once');

  const canonical = computeDragDrop(graph, drag).graph;
  assert.deepEqual(stable.placementGraph, canonical, 'cached snapped placement must equal the canonical computation');
  assert.deepEqual(
    stable.placementGraph.nodes.map(({ id, position, parentContainerId, parentSlot }) => ({ id, position, parentContainerId, parentSlot })),
    canonical.nodes.map(({ id, position, parentContainerId, parentSlot }) => ({ id, position, parentContainerId, parentSlot })),
  );
  assert.deepEqual(stable.placementGraph.edges, canonical.edges);

  const chainGraph = {
    ...graph,
    nodes: [node('source', 0), node('target', 246), node('dragged', 1000), node('drag-1', 1246), node('drag-2', 1492), node('drag-3', 1738)],
    edges: [
      { id: 'external', sourceNodeId: 'source', sourceSlotId: 'done', targetNodeId: 'target', targetSlotId: 'input', type: 'CONTROL' },
      { id: 'drag-edge-1', sourceNodeId: 'dragged', sourceSlotId: 'done', targetNodeId: 'drag-1', targetSlotId: 'input', type: 'CONTROL' },
      { id: 'drag-edge-2', sourceNodeId: 'drag-1', sourceSlotId: 'done', targetNodeId: 'drag-2', targetSlotId: 'input', type: 'CONTROL' },
      { id: 'drag-edge-3', sourceNodeId: 'drag-2', sourceSlotId: 'done', targetNodeId: 'drag-3', targetSlotId: 'input', type: 'CONTROL' },
    ],
  };
  const chainJoins = buildJoins(chainGraph, buildBlocks(chainGraph, fallbackCatalog, 'dragged'));
  const chainDrag = {
    ...drag,
    groupIds: ['dragged', 'drag-1', 'drag-2', 'drag-3'],
    startPositions: new Map([['dragged', { x: 1000, y: 100 }], ['drag-1', { x: 1246, y: 100 }], ['drag-2', { x: 1492, y: 100 }], ['drag-3', { x: 1738, y: 100 }]]),
    previewPositions: new Map([['dragged', { x: 134, y: 100 }], ['drag-1', { x: 380, y: 100 }], ['drag-2', { x: 626, y: 100 }], ['drag-3', { x: 872, y: 100 }]]),
    joins: chainJoins,
    candidate: null,
  };
  const chainCandidate = findInsertCandidate(chainGraph, chainDrag, fallbackCatalog);
  assert.equal(chainCandidate?.kind, 'insert', 'a dragged chain must use its selected root card as the insertion anchor');
  assert.equal(chainCandidate?.edge.id, 'external', 'the middle of a long dragged chain must not control insertion focus');

  const originalCandidate = drag.candidate;
  drag.candidate = {
    kind: 'append', sourceNodeId: 'source', sourceSlotId: 'done', valid: true, message: '',
    join: { id: 'append:source:done', from: 'source', to: 'dragged', branch: 'main', x: 220, y: 100, width: 20 },
  };
  cache.getOrCompute(graph, 0, fallbackCatalog, 'dragged', drag, compute);
  assert.equal(builds, 2, 'candidate kind/target changes must recompute');

  drag.candidate = { kind: 'container', containerNodeId: 'loop', slotId: 'body', valid: true, message: '', join: { id: 'container:loop:body', from: 'loop', to: 'dragged', branch: 'main', x: 0, y: 0, width: 20 } };
  cache.getOrCompute(graph, 0, fallbackCatalog, 'dragged', drag, compute);
  drag.candidate = { kind: 'condition-slot', containerNodeId: 'loop', slotId: 'condition-a', valid: true, message: '', join: { id: 'slot-a', from: 'loop', to: 'dragged', branch: 'main', x: 0, y: 0, width: 20 } };
  cache.getOrCompute(graph, 0, fallbackCatalog, 'dragged', drag, compute);
  drag.candidate = { ...drag.candidate, slotId: 'condition-b', join: { ...drag.candidate.join, id: 'slot-b' } };
  cache.getOrCompute(graph, 0, fallbackCatalog, 'dragged', drag, compute);
  assert.equal(builds, 5, 'container/external and condition slot switches must not share previews');

  drag.candidate = originalCandidate;
  cache.getOrCompute(graph, 1, fallbackCatalog, 'dragged', drag, compute);
  const movedGraph = { ...graph, nodes: graph.nodes.map((item) => item.id === 'target' ? { ...item, position: { x: 250, y: 100 }, parentContainerId: 'loop', parentSlot: 'body' } : item) };
  cache.getOrCompute(movedGraph, 1, fallbackCatalog, 'dragged', drag, () => compute(movedGraph));
  assert.equal(builds, 7, 'graph revision and graph identity changes must invalidate positions/edges/membership/geometry');
  const edgeGraph = { ...movedGraph, edges: [] };
  cache.getOrCompute(edgeGraph, 1, fallbackCatalog, 'dragged', drag, () => compute(edgeGraph));
  const containerGraph = {
    ...edgeGraph,
    nodes: edgeGraph.nodes.map((item) => item.id === 'target'
      ? { ...item, type: 'CONTROL_LOOP_UNTIL', blockId: 'control.loop.until', conditionSlots: [{ slotId: 'condition-a', negated: false }] }
      : item),
  };
  cache.getOrCompute(containerGraph, 1, fallbackCatalog, 'dragged', drag, () => compute(containerGraph));
  assert.equal(builds, 9, 'edge, container geometry, and condition-slot graph replacements must invalidate');

  drag.groupIds = ['dragged', 'target'];
  drag.startPositions.set('target', { x: 246, y: 100 });
  drag.previewPositions.set('target', { x: -304, y: 104 });
  cache.getOrCompute(graph, 1, fallbackCatalog, 'dragged', drag, compute);
  assert.equal(builds, 10, 'drag group changes must invalidate');

  drag.groupIds = ['dragged'];
  drag.candidate = null;
  cache.getOrCompute(graph, 1, fallbackCatalog, 'dragged', drag, compute);
  cache.getOrCompute(graph, 1, fallbackCatalog, 'dragged', drag, compute);
  assert.equal(builds, 12, 'free move must never be cached');
  drag.candidate = { ...originalCandidate, valid: false };
  cache.getOrCompute(graph, 1, fallbackCatalog, 'dragged', drag, compute);
  cache.getOrCompute(graph, 1, fallbackCatalog, 'dragged', drag, compute);
  assert.equal(builds, 14, 'invalid candidates must never be cached');
  drag.candidate = originalCandidate;
  cache.getOrCompute(graph, 1, fallbackCatalog, 'dragged', drag, compute);
  cache.clear();
  cache.getOrCompute(graph, 1, fallbackCatalog, 'dragged', drag, compute);
  assert.equal(builds, 16, 'cancel/render clear must release the active-drag entry');

  const app = readFileSync(new URL('../src/ui/app.ts', import.meta.url), 'utf8');
  const pointerUp = app.slice(app.indexOf('function endBlockDrag'), app.indexOf('function applyDragPreviewPositions'));
  const cancel = app.slice(app.indexOf('function cancelBlockDrag'), app.indexOf('function pointerToWorld'));
  const render = app.slice(app.indexOf('function renderApp'), app.indexOf('function refreshDraftIndicators'));
  assert.match(pointerUp, /computeDragDrop\(currentGraph\(\), drag\)/, 'pointerup must always recompute the canonical final drop');
  assert.doesNotMatch(pointerUp, /getOrCompute/, 'pointerup must not trust the preview cache');
  assert.match(cancel, /clearInsertPreview\(\)/, 'pointercancel and Esc route must clear preview state');
  assert.match(render, /dragPreviewCache\.clear\(\)/, 'render replacement must clear the cache');
  const animations = readFileSync(new URL('../src/ui/canvas/interactionAnimations.ts', import.meta.url), 'utf8');
  const placementPreview = animations.slice(animations.indexOf('export function showPlacementPreview'), animations.indexOf('export function clearPlacementPreview'));
  assert.equal(placementPreview.match(/querySelectorAll/g)?.length, 1, 'one preview operation must enumerate block DOM once');
  assert.doesNotMatch(animations, /function blockElement/, 'per-node full DOM scans must stay removed');
  const dragInsert = readFileSync(new URL('../src/ui/canvas/dragInsert.ts', import.meta.url), 'utf8');
  const containerPlacement = readFileSync(new URL('../src/ui/canvas/containerPlacement.ts', import.meta.url), 'utf8');
  assert.match(dragInsert, /edgeById\.get\(join\.id\)/, 'join lookup must use the per-computation edge index');
  assert.equal((dragInsert + containerPlacement).match(/function graphWithDragStartPositions/g)?.length, 1, 'drag start projection must have one implementation');
  assert.doesNotMatch(containerPlacement, /function nodeRect/, 'container membership must reuse blockVisualRect');
  console.log('drag performance self-check passed');
} finally {
  await server.close();
}
