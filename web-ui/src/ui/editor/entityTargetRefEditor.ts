import { entityTargetLabel, entityTargetSources } from '../../model/entityTargetReference';
import type { EditableField, EntityTargetRef, OnlinePlayerDirectory, OnlinePlayerSummary } from '../../model/graphTypes';
import { escapeAttr, escapeHtml } from '../../utils/dom';

export function renderEntityTargetRefEditor(field: EditableField, directory: OnlinePlayerDirectory): string {
  const target = field.entityTarget ?? null;
  return `
    <div class="field-row entity-target-field is-full" data-entity-target-editor data-config-key="${escapeAttr(field.key)}">
      <span>${escapeHtml(field.label)}</span>
      ${renderSourceButtons(field.key, field.label, target)}
      ${target?.source === 'TARGET_ENTITY' ? '<small class="entity-target-warning" role="status">当前执行路径可能不提供目标实体。</small>' : ''}
      ${target?.source === 'ONLINE_PLAYER' ? renderPlayerSelect(field.key, target, directory) : ''}
      ${field.description ? `<small>${escapeHtml(field.description)}</small>` : ''}
    </div>
  `;
}

function renderSourceButtons(key: string, label: string, target: EntityTargetRef | null): string {
  return `
    <div class="segmented-control entity-target-source-control" role="group" aria-label="${escapeAttr(label)}">
      ${entityTargetSources.map((source) => `
        <button
          type="button"
          data-entity-target-source="${escapeAttr(source.value)}"
          data-entity-target-key="${escapeAttr(key)}"
          aria-pressed="${source.value === target?.source}"
        >${escapeHtml(source.label)}</button>
      `).join('')}
    </div>
  `;
}

function renderPlayerSelect(key: string, target: EntityTargetRef, directory: OnlinePlayerDirectory): string {
  const options = playerOptions(target, directory);
  const selected = options.find((player) => player.uuid === target.playerUuid) ?? null;
  const trigger = target.playerUuid
    ? playerOptionLabel(selected ?? { uuid: target.playerUuid, name: target.playerNameHint?.trim() || target.playerUuid }, directory.loaded, true)
    : '请选择在线玩家';
  return `
    <div class="entity-target-player-row">
      <div class="custom-select entity-target-player-select" data-custom-select>
        <button
          type="button"
          class="custom-select-trigger"
          data-custom-select-toggle
          data-entity-target-player-toggle
          aria-haspopup="listbox"
          aria-expanded="false"
          ${directory.loading ? 'disabled' : ''}
        ><span>${escapeHtml(directory.loading ? '正在加载在线玩家…' : trigger)}</span></button>
        <div class="custom-select-list" role="listbox" hidden>
          ${options.length > 0 ? options.map((player) => {
            const active = player.uuid === target.playerUuid;
            return `
              <button
                type="button"
                class="custom-select-option"
                role="option"
                data-custom-select-option
                data-entity-target-player="${escapeAttr(player.uuid)}"
                data-entity-target-player-name="${escapeAttr(player.name ?? player.uuid)}"
                data-entity-target-key="${escapeAttr(key)}"
                aria-selected="${active}"
                aria-pressed="${active}"
              >${escapeHtml(playerOptionLabel(player, directory.loaded, !directory.players.some((item) => item.uuid === player.uuid)))}</button>
            `;
          }).join('') : '<span class="custom-select-empty">当前没有在线玩家</span>'}
        </div>
      </div>
      <button type="button" class="ghost-button entity-target-refresh" data-entity-target-refresh ${directory.loading ? 'disabled' : ''}>刷新</button>
    </div>
    ${directory.error ? `<small class="entity-target-warning" role="alert">${escapeHtml(directory.error)}</small>` : ''}
    ${target.playerUuid
      && directory.selected?.uuid === target.playerUuid
      && directory.selected.availability?.toUpperCase() !== 'ONLINE'
      ? '<small class="entity-target-warning" role="status">已保存的玩家当前不在线；可以保留此配置或重新选择。</small>'
      : ''}
  `;
}

function playerOptions(target: EntityTargetRef, directory: OnlinePlayerDirectory): OnlinePlayerSummary[] {
  const options = directory.players.map((player) => ({ ...player, name: player.name?.trim() || player.uuid }));
  if (!target.playerUuid || options.some((player) => player.uuid === target.playerUuid)) {
    return options;
  }
  const selected = directory.selected?.uuid === target.playerUuid
    ? { ...directory.selected, name: directory.selected.name?.trim() || target.playerNameHint?.trim() || target.playerUuid }
    : { uuid: target.playerUuid, name: target.playerNameHint?.trim() || target.playerUuid };
  return [selected, ...options];
}

function playerOptionLabel(player: OnlinePlayerSummary, loaded: boolean, selectedOnly: boolean): string {
  const offline = selectedOnly && loaded && player.availability !== undefined
    && player.availability.toUpperCase() !== 'ONLINE';
  const name = player.name?.trim() || player.uuid;
  return offline ? `${name}（当前不在线）` : name;
}

export function entityTargetFieldSummary(field: EditableField): string {
  return entityTargetLabel(field.entityTarget ?? null);
}
