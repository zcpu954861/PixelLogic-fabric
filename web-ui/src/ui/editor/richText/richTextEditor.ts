import type { EditableField } from '../../../model/graphTypes';
import {
  minecraftColors,
  normalizeRichText,
  richTextStyleKeys,
  type MinecraftColor,
  type RichTextConfig,
  type RichTextSegment,
  type RichTextStyle,
  type RichTextStyleKey,
} from '../../../model/richText';
import { escapeAttr, escapeHtml } from '../../../utils/dom';

const styleButtons: Array<{ key: RichTextStyleKey; label: string; title: string; className: string }> = [
  { key: 'bold', label: 'B', title: '加粗', className: 'rich-style-bold' },
  { key: 'italic', label: 'I', title: '斜体', className: 'rich-style-italic' },
  { key: 'underlined', label: 'U', title: '下划线', className: 'rich-style-underlined' },
  { key: 'strikethrough', label: 'S', title: '删除线', className: 'rich-style-strikethrough' },
  { key: 'obfuscated', label: '乱', title: '混淆', className: 'rich-style-obfuscated' },
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
              aria-pressed="false"
              style="--swatch:${escapeAttr(color.css)}"
            ></button>
          ` : `
            <button type="button" class="rich-color-default" data-rich-color="" title="默认颜色" aria-label="默认颜色" aria-pressed="false">默认</button>
          `).join('')}
        </div>
        <div class="rich-text-styles" aria-label="文字样式">
          ${styleButtons.map((button) => `
            <button
              type="button"
              class="rich-style-button ${escapeAttr(button.className)}"
              data-rich-style="${escapeAttr(button.key)}"
              title="${escapeAttr(button.title)}"
              aria-label="${escapeAttr(button.title)}"
              aria-pressed="false"
            >
              ${escapeHtml(button.label)}
            </button>
          `).join('')}
        </div>
      </div>
      <div
        class="rich-text-editor"
        contenteditable="true"
        role="textbox"
        aria-multiline="true"
        data-config-key="${escapeAttr(field.key)}"
        data-rich-editor="true"
        data-rich-required="${field.required ? 'true' : 'false'}"
        data-placeholder="${escapeAttr(field.placeholder ?? '')}"
        style="min-height:${Math.max(86, rows * 22 + 20)}px"
      >${renderRichTextContent(config)}</div>
    </div>
  `;
}

export function renderRichTextContent(raw: string | RichTextConfig = ''): string {
  const config = typeof raw === 'string' ? normalizeRichText(raw) : raw;
  if (!config.plainText) {
    return '';
  }
  return config.segments.map((segment) => {
    const style = cssText(segment.style);
    const attrs = segmentAttrs(segment.style);
    const text = escapeHtml(segment.text);
    return `<span${attrs}${style ? ` style="${escapeAttr(style)}"` : ''}>${text}</span>`;
  }).join('');
}

export function richTextSegmentsFromEditor(editorEl: HTMLElement): RichTextSegment[] {
  const segments: RichTextSegment[] = [];

  const visit = (node: Node, inheritedStyle: RichTextStyle): void => {
    if (node.nodeType === Node.TEXT_NODE) {
      const text = node.nodeValue ?? '';
      if (text) {
        segments.push({ text, style: { ...inheritedStyle } });
      }
      return;
    }
    if (!(node instanceof HTMLElement)) {
      return;
    }
    if (node instanceof HTMLBRElement) {
      segments.push({ text: '\n', style: { ...inheritedStyle } });
      return;
    }
    const style = mergeElementStyle(inheritedStyle, node);
    node.childNodes.forEach((child) => visit(child, style));
  };

  editorEl.childNodes.forEach((child) => visit(child, {}));
  return segments;
}

export function richTextSelectionOffsets(editorEl: HTMLElement): { start: number; end: number } | null {
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0) {
    return null;
  }
  const range = selection.getRangeAt(0);
  if (!editorEl.contains(range.commonAncestorContainer)) {
    return null;
  }
  const start = richTextOffsetFromBoundary(editorEl, range.startContainer, range.startOffset);
  const end = richTextOffsetFromBoundary(editorEl, range.endContainer, range.endOffset);
  return {
    start: Math.min(start, end),
    end: Math.max(start, end),
  };
}

export function setRichTextSelectionOffsets(editorEl: HTMLElement, start: number, end: number): void {
  const selection = window.getSelection();
  if (!selection) {
    return;
  }
  const range = document.createRange();
  const startBoundary = richTextBoundaryAtOffset(editorEl, start);
  const endBoundary = richTextBoundaryAtOffset(editorEl, end);
  range.setStart(startBoundary.node, startBoundary.offset);
  range.setEnd(endBoundary.node, endBoundary.offset);
  selection.removeAllRanges();
  selection.addRange(range);
  editorEl.focus();
}

export function insertTextAtRichTextSelection(editorEl: HTMLElement, text: string): boolean {
  if (!text) {
    return false;
  }
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0) {
    return false;
  }
  const range = selection.getRangeAt(0);
  if (!editorEl.contains(range.commonAncestorContainer)) {
    return false;
  }
  range.deleteContents();
  const textNode = document.createTextNode(text);
  range.insertNode(textNode);
  range.setStart(textNode, text.length);
  range.setEnd(textNode, text.length);
  selection.removeAllRanges();
  selection.addRange(range);
  editorEl.focus();
  return true;
}

function cssText(style: RichTextStyle): string {
  const rules: string[] = [];
  const color = minecraftColors.find((item) => item.value === style.color);
  if (color) {
    rules.push(`color:${color.css}`);
  }
  if (style.bold) {
    rules.push('font-weight:950');
  }
  if (style.italic) {
    rules.push('font-style:italic;font-family:Georgia,"Times New Roman",serif');
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

function segmentAttrs(style: RichTextStyle): string {
  const attrs: string[] = [];
  if (style.color) {
    attrs.push(`data-rich-color="${escapeAttr(style.color)}"`);
  }
  richTextStyleKeys.forEach((key) => {
    if (style[key]) {
      attrs.push(`data-rich-${escapeAttr(key)}="true"`);
    }
  });
  return attrs.length > 0 ? ` ${attrs.join(' ')}` : '';
}

function mergeElementStyle(inheritedStyle: RichTextStyle, element: HTMLElement): RichTextStyle {
  const style: RichTextStyle = { ...inheritedStyle };
  const color = element.dataset.richColor;
  if (color !== undefined) {
    if (minecraftColors.some((item) => item.value === color && color)) {
      style.color = color as MinecraftColor;
    } else {
      delete style.color;
    }
  }
  richTextStyleKeys.forEach((key) => {
    if (element.dataset[`rich${capitalize(key)}`] === 'true') {
      style[key] = true;
    }
  });

  const tagName = element.tagName.toLowerCase();
  if (tagName === 'b' || tagName === 'strong') {
    style.bold = true;
  }
  if (tagName === 'i' || tagName === 'em') {
    style.italic = true;
  }
  if (tagName === 'u') {
    style.underlined = true;
  }
  if (tagName === 's' || tagName === 'strike' || tagName === 'del') {
    style.strikethrough = true;
  }
  return style;
}

function capitalize(value: string): string {
  return `${value[0].toUpperCase()}${value.slice(1)}`;
}

function richTextOffsetFromBoundary(root: HTMLElement, container: Node, offset: number): number {
  let position = 0;
  let found = false;
  const visit = (node: Node): void => {
    if (found) {
      return;
    }
    if (node === container) {
      position += richTextInnerOffset(node, offset);
      found = true;
      return;
    }
    if (node.nodeType === Node.TEXT_NODE) {
      position += node.nodeValue?.length ?? 0;
      return;
    }
    if (node instanceof HTMLBRElement) {
      position += 1;
      return;
    }
    node.childNodes.forEach(visit);
  };
  visit(root);
  return position;
}

function richTextInnerOffset(node: Node, offset: number): number {
  if (node.nodeType === Node.TEXT_NODE) {
    return Math.min(offset, node.nodeValue?.length ?? 0);
  }
  return Array.from(node.childNodes)
    .slice(0, offset)
    .reduce((sum, child) => sum + richTextNodeLength(child), 0);
}

function richTextNodeLength(node: Node): number {
  if (node.nodeType === Node.TEXT_NODE) {
    return node.nodeValue?.length ?? 0;
  }
  if (node instanceof HTMLBRElement) {
    return 1;
  }
  return Array.from(node.childNodes).reduce((sum, child) => sum + richTextNodeLength(child), 0);
}

function richTextBoundaryAtOffset(root: HTMLElement, offset: number): { node: Node; offset: number } {
  const target = Math.max(0, offset);
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let remaining = target;
  let lastText: Text | null = null;
  while (walker.nextNode()) {
    const textNode = walker.currentNode as Text;
    const length = textNode.data.length;
    if (remaining <= length) {
      return { node: textNode, offset: remaining };
    }
    remaining -= length;
    lastText = textNode;
  }
  if (lastText) {
    return { node: lastText, offset: lastText.data.length };
  }
  return { node: root, offset: 0 };
}
