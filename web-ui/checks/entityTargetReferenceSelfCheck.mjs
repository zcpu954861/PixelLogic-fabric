import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createServer } from 'vite';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const server = await createServer({ appType: 'custom', logLevel: 'silent', server: { middlewareMode: true } });

try {
  const { createCatalogNode, searchCatalog } = await server.ssrLoadModule('/src/model/blockCatalog.ts');
  const { cloneGraph } = await server.ssrLoadModule('/src/model/graphLayout.ts');
  const { entityTargetDraftRef, entityTargetLabel, entityTargetRef, entityTargetSources } = await server.ssrLoadModule('/src/model/entityTargetReference.ts');
  const { renderNodeEditor } = await server.ssrLoadModule('/src/ui/editor/formControls.ts');
  const { nodeSummary, predicateNodeSummary } = await server.ssrLoadModule('/src/ui/humanize/labels.ts');
  const { renderCatalogLibrary } = await server.ssrLoadModule('/src/ui/catalog/catalogLibrary.ts');

  const slot = (id, direction) => ({ id, direction, edgeType: 'CONTROL' });
  const formField = (key, type, label, defaultValue = '') => ({
    key, type, label, description: '', defaultValue, placeholder: '', options: [], required: true,
    min: '', max: '', step: '', ui: type === 'entity_target' ? 'fullWidth' : '', suffix: '',
  });
  const targetJson = JSON.stringify({ source: 'CURRENT_ENTITY' });
  const block = (id, nodeType, nodeKind, extra = {}) => ({
    id,
    version: 1,
    displayName: id,
    description: id,
    categoryId: 'player-entity.tags',
    subcategoryId: 'player-entity.tags',
    tags: [],
    capabilities: nodeKind === 'condition' ? ['PREDICATE'] : [],
    nodeKind,
    nodeType,
    defaultConfig: { target: targetJson, tag: 'ready', ...(extra.defaultConfig ?? {}) },
    formSchema: [formField('target', 'entity_target', '目标', targetJson), ...(nodeKind === 'condition' ? [formField('outputMode', 'segmented', '条件用途', 'PASS_ONLY')] : []), formField('tag', 'string', '标签', 'ready')],
    summaryTemplate: '',
    summaryFormatter: '',
    predicateSummaryTemplate: '{target}拥有标签「{tag}」',
    predicateNegatedSummaryTemplate: '{target}没有标签「{tag}」',
    containerSlots: extra.containerSlots ?? [],
    inputSlots: [slot('input', 'INPUT')],
    outputSlots: nodeKind === 'condition' ? [slot('pass', 'OUTPUT'), slot('fail', 'OUTPUT')] : [slot('done', 'OUTPUT')],
    simulationCapability: 'FULLY_SIMULATABLE',
    mcCapability: 'REQUIRES_MINECRAFT_RUNTIME',
    safetyFlags: [],
    deprecated: false,
    hidden: false,
    visibility: 'BROWSE',
    aliases: [],
    searchKeywords: ['实体标签', '玩家标签'],
    ...extra,
  });
  const blocks = [
    block('condition.entity.has_tag', 'ENTITY_HAS_TAG_CONDITION', 'condition'),
    block('action.entity.add_tag', 'ENTITY_ADD_TAG_ACTION', 'action'),
    block('action.entity.remove_tag', 'ENTITY_REMOVE_TAG_ACTION', 'action'),
    block('context.entity.execute_as', 'CONTEXT_ENTITY_EXECUTE_AS', 'control', {
      categoryId: 'player-entity.context',
      subcategoryId: 'player-entity.context',
      defaultConfig: { target: JSON.stringify({ source: 'CONDITION_SUBJECT' }) },
      formSchema: [formField('target', 'entity_target', '目标', JSON.stringify({ source: 'CONDITION_SUBJECT' }))],
      containerSlots: ['body'],
      searchKeywords: ['执行实体'],
    }),
  ];
  const catalog = {
    packs: [{ id: 'player-entity', displayName: '玩家与实体', description: '', icon: '◎', order: 1 }],
    categories: [
      { id: 'player-entity.tags', packId: 'player-entity', displayName: '标签', description: '', icon: '#', order: 1, visibleByDefault: true },
      { id: 'player-entity.context', packId: 'player-entity', displayName: '执行上下文', description: '', icon: '◎', order: 2, visibleByDefault: true },
    ],
    subcategories: [],
    blocks,
  };

  assert.deepEqual(entityTargetSources.map((source) => source.value), ['CURRENT_ENTITY', 'CONDITION_SUBJECT', 'TARGET_ENTITY', 'ONLINE_PLAYER']);
  assert.equal(entityTargetLabel({ source: 'CURRENT_ENTITY' }), '当前执行实体');
  assert.equal(entityTargetLabel({ source: 'CONDITION_SUBJECT' }), '当前条件主体');
  assert.equal(entityTargetLabel({ source: 'TARGET_ENTITY' }), '当前目标实体');
  assert.equal(entityTargetLabel({ source: 'ONLINE_PLAYER', playerUuid: '00000000-0000-0000-0000-000000000303', playerNameHint: 'Steve' }), '玩家 Steve');
  assert.equal(entityTargetLabel(null), '目标未配置', 'missing target must not decode to an implicit default');
  const unselectedOnlinePlayer = { target: { source: 'ONLINE_PLAYER' } };
  assert.equal(entityTargetRef(unselectedOnlinePlayer), null, 'formal target validation must still require an online player UUID');
  assert.deepEqual(entityTargetDraftRef(unselectedOnlinePlayer), { source: 'ONLINE_PLAYER' }, 'the editor must retain the source while the player is unselected');

  const created = blocks.map((item, index) => createCatalogNode(item, `node-${index}`, { x: index * 20, y: 0 }));
  assert.deepEqual(created[0].config.target, { source: 'CURRENT_ENTITY' }, 'catalog canonical JSON must decode to a real target object');
  assert.deepEqual(created[3].config.target, { source: 'CONDITION_SUBJECT' });
  created[0].config.target.source = 'TARGET_ENTITY';
  assert.deepEqual(created[1].config.target, { source: 'CURRENT_ENTITY' }, 'catalog nodes must not share nested target defaults');
  assert.equal(typeof blocks[0].defaultConfig.target, 'string', 'catalog snapshot itself must remain untouched');

  const graph = { schemaVersion: 1, id: 'targets', displayName: 'targets', createdAt: '', updatedAt: '', fingerprint: '', triggerEntries: {}, nodes: created, edges: [] };
  const cloned = cloneGraph(graph);
  cloned.nodes[1].config.target.source = 'ONLINE_PLAYER';
  assert.deepEqual(graph.nodes[1].config.target, { source: 'CURRENT_ENTITY' }, 'graph/editor clones must isolate nested target drafts');

  const directory = {
    players: [{ uuid: '00000000-0000-0000-0000-000000000404', name: 'Alex', availability: 'ONLINE' }],
    selected: { uuid: '00000000-0000-0000-0000-000000000303', name: 'Steve', availability: 'OFFLINE' },
    loaded: true,
    loading: false,
    error: '',
  };
  for (const nodeItem of created) {
    const html = renderNodeEditor(nodeItem, catalog, undefined, false, directory);
    assert.match(html, /data-entity-target-editor/, `${nodeItem.blockId} must use the shared target editor`);
  }
  const targetNode = createCatalogNode(blocks[0], 'target-warning', { x: 0, y: 0 });
  targetNode.config.target = { source: 'TARGET_ENTITY' };
  assert.match(renderNodeEditor(targetNode, catalog, undefined, false, directory), /当前执行路径可能不提供目标实体/);

  const sourcePickerHtml = renderNodeEditor(created[0], catalog, undefined, false, directory);
  assert.match(sourcePickerHtml, /class="segmented-control entity-target-source-control"/, 'entity target sources must render as one segmented button row');
  assert.equal((sourcePickerHtml.match(/data-entity-target-source=/g) ?? []).length, 4, 'the source picker must expose all four target sources');
  assert.doesNotMatch(sourcePickerHtml, /data-custom-select-toggle/, 'fixed entity target sources must not be hidden in a dropdown');

  const unselectedPlayerNode = createCatalogNode(blocks[1], 'unselected-online-player', { x: 0, y: 0 });
  unselectedPlayerNode.config.target = { source: 'ONLINE_PLAYER' };
  assert.match(renderNodeEditor(unselectedPlayerNode, catalog, undefined, false, directory), /data-entity-target-player-toggle/, 'choosing ONLINE_PLAYER must keep the player picker visible');

  const playerNode = createCatalogNode(blocks[1], 'online-player', { x: 0, y: 0 });
  playerNode.config.target = { source: 'ONLINE_PLAYER', playerUuid: '00000000-0000-0000-0000-000000000303', playerNameHint: 'Steve' };
  const playerHtml = renderNodeEditor(playerNode, catalog, undefined, false, directory);
  assert.match(playerHtml, /data-entity-target-player="00000000-0000-0000-0000-000000000303"/);
  assert.match(playerHtml, /Steve（当前不在线）/);
  assert.match(playerHtml, /data-entity-target-player="00000000-0000-0000-0000-000000000404"/);
  assert.match(playerHtml, /data-entity-target-refresh/);
  assert.doesNotMatch(playerHtml, /<input[^>]*(?:uuid|player)/i, 'online player identity must not be manually editable');
  assert.equal(entityTargetRef(playerNode.config).playerUuid, '00000000-0000-0000-0000-000000000303');
  const selectedOnlineHtml = renderNodeEditor(playerNode, catalog, undefined, false, {
    ...directory,
    selected: { uuid: '00000000-0000-0000-0000-000000000303', name: 'Steve', availability: 'ONLINE' },
  });
  assert.doesNotMatch(selectedOnlineHtml, /Steve（当前不在线）|已保存的玩家当前不在线/,
    'paged-list absence must not mark an explicitly resolved online selection offline');

  const condition = createCatalogNode(blocks[0], 'condition', { x: 0, y: 0 });
  condition.config.outputMode = 'PASS_ONLY';
  assert.equal(nodeSummary(condition, catalog), '当「当前执行实体」拥有标签「ready」时继续。');
  assert.equal(predicateNodeSummary(condition, catalog, true), '当前执行实体没有标签「ready」');
  const add = createCatalogNode(blocks[1], 'add', { x: 0, y: 0 });
  add.config.target = { source: 'ONLINE_PLAYER', playerUuid: '00000000-0000-0000-0000-000000000303', playerNameHint: 'Steve' };
  assert.equal(nodeSummary(add, catalog), '给「玩家 Steve」添加标签「ready」。');
  assert.equal(nodeSummary(created[3], catalog), '以「当前条件主体」为上下文执行。');

  const removedIds = [
    ['condition', 'player', 'has_tag'].join('.'),
    ['action', 'player', 'add_tag'].join('.'),
    ['action', 'player', 'remove_tag'].join('.'),
    ['condition', 'context_entity', 'has_tag'].join('.'),
    ['action', 'context_entity', 'add_tag'].join('.'),
    ['action', 'context_entity', 'remove_tag'].join('.'),
  ];
  for (const id of removedIds) {
    assert.equal(catalog.blocks.some((item) => item.id === id || item.aliases.includes(id)), false, `${id} must remain removed`);
  }
  const removedNodeTypes = [
    ['PLAYER', 'HAS_TAG', 'CONDITION'].join('_'),
    ['PLAYER', 'ADD_TAG', 'ACTION'].join('_'),
    ['PLAYER', 'REMOVE_TAG', 'ACTION'].join('_'),
    ['CONTEXT_ENTITY', 'HAS_TAG', 'CONDITION'].join('_'),
    ['CONTEXT_ENTITY', 'ADD_TAG', 'ACTION'].join('_'),
    ['CONTEXT_ENTITY', 'REMOVE_TAG', 'ACTION'].join('_'),
  ];
  for (const nodeType of removedNodeTypes) {
    assert.equal(catalog.blocks.some((item) => item.nodeType === nodeType), false, `${nodeType} must remain removed`);
  }
  assert.deepEqual(catalog.blocks.filter((item) => item.categoryId === 'player-entity.tags').map((item) => item.id),
    ['condition.entity.has_tag', 'action.entity.add_tag', 'action.entity.remove_tag']);
  assert.deepEqual(searchCatalog(catalog, '实体标签').map((item) => item.block.id).sort(),
    ['action.entity.add_tag', 'action.entity.remove_tag', 'condition.entity.has_tag']);
  const libraryHtml = renderCatalogLibrary(catalog, { location: { level: 'blocks', packId: 'player-entity', categoryId: 'player-entity.tags' }, query: '', filter: null });
  assert.match(libraryHtml, /data-catalog-block="condition\.entity\.has_tag"/, 'drag payload must remain the blockId');

  const app = read('src/ui/app.ts');
  const forms = read('src/styles/forms.css');
  const polling = read('src/api/testRunPolling.ts');
  const onlineLoader = app.slice(app.indexOf('async function loadOnlinePlayers'), app.indexOf('function editorDraftKeyNeedsRerender'));
  const pollingRefresh = app.slice(app.indexOf('function refreshTestExecutionView'), app.indexOf('function apiBusyAttr'));
  assert.match(onlineLoader, /runtime\/online-players/);
  assert.match(onlineLoader, /URLSearchParams\(\{ query: '', limit: '50' \}\)/);
  assert.match(onlineLoader, /if \(selectedUuid\)[\s\S]*query\.set\('selectedUuid', selectedUuid\)/, 'empty selections must omit the canonical UUID query');
  assert.match(onlineLoader, /selectedKnown[\s\S]*directory\.players\.some[\s\S]*directory\.selected\?\.uuid[\s\S]*directory\.loaded && selectedKnown/, 'a cached directory must revalidate an unknown saved UUID');
  assert.match(onlineLoader, /entityTargetDraftRef/, 'the loader must accept an ONLINE_PLAYER draft before a UUID is selected');
  assert.doesNotMatch(onlineLoader, /setInterval|setTimeout|startTestRunPolling/, 'online players must load only on demand');
  assert.doesNotMatch(onlineLoader, /config\[[^\]]+\]\s*=|config\.target\s*=/, 'list refresh must not rewrite the saved target draft');
  assert.match(app, /data-entity-target-refresh[\s\S]*loadOnlinePlayers\(true\)/, 'manual refresh must be explicit');
  assert.match(app, /data-entity-target-player-toggle[\s\S]*loadOnlinePlayers\(\)/, 'opening the player control must load the list on demand');
  assert.match(app, /targetForOnlinePlayer\(uuid, name\)/, 'player selection must save UUID and name hint together');
  assert.match(app, /targetForSource\(source\)/, 'source changes must replace the target object and clear player-only fields');
  assert.match(forms, /\.entity-target-source-control\s*\{[\s\S]*?grid-template-columns:\s*repeat\(4, minmax\(0, 1fr\)\)/, 'entity target source buttons must keep four equal columns');
  assert.doesNotMatch(pollingRefresh, /renderApp\(|data-modal-overlay/, 'run polling must not rebuild the editor');
  assert.match(polling, /window\.setTimeout/, 'only test-run status polling owns a timer');
  assert.match(app, /target instanceof HTMLInputElement[\s\S]*target instanceof HTMLTextAreaElement/, 'ordinary form inputs must keep native Delete behavior');

  console.log('entity target reference WebUI self-check passed');
} finally {
  await server.close();
}
