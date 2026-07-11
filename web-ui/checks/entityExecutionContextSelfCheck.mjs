import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createServer } from 'vite';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const server = await createServer({ appType: 'custom', logLevel: 'silent', server: { middlewareMode: true } });

try {
  const {
    addSimulationTargetEntityTag,
    cloneSimulationTestContext,
    defaultSimulationTestContext,
    simulationTestPayload,
    updateSimulationDisplayName,
    updateSimulationTargetEntity,
    updateSimulationTargetEntityEnabled,
    validateSimulationTestContext,
  } = await server.ssrLoadModule('/src/model/simulationTestContext.ts');
  const { renderSimulationTestContextModal, renderSimulationTestResultSummary } = await server.ssrLoadModule(
    '/src/ui/simulation/simulationTestContextPanel.ts',
  );
  const { isBodyContainerNode } = await server.ssrLoadModule('/src/model/containerNodes.ts');
  const { blockMetrics, containerBodyEntryAnchor } = await server.ssrLoadModule('/src/model/graphLayout.ts');
  const { containerMinimumHeight, containerMinimumWidth } = await server.ssrLoadModule('/src/model/containerGeometry.ts');
  const { computeDragDrop, findInsertCandidate } = await server.ssrLoadModule('/src/ui/canvas/dragInsert.ts');
  const { edge, input, node, out } = await server.ssrLoadModule('/src/model/demoGraph.ts');
  const { blockKind, entitySourceLabel } = await server.ssrLoadModule('/src/ui/humanize/labels.ts');

  let context = defaultSimulationTestContext();
  assert.deepEqual(context.world.targetEntity, {
    enabled: false,
    entityTypeId: 'minecraft:zombie',
    displayName: '测试僵尸',
    tags: [],
  });
  context = updateSimulationTargetEntityEnabled(context, true);
  context = updateSimulationTargetEntity(context, 'entityTypeId', 'minecraft:skeleton');
  context = updateSimulationTargetEntity(context, 'displayName', '测试骷髅');
  const tagged = addSimulationTargetEntityTag(context, ' boss ');
  assert.equal(tagged.error, '');
  context = updateSimulationDisplayName(tagged.context, 'Steve');
  assert.deepEqual(context.world.targetEntity, {
    enabled: true,
    entityTypeId: 'minecraft:skeleton',
    displayName: '测试骷髅',
    tags: ['boss'],
  }, 'editing actor fields must not discard the target entity draft');
  assert.equal(validateSimulationTestContext(context), '');
  assert.match(
    validateSimulationTestContext({
      ...context,
      world: { ...context.world, targetEntity: { ...context.world.targetEntity, entityTypeId: 'zombie' } },
    }),
    /实体类型 ID/,
  );
  assert.match(
    validateSimulationTestContext({
      ...context,
      world: { ...context.world, targetEntity: { ...context.world.targetEntity, tags: ['bad tag'] } },
    }),
    /空白字符/,
  );
  assert.match(addSimulationTargetEntityTag(context, '').error, /标签不能为空/);

  context = {
    ...context,
    actor: { ...context.actor, tags: ['actor-tag'] },
    world: {
      ...context.world,
      regions: [{ name: 'arena', dimensionId: 'minecraft:overworld', minX: 0, minY: 0, minZ: 0, maxX: 10, maxY: 10, maxZ: 10 }],
    },
  };
  const cloned = cloneSimulationTestContext(context);
  cloned.actor.tags.push('actor-clone-only');
  cloned.world.targetEntity.tags.push('clone-only');
  cloned.world.regions[0].name = 'changed';
  assert.deepEqual(context.actor.tags, ['actor-tag'], 'actor tags must be deep-cloned');
  assert.deepEqual(context.world.targetEntity.tags, ['boss'], 'target entity tags must be deep-cloned');
  assert.equal(context.world.regions[0].name, 'arena', 'regions must be deep-cloned');
  assert.deepEqual(simulationTestPayload(context).testContext.world.targetEntity, context.world.targetEntity);

  const modal = renderSimulationTestContextModal(context, { closing: false, error: '', steady: true });
  assert.match(modal, /测试目标实体/);
  assert.match(modal, /data-sim-target-entity-enabled/);
  assert.match(modal, /data-sim-target-entity-field="entityTypeId"/);
  assert.match(modal, /data-sim-target-entity-tag-input/);

  const resultHtml = renderSimulationTestResultSummary({
    success: true,
    traceId: 'entity-context-trace',
    message: '执行完成。',
    actorDisplayName: 'Steve',
    actorOperator: false,
    initialActorTags: ['ready'],
    actorTags: ['ready', 'done'],
    targetEntityEnabled: true,
    targetEntityTypeId: 'minecraft:skeleton',
    targetEntityDisplayName: '测试骷髅',
    initialTargetEntityTags: ['boss'],
    targetEntityTags: ['boss', 'target'],
    timerScheduled: false,
    status: 'COMPLETED',
  });
  assert.match(resultHtml, /测试目标实体/);
  assert.match(resultHtml, /测试骷髅/);
  assert.match(resultHtml, /目标实体最终标签/);
  assert.match(resultHtml, /boss，target/);

  const graph = (nodes, edges = []) => ({
    schemaVersion: 1,
    id: 'entity-context-webui-check',
    displayName: 'entity-context-webui-check',
    createdAt: '',
    updatedAt: '',
    fingerprint: '',
    triggerEntries: {},
    nodes,
    edges,
  });
  const action = (id, position, parentContainerId = '') => ({
    ...node(id, 'CONTEXT_ENTITY_ADD_TAG_ACTION', id, { tag: 'ready' }, position, [input('input'), out('done')], 'action.context_entity.add_tag'),
    parentContainerId,
    parentSlot: parentContainerId ? 'body' : '',
  });
  const contextContainer = (id, position = { x: 0, y: 0 }, parentContainerId = '') => ({
    ...node(
      id,
      'CONTEXT_ENTITY_EXECUTE_AS',
      id,
      { entitySource: 'CONDITION_SUBJECT' },
      position,
      [input('input'), out('done')],
      'context.entity.execute_as',
    ),
    parentContainerId,
    parentSlot: parentContainerId ? 'body' : '',
  });
  const loop = (id, position = { x: 0, y: 0 }) => node(
    id,
    'CONTROL_LOOP_COUNT',
    id,
    { count: '1' },
    position,
    [input('input'), out('done')],
    'control.loop.count',
  );
  const drag = (rootId, start, preview) => ({
    pointerId: 1,
    rootId,
    groupIds: [rootId],
    started: true,
    startClient: { x: 0, y: 0 },
    startWorld: start,
    startPositions: new Map([[rootId, start]]),
    previewPositions: new Map([[rootId, preview]]),
    joins: [],
    candidate: null,
  });

  const emptyContext = contextContainer('context');
  assert.equal(isBodyContainerNode(emptyContext), true);
  assert.equal(blockKind(emptyContext.type), 'control');
  assert.equal(blockMetrics(graph([emptyContext]), emptyContext).width, containerMinimumWidth);
  assert.equal(blockMetrics(graph([emptyContext]), emptyContext).height, containerMinimumHeight);
  assert.equal(entitySourceLabel('CONDITION_SUBJECT'), '当前条件对象');
  assert.equal(entitySourceLabel('RUN_ENTITY'), '运行实体');
  assert.equal(entitySourceLabel('TARGET_ENTITY'), '目标实体');

  const bodyGraph = graph([emptyContext, action('dragged', { x: 900, y: 100 })]);
  const bodyDrag = drag('dragged', { x: 900, y: 100 }, { x: 170, y: 100 });
  bodyDrag.candidate = findInsertCandidate(bodyGraph, bodyDrag);
  assert.equal(bodyDrag.candidate?.kind, 'container', 'execute-as must use the normal empty-body candidate');
  const bodyBefore = JSON.stringify(bodyGraph);
  const bodyDrop = computeDragDrop(bodyGraph, bodyDrag).graph;
  assert.equal(JSON.stringify(bodyGraph), bodyBefore, 'context placement preview must not mutate the live graph');
  assert.equal(bodyDrop.nodes.find((item) => item.id === 'dragged')?.parentContainerId, 'context');
  assert.equal(bodyDrop.nodes.find((item) => item.id === 'dragged')?.parentSlot, 'body');

  const populatedGraph = graph([
    contextContainer('populated'),
    action('source', { x: 42, y: 100 }, 'populated'),
    action('target', { x: 288, y: 100 }, 'populated'),
    action('inserted', { x: 800, y: 100 }),
  ], [edge('inside', 'source', 'done', 'target', 'input')]);
  const populatedDrag = drag('inserted', { x: 800, y: 100 }, { x: 166, y: 100 });
  populatedDrag.joins = [{
    id: 'inside', from: 'source', to: 'target', branch: 'main', x: 286, y: 160, width: 20, tone: 'normal',
  }];
  populatedDrag.candidate = findInsertCandidate(populatedGraph, populatedDrag);
  assert.equal(populatedDrag.candidate?.kind, 'insert', 'a populated execute-as body must allow internal insertion');
  const populatedDrop = computeDragDrop(populatedGraph, populatedDrag).graph;
  assert.equal(populatedDrop.nodes.find((item) => item.id === 'inserted')?.parentContainerId, 'populated');
  assert.equal(populatedDrop.edges.some((item) => item.sourceNodeId === 'source' && item.targetNodeId === 'inserted'), true);
  assert.equal(populatedDrop.edges.some((item) => item.sourceNodeId === 'inserted' && item.targetNodeId === 'target'), true);

  const nestedGraph = graph([loop('outer'), contextContainer('inner', { x: 800, y: 100 })]);
  const nestedDrag = drag('inner', { x: 800, y: 100 }, { x: 42, y: 100 });
  nestedDrag.candidate = findInsertCandidate(nestedGraph, nestedDrag);
  const nestedDrop = computeDragDrop(nestedGraph, nestedDrag).graph;
  const nestedContext = nestedDrop.nodes.find((item) => item.id === 'inner');
  const outer = nestedDrop.nodes.find((item) => item.id === 'outer');
  const entry = containerBodyEntryAnchor(nestedDrop, outer);
  assert.equal(nestedContext?.parentContainerId, 'outer');
  assert.equal(nestedContext?.position.x, entry.x);
  assert.equal(nestedContext?.position.y + blockMetrics(nestedDrop, nestedContext).inputY, entry.y);

  const app = read('src/ui/app.ts');
  const dragInsert = read('src/ui/canvas/dragInsert.ts');
  const containerPlacement = read('src/ui/canvas/containerPlacement.ts');
  const animations = read('src/ui/canvas/interactionAnimations.ts');
  const blockView = read('src/ui/canvas/blockView.ts');
  const styles = read('src/styles/blocks.css');
  const pointerMove = app.slice(app.indexOf('function moveBlockDrag'), app.indexOf('function endBlockDrag'));
  const pointerUp = app.slice(app.indexOf('function endBlockDrag'), app.indexOf('function applyDragPreviewPositions'));
  assert.doesNotMatch(pointerMove, /applyGraphEdit\(/, 'pointer move must remain preview-only');
  assert.equal(pointerUp.match(/applyGraphEdit\(/g)?.length, 1, 'pointer up must commit one graph edit');
  assert.match(dragInsert, /isBodyContainerNode/, 'drag insertion must use the shared body-container predicate');
  assert.match(containerPlacement, /isBodyContainerNode/, 'container resize must use the shared body-container predicate');
  assert.doesNotMatch(app + dragInsert + containerPlacement, /startsWith\('control\.loop\.'/,
    'context-aware placement must not keep loop-only container checks');
  assert.match(animations, /renderBlock\(/, 'context placement must reuse the existing rendered-block ghost');
  assert.match(blockView, /data-category=/, 'rendered blocks must expose their catalog category for styling');
  assert.match(styles, /\.logic-block\.control\[data-category="context"\]/,
    'execute-as must be visually distinct without a second geometry system');

  // Keep one ordinary control edge in this check so the graph fixture exercises the same shape as real documents.
  assert.equal(edge('e', 'source', 'done', 'target', 'input').type, 'CONTROL');
  console.log('entity execution context WebUI self-check passed');
} finally {
  await server.close();
}
