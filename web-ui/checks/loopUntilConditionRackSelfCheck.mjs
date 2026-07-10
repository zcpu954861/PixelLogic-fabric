import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createServer } from 'vite';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const appSource = read('src/ui/app.ts');
const animationSource = read('src/ui/canvas/interactionAnimations.ts');
const blockStyles = read('src/styles/blocks.css');
const animationStyles = read('src/styles/interaction-animations.css');

const server = await createServer({ appType: 'custom', logLevel: 'silent', server: { middlewareMode: true } });

try {
  const { edge, input, node, out } = await server.ssrLoadModule('/src/model/demoGraph.ts');
  const {
    blockMetrics,
    blockVisualRect,
    cloneGraph,
    conditionSlotRects,
    containerBodyEntryAnchor,
    containerChildren,
    containerDescendantNodeIds,
    nodePosition,
    subtreeLaneSpan,
  } = await server.ssrLoadModule('/src/model/graphLayout.ts');
  const {
    conditionRackFrame,
    containerFrame,
    containerMinimumHeight,
    containerMinimumWidth,
  } = await server.ssrLoadModule('/src/model/containerGeometry.ts');
  const {
    conditionSlotMember,
    conditionSlots,
    isPredicateNode,
  } = await server.ssrLoadModule('/src/model/conditionRack.ts');
  const {
    applyConditionSlotDrop,
    findConditionSlotCandidate,
  } = await server.ssrLoadModule('/src/ui/canvas/conditionRackPlacement.ts');
  const {
    appendCandidates,
    attachCandidates,
    computeDragDrop,
    draggedTailOutput,
    findInsertCandidate,
  } = await server.ssrLoadModule('/src/ui/canvas/dragInsert.ts');
  const {
    makeContainerBodyGap,
  } = await server.ssrLoadModule('/src/ui/canvas/containerPlacement.ts');
  const {
    buildBlocks,
    canvasContentBounds,
    updateWorldSize,
    visualBlocks,
  } = await server.ssrLoadModule('/src/ui/canvas/slotFlowViewModel.ts');
  const { renderBlock, renderBlockShape } = await server.ssrLoadModule('/src/ui/canvas/blockView.ts');
  const { renderConditionRackEditor } = await server.ssrLoadModule('/src/ui/editor/conditionRackEditor.ts');
  const { renderEditorModal } = await server.ssrLoadModule('/src/ui/editor/blockEditorModal.ts');
  const { predicateNodeSummary } = await server.ssrLoadModule('/src/ui/humanize/labels.ts');
  const { renderNodeInfo } = await server.ssrLoadModule('/src/ui/sidebar/selectionSummary.ts');

  const formField = (key, label, type, defaultValue, options = []) => ({
    key,
    type,
    label,
    description: '',
    defaultValue,
    placeholder: '',
    options,
    required: false,
    min: '',
    max: '',
    step: '',
    ui: type === 'segmented' ? 'segmented fullWidth' : '',
    suffix: '',
  });
  const catalogBlock = ({
    id,
    nodeType,
    nodeKind,
    categoryId,
    capabilities = [],
    predicateSummaryTemplate = '',
    predicateNegatedSummaryTemplate = '',
    formSchema = [],
  }) => ({
    id,
    version: 1,
    displayName: id,
    description: id,
    categoryId,
    subcategoryId: '',
    tags: [],
    capabilities,
    nodeKind,
    nodeType,
    defaultConfig: {},
    formSchema,
    summaryTemplate: id,
    summaryFormatter: id,
    predicateSummaryTemplate,
    predicateNegatedSummaryTemplate,
    containerSlots: id === 'control.loop.until' ? ['body'] : [],
    inputSlots: nodeKind === 'trigger' ? [] : [input('input')],
    outputSlots: nodeKind === 'control' ? [out('done')] : nodeKind === 'condition' ? [out('pass'), out('fail')] : [out('done')],
    simulationCapability: 'FULLY_SIMULATABLE',
    mcCapability: 'REQUIRES_MINECRAFT_RUNTIME',
    safetyFlags: [],
    deprecated: false,
    hidden: false,
    aliases: [],
  });
  const catalog = {
    categories: [
      { id: 'control', displayName: '控制流', description: '', order: 1, visibleByDefault: true },
      { id: 'condition', displayName: '条件判断块(胶囊)', description: '', order: 2, visibleByDefault: true },
      { id: 'action', displayName: '动作', description: '', order: 3, visibleByDefault: true },
    ],
    subcategories: [],
    blocks: [
      catalogBlock({ id: 'control.loop.until', nodeType: 'CONTROL_LOOP_UNTIL', nodeKind: 'control', categoryId: 'control', capabilities: ['PREDICATE_RACK'] }),
      catalogBlock({
        id: 'condition.player.has_tag',
        nodeType: 'PLAYER_HAS_TAG_CONDITION',
        nodeKind: 'condition',
        categoryId: 'condition',
        capabilities: ['PREDICATE'],
        predicateSummaryTemplate: '玩家拥有标签「{tag}」',
        predicateNegatedSummaryTemplate: '玩家没有标签「{tag}」',
        formSchema: [
          formField('outputMode', '条件用途', 'segmented', 'PASS_ONLY', [
            { value: 'PASS_ONLY', label: '拥有标签时继续' },
            { value: 'FAIL_ONLY', label: '不拥有标签时继续' },
            { value: 'BRANCH', label: '分开执行' },
          ]),
          formField('tag', '标签', 'string', 'runner'),
        ],
      }),
      catalogBlock({ id: 'action.message.chat', nodeType: 'MESSAGE_ACTION', nodeKind: 'action', categoryId: 'action' }),
    ],
  };

  const graph = (nodes, edges = []) => ({
    schemaVersion: 1,
    id: 'loop-until-rack-self-check',
    displayName: 'loop until rack self-check',
    createdAt: '',
    updatedAt: '',
    fingerprint: '',
    triggerEntries: {},
    nodes,
    edges,
  });
  const loop = (id, position, slots = []) => ({
    ...node(id, 'CONTROL_LOOP_UNTIL', '循环直到', {}, position, [input('input'), out('done')], 'control.loop.until'),
    conditionSlots: slots.map((slot) => ({ ...slot })),
    parentContainerId: '',
    parentSlot: '',
  });
  const predicate = (id, position, parentContainerId = '', parentSlot = '') => ({
    ...node(id, 'PLAYER_HAS_TAG_CONDITION', '玩家是否拥有标签', { outputMode: 'PASS_ONLY', tag: 'ready' }, position, [input('input'), out('pass'), out('fail')], 'condition.player.has_tag'),
    parentContainerId,
    parentSlot,
    conditionSlots: [],
  });
  const action = (id, position, parentContainerId = '', parentSlot = '') => ({
    ...node(id, 'MESSAGE_ACTION', '发送消息', { message: id }, position, [input('input'), out('done')], 'action.message.chat'),
    parentContainerId,
    parentSlot,
    conditionSlots: [],
  });
  const drag = (rootId, start, preview, options = {}) => ({
    pointerId: 1,
    rootId,
    groupIds: options.groupIds ?? [rootId],
    started: true,
    startClient: { x: 0, y: 0 },
    startWorld: options.startWorld ?? { ...start },
    startPositions: options.startPositions ?? new Map([[rootId, { ...start }]]),
    previewPositions: options.previewPositions ?? new Map([[rootId, { ...preview }]]),
    joins: [],
    candidate: options.candidate ?? null,
  });

  const slots = [
    { slotId: 'condition-a', negated: false },
    { slotId: 'condition-b', negated: true },
    { slotId: 'condition-c', negated: false },
  ];

  // Typed clone isolation and stable ordered ids.
  const cloneSource = graph([loop('loop', { x: 400, y: 300 }, slots), predicate('capsule', { x: 0, y: 0 }, 'loop', 'condition-a')]);
  const cloned = cloneGraph(cloneSource);
  assert.notEqual(cloned.nodes[0].conditionSlots, cloneSource.nodes[0].conditionSlots, 'condition slot arrays must be deep cloned');
  assert.notEqual(cloned.nodes[0].conditionSlots[0], cloneSource.nodes[0].conditionSlots[0], 'condition slot records must be deep cloned');
  cloned.nodes[0].conditionSlots[0].negated = true;
  cloned.nodes[0].conditionSlots[0].slotId = 'changed';
  assert.deepEqual(cloneSource.nodes[0].conditionSlots, slots, 'history/editor clones must not mutate live typed slots');
  assert.deepEqual(slots.filter((slot) => slot.slotId !== 'condition-b').map((slot) => slot.slotId), ['condition-a', 'condition-c'], 'deleting a middle slot must not renumber stable ids');

  // Rack grows upward while the body/header anchors remain fixed.
  const emptyRack = conditionRackFrame(containerMinimumWidth, containerMinimumHeight, []);
  const fullRack = conditionRackFrame(containerMinimumWidth, containerMinimumHeight, slots);
  assert.equal(emptyRack.rackHeight, 0, 'zero slots must have no rack height');
  assert.equal(emptyRack.rows.length, 0, 'zero slots must render no hidden rows');
  assert.equal(fullRack.rows.length, 3, 'multiple empty or filled slots must each retain one row');
  assert.equal(fullRack.fullBounds.y, -fullRack.rackHeight, 'full bounds must include the upward rack');
  assert.equal(fullRack.fullBounds.height, containerMinimumHeight + fullRack.rackHeight);
  assert.ok(fullRack.rows[0].rowRect.y > fullRack.rows[1].rowRect.y, 'slot 1 must remain closest to the loop body');
  assert.ok(fullRack.rows[1].rowRect.y > fullRack.rows[2].rowRect.y, 'later slots must stack upward');
  assert.equal(fullRack.rows[0].rowRect.y + fullRack.rows[0].rowRect.height, -10, 'nearest row must stop at the shared rack gap');

  const noRackGraph = graph([loop('loop', { x: 400, y: 300 }, [])]);
  const rackGraph = graph([
    loop('loop', { x: 400, y: 300 }, slots),
    predicate('capsule', { x: 0, y: 0 }, 'loop', 'condition-a'),
  ]);
  const noRackMetrics = blockMetrics(noRackGraph, noRackGraph.nodes[0]);
  const rackMetrics = blockMetrics(rackGraph, rackGraph.nodes[0]);
  assert.equal(rackMetrics.height, noRackMetrics.height, 'rack rows must not increase the loop body height');
  assert.equal(rackMetrics.inputY, noRackMetrics.inputY, 'external input anchor must not move');
  assert.deepEqual(rackMetrics.outputOffsets, noRackMetrics.outputOffsets, 'external done anchor must not move');
  assert.ok(rackMetrics.visualBounds.y < 0 && rackMetrics.visualBounds.height > rackMetrics.height, 'rack-aware visual bounds must extend upward');
  assert.deepEqual(containerBodyEntryAnchor(rackGraph, rackGraph.nodes[0]), containerBodyEntryAnchor(noRackGraph, noRackGraph.nodes[0]), 'body origin must not move when slots are added');
  assert.equal(blockVisualRect(rackGraph, rackGraph.nodes[0]).y, 300 + rackMetrics.visualBounds.y, 'selection/collision rect must start at rack top');

  const noRackBlock = buildBlocks(noRackGraph, catalog, '')[0];
  const rackBlock = buildBlocks(rackGraph, catalog, '')[0];
  assert.equal(noRackBlock.y + noRackBlock.inputY, rackBlock.y + rackBlock.inputY, 'render-space input anchor must stay fixed');
  assert.equal(noRackBlock.y + noRackBlock.outputOffsets.done, rackBlock.y + rackBlock.outputOffsets.done, 'render-space done anchor must stay fixed');
  assert.equal(rackBlock.hasChildren, false, 'a condition capsule must not count as a body child');

  // Rack-aware subtree lane and canvas content bounds include negative visual top.
  const lane = subtreeLaneSpan(rackGraph, 'loop', new Map(), new Set());
  assert.ok(lane.above > noRackMetrics.inputY, 'subtree lane above-span must reserve the rack visual bounds');
  const content = canvasContentBounds([rackBlock]);
  assert.equal(content.minTop, rackBlock.y, 'content bounds must use rack-aware top instead of loop body y');
  assert.ok(content.minTop < rackGraph.nodes[0].position.y, 'upward rack must lower canvas minTop');
  const world = { width: 0, height: 0, minLeft: 0, minTop: 0, maxRight: 0, maxBottom: 0, contentWidth: 0, contentHeight: 0 };
  updateWorldSize([rackBlock], world);
  assert.equal(world.minTop, content.minTop, 'world sizing must retain negative/upward content origin');
  assert.equal(world.contentHeight, content.height, 'fit data must use content height rather than only maxBottom');

  // Predicate capability and card-center rack hit testing.
  assert.equal(isPredicateNode(predicate('p', { x: 0, y: 0 }), catalog), true);
  assert.equal(isPredicateNode(action('a', { x: 0, y: 0 }), catalog), false);
  const oneSlotGraph = graph([loop('loop', { x: 400, y: 300 }, [slots[0]]), predicate('dragged', { x: 800, y: 300 })]);
  const pointerRow = conditionSlotRects(oneSlotGraph, oneSlotGraph.nodes[0], 'condition-a').row;
  const sourcePosition = { x: 800, y: 300 };
  const sourceHeight = blockMetrics(oneSlotGraph, oneSlotGraph.nodes[1]).visualBounds.height;
  const centeredPreview = {
    x: 419,
    y: pointerRow.y + pointerRow.height / 2 - sourceHeight / 2,
  };
  const pointerX = pointerRow.x + 4;
  const startWorldX = sourcePosition.x + pointerX - centeredPreview.x;
  const topGrab = drag('dragged', sourcePosition, centeredPreview, {
    startWorld: { x: startWorldX, y: sourcePosition.y + 10 },
  });
  const bottomGrab = drag('dragged', sourcePosition, centeredPreview, {
    startWorld: { x: startWorldX, y: sourcePosition.y + sourceHeight - 10 },
  });
  const topPointerY = topGrab.startWorld.y + centeredPreview.y - sourcePosition.y;
  const bottomPointerY = bottomGrab.startWorld.y + centeredPreview.y - sourcePosition.y;
  assert.ok(topPointerY < pointerRow.y && bottomPointerY > pointerRow.y + pointerRow.height, 'fixture must prove both grab points are outside opposite sides of the row');
  assert.equal(findInsertCandidate(oneSlotGraph, topGrab, catalog)?.slotId, 'condition-a', 'top grab must use the same card-center slot');
  assert.equal(findInsertCandidate(oneSlotGraph, bottomGrab, catalog)?.slotId, 'condition-a', 'bottom grab must use the same card-center slot');

  const pointerDrag = drag('dragged', sourcePosition, { x: 419, y: 227 }, { startWorld: { x: 805, y: 305 } });
  const derivedPointer = {
    x: pointerDrag.startWorld.x + pointerDrag.previewPositions.get('dragged').x - pointerDrag.startPositions.get('dragged').x,
    y: pointerDrag.startWorld.y + pointerDrag.previewPositions.get('dragged').y - pointerDrag.startPositions.get('dragged').y,
  };
  const cardCenterY = pointerDrag.previewPositions.get('dragged').y + sourceHeight / 2;
  assert.ok(derivedPointer.y >= pointerRow.y && derivedPointer.y <= pointerRow.y + pointerRow.height, 'derived pointer must be inside the rack row');
  assert.ok(cardCenterY > pointerRow.y + pointerRow.height, 'off-center grab fixture must keep card center outside the row');
  assert.notEqual(findInsertCandidate(oneSlotGraph, pointerDrag, catalog)?.kind, 'condition-slot', 'pointer-only overlap must not select a rack slot');
  assert.equal(findConditionSlotCandidate(oneSlotGraph, pointerDrag, derivedPointer, catalog)?.slotId, 'condition-a');

  const actionGraph = graph([loop('loop', { x: 400, y: 300 }, [slots[0]]), action('dragged', { x: 800, y: 300 })]);
  const actionDrag = drag('dragged', { x: 800, y: 300 }, { x: 419, y: 227 }, { startWorld: { x: 805, y: 305 } });
  assert.equal(findConditionSlotCandidate(actionGraph, actionDrag, derivedPointer, catalog), null, 'non-predicate action must not create a rack candidate');

  const occupiedGraph = graph([
    loop('loop', { x: 400, y: 300 }, [slots[0]]),
    predicate('occupied', { x: 0, y: 0 }, 'loop', 'condition-a'),
    predicate('dragged', { x: 800, y: 300 }),
  ]);
  assert.equal(findConditionSlotCandidate(occupiedGraph, pointerDrag, derivedPointer, catalog), null, 'filled slot must not accept a second node');

  // Dropping preserves node identity, clears every root edge, and does not mutate the source graph.
  const dropGraph = graph([
    loop('loop', { x: 400, y: 300 }, [slots[0]]),
    action('source', { x: 0, y: 300 }),
    predicate('dragged', { x: 800, y: 300 }),
    action('target', { x: 1046, y: 300 }),
  ], [
    edge('incoming', 'source', 'done', 'dragged', 'input'),
    edge('outgoing', 'dragged', 'pass', 'target', 'input'),
  ]);
  const dropDrag = drag('dragged', { x: 800, y: 300 }, { x: 419, y: 227 });
  dropDrag.candidate = findConditionSlotCandidate(dropGraph, dropDrag, { x: pointerRow.x + 4, y: pointerRow.y + 4 }, catalog);
  const dropResult = computeDragDrop(dropGraph, dropDrag);
  const dropped = dropResult.graph.nodes.find((item) => item.id === 'dragged');
  assert.equal(dropResult.inserted, true);
  assert.equal(dropped.id, 'dragged', 'slot drop must retain the same node id');
  assert.equal(dropped.parentContainerId, 'loop');
  assert.equal(dropped.parentSlot, 'condition-a');
  assert.equal(dropResult.graph.nodes.length, dropGraph.nodes.length, 'slot drop must move rather than copy the condition');
  assert.equal(dropResult.graph.edges.some((item) => item.sourceNodeId === 'dragged' || item.targetNodeId === 'dragged'), false, 'slot drop must remove all root control edges');
  assert.equal(dropGraph.nodes.find((item) => item.id === 'dragged').parentContainerId, '', 'placement calculation must not mutate live graph membership');

  // Moving between slots keeps id/config; dragging out clears membership and restores a full card.
  const moveGraph = graph([
    loop('loop', { x: 400, y: 300 }, slots.slice(0, 2)),
    predicate('capsule', { x: 0, y: 0 }, 'loop', 'condition-a'),
  ]);
  const capsuleStart = nodePosition(moveGraph, 'capsule');
  const slotB = conditionSlotRects(moveGraph, moveGraph.nodes[0], 'condition-b');
  const moveDrag = drag('capsule', capsuleStart, { x: slotB.capsule.x, y: slotB.capsule.y });
  moveDrag.candidate = findInsertCandidate(moveGraph, moveDrag, catalog);
  assert.equal(moveDrag.candidate?.slotId, 'condition-b', 'capsule-to-slot hit must use the source capsule height, not a restored full-card height');
  const movedGraph = computeDragDrop(moveGraph, moveDrag).graph;
  const moved = movedGraph.nodes.find((item) => item.id === 'capsule');
  assert.equal(moved.id, 'capsule');
  assert.equal(moved.parentSlot, 'condition-b');
  assert.equal(moved.config.tag, 'ready');
  assert.deepEqual(conditionSlots(movedGraph.nodes[0]).map((slot) => slot.slotId), ['condition-a', 'condition-b'], 'slot move must not rewrite rack ids');

  const movedStart = nodePosition(movedGraph, 'capsule');
  const outDrag = drag('capsule', movedStart, { x: 900, y: 520 });
  const outResult = computeDragDrop(movedGraph, outDrag);
  const removed = outResult.graph.nodes.find((item) => item.id === 'capsule');
  assert.equal(outResult.movedOutOfContainer, true);
  assert.equal(removed.parentContainerId, '');
  assert.equal(removed.parentSlot, '');
  const removedBlock = buildBlocks(outResult.graph, catalog, '').find((item) => item.id === 'capsule');
  assert.equal(removedBlock.presentation, undefined, 'dragged-out capsule must return to a full condition card');
  assert.notEqual(removedBlock.inputY, null, 'dragged-out condition must recover its control input anchor');

  // A rack capsule may leave the rack and attach/insert in the same drop; preview and commit must agree.
  const attachGraph = graph([
    loop('loop', { x: 400, y: 300 }, [slots[0]]),
    predicate('capsule', { x: 0, y: 0 }, 'loop', 'condition-a'),
    action('target', { x: 900, y: 300 }),
  ]);
  const attachStart = nodePosition(attachGraph, 'capsule');
  const attachDrag = drag('capsule', attachStart, { x: 640, y: 300 }, {
    candidate: {
      kind: 'attach',
      sourceNodeId: 'capsule',
      sourceSlotId: 'pass',
      targetNodeId: 'target',
      targetSlotId: 'input',
      join: { id: 'attach-capsule', from: 'capsule', to: 'target', branch: 'main', x: 890, y: 360, width: 20 },
      valid: true,
      message: '',
    },
  });
  const attached = computeDragDrop(attachGraph, attachDrag);
  assert.equal(attached.inserted, true, 'dropping a capsule onto an input must commit the valid attach preview');
  assert.equal(attached.graph.nodes.find((item) => item.id === 'capsule').parentContainerId, '');
  assert.equal(attached.graph.edges.some((item) => item.sourceNodeId === 'capsule' && item.targetNodeId === 'target'), true);

  const insertGraph = graph([
    action('source', { x: 0, y: 300 }),
    action('target', { x: 246, y: 300 }),
    loop('loop', { x: 700, y: 300 }, [slots[0]]),
    predicate('capsule', { x: 0, y: 0 }, 'loop', 'condition-a'),
  ], [edge('source-target', 'source', 'done', 'target', 'input')]);
  const insertStart = nodePosition(insertGraph, 'capsule');
  const insertDrag = drag('capsule', insertStart, { x: 116, y: 300 }, {
    candidate: {
      kind: 'insert',
      edge: insertGraph.edges[0],
      join: { id: 'source-target', from: 'source', to: 'target', branch: 'main', x: 244, y: 360, width: 20 },
      valid: true,
      message: '',
    },
  });
  const insertedFromRack = computeDragDrop(insertGraph, insertDrag);
  assert.equal(insertedFromRack.inserted, true, 'dropping a capsule onto an edge must commit the valid insert preview');
  assert.equal(insertedFromRack.graph.edges.some((item) => item.sourceNodeId === 'source' && item.targetNodeId === 'capsule'), true);
  assert.equal(insertedFromRack.graph.edges.some((item) => item.sourceNodeId === 'capsule' && item.targetNodeId === 'target'), true);

  // Rack members are excluded from chain attach/append, while the owning loop remains one done tail.
  const nearCapsuleGraph = graph([
    loop('loop', { x: 400, y: 300 }, [slots[0]]),
    predicate('capsule', { x: 0, y: 0 }, 'loop', 'condition-a'),
    action('dragged', { x: 800, y: 300 }),
  ]);
  const nearCapsuleDrag = drag('dragged', { x: 800, y: 300 }, { x: 431, y: 239 });
  assert.equal(appendCandidates(nearCapsuleGraph, nearCapsuleDrag).some((item) => item.sourceNodeId === 'capsule'), false, 'rack capsule must not be an append source');
  assert.equal(attachCandidates(nearCapsuleGraph, nearCapsuleDrag).some((item) => item.targetNodeId === 'capsule'), false, 'rack capsule must not be an attach target');
  assert.deepEqual(draggedTailOutput(nearCapsuleGraph, new Set(['loop', 'capsule'])), { nodeId: 'loop', slot: out('done') }, 'loop plus embedded capsule must retain one outer done tail');

  // Body layout scopes ignore rack members but descendants/grouping still include them.
  const bodyGraph = graph([
    loop('loop', { x: 400, y: 300 }, [slots[0]]),
    predicate('capsule', { x: 0, y: 0 }, 'loop', 'condition-a'),
    action('body', { x: 442, y: 400 }, 'loop', 'body'),
    action('dragged', { x: 900, y: 400 }),
  ]);
  assert.deepEqual(containerChildren(bodyGraph, 'loop', 'body').map((item) => item.id), ['body'], 'body helpers must exclude condition rack members');
  assert.ok(containerDescendantNodeIds(bodyGraph, 'loop').includes('capsule'), 'whole-container drag group must still include capsule descendants');
  const bodyGapGraph = cloneGraph(bodyGraph);
  const bodyGapDrag = drag('dragged', { x: 900, y: 400 }, { x: 900, y: 400 });
  const capsuleRawPosition = { ...bodyGapGraph.nodes.find((item) => item.id === 'capsule').position };
  const bodyX = bodyGapGraph.nodes.find((item) => item.id === 'body').position.x;
  makeContainerBodyGap(bodyGapGraph, bodyGapDrag, 'loop');
  assert.ok(bodyGapGraph.nodes.find((item) => item.id === 'body').position.x > bodyX, 'body gap should shift body siblings');
  assert.deepEqual(bodyGapGraph.nodes.find((item) => item.id === 'capsule').position, capsuleRawPosition, 'body gap must not shift rack members as body siblings');

  // Rendered rack/capsule structure has no ordinary anchors and exposes accessible NOT state.
  const renderedBlocks = buildBlocks(rackGraph, catalog, 'capsule');
  const renderedLoop = renderedBlocks.find((item) => item.id === 'loop');
  const renderedCapsule = renderedLoop.conditionRack.rows[0].capsule;
  const loopHtml = renderBlock(renderedLoop, null);
  const capsuleHtml = renderBlock(renderedCapsule, null);
  assert.equal(renderedCapsule.inputY, null);
  assert.deepEqual(renderedCapsule.outputOffsets, {});
  assert.match(renderBlockShape(renderedCapsule), /<rect class="block-body"/, 'capsule should use compact rounded shape');
  assert.doesNotMatch(capsuleHtml, /slot-join|branch-tab|container-body-zone|data-from=|data-to=/, 'capsule markup must expose no ordinary control anchors');
  assert.match(capsuleHtml, /data-embedded-parent="loop"/, 'capsule must declare its embedded animation parent');
  assert.equal((loopHtml.match(/data-condition-slot=/g) ?? []).length, 3, 'every empty or filled slot must render one row');
  assert.equal((loopHtml.match(/class="condition-rack-slot/g) ?? []).length, 3, 'every row must render a distinct inner capsule slot');
  assert.equal((loopHtml.match(/class="condition-rack-slot is-empty"/g) ?? []).length, 2, 'empty rows must keep their own inner capsule');
  assert.ok(renderedLoop.conditionRack.rows.every((row) => row.slotRect.width < row.width), 'shared geometry must keep the inner slot inside its shell');
  assert.match(loopHtml, /class="condition-negate"[^>]*style="left:\d+px; top:\d+px; width:\d+px; height:\d+px"/, 'toggle placement must consume shared geometry');
  assert.match(loopHtml, /aria-label="取反此条件"[\s\S]*aria-pressed="false"[\s\S]*title="点击取反"/);
  assert.match(loopHtml, /aria-label="取消取反"[\s\S]*aria-pressed="true"[\s\S]*title="已取反，点击取消"/);
  assert.match(blockStyles, /\.condition-rack-row\s*\{[\s\S]*?border-radius:\s*6px;/, 'rack shell must use an angular outlined frame');
  assert.match(blockStyles, /\.condition-rack-slot\s*\{[\s\S]*?border-radius:\s*999px;/, 'inner slot must retain a capsule shape');
  assert.match(blockStyles, /\.condition-rack-slot\.is-empty\s*\{[\s\S]*?border:\s*2px dashed/, 'empty inner slot must retain a dashed outline');
  assert.doesNotMatch(blockStyles, /\.condition-rack-row\.is-empty\s*\{[\s\S]*?border-style:\s*dashed/, 'the angular shell must not become the empty capsule');
  assert.match(blockStyles, /\.condition-rack-row\.condition-slot-target \.condition-rack-slot\.is-empty/, 'drop targeting must highlight the inner slot');
  assert.ok(visualBlocks(renderedBlocks).some((item) => item.id === 'capsule'), 'ghost/animation visual block set must include embedded capsules');

  const editorHtml = renderConditionRackEditor(rackGraph.nodes[0], rackGraph, catalog);
  assert.match(editorHtml, /data-rack-draft-action="add"/);
  assert.match(editorHtml, /data-rack-draft-action="toggle"/);
  assert.match(editorHtml, /aria-pressed="true"/);
  assert.match(editorHtml, /aria-label="取消取反条件槽 2"/);
  assert.match(editorHtml, /title="删除槽后，保存时会同时删除其中的条件积木"/);
  assert.equal((editorHtml.match(/data-rack-draft-slot=/g) ?? []).length, 3);

  const negatedGraph = graph([
    loop('negated-loop', { x: 400, y: 300 }, [{ slotId: 'condition-negated', negated: true }]),
    predicate('negated-capsule', { x: 0, y: 0 }, 'negated-loop', 'condition-negated'),
  ]);
  const negatedLoopBlock = buildBlocks(negatedGraph, catalog, '')[0];
  assert.equal(negatedLoopBlock.conditionRack.rows[0].capsule.title, '玩家没有标签「ready」', 'NOT must use the catalog negated summary');
  assert.match(renderConditionRackEditor(negatedGraph.nodes[0], negatedGraph, catalog), /玩家没有标签「ready」/);
  const modalOptions = {
    editorClosing: false,
    error: '',
    hasValidation: false,
    modalIssue: '',
    steady: true,
    simulationTestContext: undefined,
    graph: negatedGraph,
  };
  const capsuleModalHtml = renderEditorModal(negatedGraph.nodes[1], catalog, modalOptions);
  assert.match(capsuleModalHtml, /data-modal-summary>玩家没有标签「ready」</, 'capsule modal must keep the rack-aware NOT summary');
  assert.doesNotMatch(capsuleModalHtml, /条件用途|data-config-key="outputMode"/, 'capsule modal must hide ordinary chain output mode');
  assert.match(capsuleModalHtml, /data-config-key="tag"/, 'capsule modal must keep predicate fields');
  const ordinaryGraph = graph([predicate('ordinary-condition', { x: 800, y: 300 })]);
  const ordinaryModalHtml = renderEditorModal(ordinaryGraph.nodes[0], catalog, { ...modalOptions, graph: ordinaryGraph });
  assert.match(ordinaryModalHtml, /条件用途[\s\S]*data-config-key="outputMode"/, 'ordinary condition cards must retain output mode');
  const capsuleSidebarHtml = renderNodeInfo(negatedGraph.nodes[1], negatedGraph, 'negated-capsule', catalog);
  assert.match(capsuleSidebarHtml, /玩家没有标签「ready」/, 'capsule sidebar must use the rack-aware NOT summary');
  assert.doesNotMatch(capsuleSidebarHtml, /条件用途/, 'capsule sidebar must hide ordinary chain output mode');
  const legacyCatalog = {
    ...catalog,
    blocks: catalog.blocks.map((item) => {
      const { predicateNegatedSummaryTemplate, ...legacyItem } = item;
      return legacyItem;
    }),
  };
  assert.equal(predicateNodeSummary(negatedGraph.nodes[1], legacyCatalog, true), '非（玩家拥有标签「ready」）', 'old catalogs must not show a negated slot as positive');

  // Modal edits stay in a cloned graph and delete owned nodes/edges only in the saved draft graph.
  assert.match(appSource, /const draftGraph = cloneGraph\(graph\);[\s\S]*rackEditorSession = \{[\s\S]*draftGraph,[\s\S]*originalGraph: cloneGraph\(graph\)/, 'rack editor must begin with isolated draft and original snapshots');
  assert.match(appSource, /function updateRackEditorDraft[\s\S]*session\.draftGraph\.nodes = session\.draftGraph\.nodes\.filter[\s\S]*session\.draftGraph\.edges = session\.draftGraph\.edges\.filter/, 'filled-slot deletion must remain local to the rack draft');
  assert.match(appSource, /const nextGraph = rackEditorSession[\s\S]*cloneGraph\(rackEditorSession\.draftGraph\)[\s\S]*applyGraphEdit\(nextGraph/s, 'one modal save must apply the complete draft graph once');
  assert.match(appSource, /rackEditorSession = null;[\s\S]*renderApp\(\)/, 'closing/cancelling must discard the rack session without applying it');
  assert.match(appSource, /clientToWorld\(moveEvent\.clientX, ghostRect\.top \+ ghostRect\.height \/ 2\)[\s\S]*catalogConditionSlotAtPoint\(currentGraph\(\), conditionPoint/, 'catalog hover must probe with the visible card center height');
  assert.match(appSource, /const onUp[\s\S]*clientToWorld\(upEvent\.clientX, ghostRect\.top \+ ghostRect\.height \/ 2\)[\s\S]*addCatalogBlockAt\([\s\S]*conditionPoint/, 'catalog drop must commit with the same center-height probe as its preview');
  assert.match(appSource, /const conditionDropPoint = conditionProbePoint \?\? dropPoint;[\s\S]*catalogConditionSlotAtPoint\(graph, conditionDropPoint/, 'catalog drop must consume the center-height probe instead of ignoring it');

  // Embedded transforms/ghosts are parent-relative and avoid double-moving a capsule with its parent.
  assert.match(appSource, /embeddedParentId[\s\S]*!drag\.groupIds\.includes\(embeddedParentId\)[\s\S]*style\.transform/, 'live drag transform must skip an embedded capsule when its parent is in the group');
  assert.match(animationSource, /parentDx[\s\S]*parentDy[\s\S]*placementBlock\.x - baseBlock\.x - parentDx[\s\S]*placementBlock\.y - baseBlock\.y - parentDy/, 'placement animation must subtract embedded parent motion');
  assert.match(animationSource, /block\.embeddedParentId && draggedNodeIds\.has\(block\.embeddedParentId\)/, 'ghost rendering must skip a child already embedded in a dragged parent ghost');

  // Body placement and viewport fit use body-only scope plus rack-aware minTop/content bounds.
  assert.match(appSource, /item\.parentContainerId === selectedContainer\.id && \(item\.parentSlot \|\| 'body'\) === 'body'/, 'catalog body placement index must ignore rack members');
  assert.match(appSource, /world\.contentWidth[\s\S]*world\.contentHeight[\s\S]*world\.minLeft \* scale[\s\S]*world\.minTop \* scale/, 'fitView must fit content bounds and translate from minLeft/minTop');
  assert.match(appSource, /world\.minLeft \+ world\.maxRight[\s\S]*world\.minTop \+ world\.maxBottom/, 'centerView must center the complete visual bounds');

  // Reduced motion skips transforms without removing rack structure or ghosts.
  assert.match(animationSource, /if \(!prefersReducedMotion\(\)\)/);
  assert.match(animationSource, /matchMedia\('\(prefers-reduced-motion: reduce\)'\)/);
  assert.match(blockStyles + animationStyles, /@media \(prefers-reduced-motion: reduce\)/);
  assert.match(loopHtml, /condition-rack/, 'reduced motion must not be the source of structural rack rendering');

  // Direct helper must reject overwriting and preserve identity even when called independently.
  const occupiedCandidate = {
    kind: 'condition-slot', containerNodeId: 'loop', slotId: 'condition-a',
    join: { id: 'slot', from: 'loop', to: 'dragged', branch: 'main', x: 0, y: 0, width: 20 },
    valid: true, message: '',
  };
  const protectedGraph = cloneGraph(occupiedGraph);
  assert.equal(applyConditionSlotDrop(protectedGraph, pointerDrag, occupiedCandidate), false, 'direct drop helper must not overwrite a filled slot');
  assert.equal(conditionSlotMember(protectedGraph, 'loop', 'condition-a')?.id, 'occupied');

  console.log('loop until condition rack WebUI self-check passed');
} finally {
  await server.close();
}
