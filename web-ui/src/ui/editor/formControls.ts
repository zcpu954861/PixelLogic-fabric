import { catalogBlock } from '../../model/blockCatalog';
import type { BlockCatalog, CatalogFormField, EditableField, EditorSection, FieldOption, GraphNode } from '../../model/graphTypes';
import { richTextPlainText } from '../../model/richText';
import { escapeAttr, escapeHtml } from '../../utils/dom';
import { booleanLabel, booleanOptions, conditionOutputModeLabel, nodeTypeLabel, stateScopeOptions, targetLabel, valueTypeOptions } from '../humanize/labels';

export function renderNodeEditor(nodeItem: GraphNode, catalog: BlockCatalog): string {
  const section = editorSection(nodeItem, catalog);

  return `
    <section class="form-card editor-section">
      <b>基础信息</b>
      <div class="field-grid">
        <label class="field-row is-full">名称<input value="${escapeAttr(nodeItem.displayName)}" data-node-field="displayName" /></label>
        <div class="readonly-field"><span>积木类型</span><b>${escapeHtml(catalogBlock(catalog, nodeItem.blockId ?? '')?.displayName ?? nodeTypeLabel(nodeItem.type))}</b></div>
      </div>
    </section>
    <section class="form-card editor-section">
      <b>${escapeHtml(section.title)}</b>
      ${section.fields.length > 0 ? `
        <div class="field-grid">
          ${section.fields.map(renderEditableField).join('')}
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
    return `
      <select data-config-key="${escapeAttr(field.key)}"${field.required ? ' required' : ''}${describedBy ? ` aria-describedby="${describedBy}"` : ''}>
        ${fieldOptions(field)
          .map((option) => `<option value="${escapeAttr(option.value)}"${option.value === field.value ? ' selected' : ''}>${escapeHtml(option.label)}</option>`)
          .join('')}
      </select>
    `;
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
  if (field.control === 'rich_text_component') {
    const plainText = richTextPlainText(field.value);
    return `
      <div class="rich-text-field">
        <textarea rows="${textareaRows(field)}" data-config-key="${escapeAttr(field.key)}" data-rich-text="true" placeholder="${escapeAttr(field.placeholder ?? '')}"${field.required ? ' required' : ''}>${escapeHtml(plainText)}</textarea>
        <div class="rich-text-preview">
          <span>预览</span>
          <p data-rich-preview>${escapeHtml(plainText || '未填写')}</p>
        </div>
        <small>当前为富文本基础模式，保存为 vanilla text component 语义；后续可扩展颜色、格式和变量。</small>
      </div>
    `;
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

export function editorSection(nodeItem: GraphNode, catalog: BlockCatalog): EditorSection {
  const blockItem = catalogBlock(catalog, nodeItem.blockId ?? '');
  if (blockItem) {
    return { title: blockItem.displayName, fields: schemaDrivenFields(nodeItem, blockItem.formSchema) };
  }
  return legacyNodeTypeEditorSection(nodeItem);
}

function schemaDrivenFields(nodeItem: GraphNode, formSchema: CatalogFormField[]): EditableField[] {
  return formSchema
    .filter((field) => field.type !== 'hidden')
    .map((field) => {
      const value = nodeItem.config[field.key] ?? field.defaultValue ?? '';
      const control = field.key === 'value' && nodeItem.config.valueType && nodeItem.config.valueType !== 'BOOLEAN'
        ? nodeItem.config.valueType === 'INTEGER' ? 'integer' : 'string'
        : field.type;
      return {
        label: field.label,
        key: field.key,
        value,
        control,
        description: field.description,
        defaultValue: field.defaultValue,
        placeholder: field.placeholder,
        required: field.required,
        options: fieldOptionsForSchema(field),
        full: field.ui.includes('fullWidth') || field.type === 'rich_text_component' || field.type === 'textarea',
        min: field.min,
        max: field.max,
        step: field.step,
        ui: field.ui,
        suffix: field.suffix,
      };
    });
}

function fieldOptionsForSchema(field: CatalogFormField): FieldOption[] {
  if (field.type === 'boolean' || field.type === 'segmented') {
    return field.options.length > 0 ? field.options : booleanOptions();
  }
  if (field.type === 'scope') {
    return field.options.length > 0 ? field.options : stateScopeOptions();
  }
  return field.options;
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

export function nodeConfigItems(nodeItem: GraphNode, catalog: BlockCatalog): Array<{ label: string; value: string }> {
  return editorSection(nodeItem, catalog).fields
    .filter((field) => field.control !== 'hidden')
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
    return conditionOutputModeLabel(value);
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
