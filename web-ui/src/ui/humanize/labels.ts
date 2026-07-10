import { catalogBlock, catalogCategory } from '../../model/blockCatalog';
import { conditionOutputMode, conditionOutputModeLabel } from '../../model/conditionOutputMode';
import type { BlockCatalog, BlockKind, CatalogBlock, FieldOption, GraphNode } from '../../model/graphTypes';
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
  if (type.startsWith('CONTROL_LOOP_')) {
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
      return '条件判断';
    case 'MESSAGE_ACTION':
      return '发送消息';
    case 'PLAYER_HAS_TAG_CONDITION':
    case 'PLAYER_IS_ADMIN_CONDITION':
    case 'PLAYER_DIMENSION_CONDITION':
    case 'PLAYER_IN_REGION_CONDITION':
    case 'PLAYER_Y_COMPARE_CONDITION':
    case 'TARGET_BLOCK_TYPE_CONDITION':
    case 'TARGET_BLOCK_IN_REGION_CONDITION':
    case 'TARGET_BLOCK_Y_COMPARE_CONDITION':
    case 'PLAYER_NEAR_TARGET_BLOCK_CONDITION':
      return '条件判断';
    case 'CONTROL_LOOP_COUNT':
    case 'CONTROL_LOOP_FOREVER':
      return '控制流';
    case 'PLAYER_ADD_TAG_ACTION':
      return '添加玩家标签';
    case 'PLAYER_REMOVE_TAG_ACTION':
      return '移除玩家标签';
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

export function predicateNodeSummary(nodeItem: GraphNode, catalog: BlockCatalog): string {
  const blockItem = catalogBlock(catalog, nodeItem.blockId ?? '')
    ?? catalog.blocks.find((item) => item.nodeType === nodeItem.type);
  const template = blockItem?.predicateSummaryTemplate ?? '';
  if (template) {
    return template
      .replaceAll('{tag}', nodeItem.config.tag || '标签')
      .replaceAll('{dimensionId}', nodeItem.config.dimensionId || 'minecraft:overworld')
      .replaceAll('{regionName}', nodeItem.config.regionName || '区域名称')
      .replaceAll('{blockId}', nodeItem.config.blockId || 'minecraft:stone');
  }
  return nodeSummary(nodeItem, catalog);
}

function catalogSummary(blockItem: CatalogBlock, nodeItem: GraphNode): string {
  if (blockItem.id === 'condition.state.equals') {
    return conditionStateSummary(nodeItem);
  }
  if (blockItem.id === 'condition.player.has_tag') {
    return playerTagConditionSummary(nodeItem);
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
    return `把内部积木循环 ${nodeItem.config.count || '3'} 次后继续。`;
  }
  if (blockItem.id === 'control.loop.forever') {
    return `持续循环内部积木，每轮间隔 ${nodeItem.config.intervalSeconds || '1'} 秒。`;
  }
  if (blockItem.id === 'control.loop.until') {
    return `配置了 ${nodeItem.conditionSlots?.length ?? 0} 个结束条件槽。`;
  }
  const template = blockItem.summaryTemplate;
  if (!template) {
    return legacyNodeTypeSummary(nodeItem);
  }
  return template
    .replaceAll('{scope}', scopeLabel(nodeItem.config.scope))
    .replaceAll('{key}', nodeItem.config.key || '状态名')
    .replaceAll('{expected}', booleanLabel(nodeItem.config.expected ?? 'false'))
    .replaceAll('{missing}', booleanLabel(nodeItem.config.missing ?? 'false'))
    .replaceAll('{value}', stateValueLabel(nodeItem.config.value ?? '', nodeItem.config.valueType ?? 'BOOLEAN'))
    .replaceAll('{amount}', nodeItem.config.amount ?? '1')
    .replaceAll('{durationSeconds}', nodeItem.config.durationSeconds ?? '30')
    .replaceAll('{target}', targetLabel(nodeItem.config.target ?? 'CURRENT_PLAYER'))
    .replaceAll('{message.plainText}', shortRichText(nodeItem.config.message ?? ''))
    .replaceAll('{message}', shortRichText(nodeItem.config.message ?? ''))
    .replaceAll('{tag}', nodeItem.config.tag ?? '标签');
}

function legacyNodeTypeSummary(nodeItem: GraphNode): string {
  const config = nodeItem.config;
  switch (nodeItem.type) {
    case 'MANUAL_TRIGGER':
      return 'WebUI 点击后调用真实后端 API';
    case 'STATE_COMPARE_CONDITION':
      return conditionStateSummary(nodeItem);
    case 'MESSAGE_ACTION':
      return `向当前玩家发送：${richTextPlainText(config.message ?? '')}`;
    case 'PLAYER_HAS_TAG_CONDITION':
      return playerTagConditionSummary(nodeItem);
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
      return `把内部积木循环 ${config.count ?? '3'} 次后继续。`;
    case 'CONTROL_LOOP_FOREVER':
      return `持续循环内部积木，每轮间隔 ${config.intervalSeconds ?? '1'} 秒。`;
    case 'CONTROL_LOOP_UNTIL':
      return `配置了 ${nodeItem.conditionSlots?.length ?? 0} 个结束条件槽。`;
    case 'PLAYER_ADD_TAG_ACTION':
      return `给当前玩家添加标签“${config.tag ?? '标签'}”。`;
    case 'PLAYER_REMOVE_TAG_ACTION':
      return `移除当前玩家的标签“${config.tag ?? '标签'}”。`;
    case 'STATE_SET_ACTION':
      return `把“${scopeLabel(config.scope)}”的 ${config.key ?? '状态名'} 设置为“${stateValueLabel(config.value ?? '', config.valueType ?? 'BOOLEAN')}”。`;
    case 'STATE_ADD_ACTION':
      return `把“${scopeLabel(config.scope)}”的 ${config.key ?? '状态名'} 增加 ${config.amount ?? '1'}。`;
    case 'TIMER_START_ACTION':
      return `等待 ${config.durationSeconds ?? '30'} 秒后继续。`;
    case 'DEBUG_LOG_ACTION':
      return `记录：${config.message ?? ''}`;
    default:
      return nodeItem.id;
  }
}

export function scopeLabel(value = 'PLAYER'): string {
  return stateScopeOptions().find((option) => option.value === value)?.label ?? value;
}

export function valueTypeLabel(value = 'BOOLEAN'): string {
  return valueTypeOptions().find((option) => option.value === value)?.label ?? value;
}

export function booleanLabel(value = 'false'): string {
  return booleanOptions().find((option) => option.value === value)?.label ?? value;
}

export { conditionOutputModeLabel };

function conditionStateSummary(nodeItem: GraphNode): string {
  const config = nodeItem.config;
  const subject = `「${scopeLabel(config.scope)}」的 ${config.key || '状态名'}`;
  const expected = `「${booleanLabel(config.expected ?? 'false')}」`;
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return `当${subject}等于${expected}时继续。`;
    case 'FAIL_ONLY':
      return `当${subject}不等于${expected}时继续。`;
    case 'BRANCH':
      return `按${subject}是否等于${expected}分成两路。`;
  }
}

function playerTagConditionSummary(nodeItem: GraphNode): string {
  const tag = nodeItem.config.tag || '标签';
  switch (conditionOutputMode(nodeItem)) {
    case 'PASS_ONLY':
      return `当拥有标签「${tag}」时继续。`;
    case 'FAIL_ONLY':
      return `当当前玩家不拥有标签「${tag}」时继续。`;
    case 'BRANCH':
      return `按当前玩家是否拥有标签「${tag}」分开执行。`;
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
  const dimensionId = nodeItem.config.dimensionId || 'minecraft:overworld';
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
  const regionName = nodeItem.config.regionName || '区域名称';
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
  const blockId = nodeItem.config.blockId || 'minecraft:stone';
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
  const maxDistance = nodeItem.config.maxDistance || '5';
  const distanceMode = nodeItem.config.horizontalOnly === 'false' ? '三维距离' : '水平距离';
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
  const regionName = nodeItem.config.regionName || '区域名称';
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
  switch (nodeItem.config.compareMode || 'AT_OR_ABOVE') {
    case 'AT_OR_BELOW':
      return `不高于 ${nodeItem.config.targetY || '64'}`;
    case 'EQUAL':
      return `等于 ${nodeItem.config.targetY || '64'}`;
    case 'BETWEEN':
      return `在 ${nodeItem.config.minY || '60'} 到 ${nodeItem.config.maxY || '80'} 之间`;
    default:
      return `不低于 ${nodeItem.config.targetY || '64'}`;
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
