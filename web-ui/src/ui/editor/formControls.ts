import type { EditableField, EditorSection, GraphNode } from '../../model/graphTypes';
import { escapeAttr, escapeHtml } from '../../utils/dom';
import { booleanLabel, booleanOptions, nodeTypeLabel, stateScopeOptions, valueTypeOptions } from '../humanize/labels';

export function renderNodeEditor(nodeItem: GraphNode): string {
  const section = editorSection(nodeItem);

  return `
    <section class="form-card editor-section">
      <b>基础信息</b>
      <div class="field-grid">
        <label class="field-row is-full">名称<input value="${escapeAttr(nodeItem.displayName)}" data-node-field="displayName" /></label>
        <div class="readonly-field"><span>积木类型</span><b>${escapeHtml(nodeTypeLabel(nodeItem.type))}</b></div>
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
  const inputType = field.control === 'number' ? 'number' : 'text';
  const control = field.control === 'select'
    ? `
      <select data-config-key="${escapeAttr(field.key)}">
        ${(field.options ?? [])
          .map((option) => `<option value="${escapeAttr(option.value)}"${option.value === field.value ? ' selected' : ''}>${escapeHtml(option.label)}</option>`)
          .join('')}
      </select>
    `
    : field.control === 'boolean'
      ? `
        <div class="segmented-control" role="group" aria-label="${escapeAttr(field.label)}">
          ${booleanOptions()
            .map((option) => `
              <button type="button" data-config-key="${escapeAttr(field.key)}" data-config-value="${escapeAttr(option.value)}" aria-pressed="${option.value === field.value}">
                ${escapeHtml(option.label)}
              </button>
            `)
            .join('')}
        </div>
      `
      : `
        <span class="input-with-suffix">
          <input type="${inputType}" value="${escapeAttr(field.value)}" data-config-key="${escapeAttr(field.key)}" />
          ${field.suffix ? `<span>${escapeHtml(field.suffix)}</span>` : ''}
        </span>
      `;

  return `
    <label class="field-row${field.full ? ' is-full' : ''}">${escapeHtml(field.label)}
      ${control}
    </label>
  `;
}

export function editorSection(nodeItem: GraphNode): EditorSection {
  switch (nodeItem.type) {
    case 'STATE_COMPARE_CONDITION':
      return { title: '条件设置', fields: editableFields(nodeItem) };
    case 'STATE_SET_ACTION':
    case 'STATE_ADD_ACTION':
      return { title: '状态设置', fields: editableFields(nodeItem) };
    case 'MESSAGE_ACTION':
      return { title: '消息内容', fields: editableFields(nodeItem) };
    case 'TIMER_START_ACTION':
      return { title: '计时设置', fields: editableFields(nodeItem) };
    case 'DEBUG_LOG_ACTION':
      return { title: '记录内容', fields: editableFields(nodeItem) };
    default:
      return { title: '配置内容', fields: editableFields(nodeItem) };
  }
}

export function editableFields(nodeItem: GraphNode): EditableField[] {
  const config = nodeItem.config;
  switch (nodeItem.type) {
    case 'STATE_COMPARE_CONDITION':
      return [
        { label: '作用对象', key: 'scope', value: config.scope ?? 'PLAYER', control: 'select', options: stateScopeOptions() },
        { label: '状态名', key: 'key', value: config.key ?? '', control: 'text' },
        { label: '目标值', key: 'expected', value: config.expected ?? 'false', control: 'boolean' },
        { label: '缺失时视为', key: 'missing', value: config.missing ?? 'false', control: 'boolean' },
      ];
    case 'MESSAGE_ACTION':
      return [{ label: '消息', key: 'message', value: config.message ?? '', control: 'text', full: true }];
    case 'DEBUG_LOG_ACTION':
      return [{ label: '内容', key: 'message', value: config.message ?? '', control: 'text', full: true }];
    case 'STATE_SET_ACTION':
      return [
        { label: '作用对象', key: 'scope', value: config.scope ?? 'PLAYER', control: 'select', options: stateScopeOptions() },
        { label: '状态名', key: 'key', value: config.key ?? '', control: 'text' },
        { label: '数据类型', key: 'valueType', value: config.valueType ?? 'BOOLEAN', control: 'select', options: valueTypeOptions() },
        { label: '设置为', key: 'value', value: config.value ?? '', control: config.valueType === 'BOOLEAN' ? 'boolean' : 'text' },
      ];
    case 'STATE_ADD_ACTION':
      return [
        { label: '作用对象', key: 'scope', value: config.scope ?? 'PLAYER', control: 'select', options: stateScopeOptions() },
        { label: '状态名', key: 'key', value: config.key ?? '', control: 'text' },
        { label: '增加数值', key: 'amount', value: config.amount ?? '1', control: 'number' },
      ];
    case 'TIMER_START_ACTION':
      return [{ label: '等待时间', key: 'durationSeconds', value: config.durationSeconds ?? '30', control: 'number', suffix: '秒' }];
    default:
      return [];
  }
}

export function nodeConfigItems(nodeItem: GraphNode): Array<{ label: string; value: string }> {
  return editableFields(nodeItem).map((field) => ({ label: field.label, value: displayFieldValue(field) }));
}

export function displayFieldValue(field: EditableField): string {
  if (!field.value) {
    return '未填写';
  }
  if (field.control === 'boolean') {
    return booleanLabel(field.value);
  }
  if (field.control === 'select') {
    return field.options?.find((option) => option.value === field.value)?.label ?? field.value;
  }
  return field.suffix ? `${field.value} ${field.suffix}` : field.value;
}