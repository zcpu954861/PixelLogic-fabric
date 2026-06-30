import { catalogBlock } from '../../model/blockCatalog';
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
      return '玩家标签判断';
    case 'PLAYER_ADD_TAG_ACTION':
      return '添加玩家标签';
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

export function nodeSummary(nodeItem: GraphNode, catalog?: BlockCatalog): string {
  const blockItem = catalog ? catalogBlock(catalog, nodeItem.blockId ?? '') : null;
  if (blockItem) {
    return catalogSummary(blockItem, nodeItem);
  }
  return legacyNodeTypeSummary(nodeItem);
}

function catalogSummary(blockItem: CatalogBlock, nodeItem: GraphNode): string {
  if (blockItem.id === 'condition.state.equals') {
    return conditionStateSummary(nodeItem);
  }
  if (blockItem.id === 'condition.player.has_tag') {
    return playerTagConditionSummary(nodeItem);
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
    case 'PLAYER_ADD_TAG_ACTION':
      return `给当前玩家添加标签“${config.tag ?? '标签'}”。`;
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
      return `当当前玩家拥有标签「${tag}」时继续。`;
    case 'FAIL_ONLY':
      return `当当前玩家没有标签「${tag}」时继续。`;
    case 'BRANCH':
      return `按当前玩家是否拥有标签「${tag}」分成两路。`;
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
