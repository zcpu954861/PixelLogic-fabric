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
              <b>${options.error ? '保存提示' : '检查结果'}</b>
              <p data-modal-error>${escapeHtml(modalIssue)}</p>
            </section>
          ` : ''}
        </div>
        <footer class="editor-actions">
          <button type="button" class="ghost-button" data-modal-action="cancel">关闭</button>
          <button type="button" class="run-button" data-modal-action="save">保存</button>
        </footer>
        <div class="unsaved-confirm" data-unsaved-confirm hidden>
          <section role="alertdialog" aria-modal="true" aria-labelledby="unsaved-confirm-title">
            <b id="unsaved-confirm-title">还有未保存的修改，确定要放弃吗？</b>
            <div>
              <button type="button" class="ghost-button" data-modal-action="continue-edit">继续编辑</button>
              <button type="button" class="ghost-button danger" data-modal-action="discard">放弃修改</button>
            </div>
          </section>
        </div>
        <div class="unsaved-confirm" data-mode-switch-confirm hidden>
          <section role="alertdialog" aria-modal="true" aria-labelledby="mode-switch-confirm-title">
            <b id="mode-switch-confirm-title">切换后，部分分支连接会被断开。确定继续吗？</b>
            <div>
              <button type="button" class="ghost-button" data-modal-action="continue-mode-edit">继续编辑</button>
              <button type="button" class="ghost-button danger" data-modal-action="switch-disconnect">切换并断开</button>
            </div>
          </section>
        </div>
      </section>
    </div>
  `;
}
