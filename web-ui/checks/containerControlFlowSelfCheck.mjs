import assert from 'node:assert/strict';
import { createServer } from 'vite';

const server = await createServer({ appType: 'custom', logLevel: 'silent', server: { middlewareMode: true } });

try {
  const { containerFrame, containerMinimumHeight, containerMinimumWidth } = await server.ssrLoadModule('/src/model/containerGeometry.ts');
  const { edge, input, node, out } = await server.ssrLoadModule('/src/model/demoGraph.ts');
  const { blockMetrics, containerBodyEntryAnchor } = await server.ssrLoadModule('/src/model/graphLayout.ts');
  const { appendCandidates, computeDragDrop, draggedTailOutput, findInsertCandidate } = await server.ssrLoadModule('/src/ui/canvas/dragInsert.ts');
  const { canAssignContainerMembership } = await server.ssrLoadModule('/src/ui/canvas/containerPlacement.ts');

  const frame = containerFrame(containerMinimumWidth, containerMinimumHeight);
  assert.deepEqual(frame.childOrigin, { x: 42, y: 100 });
  assert.deepEqual(frame.bodyEntryAnchor, { x: 42, y: 175 });
  assert.equal(frame.bodyRect.y + frame.bodyRect.height, containerMinimumHeight - 38);

  const graph = (nodes, edges = []) => ({
    schemaVersion: 1,
    id: 'self-check',
    displayName: 'self-check',
    createdAt: '',
    updatedAt: '',
    fingerprint: '',
    triggerEntries: {},
    nodes,
    edges,
  });
  const action = (id, position, parentContainerId = '') => ({
    ...node(id, 'MESSAGE_ACTION', id, {}, position, [input('input'), out('done')], 'action.message.chat'),
    parentContainerId,
    parentSlot: parentContainerId ? 'body' : '',
  });
  const container = (id, position = { x: 0, y: 0 }) => node(
    id,
    'CONTROL_LOOP_COUNT',
    id,
    {},
    position,
    [input('input'), out('done')],
    'control.loop.count',
  );
  const drag = (root, start, preview, joins = []) => ({
    pointerId: 1,
    rootId: root,
    groupIds: [root],
    started: true,
    startClient: { x: 0, y: 0 },
    startWorld: start,
    startPositions: new Map([[root, start]]),
    previewPositions: new Map([[root, preview]]),
    joins,
    candidate: null,
  });

  const edgeGraph = graph([
    container('loop'),
    action('source', { x: 42, y: 100 }, 'loop'),
    action('target', { x: 288, y: 100 }, 'loop'),
    action('dragged', { x: 800, y: 100 }),
  ], [edge('e1', 'source', 'done', 'target', 'input')]);
  const edgeDrag = drag('dragged', { x: 800, y: 100 }, { x: 166, y: 100 }, [{
    id: 'e1', from: 'source', to: 'target', branch: 'main', x: 286, y: 160, width: 20, tone: 'normal',
  }]);
  edgeDrag.candidate = findInsertCandidate(edgeGraph, edgeDrag);
  assert.equal(edgeDrag.candidate?.kind, 'insert', 'internal edge insert must win inside body');
  const insertedEdgeGraph = computeDragDrop(edgeGraph, edgeDrag).graph;
  assert.equal(insertedEdgeGraph.nodes.find((item) => item.id === 'dragged')?.parentContainerId, 'loop');
  assert.equal(insertedEdgeGraph.edges.some((item) => item.sourceNodeId === 'source' && item.targetNodeId === 'dragged'), true);
  assert.equal(insertedEdgeGraph.edges.some((item) => item.sourceNodeId === 'dragged' && item.targetNodeId === 'target'), true);

  const bodyGraph = graph([
    action('outside-a', { x: -246, y: 0 }),
    container('loop'),
    action('outside-b', { x: 330, y: 0 }),
    action('source', { x: 42, y: 100 }, 'loop'),
    action('dragged', { x: 900, y: 100 }),
  ], [
    edge('outside-in', 'outside-a', 'done', 'loop', 'input'),
    edge('outside-out', 'loop', 'done', 'outside-b', 'input'),
  ]);
  const bodyDrag = drag('dragged', { x: 900, y: 100 }, { x: 288, y: 100 }, [{
    id: 'outside-out', from: 'loop', to: 'outside-b', branch: 'main', x: 328, y: 60, width: 20, tone: 'normal',
  }]);
  assert.equal(blockMetrics(bodyGraph, bodyGraph.nodes.find((item) => item.id === 'loop')).width, containerMinimumWidth, 'visible body must stay tight around one card');
  bodyDrag.candidate = findInsertCandidate(bodyGraph, bodyDrag);
  assert.equal(bodyDrag.candidate?.kind, 'append', 'internal append must beat external container edges');
  assert.equal(bodyDrag.candidate?.sourceNodeId, 'source');
  const bodyBefore = JSON.stringify(bodyGraph);
  const firstDrop = computeDragDrop(bodyGraph, bodyDrag);
  const secondDrop = computeDragDrop(bodyGraph, bodyDrag);
  assert.equal(JSON.stringify(bodyGraph), bodyBefore, 'placement calculation must not mutate the live graph');
  assert.equal(firstDrop.graph.nodes.find((item) => item.id === 'dragged')?.parentContainerId, 'loop');
  assert.equal(firstDrop.graph.edges.some((item) => item.id === 'outside-in'), true);
  assert.equal(firstDrop.graph.edges.some((item) => item.id === 'outside-out'), true);
  assert.deepEqual(
    firstDrop.graph.nodes.map((item) => item.position),
    secondDrop.graph.nodes.map((item) => item.position),
    'preview and final placement calculation must be deterministic',
  );
  const draggedInBody = firstDrop.graph.nodes.find((item) => item.id === 'dragged');
  const removeDrag = drag('dragged', draggedInBody.position, { x: 900, y: 500 });
  removeDrag.candidate = findInsertCandidate(firstDrop.graph, removeDrag);
  const removed = computeDragDrop(firstDrop.graph, removeDrag).graph;
  assert.equal(removed.nodes.find((item) => item.id === 'dragged')?.parentContainerId, '');
  assert.equal(removed.nodes.find((item) => item.id === 'source')?.parentContainerId, 'loop');
  assert.equal(removed.edges.some((item) => item.id === 'outside-in'), true);
  assert.equal(removed.edges.some((item) => item.id === 'outside-out'), true);

  const emptyScopeGraph = graph([
    action('outside-a', { x: -246, y: 0 }),
    container('loop'),
    action('outside-b', { x: 330, y: 0 }),
    action('dragged', { x: 900, y: 100 }),
  ], [edge('outside-out', 'loop', 'done', 'outside-b', 'input')]);
  const emptyScopeDrag = drag('dragged', { x: 900, y: 100 }, { x: 170, y: 100 }, [{
    id: 'outside-out', from: 'loop', to: 'outside-b', branch: 'main', x: 328, y: 60, width: 20, tone: 'normal',
  }]);
  assert.equal(findInsertCandidate(emptyScopeGraph, emptyScopeDrag)?.kind, 'container', 'external edge must not steal empty body');

  const nestedGraph = graph([
    container('outer'),
    { ...container('inner', { x: 42, y: 100 }), parentContainerId: 'outer', parentSlot: 'body' },
    action('inner-child', { x: 84, y: 200 }, 'inner'),
  ]);
  const outerEntry = containerBodyEntryAnchor(nestedGraph, nestedGraph.nodes[0]);
  const innerMetrics = blockMetrics(nestedGraph, nestedGraph.nodes[1]);
  assert.equal(nestedGraph.nodes[1].position.x, outerEntry.x);
  assert.equal(nestedGraph.nodes[1].position.y + innerMetrics.inputY, outerEntry.y, 'nested container input must stay on body anchor');
  const nestedDropGraph = graph([container('outer'), container('inner', { x: 800, y: 100 })]);
  const nestedDropDrag = drag('inner', { x: 800, y: 100 }, { x: 42, y: 100 });
  nestedDropDrag.candidate = findInsertCandidate(nestedDropGraph, nestedDropDrag);
  const nestedDrop = computeDragDrop(nestedDropGraph, nestedDropDrag).graph;
  const droppedInner = nestedDrop.nodes.find((item) => item.id === 'inner');
  const droppedOuter = nestedDrop.nodes.find((item) => item.id === 'outer');
  const droppedEntry = containerBodyEntryAnchor(nestedDrop, droppedOuter);
  assert.equal(droppedInner.position.x, droppedEntry.x);
  assert.equal(droppedInner.position.y + blockMetrics(nestedDrop, droppedInner).inputY, droppedEntry.y);

  const linkGraph = graph([
    action('source', { x: 0, y: 0 }),
    action('dragged', { x: 900, y: 0 }),
    action('target', { x: 506, y: 0 }),
  ]);
  const linkDrag = drag('dragged', { x: 900, y: 0 }, { x: 246, y: 0 });
  assert.equal(findInsertCandidate(linkGraph, linkDrag)?.kind, 'append', 'append must beat attach');

  const branch = node(
    'branch',
    'STATE_COMPARE_CONDITION',
    'branch',
    { outputMode: 'BRANCH' },
    { x: 0, y: 0 },
    [input('input'), out('pass'), out('fail')],
    'condition.state.equals',
  );
  assert.equal(draggedTailOutput(graph([branch]), new Set(['branch'])), null, 'BRANCH must not be a single chain tail');
  const passOnly = { ...branch, id: 'pass-only', config: { outputMode: 'PASS_ONLY' } };
  assert.equal(draggedTailOutput(graph([passOnly]), new Set(['pass-only']))?.slot.id, 'pass');
  const conditionGraph = graph([passOnly, action('dragged', { x: 900, y: 0 })]);
  assert.equal(appendCandidates(conditionGraph, drag('dragged', { x: 900, y: 0 }, { x: 246, y: 0 })).length, 1);
  assert.equal(appendCandidates(graph([branch, action('dragged', { x: 900, y: 0 })]), drag('dragged', { x: 900, y: 0 }, { x: 246, y: 0 })).length, 0);

  const conditionHead = {
    ...passOnly,
    id: 'condition-head',
    position: { x: 42, y: 100 },
    parentContainerId: 'loop',
    parentSlot: 'body',
  };
  const prependGraph = graph([
    action('outside-a', { x: -246, y: 0 }),
    container('loop'),
    conditionHead,
    action('dragged', { x: 900, y: 100 }),
  ], [edge('outside-in', 'outside-a', 'done', 'loop', 'input')]);
  const prependDrag = drag('dragged', { x: 900, y: 100 }, { x: -88, y: 100 }, [{
    id: 'outside-in', from: 'outside-a', to: 'loop', branch: 'main', x: -2, y: 60, width: 20, tone: 'normal',
  }]);
  prependDrag.candidate = findInsertCandidate(prependGraph, prependDrag);
  assert.equal(prependDrag.candidate?.kind, 'attach', 'dragging the card center to the body entrance must prepend before a condition');
  const prepended = computeDragDrop(prependGraph, prependDrag).graph;
  assert.equal(prepended.nodes.find((item) => item.id === 'dragged')?.parentContainerId, 'loop');
  assert.equal(prepended.nodes.find((item) => item.id === 'dragged')?.position.x, 42);
  assert.equal(prepended.nodes.find((item) => item.id === 'condition-head')?.position.x, 288);
  assert.equal(prepended.edges.some((item) => item.sourceNodeId === 'dragged' && item.targetNodeId === 'condition-head'), true);

  const nested = ['c0', 'c1', 'c2', 'c3', 'c4'].map((id, index) => ({
    ...container(id),
    parentContainerId: index === 0 ? '' : `c${index - 1}`,
    parentSlot: index === 0 ? '' : 'body',
  }));
  const tooDeepGraph = graph([...nested, action('dragged', { x: 0, y: 0 })]);
  assert.equal(canAssignContainerMembership(tooDeepGraph, new Set(['dragged']), 'c4').valid, false);

  console.log('container control flow WebUI self-check passed');
} finally {
  await server.close();
}
