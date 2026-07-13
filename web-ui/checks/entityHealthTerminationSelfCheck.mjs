import assert from 'node:assert/strict';
import { createServer } from 'vite';

const server = await createServer({ appType: 'custom', logLevel: 'silent', server: { middlewareMode: true } });

try {
  const { createCatalogNode, searchCatalog } = await server.ssrLoadModule('/src/model/blockCatalog.ts');
  const { renderNodeEditor } = await server.ssrLoadModule('/src/ui/editor/formControls.ts');
  const { nodeSummary } = await server.ssrLoadModule('/src/ui/humanize/labels.ts');
  const simulation = await server.ssrLoadModule('/src/model/simulationTestContext.ts');
  const { renderSimulationTestContextModal } = await server.ssrLoadModule('/src/ui/simulation/simulationTestContextPanel.ts');

  const slot = (id, direction) => ({ id, direction, edgeType: 'CONTROL' });
  const target = JSON.stringify({ source: 'CURRENT_ENTITY' });
  const field = (key, type, label, defaultValue = '', options = []) => ({
    key, type, label, description: '', defaultValue, placeholder: '', options, required: true,
    min: '', max: '', step: '', ui: type === 'entity_target' ? 'fullWidth' : '', suffix: '',
  });
  const block = (id, nodeType, categoryId, fields, defaults, keywords) => ({
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
    entityTargetRequirement: id.endsWith('.remove') ? 'ANY_ENTITY' : 'LIVING_ENTITY',
    defaultConfig: { target, ...defaults },
    formSchema: [field('target', 'entity_target', '目标', target), ...fields],
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
  const damageKinds = [
    { value: 'GENERIC', label: '普通' },
    { value: 'MAGIC', label: '魔法' },
    { value: 'FIRE', label: '火焰' },
    { value: 'FALL', label: '摔落' },
    { value: 'VOID', label: '虚空' },
  ];
  const blocks = [
    block('action.entity.damage', 'ENTITY_DAMAGE_ACTION', 'player-entity.health-attributes', [
      field('amount', 'number', '伤害值', '4'), field('damageKind', 'select', '伤害类型', 'GENERIC', damageKinds),
    ], { amount: '4', damageKind: 'GENERIC' }, ['伤害', 'damage']),
    block('action.entity.heal', 'ENTITY_HEAL_ACTION', 'player-entity.health-attributes', [
      field('amount', 'number', '恢复值', '6'),
    ], { amount: '6' }, ['回血', 'heal']),
    block('action.entity.set_health', 'ENTITY_SET_HEALTH_ACTION', 'player-entity.health-attributes', [
      field('health', 'number', '生命值', '20'),
    ], { health: '20' }, ['设置生命', 'health']),
    block('action.entity.kill', 'ENTITY_KILL_ACTION', 'player-entity.entity-management', [], {}, ['杀死', 'kill']),
    block('action.entity.remove', 'ENTITY_REMOVE_ACTION', 'player-entity.entity-management', [], {}, ['移除', 'remove']),
  ];
  const catalog = {
    packs: [{ id: 'player-entity', displayName: '玩家与实体', description: '', icon: '♥', order: 1 }],
    categories: [
      { id: 'player-entity.health-attributes', packId: 'player-entity', displayName: '生命与属性', description: '', icon: '♥', order: 1, visibleByDefault: true },
      { id: 'player-entity.entity-management', packId: 'player-entity', displayName: '实体管理', description: '', icon: '×', order: 2, visibleByDefault: true },
    ],
    subcategories: [],
    blocks,
  };

  const nodes = blocks.map((item, index) => createCatalogNode(item, `health-${index}`, { x: index * 20, y: 0 }));
  nodes.forEach((node) => {
    const html = renderNodeEditor(node, catalog, undefined, false, { players: [], selected: null, loaded: true, loading: false, error: '' });
    assert.match(html, /data-entity-target-editor/, `${node.blockId} should use the shared target editor`);
  });
  assert.deepEqual(blocks[0].formSchema.find((item) => item.key === 'damageKind').options.map((item) => item.value),
    ['GENERIC', 'MAGIC', 'FIRE', 'FALL', 'VOID']);
  assert.equal(nodeSummary(nodes[0], catalog), '对「当前执行实体」造成 4 点普通伤害。');
  assert.equal(nodeSummary(nodes[1], catalog), '恢复「当前执行实体」 6 点生命值。');
  assert.equal(nodeSummary(nodes[2], catalog), '将「当前执行实体」的生命值设为 20。');
  assert.equal(nodeSummary(nodes[3], catalog), '杀死「当前执行实体」。');
  assert.equal(nodeSummary(nodes[4], catalog), '直接移除「当前执行实体」。');
  assert.deepEqual(searchCatalog(catalog, 'health').map((result) => result.block.id).sort(),
    ['action.entity.set_health']);

  const context = simulation.defaultSimulationTestContext();
  assert.deepEqual(
    { health: context.actor.health, maxHealth: context.actor.maxHealth, invulnerable: context.actor.invulnerable },
    { health: 20, maxHealth: 20, invulnerable: false },
  );
  assert.deepEqual(
    {
      living: context.world.targetEntity.living,
      health: context.world.targetEntity.health,
      maxHealth: context.world.targetEntity.maxHealth,
      invulnerable: context.world.targetEntity.invulnerable,
    },
    { living: true, health: 20, maxHealth: 20, invulnerable: false },
  );
  const lowHealth = simulation.updateSimulationActorHealth(context, 'health', '10');
  assert.equal(simulation.validateSimulationTestContext(lowHealth), '');
  assert.match(simulation.validateSimulationTestContext(simulation.updateSimulationActorHealth(context, 'health', '21')), /最大生命值/);
  const targetHealth = simulation.updateSimulationTargetEntityHealth(context, 'health', '0');
  assert.equal(simulation.validateSimulationTestContext(targetHealth), '');
  const targetFlags = simulation.updateSimulationTargetEntityFlag(targetHealth, 'invulnerable', true);
  assert.equal(targetFlags.world.targetEntity.invulnerable, true);

  const modal = renderSimulationTestContextModal(context, { closing: false, error: '', steady: true });
  assert.match(modal, /data-sim-draft-health-field="health"/);
  assert.match(modal, /data-sim-draft-invulnerable="true"/);
  assert.match(modal, /data-sim-target-entity-health-field="maxHealth"/);
  assert.match(modal, /data-sim-target-entity-flag="living"/);

  console.log('entity health termination WebUI self-check passed');
} finally {
  await server.close();
}
