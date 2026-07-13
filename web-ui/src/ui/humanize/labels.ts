import { catalogBlock, catalogCategory } from '../../model/blockCatalog';
import { entityTargetLabel, entityTargetRef, graphConfigString } from '../../model/entityTargetReference';
import { conditionRackParent, conditionSlots } from '../../model/conditionRack';
import { conditionOutputMode, conditionOutputModeLabel } from '../../model/conditionOutputMode';
import type { BlockCatalog, BlockKind, CatalogBlock, FieldOption, GraphDocument, GraphNode } from '../../model/graphTypes';
import { richTextPlainText, shortRichText } from '../../model/richText';

export function blockKind(type: string): BlockKind {
  if (type.includes('TRIGGER')) {
    return 'trigger';
  }
  if (type.includes('CONDITION')) {
    return 'condition';
  }
  if (type === 'STATE_SET_ACTION' || type === 'STATE_ADD_ACTION') {
    return 'state';
  }
  if (type === 'TIMER_START_ACTION') {
    return 'timer';
  }
  if (type === 'DEBUG_LOG_ACTION') {
    return 'debug';
  }
  if (type.startsWith('CONTROL_LOOP_') || type === 'CONTEXT_ENTITY_EXECUTE_AS') {
    return 'control';
  }
  return 'action';
}

export function booleanOptions(): FieldOption[] {
  return [
    { value: 'true', label: '是' },
    { value: 'false', label: '否' },
  ];
}

export function stateScopeOptions(): FieldOption[] {
  return [
    { value: 'PLAYER', label: '玩家' },
    { value: 'GLOBAL', label: '全局' },
    { value: 'SESSION', label: '当前会话' },
  ];
}

export function valueTypeOptions(): FieldOption[] {
  return [
    { value: 'BOOLEAN', label: '是或否' },
    { value: 'INTEGER', label: '数字' },
    { value: 'STRING', label: '文本' },
  ];
}

export function targetLabel(value = 'CURRENT_PLAYER'): string {
  switch (value) {
    case 'CURRENT_PLAYER':
      return '当前玩家';
    case 'SIMULATED_PLAYER':
      return '模拟玩家';
    default:
      return value;
  }
}

export function nodeTypeLabel(type: string): string {
  switch (type) {
    case 'MANUAL_TRIGGER':
      return '测试触发';
    case 'COMMAND_TRIGGER':
      return '命令触发';
    case 'STATE_COMPARE_CONDITION':
      return '条件判断块(胶囊)';
    case 'MESSAGE_ACTION':
      return '发送消息';
    case 'ENTITY_HAS_TAG_CONDITION':
    case 'PLAYER_IS_ADMIN_CONDITION':
    case 'PLAYER_DIMENSION_CONDITION':
    case 'PLAYER_IN_REGION_CONDITION':
    case 'PLAYER_Y_COMPARE_CONDITION':
    case 'TARGET_BLOCK_TYPE_CONDITION':
    case 'TARGET_BLOCK_IN_REGION_CONDITION':
    case 'TARGET_BLOCK_Y_COMPARE_CONDITION':
    case 'PLAYER_NEAR_TARGET_BLOCK_CONDITION':
      return '条件判断块(胶囊)';
    case 'CONTROL_LOOP_COUNT':
    case 'CONTROL_LOOP_FOREVER':
      return '控制流';
    case 'CONTEXT_ENTITY_EXECUTE_AS':
      return '执行上下文';
    case 'ENTITY_ADD_TAG_ACTION':
      return '添加实体标签';
    case 'ENTITY_REMOVE_TAG_ACTION':
      return '移除实体标签';
    case 'ENTITY_DAMAGE_ACTION':
      return '伤害实体';
    case 'ENTITY_HEAL_ACTION':
      return '恢复实体生命值';
    case 'ENTITY_SET_HEALTH_ACTION':
      return '设置实体生命值';
    case 'ENTITY_KILL_ACTION':
      return '杀死实体';
    case 'ENTITY_REMOVE_ACTION':
      return '移除实体';
    case 'STATE_SET_ACTION':
      return '状态写入';
    case 'STATE_ADD_ACTION':
      return '状态累加';
    case 'TIMER_START_ACTION':
      return '计时器';
    case 'DEBUG_LOG_ACTION':
      return '调试记录';
    default:
      return '积木';
  }
}

export function nodeCategoryLabel(nodeItem: GraphNode, catalog?: BlockCatalog): string {
  if (!catalog) {
    return nodeTypeLabel(nodeItem.type);
  }
  const blockItem = catalogBlock(catalog, nodeItem.blockId ?? '') ?? catalog.blocks.find((item) => item.nodeType === nodeItem.type) ?? null;
  const categoryItem = blockItem ? catalogCategory(catalog, blockItem.categoryId) : null;
  return categoryItem?.displayName ?? nodeTypeLabel(nodeItem.type);
}

export function nodeOfficialLabel(nodeItem: GraphNode, catalog?: BlockCatalog): string {
  return catalog ? catalogBlock(catalog, nodeItem.blockId ?? '')?.displayName ?? nodeTypeLabel(nodeItem.type) : nodeTypeLabel(nodeItem.type);
}

export function nodeTypeMetaLabel(nodeItem: GraphNode, catalog?: BlockCatalog): string {
  return `${nodeCategoryLabel(nodeItem, catalog)}：${nodeOfficialLabel(nodeItem, catalog)}`;
}

export function nodeSummary(nodeItem: GraphNode, catalog?: BlockCatalog): string {
  const blockItem = catalog ? catalogBlock(catalog, nodeItem.blockId ?? '') : null;
  if (blockItem) {
    return catalogSummary(blockItem, nodeItem);
  }
  return legacyNodeTypeSummary(nodeItem);
}

export function predicateNodeSummary(nodeItem: GraphNode, catalog: BlockCatalog, negated = false): string {
  const blockItem = catalogBlock(catalog, nodeItem.blockId ?? '')
    ?? catalog.blocks.find((item) => item.nodeType === nodeItem.type);
  const positiveTemplate = blockItem?.predicateSummaryTemplate ?? '';
  const template = negated
    ? blockItem?.predicateNegatedSummaryTemplate || (positiveTemplate ? `非（${positiveTemplate}）` : '')
    : positiveTemplate;
  if (template) {
    return template
      .replaceAll('{target}', entityTargetLabel(entityTargetRef(nodeItem.config)))
      .replaceAll('{tag}', graphConfigString(nodeItem.config, 'tag', '标签'))
      .replaceAll('{dimensionId}', graphConfigString(nodeItem.config, 'dimensionId', 'minecraft:overworld'))
      .replaceAll('{regionName}', graphConfigString(nodeItem.config, 'regionName', '区域名称'))
      .replaceAll('{blockId}', graphConfigString(nodeItem.config, 'blockId', 'minecraft:stone'));
  }
  return nodeSummary(nodeItem, catalog);
}

export function rackAwareNodeSummary(nodeItem: GraphNode, graph: GraphDocument, catalog: BlockCatalog): string {
  const parent = conditionRackParent(graph, nodeItem);
  const slot = parent
    ? conditionSlots(parent).find((item) => item.slotId === nodeItem.parentSlot)
    : null;
  return slot ? predicateNodeSummary(nodeItem, catalog, slot.negated) : nodeSummary(nodeItem, catalog);
}

function catalogSummary(blockItem: CatalogBlock, nodeItem: GraphNode): string {
  if (blockItem.id === 'condition.state.equals') {
    return conditionStateSummary(nodeItem);
  }
  if (blockItem.id === 'condition.entity.has_tag') {
    return entityTagConditionSummary(nodeItem);
  }
  if (blockItem.id === 'action.entity.add_tag') {
    return `给「${entityTargetLabel(entityTargetRef(nodeItem.config))}」添加标签「${graphConfigString(nodeItem.config, 'tag', '标签')}」。`;
  }
  if (blockItem.id === 'action.entity.remove_tag') {
    return `移除「${entityTargetLabel(entityTargetRef(nodeItem.config))}」的标签「${graphConfigString(nodeItem.config, 'tag', '标签')}」。`;
  }
  if (blockItem.id === 'action.entity.damage') {
    return `对「${entityTargetLabel(entityTargetRef(nodeItem.config))}」造成 ${graphConfigString(nodeItem.config, 'amount', '4')} 点${damageKindLabel(graphConfigString(nodeItem.config, 'damageKind', 'GENERIC'))}伤害。`;
  }
  if (blockItem.id === 'action.entity.heal') {
    return `恢复「${entityTargetLabel(entityTargetRef(nodeItem.config))}」 ${graphConfigString(nodeItem.config, 'amount', '6')} 点生命值。`;
  }
  if (blockItem.id === 'action.entity.set_health') {
    return `将「${entityTargetLabel(entityTargetRef(nodeItem.config))}」的生命值设为 ${graphConfigString(nodeItem.config, 'health', '20')}。`;
  }
  if (blockItem.id === 'action.entity.kill') {
    return `杀死「${entityTargetLabel(entityTargetRef(nodeItem.config))}」。`;
  }
  if (blockItem.id === 'action.entity.remove') {
    return `直接移除「${entityTargetLabel(entityTargetRef(nodeItem.config))}」。`;
  }
  if (blockItem.id === 'condition.player.is_admin') {
    return playerAdminConditionSummary(nodeItem);
  }
  if (blockItem.id === 'condition.player.dimension_is') {
    return playerDimensionConditionSummary(nodeItem);
  }
  if (blockItem.id === 'condition.player.in_region') {
    return playerRegionConditionSummary(nodeItem);
  }
  if (blockItem.id === 'condition.player.y_compare') {
    return playerYCompareConditionSummary(nodeItem);
  }
  if (blockItem.id === 'condition.target_block.is_type') {
    return targetBlockTypeConditionSummary(nodeItem);
  }
  if (blockItem.id === 'condition.target_block.in_region') {
    return targetBlockRegionConditionSummary(nodeItem);
  }
  if (blockItem.id === 'condition.target_block.y_compare') {
    return targetBlockYCompareConditionSummary(nodeItem);
  }
  if (blockItem.id === 'condition.player.near_target_block') {
    return playerNearTargetBlockConditionSummary(nodeItem);
  }
  if (blockItem.id === 'control.loop.count') {
    return `把内部积木循环 ${graphConfigString(nodeItem.config, 'count', '3')} 次后继续。`;
  }
  if (blockItem.id === 'control.loop.forever') {
    return `持续循环内部积木，每轮间隔 ${graphConfigString(nodeItem.config, 'intervalSeconds', '1')} 秒。`;
  }
  if (blockItem.id === 'control.loop.until') {
    return `配置了 ${nodeItem.conditionSlots?.length ?? 0} 个结束条件槽。`;
  }
  if (blockItem.id === 'context.entity.execute_as') {
    return `以「${entityTargetLabel(entityTargetRef(nodeItem.config))}」为上下文执行。`;
  }
  const template = blockItem.summaryTemplate;
  if (!template) {
    return legacyNodeTypeSummary(nodeItem);
  }
  return template
    .replaceAll('{scope}', scopeLabel(graphConfigString(nodeItem.config, 'scope')))
    .replaceAll('{key}', graphConfigString(nodeItem.config, 'key', '状态名'))
    .replaceAll('{expected}', booleanLabel(graphConfigString(nodeItem.config, 'expected', 'false')))
    .replaceAll('{missing}', booleanLabel(graphConfigString(nodeItem.config, 'missing', 'false')))
    .replaceAll('{value}', stateValueLabel(graphConfigString(nodeItem.config, 'value'), graphConfigString(nodeItem.config, 'valueType', 'BOOLEAN')))
    .replaceAll('{amount}', graphConfigString(nodeItem.config, 'amount', '1'))
    .replaceAll('{durationSeconds}', graphConfigString(nodeItem.config, 'durationSeconds', '30'))
    .replaceAll('{target}', targetLabel(graphConfigString(nodeItem.config, 'target', 'CURRENT_PLAYER')))
    .replaceAll('{message.plainText}', shortRichText(graphConfigString(nodeItem.config, 'message')))
    .replaceAll('{message}', shortRichText(graphConfigString(nodeItem.config, 'message')))
    .replaceAll('{tag}', graphConfigString(nodeItem.config, 'tag', '标签'));
}

function legacyNodeTypeSummary(nodeItem: GraphNode): string {
  const config = nodeItem.config;
  const value = (key: string, fallback = '') => graphConfigString(config, key, fallback);
  switch (nodeItem.type) {
    case 'MANUAL_TRIGGER':
      return 'WebUI 点击后调用真实后端 API';
    case 'STATE_COMPARE_CONDITION':
      return conditionStateSummary(nodeItem);
    case 'MESSAGE_ACTION':
      return `向当前玩家发送：${richTextPlainText(value('message'))}`;
    case 'ENTITY_HAS_TAG_CONDITION':
      return entityTagConditionSummary(nodeItem);
    case 'PLAYER_IS_ADMIN_CONDITION':
      return playerAdminConditionSummary(nodeItem);
    case 'PLAYER_DIMENSION_CONDITION':
      return playerDimensionConditionSummary(nodeItem);
    case 'PLAYER_IN_REGION_CONDITION':
      return playerRegionConditionSummary(nodeItem);
    case 'PLAYER_Y_COMPARE_CONDITION':
      return playerYCompareConditionSummary(nodeItem);
    case 'TARGET_BLOCK_TYPE_CONDITION':
      return targetBlockTypeConditionSummary(nodeItem);
    case 'TARGET_BLOCK_IN_REGION_CONDITION':
      return targetBlockRegionConditionSummary(nodeItem);
    case 'TARGET_BLOCK_Y_COMPARE_CONDITION':
      return targetBlockYCompareConditionSummary(nodeItem);
    case 'PLAYER_NEAR_TARGET_BLOCK_CONDITION':
      return playerNearTargetBlockConditionSummary(nodeItem);
    case 'CONTROL_LOOP_COUNT':
      return `把内部积木循环 ${value('count', '3')} 次后继续。`;
    case 'CONTROL_LOOP_FOREVER':
      return `持续循环内部积木，每轮间隔 ${value('intervalSeconds', '1')} 秒。`;
    case 'CONTROL_LOOP_UNTIL':
      return `配置了 ${nodeItem.conditionSlots?.length ?? 0} 个结束条件槽。`;
    case 'CONTEXT_ENTITY_EXECUTE_AS':
      return `以「${entityTargetLabel(entityTargetRef(config))}」为上下文执行。`;
    case 'ENTITY_ADD_TAG_ACTION':
      return `给「${entityTargetLabel(entityTargetRef(config))}」添加标签「${value('tag', '标签')}」。`;
    case 'ENTITY_REMOVE_TAG_ACTION':
      return `移除「${entityTargetLabel(entityTargetRef(config))}」的标签「${value('tag', '标签')}」。`;
    case 'ENTITY_DAMAGE_ACTION':
      return `对「${entityTargetLabel(entityTargetRef(config))}」造成 ${value('amount', '4')} 点${damageKindLabel(value('damageKind', 'GENERIC'))}伤害。`;
    case 'ENTITY_HEAL_ACTION':
      return `恢复「${entityTargetLabel(entityTargetRef(config))}」 ${value('amount', '6')} 点生命值。`;
    case 'ENTITY_SET_HEALTH_ACTION':
      return `将「${entityTargetLabel(entityTargetRef(config))}」的生命值设为 ${value('health', '20')}。`;
    case 'ENTITY_KILL_ACTION':
      return `杀死「${entityTargetLabel(entityTargetRef(config))}」。`;
    case 'ENTITY_REMOVE_ACTION':
      return `直接移除「${entityTargetLabel(entityTargetRef(config))}」。`;
    case 'STATE_SET_ACTION':
      return `把“${scopeLabel(value('scope'))}”的 ${value('key', '状态名')} 设置为“${stateValueLabel(value('value'), value('valueType', 'BOOLEAN'))}”。`;
    case 'STATE_ADD_ACTION':
      return `把“${scopeLabel(value('scope'))}”的 ${value('key', '状态名')} 增加 ${value('amount', '1')}。`;
    case 'TIMER_START_ACTION':
      return `等待 ${value('durationSeconds', '30')} 秒后继续。`;
    case 'DEBUG_LOG_ACTION':
      return `记录：${value('message')}`;
    default:
      return nodeItem.id;
  }
}

export function scopeLabel(value = 'PLAYER'): string {
  return stateScopeOptions().find((option) => option.value === value)?.label ?? value;
}

export function booleanLabel(value = 'false'): string {
  return booleanOptions().find((option) => option.value === value)?.label ?? value;
}

function damageKindLabel(value: string): string {
  return ({ GENERIC: '普通', MAGIC: '魔法', FIRE: '火焰', FALL: '摔落', VOID: '虚空' } as Record<string, string>)[value] ?? value;
}

export { conditionOutputModeLabel };

function conditionStateSummary(nodeItem: GraphNode): string {
  const config = nodeItem.config;
  const subject = `「${scopeLabel(graphConfigString(config, 'scope'))}」的 ${graphConfigString(config, 'key', '状态名')}`;
  const expected = `「${booleanLabel(graphConfigString(config, 'expected', 'false'))}」`;
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return `当${subject}等于${expected}时继续。`;
    case 'FAIL_ONLY':
      return `当${subject}不等于${expected}时继续。`;
    case 'BRANCH':
      return `按${subject}是否等于${expected}分成两路。`;
  }
}

function entityTagConditionSummary(nodeItem: GraphNode): string {
  const target = entityTargetLabel(entityTargetRef(nodeItem.config));
  const tag = graphConfigString(nodeItem.config, 'tag', '标签');
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return `当「${target}」拥有标签「${tag}」时继续。`;
    case 'FAIL_ONLY':
      return `当「${target}」没有标签「${tag}」时继续。`;
    case 'BRANCH':
      return `按「${target}」是否拥有标签「${tag}」分开执行。`;
  }
}

function playerAdminConditionSummary(nodeItem: GraphNode): string {
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return '当当前玩家是管理员时继续。';
    case 'FAIL_ONLY':
      return '当不是管理员时继续。';
    case 'BRANCH':
      return '按当前玩家是否为管理员分开执行。';
  }
}

function playerDimensionConditionSummary(nodeItem: GraphNode): string {
  const dimensionId = graphConfigString(nodeItem.config, 'dimensionId', 'minecraft:overworld');
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return `当当前玩家位于维度「${dimensionId}」时继续。`;
    case 'FAIL_ONLY':
      return `当当前玩家不在维度「${dimensionId}」时继续。`;
    case 'BRANCH':
      return `按当前玩家所在维度是否为「${dimensionId}」分开执行。`;
  }
}

function playerRegionConditionSummary(nodeItem: GraphNode): string {
  const regionName = graphConfigString(nodeItem.config, 'regionName', '区域名称');
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return `当当前玩家在区域「${regionName}」内时继续。`;
    case 'FAIL_ONLY':
      return `当区域「${regionName}」不包含当前玩家时继续。`;
    case 'BRANCH':
      return `按当前玩家是否在区域「${regionName}」内分开执行。`;
  }
}

function playerYCompareConditionSummary(nodeItem: GraphNode): string {
  const condition = yCompareLabel(nodeItem);
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return `当当前玩家高度${condition}时继续。`;
    case 'FAIL_ONLY':
      return `当当前玩家高度不满足条件时继续。`;
    case 'BRANCH':
      return `按当前玩家高度是否满足条件分开执行。`;
  }
}

function targetBlockTypeConditionSummary(nodeItem: GraphNode): string {
  const blockId = graphConfigString(nodeItem.config, 'blockId', 'minecraft:stone');
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return `当目标方块为「${blockId}」时继续。`;
    case 'FAIL_ONLY':
      return `当目标方块不为「${blockId}」时继续。`;
    case 'BRANCH':
      return `按目标方块是否为「${blockId}」分开执行。`;
  }
}

function targetBlockYCompareConditionSummary(nodeItem: GraphNode): string {
  const condition = yCompareLabel(nodeItem);
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return `当目标方块高度${condition}时继续。`;
    case 'FAIL_ONLY':
      return `当目标方块高度不满足条件时继续。`;
    case 'BRANCH':
      return `按目标方块高度是否满足条件分开执行。`;
  }
}

function playerNearTargetBlockConditionSummary(nodeItem: GraphNode): string {
  const maxDistance = graphConfigString(nodeItem.config, 'maxDistance', '5');
  const distanceMode = graphConfigString(nodeItem.config, 'horizontalOnly') === 'false' ? '三维距离' : '水平距离';
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return `当玩家${distanceMode}距离目标方块不超过 ${maxDistance} 格时继续。`;
    case 'FAIL_ONLY':
      return `当玩家不靠近目标方块时继续。`;
    case 'BRANCH':
      return `按玩家是否靠近目标方块分开执行。`;
  }
}

function targetBlockRegionConditionSummary(nodeItem: GraphNode): string {
  const regionName = graphConfigString(nodeItem.config, 'regionName', '区域名称');
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return `当目标方块在区域「${regionName}」内时继续。`;
    case 'FAIL_ONLY':
      return `当区域「${regionName}」不包含目标方块时继续。`;
    case 'BRANCH':
      return `按目标方块是否在区域「${regionName}」内分开执行。`;
  }
}

function yCompareLabel(nodeItem: GraphNode): string {
  switch (graphConfigString(nodeItem.config, 'compareMode', 'AT_OR_ABOVE')) {
    case 'AT_OR_BELOW':
      return `不高于 ${graphConfigString(nodeItem.config, 'targetY', '64')}`;
    case 'EQUAL':
      return `等于 ${graphConfigString(nodeItem.config, 'targetY', '64')}`;
    case 'BETWEEN':
      return `在 ${graphConfigString(nodeItem.config, 'minY', '60')} 到 ${graphConfigString(nodeItem.config, 'maxY', '80')} 之间`;
    default:
      return `不低于 ${graphConfigString(nodeItem.config, 'targetY', '64')}`;
  }
}

export function slotLabel(value: string): string {
  switch (value) {
    case 'input':
      return '输入';
    case 'started':
      return '开始';
    case 'pass':
      return '满足';
    case 'fail':
      return '不满足';
    case 'done':
      return '完成';
    case 'tagged':
      return '已添加标签';
    case 'timer_completed':
      return '计时完成';
    default:
      return value;
  }
}

export function stateValueLabel(value: string, valueType: string): string {
  return valueType === 'BOOLEAN' ? booleanLabel(value || 'false') : value || '未填写';
}

export function humanizeTraceMessage(message: string): string {
  return message
    .replace(/\bPLAYER\.([A-Za-z0-9_]+)/g, '玩家状态 $1')
    .replace(/\bGLOBAL\.([A-Za-z0-9_]+)/g, '全局状态 $1')
    .replace(/\bSESSION\.([A-Za-z0-9_]+)/g, '当前会话状态 $1')
    .replace(/\bBOOLEAN\b/g, '是或否')
    .replace(/\bINTEGER\b/g, '数字')
    .replace(/\bSTRING\b/g, '文本')
    .replace(/\btrue\b/g, '是')
    .replace(/\bfalse\b/g, '否');
}
