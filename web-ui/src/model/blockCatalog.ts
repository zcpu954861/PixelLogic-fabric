import type { BlockCatalog, BlockKind, CatalogBlock, CatalogCategory, CatalogSubcategory, FieldOption, GraphNode, GraphPosition, GraphSlot } from './graphTypes';
import { richTextConfig } from './richText';

const category = (id: string, displayName: string, description: string, order: number): CatalogCategory => ({
  id,
  displayName,
  description,
  order,
  visibleByDefault: true,
});

const subcategory = (id: string, categoryId: string, displayName: string, description: string, order: number): CatalogSubcategory => ({
  id,
  categoryId,
  displayName,
  description,
  order,
});

const option = (value: string, label: string): FieldOption => ({ value, label });
const input = (id: string): GraphSlot => ({ id, direction: 'INPUT', edgeType: 'CONTROL' });
const out = (id: string): GraphSlot => ({ id, direction: 'OUTPUT', edgeType: 'CONTROL' });

const field = (
  key: string,
  label: string,
  type: CatalogBlock['formSchema'][number]['type'],
  options: FieldOption[] = [],
  ui = '',
  suffix = '',
) => ({
  key,
  type,
  label,
  description: '',
  defaultValue: '',
  placeholder: '',
  options,
  required: true,
  min: '',
  max: '',
  step: '',
  ui,
  suffix,
});

const readonlyField = (key: string, label: string, defaultValue: string, description = ''): CatalogBlock['formSchema'][number] => ({
  key,
  type: 'readonly',
  label,
  description,
  defaultValue,
  placeholder: '',
  options: [],
  required: false,
  min: '',
  max: '',
  step: '',
  ui: 'readonlyBadge',
  suffix: '',
});

const hiddenField = (key: string, defaultValue: string): CatalogBlock['formSchema'][number] => ({
  key,
  type: 'hidden',
  label: key,
  description: '',
  defaultValue,
  placeholder: '',
  options: [],
  required: false,
  min: '',
  max: '',
  step: '',
  ui: '',
  suffix: '',
});

const block = (
  id: string,
  displayName: string,
  description: string,
  categoryId: string,
  subcategoryId: string,
  nodeKind: BlockKind,
  nodeType: string,
  defaultConfig: Record<string, string>,
  inputSlots: GraphSlot[],
  outputSlots: GraphSlot[],
  formSchema = [] as CatalogBlock['formSchema'],
  summaryTemplate = '',
  summaryFormatter = '',
  simulationCapability = 'FULLY_SIMULATABLE',
  safetyFlags = ['READ_ONLY'],
  aliases = [] as string[],
): CatalogBlock => ({
  id,
  version: 1,
  displayName,
  description,
  categoryId,
  subcategoryId,
  tags: [],
  nodeKind,
  nodeType,
  defaultConfig,
  formSchema,
  summaryTemplate,
  summaryFormatter,
  inputSlots,
  outputSlots,
  simulationCapability,
  mcCapability: 'REQUIRES_MINECRAFT_RUNTIME',
  safetyFlags,
  deprecated: false,
  hidden: false,
  aliases,
});

export const fallbackCatalog: BlockCatalog = {
  categories: [
    category('trigger', '触发事件', '从玩家操作或测试入口开始一条逻辑流。', 10),
    category('condition', '条件判断', '按状态或上下文决定走哪条分支。', 20),
    category('message', '消息显示', '向玩家或调试视图展示文本反馈。', 30),
    category('state', '状态数据', '读取或修改流程运行时状态。', 40),
    category('timer', '时间调度', '等待一段时间后继续流程。', 50),
    category('debug', '调试诊断', '记录测试和排查信息。', 60),
  ],
  subcategories: [
    subcategory('trigger.manual', 'trigger', '手动测试', '用于 WebUI 和本地验证的测试入口。', 10),
    subcategory('condition.state', 'condition', '状态条件', '基于玩家、全局或会话状态做判断。', 10),
    subcategory('message.player', 'message', '玩家消息', '面向玩家的文本反馈。', 10),
    subcategory('state.write', 'state', '写入状态', '设置或累加状态值。', 10),
    subcategory('timer.basic', 'timer', '基础等待', '等待后继续执行。', 10),
    subcategory('debug.basic', 'debug', '调试输出', '记录模拟执行信息。', 10),
  ],
  blocks: [
    block('trigger.manual_test', 'WebUI 测试运行', '点击测试运行时进入这条流程。', 'trigger', 'trigger.manual', 'trigger', 'MANUAL_TRIGGER', {}, [], [out('started')], [
      readonlyField('triggerType', '积木类型', '手动测试触发', '测试运行从这里进入流程。'),
    ], '手动测试触发入口。', '', 'FULLY_SIMULATABLE', ['READ_ONLY'], ['manual.test.start']),
    block('condition.state.equals', '判断状态是否等于', '比较一个状态值，按通过或失败继续。', 'condition', 'condition.state', 'condition', 'STATE_COMPARE_CONDITION', { scope: 'PLAYER', key: 'started', valueType: 'BOOLEAN', expected: 'false', missing: 'false' }, [input('input')], [out('pass'), out('fail')], [
      field('scope', '作用对象', 'scope', [option('PLAYER', '玩家'), option('GLOBAL', '全局'), option('SESSION', '当前会话')]),
      field('key', '状态名', 'string', [], 'fullWidth'),
      hiddenField('valueType', 'BOOLEAN'),
      field('expected', '目标值', 'boolean', [option('true', '是'), option('false', '否')], 'segmented'),
      field('missing', '缺失时视为', 'boolean', [option('true', '是'), option('false', '否')], 'segmented'),
    ], '当「{scope}」的 {key} 等于「{expected}」时走通过分支。', 'condition.state.equals'),
    block('action.message.chat', '发送聊天消息', '向当前玩家或模拟玩家发送一条 vanilla text component 语义消息。', 'message', 'message.player', 'action', 'MESSAGE_ACTION', { target: 'CURRENT_PLAYER', message: richTextConfig('新消息') }, [input('input')], [out('done')], [
      readonlyField('target', '接收者', 'CURRENT_PLAYER', '当前发送给触发这条流程的玩家或 WebUI 模拟玩家。'),
      field('message', '消息内容', 'rich_text_component', [], 'fullWidth textareaRows:4'),
    ], '向「{target}」发送「{message.plainText}」。', 'action.message.chat', 'APPROXIMATE_SIMULATION', ['PLAYER_MUTATING', 'REQUIRES_PLAYER']),
    block('state.set', '设置状态', '把一个状态写成指定值。', 'state', 'state.write', 'state', 'STATE_SET_ACTION', { scope: 'PLAYER', key: 'started', valueType: 'BOOLEAN', value: 'true' }, [input('input')], [out('done')], [
      field('scope', '作用对象', 'scope', [option('PLAYER', '玩家'), option('GLOBAL', '全局'), option('SESSION', '当前会话')]),
      field('key', '状态名', 'string'),
      field('valueType', '数据类型', 'select', [option('BOOLEAN', '是或否'), option('INTEGER', '数字'), option('STRING', '文本')]),
      field('value', '设置为', 'segmented', [option('true', '是'), option('false', '否')], 'segmented'),
    ], '把「{scope}」的 {key} 设置为「{value}」。', 'state.set', 'FULLY_SIMULATABLE', ['STATE_MUTATING']),
    block('state.add', '累加状态', '把数字状态增加指定数值。', 'state', 'state.write', 'state', 'STATE_ADD_ACTION', { scope: 'PLAYER', key: 'start_count', valueType: 'INTEGER', amount: '1' }, [input('input')], [out('done')], [
      field('scope', '作用对象', 'scope', [option('PLAYER', '玩家'), option('GLOBAL', '全局'), option('SESSION', '当前会话')]),
      field('key', '状态名', 'string'),
      hiddenField('valueType', 'INTEGER'),
      { ...field('amount', '增加数值', 'integer'), min: '-999999', max: '999999', step: '1' },
    ], '把「{scope}」的 {key} 增加 {amount}。', 'state.add', 'FULLY_SIMULATABLE', ['STATE_MUTATING']),
    block('timer.wait', '等待一段时间', '等待指定秒数后继续执行。', 'timer', 'timer.basic', 'timer', 'TIMER_START_ACTION', { durationSeconds: '30' }, [input('input')], [out('timer_completed')], [
      { ...field('durationSeconds', '等待时间', 'integer', [], '', '秒'), min: '1', max: '86400', step: '1' },
    ], '等待 {durationSeconds} 秒后继续。', 'timer.wait'),
    block('debug.log', '调试记录', '在模拟执行记录里写入一条调试信息。', 'debug', 'debug.basic', 'debug', 'DEBUG_LOG_ACTION', { message: '调试记录' }, [input('input')], [out('done')], [
      field('message', '记录内容', 'textarea', [], 'fullWidth textareaRows:3'),
    ], '记录：{message}', 'debug.log'),
  ],
};

export function catalogCategories(catalog: BlockCatalog): CatalogCategory[] {
  return catalog.categories
    .filter((categoryItem) => categoryItem.visibleByDefault)
    .slice()
    .sort((left, right) => left.order - right.order || left.displayName.localeCompare(right.displayName, 'zh-CN'));
}

export function catalogBlocksForCategory(catalog: BlockCatalog, categoryId: string): CatalogBlock[] {
  const subcategoryOrder = new Map(catalog.subcategories.map((item) => [item.id, item.order]));
  return catalog.blocks
    .filter((blockItem) => !blockItem.hidden && !blockItem.deprecated && blockItem.categoryId === categoryId)
    .slice()
    .sort((left, right) => (subcategoryOrder.get(left.subcategoryId) ?? 0) - (subcategoryOrder.get(right.subcategoryId) ?? 0)
      || left.displayName.localeCompare(right.displayName, 'zh-CN'));
}

export function catalogBlock(catalog: BlockCatalog, blockId: string): CatalogBlock | null {
  return catalog.blocks.find((blockItem) => blockItem.id === blockId || blockItem.aliases.includes(blockId)) ?? null;
}

export function catalogCategory(catalog: BlockCatalog, categoryId: string): CatalogCategory | null {
  return catalog.categories.find((categoryItem) => categoryItem.id === categoryId) ?? null;
}

export function blockKindFromCatalogBlock(blockItem: CatalogBlock): BlockKind {
  return isBlockKind(blockItem.nodeKind) ? blockItem.nodeKind : blockKindFromNodeType(blockItem.nodeType);
}

export function createCatalogNode(blockItem: CatalogBlock, id: string, position: GraphPosition): GraphNode {
  return {
    id,
    type: blockItem.nodeType,
    blockId: blockItem.id,
    displayName: blockItem.displayName,
    config: { ...blockItem.defaultConfig },
    position,
    slots: [...blockItem.inputSlots, ...blockItem.outputSlots].map((slot) => ({ ...slot })),
  };
}

export function catalogNodeIdPrefix(blockItem: CatalogBlock): string {
  return blockItem.id.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '') || 'block';
}

function isBlockKind(value: string): value is BlockKind {
  return ['trigger', 'condition', 'action', 'state', 'timer', 'debug'].includes(value);
}

function blockKindFromNodeType(type: string): BlockKind {
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
