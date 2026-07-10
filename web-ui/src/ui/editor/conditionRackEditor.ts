import { conditionSlotMember, conditionSlots, hasPredicateRack } from '../../model/conditionRack';
import type { BlockCatalog, GraphDocument, GraphNode } from '../../model/graphTypes';
import { escapeAttr, escapeHtml } from '../../utils/dom';
import { predicateNodeSummary } from '../humanize/labels';

export function renderConditionRackEditor(nodeItem: GraphNode, graph: GraphDocument, catalog: BlockCatalog): string {
  if (!hasPredicateRack(nodeItem, catalog)) {
    return '';
  }
  const slots = conditionSlots(nodeItem);
  return `
    <section class="editor-section condition-rack-editor" aria-labelledby="condition-rack-editor-title">
      <div class="condition-rack-editor-head">
        <div>
          <h3 id="condition-rack-editor-title">结束条件</h3>
          <p>所有已配置条件经过各自取反后都成立，循环才会结束。</p>
        </div>
        <button type="button" class="ghost-button" data-rack-draft-action="add">＋ 新增条件槽</button>
      </div>
      <div class="condition-rack-editor-list">
        ${slots.length === 0 ? '<p class="condition-rack-editor-empty">尚未添加条件槽。图可以保存，但运行会安全停止并提示缺少结束条件。</p>' : ''}
        ${slots.map((slot, index) => {
          const member = conditionSlotMember(graph, nodeItem.id, slot.slotId);
          return `
            <article class="condition-rack-editor-row" data-rack-draft-slot="${escapeAttr(slot.slotId)}">
              <div class="condition-rack-editor-label"><b>条件槽 ${index + 1}</b><small>${escapeHtml(slot.slotId)}</small></div>
              ${member ? `
                <button type="button" class="condition-rack-editor-summary" data-rack-edit-node="${escapeAttr(member.id)}">
                  ${escapeHtml(predicateNodeSummary(member, catalog, slot.negated))}
                </button>
              ` : '<span class="condition-rack-editor-placeholder">拖入一个条件积木</span>'}
              <button
                type="button"
                class="condition-rack-editor-negate${slot.negated ? ' is-active' : ''}"
                data-rack-draft-action="toggle"
                data-rack-slot-id="${escapeAttr(slot.slotId)}"
                aria-pressed="${slot.negated}"
                aria-label="${slot.negated ? `取消取反条件槽 ${index + 1}` : `取反条件槽 ${index + 1}`}"
                title="${slot.negated ? '已取反' : '不取反'}"
              >${slot.negated ? '● 取反' : '○ 取反'}</button>
              <button
                type="button"
                class="ghost-button danger"
                data-rack-draft-action="delete"
                data-rack-slot-id="${escapeAttr(slot.slotId)}"
                title="${member ? '删除槽后，保存时会同时删除其中的条件积木' : '删除空条件槽'}"
              >删除</button>
            </article>
          `;
        }).join('')}
      </div>
    </section>
  `;
}
