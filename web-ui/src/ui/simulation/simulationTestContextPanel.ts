import type { SimulationTestContext, SimulationTestResult } from '../../model/simulationTestContext';
import {
  addSimulationTag,
  defaultSimulationTestContext,
  normalizeSimulationTags,
  removeSimulationTag,
  updateSimulationDisplayName,
  updateSimulationOperator,
} from '../../model/simulationTestContext';
import { escapeAttr, escapeHtml, shortTraceId } from '../../utils/dom';

type BindHandlers = {
  getContext: () => SimulationTestContext;
  setContext: (context: SimulationTestContext, options?: { render?: boolean }) => void;
  setError: (message: string) => void;
};

export function renderSimulationTestContextPanel(
  context: SimulationTestContext,
  result: SimulationTestResult | null,
  error: string,
): string {
  const tags = normalizeSimulationTags(context.actor.tags);
  return `
    <section class="simulation-card">
      <div class="panel-title"><span>测试玩家</span><b>${tags.length} 个标签</b></div>
      <label class="field-row">
        玩家名
        <input type="text" data-sim-name maxlength="64" value="${escapeAttr(context.actor.displayName)}" autocomplete="off">
      </label>
      <div class="field-row">
        <span>标签</span>
        <div class="sim-tag-list" aria-label="测试玩家标签">
          ${tags.length > 0 ? tags.map((tag) => `
            <button type="button" class="sim-tag" data-sim-remove-tag="${escapeAttr(tag)}" title="移除标签 ${escapeAttr(tag)}">${escapeHtml(tag)}</button>
          `).join('') : '<span class="sim-empty">无</span>'}
        </div>
        <div class="sim-tag-add">
          <input type="text" data-sim-tag-input maxlength="64" placeholder="输入标签" autocomplete="off">
          <button type="button" class="tiny-button" data-sim-action="add-tag">添加</button>
        </div>
      </div>
      <label class="sim-toggle">
        <span>管理员</span>
        <input type="checkbox" data-sim-admin ${context.actor.operator ? 'checked' : ''}>
        <b>${context.actor.operator ? '是' : '否'}</b>
      </label>
      <button type="button" class="ghost-button sim-reset" data-sim-action="reset">恢复默认</button>
      ${error ? `<p class="sim-error" role="alert">${escapeHtml(error)}</p>` : ''}
      ${renderSimulationResult(result)}
    </section>
  `;
}

export function bindSimulationTestContextPanel(root: ParentNode, handlers: BindHandlers): void {
  const nameInput = root.querySelector<HTMLInputElement>('[data-sim-name]');
  nameInput?.addEventListener('input', () => {
    handlers.setContext(updateSimulationDisplayName(handlers.getContext(), nameInput.value), { render: false });
  });

  const tagInput = root.querySelector<HTMLInputElement>('[data-sim-tag-input]');
  const addTag = () => {
    if (!tagInput) {
      return;
    }
    const result = addSimulationTag(handlers.getContext(), tagInput.value);
    if (result.error) {
      handlers.setError(result.error);
      return;
    }
    tagInput.value = '';
    handlers.setContext(result.context);
  };
  root.querySelector('[data-sim-action="add-tag"]')?.addEventListener('click', addTag);
  tagInput?.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      addTag();
    }
  });

  root.querySelectorAll<HTMLButtonElement>('[data-sim-remove-tag]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      handlers.setContext(removeSimulationTag(handlers.getContext(), buttonEl.dataset.simRemoveTag ?? ''));
    });
  });

  root.querySelector<HTMLInputElement>('[data-sim-admin]')?.addEventListener('change', (event) => {
    handlers.setContext(updateSimulationOperator(handlers.getContext(), (event.currentTarget as HTMLInputElement).checked));
  });

  root.querySelector('[data-sim-action="reset"]')?.addEventListener('click', () => {
    handlers.setContext(defaultSimulationTestContext());
  });
}

function renderSimulationResult(result: SimulationTestResult | null): string {
  if (!result) {
    return '<div class="sim-result is-empty">尚未运行</div>';
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
