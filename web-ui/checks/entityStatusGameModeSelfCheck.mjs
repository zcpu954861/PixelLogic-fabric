import assert from 'node:assert/strict';
import { createServer } from 'vite';

const server = await createServer({ appType: 'custom', logLevel: 'silent', server: { middlewareMode: true } });

try {
  const catalogModel = await server.ssrLoadModule('/src/model/blockCatalog.ts');
  const { displayFieldValue, renderNodeEditor } = await server.ssrLoadModule('/src/ui/editor/formControls.ts');
  const { nodeSummary } = await server.ssrLoadModule('/src/ui/humanize/labels.ts');

  const slot = (id, direction) => ({ id, direction, edgeType: 'CONTROL' });
  const currentEntity = JSON.stringify({ source: 'CURRENT_ENTITY' });
  const field = (key, type, label, defaultValue = '', options = [], ui = '') => ({
    key, type, label, description: `${label}说明`, defaultValue, placeholder: '', options, required: true,
    min: '', max: '', step: '', ui, suffix: '',
  });
  const block = (id, nodeType, categoryId, requirement, fields, defaults, keywords) => ({
    id,
    version: 1,
    displayName: id,
    description: id,
    categoryId,
    subcategoryId: categoryId,
    tags: [],
    capabilities: [],
    nodeKind: 'action',
    nodeType,
    entityTargetRequirement: requirement,
    defaultConfig: { target: currentEntity, ...defaults },
    formSchema: fields,
    summaryTemplate: '',
    summaryFormatter: id,
    containerSlots: [],
    inputSlots: [slot('input', 'INPUT')],
    outputSlots: [slot('done', 'OUTPUT')],
    simulationCapability: 'APPROXIMATE_SIMULATION',
    mcCapability: 'REQUIRES_MINECRAFT_RUNTIME',
    safetyFlags: ['ENTITY_MUTATING'],
    deprecated: false,
    hidden: false,
    visibility: 'BROWSE',
    aliases: [],
    searchKeywords: keywords,
  });
  const targetField = field('target', 'entity_target', '目标', currentEntity, [], 'fullWidth section:common');
  const effectField = field('effectId', 'status_effect', '状态效果', 'minecraft:speed', [], 'fullWidth section:common');
  const updatePolicies = [
    { value: 'VANILLA_UPDATE', label: '原版更新' },
    { value: 'REPLACE', label: '强制替换' },
  ];
  const gameModes = [
    { value: 'SURVIVAL', label: '生存' },
    { value: 'CREATIVE', label: '创造' },
    { value: 'ADVENTURE', label: '冒险' },
    { value: 'SPECTATOR', label: '旁观' },
  ];
  const blocks = [
    block(
      'action.entity.add_status_effect',
      'ENTITY_ADD_STATUS_EFFECT_ACTION',
      'player-entity.status-effects',
      'LIVING_ENTITY',
      [
        targetField,
        effectField,
        { ...field('durationSeconds', 'integer', '持续时间', '30', [], 'section:common'), min: '1', max: '1000000', step: '1', suffix: '秒' },
        { ...field('level', 'integer', '等级', '1', [], 'section:common'), min: '1', max: '256', step: '1', suffix: '级' },
        field('ambient', 'boolean', '环境效果', 'false', [], 'section:display'),
        field('showParticles', 'boolean', '显示粒子', 'true', [], 'section:display'),
        field('showIcon', 'boolean', '显示图标', 'true', [], 'section:display'),
        field('updatePolicy', 'select', '更新策略', 'VANILLA_UPDATE', updatePolicies, 'section:advanced'),
      ],
      {
        effectId: 'minecraft:speed', durationSeconds: '30', level: '1', updatePolicy: 'VANILLA_UPDATE',
        ambient: 'false', showParticles: 'true', showIcon: 'true',
      },
      ['状态效果', '药水效果', 'effect'],
    ),
    block(
      'action.entity.remove_status_effect',
      'ENTITY_REMOVE_STATUS_EFFECT_ACTION',
      'player-entity.status-effects',
      'LIVING_ENTITY',
      [targetField, effectField],
      { effectId: 'minecraft:speed' },
      ['状态效果', '移除效果', 'effect'],
    ),
    block(
      'action.player.set_game_mode',
      'PLAYER_SET_GAME_MODE_ACTION',
      'player-entity.player-settings',
      'PLAYER_ONLY',
      [
        { ...targetField, label: '目标玩家' },
        field('gameMode', 'segmented', '游戏模式', 'SURVIVAL', gameModes, 'segmented fullWidth'),
      ],
      { gameMode: 'SURVIVAL' },
      ['游戏模式', 'gamemode', '冒险'],
    ),
  ];
  const catalog = {
    packs: [{ id: 'player-entity', displayName: '玩家与实体', description: '', icon: '♟', order: 1 }],
    categories: [
      { id: 'player-entity.status-effects', packId: 'player-entity', displayName: '状态效果', description: '', icon: '✚', order: 1, visibleByDefault: true },
      { id: 'player-entity.player-settings', packId: 'player-entity', displayName: '玩家设置', description: '', icon: '◇', order: 2, visibleByDefault: true },
    ],
    subcategories: [],
    blocks,
  };

  const nodes = blocks.map((item, index) => catalogModel.createCatalogNode(item, `status-${index}`, { x: index * 20, y: 0 }));
  nodes.forEach((node) => {
    assert.match(
      renderNodeEditor(node, catalog, undefined, false, { players: [], selected: null, loaded: true, loading: false, error: '' }),
      /data-entity-target-editor/,
      `${node.blockId} should reuse the shared entity target editor`,
    );
  });

  const addHtml = renderNodeEditor(nodes[0], catalog);
  assert.match(addHtml, /data-config-key="effectId"/);
  assert.match(addHtml, /<summary>显示设置<\/summary>/);
  assert.match(addHtml, /<summary>高级设置<\/summary>/);
  assert.match(addHtml, /data-config-key="updatePolicy"/);
  const gameModeHtml = renderNodeEditor(nodes[2], catalog);
  for (const label of ['生存', '创造', '冒险', '旁观']) assert.match(gameModeHtml, new RegExp(`>\\s*${label}\\s*<`));
  assert.match(gameModeHtml, /field-row is-full[^>]*>游戏模式/);
  assert.equal(displayFieldValue({
    label: '游戏模式', key: 'gameMode', value: 'ADVENTURE', control: 'segmented', options: gameModes,
  }), '冒险');

  nodes[0].config.durationSeconds = '10';
  nodes[0].config.level = '2';
  assert.equal(nodeSummary(nodes[0], catalog), '给予「当前执行实体」速度 II，持续 10 秒。');
  nodes[1].config.target = { source: 'CONDITION_SUBJECT' };
  nodes[1].config.effectId = 'minecraft:poison';
  assert.equal(nodeSummary(nodes[1], catalog), '移除「当前条件主体」的中毒效果。');
  nodes[2].config.target = { source: 'ONLINE_PLAYER', playerUuid: '00000000-0000-0000-0000-000000000303', playerNameHint: 'Steve' };
  nodes[2].config.gameMode = 'ADVENTURE';
  assert.equal(nodeSummary(nodes[2], catalog), '将「玩家 Steve」设置为冒险模式。');

  assert.deepEqual(
    catalogModel.catalogCategoriesForPack(catalog, 'player-entity').map(({ id, count }) => [id, count]),
    [['player-entity.status-effects', 2], ['player-entity.player-settings', 1]],
  );
  assert.deepEqual(
    catalogModel.searchCatalog(catalog, '状态效果').map((result) => result.block.id).sort(),
    ['action.entity.add_status_effect', 'action.entity.remove_status_effect'],
  );
  assert.equal(catalogModel.searchCatalog(catalog, 'gamemode')[0]?.block.id, 'action.player.set_game_mode');

  console.log('entity status effect and game mode WebUI self-check passed');
} finally {
  await server.close();
}
