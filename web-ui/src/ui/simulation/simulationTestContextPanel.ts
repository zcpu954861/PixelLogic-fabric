import type { SimulationTestContext, SimulationTestResult } from '../../model/simulationTestContext';
import { normalizeSimulationTags } from '../../model/simulationTestContext';
import { escapeAttr, escapeHtml, shortTraceId } from '../../utils/dom';

export function renderTestRunControl(menuOpen: boolean, busyAttr: string): string {
  return `
    <div class="test-run-control${menuOpen ? ' is-open' : ''}">
      <button type="button" class="run-button run-main" data-api-action="start" ${busyAttr}>测试运行</button>
      <button type="button" class="run-menu-button" data-sim-menu-toggle aria-expanded="${menuOpen}" aria-label="展开测试玩家设置">
        <b aria-hidden="true">⌄</b>
      </button>
      ${menuOpen ? `
        <div class="test-run-menu">
          <small>配置本次测试使用的玩家名、标签和管理员状态。</small>
          <button type="button" class="ghost-button" data-sim-action="open-editor">编辑测试玩家</button>
        </div>
      ` : ''}
    </div>
  `;
}

export function renderSimulationTestContextModal(
  context: SimulationTestContext,
  options: { closing: boolean; error: string; steady: boolean },
): string {
  const tags = normalizeSimulationTags(context.actor.tags);
  return `
    <div class="editor-overlay${options.closing ? ' is-closing' : ''}${options.steady ? ' is-steady' : ''}" data-sim-modal-overlay>
      <section class="editor-dialog" role="dialog" aria-modal="true" aria-labelledby="simulation-editor-title">
        <header class="editor-head">
          <div>
            <p class="eyebrow">测试玩家</p>
            <h2 id="simulation-editor-title" tabindex="-1">编辑本次模拟玩家</h2>
          </div>
          <button type="button" class="modal-close" data-sim-modal-action="close" aria-label="关闭测试玩家编辑窗口">×</button>
        </header>
        <div class="editor-body">
          <section class="editor-summary">
            <b>当前摘要</b>
            <p>${escapeHtml(context.actor.displayName || 'WebUI 模拟玩家')} · 标签 ${escapeHtml(tags.length > 0 ? tags.join('，') : '无')} · 管理员 ${context.actor.operator ? '是' : '否'}</p>
          </section>
          <section class="editor-section">
            <b>基础信息</b>
            <div class="field-grid">
              <label class="field-row">
                玩家名
                <input type="text" data-sim-draft-name maxlength="64" value="${escapeAttr(context.actor.displayName)}" autocomplete="off">
              </label>
              <div class="field-row">
                <span>管理员</span>
                <div class="segmented-control sim-admin-control" role="group" aria-label="管理员">
                  <button type="button" data-sim-draft-admin-value="true" aria-pressed="${context.actor.operator}">是</button>
                  <button type="button" data-sim-draft-admin-value="false" aria-pressed="${!context.actor.operator}">否</button>
                </div>
              </div>
              <div class="field-row is-full">
                <span>标签</span>
                ${tags.length > 0 ? `
                  <div class="sim-tag-list" aria-label="测试玩家标签">
                    ${tags.map((tag) => `
                    <span class="sim-tag">
                      <button type="button" class="sim-tag-remove" data-sim-draft-remove-tag="${escapeAttr(tag)}" title="移除标签 ${escapeAttr(tag)}" aria-label="移除标签 ${escapeAttr(tag)}">×</button>
                      <span>${escapeHtml(tag)}</span>
                    </span>
                    `).join('')}
                  </div>
                ` : ''}
                <div class="sim-tag-add">
                  <input type="text" data-sim-draft-tag-input maxlength="64" placeholder="输入标签" autocomplete="off">
                  <button type="button" class="tiny-button" data-sim-draft-action="add-tag">添加</button>
                </div>
              </div>
              <button type="button" class="ghost-button sim-reset" data-sim-draft-action="reset">恢复默认</button>
            </div>
          </section>
          ${options.error ? `
            <section class="editor-issues" role="alert">
              <b>保存提示</b>
              <p>${escapeHtml(options.error)}</p>
            </section>
          ` : ''}
        </div>
        <footer class="editor-actions">
          <button type="button" class="ghost-button" data-sim-modal-action="cancel">关闭</button>
          <button type="button" class="run-button" data-sim-modal-action="save">保存</button>
        </footer>
        <div class="unsaved-confirm" data-sim-unsaved-confirm hidden>
          <section role="alertdialog" aria-modal="true" aria-labelledby="sim-unsaved-confirm-title">
            <b id="sim-unsaved-confirm-title">还有未保存的修改，确定要放弃吗？</b>
            <div>
              <button type="button" class="ghost-button" data-sim-modal-action="continue-edit">继续编辑</button>
              <button type="button" class="ghost-button danger" data-sim-modal-action="discard">放弃修改</button>
            </div>
          </section>
        </div>
      </section>
    </div>
  `;
}

export function renderSimulationTestResultSummary(result: SimulationTestResult | null): string {
  return `
    <section class="simulation-card sim-result-card">
      <div class="panel-title"><span>测试玩家结果</span><b>${result ? escapeHtml(shortTraceId(result.traceId)) : '未运行'}</b></div>
      ${renderSimulationResult(result)}
    </section>
  `;
}

function renderSimulationResult(result: SimulationTestResult | null): string {
  if (!result) {
    return '<div class="sim-result is-empty">测试运行后显示本次玩家和标签变化。</div>';
  }
  const initialTags = normalizeSimulationTags(result.initialActorTags ?? []);
  const finalTags = normalizeSimulationTags(result.actorTags ?? []);
  return `
    <div class="sim-result">
      <div><span>本次玩家</span><b>${escapeHtml(result.actorDisplayName || 'WebUI 模拟玩家')}</b></div>
      <div><span>初始标签</span><b>${escapeHtml(formatTags(initialTags))}</b></div>
      <div><span>结束标签</span><b>${escapeHtml(formatTags(finalTags))}</b></div>
      <div><span>标签变化</span><b>${escapeHtml(tagChanges(initialTags, finalTags))}</b></div>
      <div><span>管理员</span><b>${result.actorOperator ? '是' : '否'}</b></div>
      <small>${result.success ? '运行完成' : '运行失败'} · ${escapeHtml(shortTraceId(result.traceId))}</small>
    </div>
  `;
}

function formatTags(tags: string[]): string {
  return tags.length > 0 ? tags.join('，') : '无';
}

function tagChanges(initialTags: string[], finalTags: string[]): string {
  const initial = new Set(initialTags);
  const final = new Set(finalTags);
  const added = finalTags.filter((tag) => !initial.has(tag));
  const removed = initialTags.filter((tag) => !final.has(tag));
  if (added.length === 0 && removed.length === 0) {
    return '无变化';
  }
  return [
    added.length > 0 ? `添加 ${added.join('，')}` : '',
    removed.length > 0 ? `移除 ${removed.join('，')}` : '',
  ].filter(Boolean).join('；');
}
