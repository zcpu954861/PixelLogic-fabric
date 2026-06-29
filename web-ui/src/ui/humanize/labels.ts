import type { BlockKind, FieldOption, GraphNode } from '../../model/graphTypes';
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

export function nodeSummary(nodeItem: GraphNode): string {
  const config = nodeItem.config;
  switch (nodeItem.type) {
    case 'MANUAL_TRIGGER':
      return 'WebUI 点击后调用真实后端 API';
    case 'STATE_COMPARE_CONDITION':
      return `当“${scopeLabel(config.scope)}”的 ${config.key ?? '状态名'} 等于“${booleanLabel(config.expected ?? 'false')}”时，走“通过”分支。`;
    case 'MESSAGE_ACTION':
      return `向模拟玩家显示：${config.message ?? ''}`;
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

export function slotLabel(value: string): string {
  switch (value) {
    case 'input':
      return '输入';
    case 'started':
      return '开始';
    case 'pass':
      return '通过';
    case 'fail':
      return '失败';
    case 'done':
      return '完成';
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