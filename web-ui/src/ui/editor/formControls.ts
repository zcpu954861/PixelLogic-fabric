import { catalogBlock } from '../../model/blockCatalog';
import type { BlockCatalog, CatalogFormField, EditableField, EditorSection, FieldOption, GraphNode } from '../../model/graphTypes';
import { richTextPlainText } from '../../model/richText';
import type { SimulationTestContext } from '../../model/simulationTestContext';
import { escapeAttr, escapeHtml } from '../../utils/dom';
import { booleanLabel, booleanOptions, conditionOutputModeLabel, nodeTypeMetaLabel, stateScopeOptions, targetLabel, valueTypeOptions } from '../humanize/labels';
import { renderRichTextEditor } from './richText/richTextEditor';

export function renderNodeEditor(
  nodeItem: GraphNode,
  catalog: BlockCatalog,
  simulationTestContext?: SimulationTestContext,
  hideOutputMode = false,
): string {
  const section = editorSection(nodeItem, catalog, simulationTestContext);
  const fields = hideOutputMode ? section.fields.filter((field) => field.key !== 'outputMode') : section.fields;

  return `
    <section class="form-card editor-section">
      <b>基础信息</b>
      <div class="field-grid">
        <label class="field-row is-full">名称<input value="${escapeAttr(nodeItem.displayName)}" data-node-field="displayName" /></label>
        <div class="static-meta is-full"><span>积木类型</span><b>${escapeHtml(nodeTypeMetaLabel(nodeItem, catalog))}</b></div>
      </div>
    </section>
    <section class="form-card editor-section">
      <b>${escapeHtml(section.title)}</b>
      ${fields.length > 0 ? `
        <div class="field-grid">
          ${fields.map(renderEditableField).join('')}
        </div>
      ` : '<p class="field-hint">这个积木当前只需要修改名称。</p>'}
    </section>
  `;
}

export function renderEditableField(field: EditableField): string {
  if (field.control === 'hidden') {
    return '';
  }
  if (field.control === 'readonly') {
    return `
      <div class="readonly-field${field.full ? ' is-full' : ''}">
        <span>${escapeHtml(field.label)}</span>
        <b>${escapeHtml(displayFieldValue(field))}</b>
        ${field.description ? `<small>${escapeHtml(field.description)}</small>` : ''}
      </div>
    `;
  }

  const describedBy = field.description ? `field-help-${escapeAttr(field.key)}` : '';
  if (field.control === 'rich_text_component') {
    return `
      <div class="field-row rich-text-row${field.full ? ' is-full' : ''}">
        <span>${escapeHtml(field.label)}</span>
        ${renderRichTextEditor(field, textareaRows(field))}
        ${field.description ? `<small id="${describedBy}">${escapeHtml(field.description)}</small>` : ''}
      </div>
    `;
  }
  const control = renderFieldControl(field, describedBy);
  return `
    <label class="field-row${field.full ? ' is-full' : ''}">${escapeHtml(field.label)}
      ${control}
      ${field.description ? `<small id="${describedBy}">${escapeHtml(field.description)}</small>` : ''}
    </label>
  `;
}

function renderFieldControl(field: EditableField, describedBy: string): string {
  if (field.control === 'select' || field.control === 'scope') {
    return renderSelectField(field, describedBy);
  }
  if (field.control === 'boolean' || field.control === 'segmented') {
    return `
      <div class="segmented-control" role="group" aria-label="${escapeAttr(field.label)}">
        ${fieldOptions(field)
          .map((option) => `
            <button type="button" data-config-key="${escapeAttr(field.key)}" data-config-value="${escapeAttr(option.value)}" aria-pressed="${option.value === field.value}">
              ${escapeHtml(option.label)}
            </button>
          `)
          .join('')}
      </div>
    `;
  }
  if (field.control === 'textarea') {
    return `<textarea rows="${textareaRows(field)}" data-config-key="${escapeAttr(field.key)}" placeholder="${escapeAttr(field.placeholder ?? '')}"${field.required ? ' required' : ''}>${escapeHtml(field.value)}</textarea>`;
  }

  const inputType = field.control === 'number' || field.control === 'integer' ? 'number' : 'text';
  return `
    <span class="input-with-suffix">
      <input
        type="${inputType}"
        value="${escapeAttr(field.value)}"
        data-config-key="${escapeAttr(field.key)}"
        placeholder="${escapeAttr(field.placeholder ?? '')}"
        ${field.min ? ` min="${escapeAttr(field.min)}"` : ''}
        ${field.max ? ` max="${escapeAttr(field.max)}"` : ''}
        ${field.step ? ` step="${escapeAttr(field.step)}"` : ''}
        ${field.required ? ' required' : ''}
        ${describedBy ? ` aria-describedby="${describedBy}"` : ''}
      />
      ${field.suffix ? `<span>${escapeHtml(field.suffix)}</span>` : ''}
    </span>
  `;
}

function renderSelectField(field: EditableField, describedBy: string): string {
  const options = fieldOptions(field);
  const selected = options.find((option) => option.value === field.value) ?? options[0] ?? { value: '', label: field.value || '请选择' };
  return `
    <div class="custom-select" data-custom-select>
      <button
        type="button"
        class="custom-select-trigger"
        data-custom-select-toggle
        aria-haspopup="listbox"
        aria-expanded="false"
        ${field.required ? ' aria-required="true"' : ''}
        ${describedBy ? ` aria-describedby="${describedBy}"` : ''}
      >
        <span>${escapeHtml(selected.label)}</span>
      </button>
      <div class="custom-select-list" role="listbox" hidden>
        ${options
          .map((option) => `
            <button
              type="button"
              class="custom-select-option"
              role="option"
              data-custom-select-option
              data-config-key="${escapeAttr(field.key)}"
              data-config-value="${escapeAttr(option.value)}"
              aria-selected="${option.value === field.value}"
              aria-pressed="${option.value === field.value}"
            >
              ${escapeHtml(option.label)}
            </button>
          `)
          .join('')}
      </div>
    </div>
  `;
}

export function editorSection(nodeItem: GraphNode, catalog: BlockCatalog, simulationTestContext?: SimulationTestContext): EditorSection {
  const blockItem = catalogBlock(catalog, nodeItem.blockId ?? '') ?? uniqueCatalogBlockForNodeType(catalog, nodeItem.type);
  if (blockItem) {
    return { title: blockItem.displayName, fields: schemaDrivenFields(nodeItem, blockItem.formSchema, simulationTestContext) };
  }
  return legacyNodeTypeEditorSection(nodeItem);
}

function uniqueCatalogBlockForNodeType(catalog: BlockCatalog, nodeType: string) {
  const matches = catalog.blocks.filter((blockItem) => blockItem.nodeType === nodeType);
  return matches.length === 1 ? matches[0] : null;
}

function schemaDrivenFields(nodeItem: GraphNode, formSchema: CatalogFormField[], simulationTestContext?: SimulationTestContext): EditableField[] {
  return formSchema
    .filter((field) => field.type !== 'hidden')
    .filter((field) => fieldVisible(field, nodeItem.config, formSchema))
    .map((field) => {
      const value = schemaFieldValue(field, nodeItem);
      const options = fieldOptionsForSchema(field, value, simulationTestContext, nodeItem);
      const control = field.key === 'regionName' && options.length > 0
        ? 'select'
        : field.key === 'value' && nodeItem.config.valueType && nodeItem.config.valueType !== 'BOOLEAN'
        ? nodeItem.config.valueType === 'INTEGER' ? 'integer' : 'string'
        : field.type;
      return {
        label: schemaFieldLabel(field, nodeItem),
        key: field.key,
        value,
        control,
        description: field.description,
        defaultValue: field.defaultValue,
        placeholder: field.placeholder,
        required: field.required,
        options,
        full: field.ui.includes('fullWidth') || field.type === 'rich_text_component' || field.type === 'textarea',
        min: field.min,
        max: field.max,
        step: field.step,
        ui: field.ui,
        suffix: field.suffix,
      };
    });
}

function schemaFieldValue(field: CatalogFormField, nodeItem: GraphNode): string {
  const value = nodeItem.config[field.key] ?? field.defaultValue ?? '';
  if (!isYCompareCondition(nodeItem)) {
    return value;
  }
  if (field.key === 'compareMode' && value === 'AT_OR_BELOW') {
    return 'AT_OR_ABOVE';
  }
  if (field.key === 'outputMode' && ['AT_OR_ABOVE', 'AT_OR_BELOW'].includes(nodeItem.config.compareMode ?? 'AT_OR_ABOVE')) {
    if (value === 'BRANCH') {
      return 'BRANCH';
    }
    return nodeItem.config.compareMode === 'AT_OR_BELOW' ? 'Y_AT_OR_BELOW' : 'Y_AT_OR_ABOVE';
  }
  return value;
}

function schemaFieldLabel(field: CatalogFormField, nodeItem: GraphNode): string {
  if (!isYCompareCondition(nodeItem)) {
    return field.label;
  }
  if (field.key === 'minY') {
    return '最低 Y 值';
  }
  if (field.key === 'maxY') {
    return '最高 Y 值';
  }
  return field.label;
}

function fieldVisible(field: CatalogFormField, config: Record<string, string>, formSchema: CatalogFormField[]): boolean {
  const showWhen = field.ui.split(/\s+/).find((token) => token.startsWith('showWhen:'));
  if (!showWhen) {
    return true;
  }
  const condition = showWhen.slice('showWhen:'.length);
  const [key, rawValues] = condition.split('=');
  if (!key || !rawValues) {
    return true;
  }
  const currentValue = config[key] ?? formSchema.find((schemaField) => schemaField.key === key)?.defaultValue ?? '';
  return rawValues.split(',').includes(currentValue);
}

function fieldOptionsForSchema(field: CatalogFormField, currentValue = '', simulationTestContext?: SimulationTestContext, nodeItem?: GraphNode): FieldOption[] {
  if (field.key === 'regionName') {
    return regionNameOptions(simulationTestContext, currentValue);
  }
  if (field.key === 'compareMode' && isYCompareCondition(nodeItem)) {
    return yCompareModeOptions();
  }
  if (field.key === 'outputMode' && isYCompareCondition(nodeItem)) {
    return yCompareConditionOptions(nodeItem?.config.compareMode);
  }
  if (field.type === 'boolean' || field.type === 'segmented') {
    return field.options.length > 0 ? field.options : booleanOptions();
  }
  if (field.type === 'scope') {
    return field.options.length > 0 ? field.options : stateScopeOptions();
  }
  return field.options;
}

function isYCompareCondition(nodeItem?: GraphNode): boolean {
  return nodeItem?.blockId === 'condition.player.y_compare'
    || nodeItem?.blockId === 'condition.target_block.y_compare'
    || nodeItem?.type === 'PLAYER_Y_COMPARE_CONDITION'
    || nodeItem?.type === 'TARGET_BLOCK_Y_COMPARE_CONDITION';
}

function yCompareConditionOptions(compareMode = 'AT_OR_ABOVE'): FieldOption[] {
  if (compareMode === 'AT_OR_ABOVE' || compareMode === 'AT_OR_BELOW') {
    return [
      { value: 'Y_AT_OR_ABOVE', label: '不低于时继续' },
      { value: 'Y_AT_OR_BELOW', label: '不高于时继续' },
      { value: 'BRANCH', label: '分开执行' },
    ];
  }
  const labels = (() => {
    switch (compareMode) {
      case 'EQUAL':
        return ['等于时继续', '不等于时继续'];
      case 'BETWEEN':
        return ['在范围内时继续', '不在范围内时继续'];
      default:
        return ['不低于时继续', '不高于时继续'];
    }
  })();
  return [
    { value: 'PASS_ONLY', label: labels[0] },
    { value: 'FAIL_ONLY', label: labels[1] },
    { value: 'BRANCH', label: '分开执行' },
  ];
}

function yCompareModeOptions(): FieldOption[] {
  return [
    { value: 'AT_OR_ABOVE', label: '不低于或不高于' },
    { value: 'EQUAL', label: '等于' },
    { value: 'BETWEEN', label: '在范围内' },
  ];
}

function regionNameOptions(simulationTestContext: SimulationTestContext | undefined, currentValue: string): FieldOption[] {
  const seen = new Set<string>();
  const options = (simulationTestContext?.world.regions ?? [])
    .map((region) => region.name.trim())
    .filter((name) => {
      if (!name || seen.has(name)) {
        return false;
      }
      seen.add(name);
      return true;
    })
    .map((name) => ({ value: name, label: name }));
  const value = currentValue.trim();
  return options.length > 0 && value && !seen.has(value) ? [{ value, label: value }, ...options] : options;
}

// legacy fallback: only used when an old/unknown node cannot resolve a catalog BlockDefinition.
function legacyNodeTypeEditorSection(nodeItem: GraphNode): EditorSection {
  switch (nodeItem.type) {
    case 'STATE_COMPARE_CONDITION':
      return { title: '条件设置', fields: legacyNodeTypeEditableFields(nodeItem) };
    case 'STATE_SET_ACTION':
    case 'STATE_ADD_ACTION':
      return { title: '状态设置', fields: legacyNodeTypeEditableFields(nodeItem) };
    case 'MESSAGE_ACTION':
      return { title: '消息内容', fields: legacyNodeTypeEditableFields(nodeItem) };
    case 'TIMER_START_ACTION':
      return { title: '计时设置', fields: legacyNodeTypeEditableFields(nodeItem) };
    case 'DEBUG_LOG_ACTION':
      return { title: '记录内容', fields: legacyNodeTypeEditableFields(nodeItem) };
    default:
      return { title: '配置内容', fields: legacyNodeTypeEditableFields(nodeItem) };
  }
}

function legacyNodeTypeEditableFields(nodeItem: GraphNode): EditableField[] {
  const config = nodeItem.config;
  switch (nodeItem.type) {
    case 'STATE_COMPARE_CONDITION':
      return [
        { label: '作用对象', key: 'scope', value: config.scope ?? 'PLAYER', control: 'scope', options: stateScopeOptions() },
        { label: '状态名', key: 'key', value: config.key ?? '', control: 'string' },
        { label: '目标值', key: 'expected', value: config.expected ?? 'false', control: 'boolean', options: booleanOptions() },
        { label: '缺失时视为', key: 'missing', value: config.missing ?? 'false', control: 'boolean', options: booleanOptions() },
      ];
    case 'MESSAGE_ACTION':
      return [{ label: '消息内容', key: 'message', value: config.message ?? '', control: 'rich_text_component', full: true }];
    case 'DEBUG_LOG_ACTION':
      return [{ label: '记录内容', key: 'message', value: config.message ?? '', control: 'textarea', full: true }];
    case 'STATE_SET_ACTION':
      return [
        { label: '作用对象', key: 'scope', value: config.scope ?? 'PLAYER', control: 'scope', options: stateScopeOptions() },
        { label: '状态名', key: 'key', value: config.key ?? '', control: 'string' },
        { label: '数据类型', key: 'valueType', value: config.valueType ?? 'BOOLEAN', control: 'select', options: valueTypeOptions() },
        { label: '设置为', key: 'value', value: config.value ?? '', control: config.valueType === 'BOOLEAN' ? 'boolean' : 'string', options: booleanOptions() },
      ];
    case 'STATE_ADD_ACTION':
      return [
        { label: '作用对象', key: 'scope', value: config.scope ?? 'PLAYER', control: 'scope', options: stateScopeOptions() },
        { label: '状态名', key: 'key', value: config.key ?? '', control: 'string' },
        { label: '增加数值', key: 'amount', value: config.amount ?? '1', control: 'integer' },
      ];
    case 'TIMER_START_ACTION':
      return [{ label: '等待时间', key: 'durationSeconds', value: config.durationSeconds ?? '30', control: 'integer', suffix: '秒' }];
    default:
      return [];
  }
}

export function nodeConfigItems(
  nodeItem: GraphNode,
  catalog: BlockCatalog,
  hideOutputMode = false,
): Array<{ label: string; value: string }> {
  return editorSection(nodeItem, catalog).fields
    .filter((field) => field.control !== 'hidden')
    .filter((field) => !hideOutputMode || field.key !== 'outputMode')
    .map((field) => ({ label: field.label, value: displayFieldValue(field) }));
}

export function displayFieldValue(field: EditableField): string {
  const value = field.value || field.defaultValue || '';
  if (!value) {
    return '未填写';
  }
  if (field.key === 'target') {
    return targetLabel(value);
  }
  if (field.key === 'outputMode') {
    return fieldOptions(field).find((option) => option.value === value)?.label ?? conditionOutputModeLabel(value);
  }
  if (field.control === 'rich_text_component') {
    return richTextPlainText(value) || '未填写';
  }
  if (field.control === 'boolean' || field.control === 'segmented') {
    return booleanLabel(value);
  }
  if (field.control === 'select' || field.control === 'scope') {
    return fieldOptions(field).find((option) => option.value === value)?.label ?? value;
  }
  return field.suffix ? `${value} ${field.suffix}` : value;
}

function fieldOptions(field: EditableField): FieldOption[] {
  if (field.control === 'boolean' || field.control === 'segmented') {
    return field.options && field.options.length > 0 ? field.options : booleanOptions();
  }
  if (field.control === 'scope') {
    return field.options && field.options.length > 0 ? field.options : stateScopeOptions();
  }
  return field.options ?? [];
}

function textareaRows(field: EditableField): number {
  const match = field.ui?.match(/textareaRows:(\d+)/);
  return match ? Number.parseInt(match[1], 10) : 3;
}
