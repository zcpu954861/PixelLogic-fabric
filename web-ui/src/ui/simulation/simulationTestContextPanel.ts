import {
  formatSimulationPosition,
  formatSimulationRegions,
  formatSimulationTargetBlock,
  formatSimulationTargetEntity,
  normalizeSimulationTags,
  type SimulationPosition,
  type SimulationRegionFact,
  type SimulationTargetBlock,
  type SimulationTargetEntity,
  type SimulationTestContext,
  type SimulationTestResult,
} from '../../model/simulationTestContext';
import { escapeAttr, escapeHtml, shortTraceId } from '../../utils/dom';

export function renderTestRunControl(menuOpen: boolean, busyAttr: string): string {
  return `
    <div class="test-run-control${menuOpen ? ' is-open' : ''}">
      <button type="button" class="run-button run-main" data-api-action="start" ${busyAttr}>测试运行</button>
      <button type="button" class="run-menu-button" data-sim-menu-toggle aria-expanded="${menuOpen}" aria-label="展开测试上下文设置">
        <b aria-hidden="true">⌄</b>
      </button>
      ${menuOpen ? `
        <div class="test-run-menu">
          <small>配置本次测试使用的玩家、目标实体、位置、目标方块和区域。</small>
          <button type="button" class="ghost-button" data-sim-action="open-editor">编辑测试上下文</button>
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
      <section class="editor-dialog sim-context-dialog" role="dialog" aria-modal="true" aria-labelledby="simulation-editor-title">
        <header class="editor-head">
          <div>
            <p class="eyebrow">测试上下文</p>
            <h2 id="simulation-editor-title" tabindex="-1">编辑本次测试上下文</h2>
          </div>
          <button type="button" class="modal-close" data-sim-modal-action="close" aria-label="关闭测试上下文编辑窗口">×</button>
        </header>
        <div class="editor-body">
          <section class="editor-summary">
            <b>当前摘要</b>
            <p>${escapeHtml(context.actor.displayName || 'WebUI 模拟玩家')} · ${escapeHtml(formatSimulationPosition(context.world.playerPosition))} · 目标实体 ${escapeHtml(formatSimulationTargetEntity(context.world.targetEntity))} · 目标方块 ${escapeHtml(formatSimulationTargetBlock(context.world.targetBlock))} · 区域 ${escapeHtml(formatSimulationRegions(context.world.regions))}</p>
          </section>
          ${renderPlayerSection(context, tags)}
          ${renderPositionSection(context.world.playerPosition)}
          ${renderTargetEntitySection(context.world.targetEntity)}
          ${renderTargetBlockSection(context.world.targetBlock)}
          ${renderRegionSection(context.world.regions)}
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

function renderTargetEntitySection(target: SimulationTargetEntity): string {
  const tags = normalizeSimulationTags(target.tags);
  return `
    <section class="editor-section">
      <b>测试目标实体</b>
      <div class="field-grid">
        <div class="field-row">
          <span>启用目标实体</span>
          <div class="segmented-control" role="group" aria-label="启用测试目标实体">
            <button type="button" data-sim-target-entity-enabled="true" aria-pressed="${target.enabled}">是</button>
            <button type="button" data-sim-target-entity-enabled="false" aria-pressed="${!target.enabled}">否</button>
          </div>
        </div>
        <label class="field-row">
          实体类型 ID
          <input type="text" data-sim-target-entity-field="entityTypeId" value="${escapeAttr(target.entityTypeId)}" autocomplete="off" placeholder="minecraft:zombie">
        </label>
        <label class="field-row is-full">
          显示名称
          <input type="text" data-sim-target-entity-field="displayName" maxlength="64" value="${escapeAttr(target.displayName)}" autocomplete="off">
        </label>
        <div class="field-row is-full">
          <span>标签</span>
          ${tags.length > 0 ? `
            <div class="sim-tag-list" aria-label="测试目标实体标签">
              ${tags.map((tag) => `
                <span class="sim-tag">
                  <button type="button" class="sim-tag-remove" data-sim-target-entity-remove-tag="${escapeAttr(tag)}" aria-label="移除目标实体标签 ${escapeAttr(tag)}">×</button>
                  <span>${escapeHtml(tag)}</span>
                </span>
              `).join('')}
            </div>
          ` : ''}
          <div class="sim-tag-add">
            <input type="text" data-sim-target-entity-tag-input maxlength="64" placeholder="输入标签" autocomplete="off">
            <button type="button" class="tiny-button" data-sim-target-entity-action="add-tag">添加</button>
          </div>
        </div>
      </div>
    </section>
  `;
}

export function renderSimulationTestResultSummary(result: SimulationTestResult | null): string {
  return `
    <section class="simulation-card sim-result-card">
      <div class="panel-title"><span>测试上下文结果</span><b>${result ? escapeHtml(shortTraceId(result.traceId)) : '未运行'}</b></div>
      ${renderSimulationResult(result)}
    </section>
  `;
}

function renderPlayerSection(context: SimulationTestContext, tags: string[]): string {
  return `
    <section class="editor-section">
      <b>测试玩家</b>
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
  `;
}

function renderPositionSection(position: SimulationPosition): string {
  return `
    <section class="editor-section">
      <b>玩家位置</b>
      <div class="field-grid">
        <label class="field-row is-full">
          维度
          <input type="text" data-sim-player-position-field="dimensionId" value="${escapeAttr(position.dimensionId)}" autocomplete="off" placeholder="minecraft:overworld">
        </label>
        ${renderCoordinateInputs('sim-player-position-field', position)}
      </div>
    </section>
  `;
}

function renderTargetBlockSection(target: SimulationTargetBlock): string {
  return `
    <section class="editor-section">
      <b>目标方块</b>
      <div class="field-grid">
        <div class="field-row">
          <span>启用目标方块</span>
          <div class="segmented-control" role="group" aria-label="启用目标方块">
            <button type="button" data-sim-target-enabled="true" aria-pressed="${target.enabled}">是</button>
            <button type="button" data-sim-target-enabled="false" aria-pressed="${!target.enabled}">否</button>
          </div>
        </div>
        <label class="field-row">
          方块 ID
          <input type="text" data-sim-target-field="blockId" value="${escapeAttr(target.blockId)}" autocomplete="off" placeholder="minecraft:stone">
        </label>
        <label class="field-row is-full">
          维度
          <input type="text" data-sim-target-field="dimensionId" value="${escapeAttr(target.dimensionId)}" autocomplete="off" placeholder="minecraft:overworld">
        </label>
        ${renderCoordinateInputs('sim-target-field', target)}
      </div>
    </section>
  `;
}

function renderRegionSection(regions: SimulationRegionFact[]): string {
  return `
    <section class="editor-section">
      <div class="sim-section-title">
        <b>测试区域</b>
        <button type="button" class="tiny-button" data-sim-region-action="add">添加区域</button>
      </div>
      ${regions.length > 0 ? `
        <div class="sim-region-list">
          ${regions.map(renderRegionItem).join('')}
        </div>
      ` : '<p class="field-hint">可选。用于后续区域类积木的模拟事实。</p>'}
    </section>
  `;
}

function renderRegionItem(region: SimulationRegionFact, index: number): string {
  return `
    <section class="sim-region-item">
      <div class="sim-region-head">
        <b>${escapeHtml(region.name || `测试区域 ${index + 1}`)}</b>
        <button type="button" class="ghost-button danger" data-sim-region-action="remove" data-sim-region-index="${index}">删除</button>
      </div>
      <div class="field-grid">
        <label class="field-row">
          区域名
          <input type="text" data-sim-region-field="name" data-sim-region-index="${index}" maxlength="64" value="${escapeAttr(region.name)}" autocomplete="off">
        </label>
        <label class="field-row">
          维度
          <input type="text" data-sim-region-field="dimensionId" data-sim-region-index="${index}" value="${escapeAttr(region.dimensionId)}" autocomplete="off" placeholder="minecraft:overworld">
        </label>
        ${renderRegionCoordinates(region, index)}
      </div>
    </section>
  `;
}

function renderCoordinateInputs(dataName: string, position: SimulationPosition | SimulationTargetBlock): string {
  return `
    <label class="field-row sim-coordinate">
      X
      <input type="number" ${dataName === 'sim-target-field' ? 'data-sim-target-field' : 'data-sim-player-position-field'}="x" value="${coordinateValue(position.x)}" step="1">
    </label>
    <label class="field-row sim-coordinate">
      Y
      <input type="number" ${dataName === 'sim-target-field' ? 'data-sim-target-field' : 'data-sim-player-position-field'}="y" value="${coordinateValue(position.y)}" step="1">
    </label>
    <label class="field-row sim-coordinate">
      Z
      <input type="number" ${dataName === 'sim-target-field' ? 'data-sim-target-field' : 'data-sim-player-position-field'}="z" value="${coordinateValue(position.z)}" step="1">
    </label>
  `;
}

function renderRegionCoordinates(region: SimulationRegionFact, index: number): string {
  const fields: Array<keyof SimulationRegionFact> = ['minX', 'minY', 'minZ', 'maxX', 'maxY', 'maxZ'];
  return fields.map((field) => `
    <label class="field-row sim-coordinate">
      ${coordinateLabel(field)}
      <input type="number" data-sim-region-field="${field}" data-sim-region-index="${index}" value="${coordinateValue(region[field] as number)}" step="1">
    </label>
  `).join('');
}

function renderSimulationResult(result: SimulationTestResult | null): string {
  if (!result) {
    return '<div class="sim-result is-empty">测试运行后显示本次玩家、目标实体、位置、目标方块和区域。</div>';
  }
  const initialTags = normalizeSimulationTags(result.initialActorTags ?? []);
  const finalTags = normalizeSimulationTags(result.actorTags ?? []);
  return `
    <div class="sim-result">
      <div><span>本次玩家</span><b>${escapeHtml(result.actorDisplayName || 'WebUI 模拟玩家')}</b></div>
      <div><span>管理员</span><b>${result.actorOperator ? '是' : '否'}</b></div>
      <div><span>玩家位置</span><b>${escapeHtml(formatSimulationPosition(result.playerPosition))}</b></div>
      <div><span>目标方块</span><b>${escapeHtml(formatSimulationTargetBlock(result.targetBlock))}</b></div>
      <div><span>测试区域</span><b>${escapeHtml(formatSimulationRegions(result.regions))}</b></div>
      <div><span>初始标签</span><b>${escapeHtml(formatTags(initialTags))}</b></div>
      <div><span>结束标签</span><b>${escapeHtml(formatTags(finalTags))}</b></div>
      <div><span>标签变化</span><b>${escapeHtml(tagChanges(initialTags, finalTags))}</b></div>
      <div><span>测试目标实体</span><b>${result.targetEntityEnabled ? escapeHtml(`${result.targetEntityDisplayName}（${result.targetEntityTypeId}）`) : '未启用'}</b></div>
      <div><span>目标实体初始标签</span><b>${result.targetEntityEnabled ? escapeHtml(formatTags(normalizeSimulationTags(result.initialTargetEntityTags ?? []))) : '未启用'}</b></div>
      <div><span>目标实体最终标签</span><b>${result.targetEntityEnabled ? escapeHtml(formatTags(normalizeSimulationTags(result.targetEntityTags ?? []))) : '未启用'}</b></div>
      <small>${simulationRunStatusLabel(result)} · ${escapeHtml(shortTraceId(result.traceId))}</small>
    </div>
  `;
}

function simulationRunStatusLabel(result: SimulationTestResult): string {
  if (result.status === 'WAITING') {
    return '等待后续执行';
  }
  if (result.status === 'CANCELLED') {
    return '运行已取消';
  }
  return result.success ? '运行完成' : '运行失败';
}

function coordinateValue(value: number): string {
  return Number.isFinite(value) ? String(value) : '';
}

function coordinateLabel(field: keyof SimulationRegionFact): string {
  return String(field).replace('min', '最小 ').replace('max', '最大 ');
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
