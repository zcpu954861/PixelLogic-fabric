import type { BlockCatalog, GraphNode } from '../../model/graphTypes';
import { escapeHtml } from '../../utils/dom';
import { nodeSummary, nodeTypeLabel } from '../humanize/labels';
import { renderNodeEditor } from './formControls';

export function renderEditorModal(nodeItem: GraphNode, catalog: BlockCatalog, options: { editorClosing: boolean; error: string; hasValidation: boolean; modalIssue: string }): string {
  const title = `${nodeTypeLabel(nodeItem.type)}：${nodeItem.displayName || nodeItem.id}`;
  const modalIssue = options.modalIssue;

  return `
    <div class="editor-overlay${options.editorClosing ? ' is-closing' : ''}" data-modal-overlay>
      <section class="editor-dialog" role="dialog" aria-modal="true" aria-labelledby="block-editor-title">
        <header class="editor-head">
          <div>
            <p class="eyebrow">积木编辑</p>
            <h2 id="block-editor-title" tabindex="-1">${escapeHtml(title)}</h2>
          </div>
          <button type="button" class="modal-close" data-modal-action="close" aria-label="关闭编辑窗口">×</button>
        </header>
        <div class="editor-body">
          <section class="editor-summary">
            <b>当前摘要</b>
            <p data-modal-summary>${escapeHtml(nodeSummary(nodeItem, catalog))}</p>
          </section>
          ${renderNodeEditor(nodeItem, catalog)}
          ${options.error || options.hasValidation ? `
            <section class="editor-issues" role="${options.error ? 'alert' : 'status'}">
              <b>${options.error ? '自动保存提示' : '检查结果'}</b>
              <p data-modal-error>${escapeHtml(modalIssue)}</p>
            </section>
          ` : ''}
        </div>
        <footer class="editor-actions">
          <button type="button" class="ghost-button" data-modal-action="cancel">关闭</button>
        </footer>
      </section>
    </div>
  `;
}
