import type { EditableField } from '../../../model/graphTypes';
import { minecraftColors, normalizeRichText, type RichTextConfig, type RichTextStyle } from '../../../model/richText';
import { escapeAttr, escapeHtml } from '../../../utils/dom';

const styleButtons: Array<{ key: keyof RichTextStyle; label: string; title: string }> = [
  { key: 'bold', label: 'B', title: '加粗' },
  { key: 'italic', label: 'I', title: '斜体' },
  { key: 'underlined', label: 'U', title: '下划线' },
  { key: 'strikethrough', label: 'S', title: '删除线' },
  { key: 'obfuscated', label: '乱', title: '混淆' },
];

export function renderRichTextEditor(field: EditableField, rows: number): string {
  const config = normalizeRichText(field.value);
  return `
    <div class="rich-text-field">
      <div class="rich-text-toolbar" aria-label="文字格式工具">
        <div class="rich-text-colors" aria-label="文字颜色">
          ${minecraftColors.map((color) => color.value ? `
            <button
              type="button"
              class="rich-color-button"
              data-rich-color="${escapeAttr(color.value)}"
              title="${escapeAttr(color.label)}"
              aria-label="${escapeAttr(color.label)}"
              style="--swatch:${escapeAttr(color.css)}"
            ></button>
          ` : `
            <button type="button" class="rich-color-default" data-rich-color="" title="默认颜色" aria-label="默认颜色">默认</button>
          `).join('')}
        </div>
        <div class="rich-text-styles" aria-label="文字样式">
          ${styleButtons.map((button) => `
            <button type="button" data-rich-style="${escapeAttr(button.key)}" title="${escapeAttr(button.title)}" aria-label="${escapeAttr(button.title)}">
              ${escapeHtml(button.label)}
            </button>
          `).join('')}
          <button type="button" data-rich-clear title="清除格式" aria-label="清除格式">清</button>
        </div>
      </div>
      <textarea
        rows="${rows}"
        data-config-key="${escapeAttr(field.key)}"
        data-rich-text="true"
        placeholder="${escapeAttr(field.placeholder ?? '')}"
        ${field.required ? ' required' : ''}
      >${escapeHtml(config.plainText)}</textarea>
      <div class="rich-text-preview">
        <span>预览</span>
        <p data-rich-preview>${renderRichTextPreview(config)}</p>
      </div>
      <small>预览效果与游戏内显示可能略有差异。</small>
    </div>
  `;
}

export function renderRichTextPreview(raw: string | RichTextConfig = ''): string {
  const config = typeof raw === 'string' ? normalizeRichText(raw) : raw;
  if (!config.plainText) {
    return '未填写';
  }
  return config.segments.map((segment) => {
    const style = cssText(segment.style);
    const text = escapeHtml(segment.text).replace(/\n/g, '<br>');
    return style ? `<span style="${escapeAttr(style)}">${text}</span>` : `<span>${text}</span>`;
  }).join('');
}

function cssText(style: RichTextStyle): string {
  const rules: string[] = [];
  const color = minecraftColors.find((item) => item.value === style.color);
  if (color) {
    rules.push(`color:${color.css}`);
  }
  if (style.bold) {
    rules.push('font-weight:700');
  }
  if (style.italic) {
    rules.push('font-style:italic');
  }
  const decorations = [
    style.underlined ? 'underline' : '',
    style.strikethrough ? 'line-through' : '',
  ].filter(Boolean);
  if (decorations.length > 0) {
    rules.push(`text-decoration:${decorations.join(' ')}`);
  }
  if (style.obfuscated) {
    rules.push('font-family:monospace;letter-spacing:0;text-shadow:0 0 2px currentColor');
  }
  return rules.join(';');
}
