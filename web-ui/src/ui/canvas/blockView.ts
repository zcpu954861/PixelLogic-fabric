import type { SlotBlock, SlotJoin } from '../../model/graphTypes';
import { normalBlockHeight, puzzleMouthHalfHeight } from './blockConstants';
import { escapeAttr, escapeHtml } from '../../utils/dom';

export function puzzlePath(block: SlotBlock): string {
  const { kind, width, height } = block;
  const tab = 18;
  const notchTop = 57;
  const notchBottom = 93;

  if (kind === 'trigger') {
    return `M0 0 H${width - tab} V${notchTop} H${width} V${notchBottom} H${width - tab} V${height} H0 Z`;
  }

  if (kind === 'condition' && 'pass' in block.outputOffsets && 'fail' in block.outputOffsets) {
    const headHeight = 150;
    const inputY = block.inputY ?? height / 2;
    const passY = block.outputOffsets.pass ?? 75;
    const failY = block.outputOffsets.fail ?? 329;
    const headTop = inputY - headHeight / 2;
    const headBottom = headTop + headHeight;
    const inputTop = inputY - puzzleMouthHalfHeight;
    const inputBottom = inputY + puzzleMouthHalfHeight;
    const passTop = passY - puzzleMouthHalfHeight;
    const passBottom = passY + puzzleMouthHalfHeight;
    const failTop = failY - puzzleMouthHalfHeight;
    const failBottom = failY + puzzleMouthHalfHeight;
    const capTop = Math.max(0, Math.min(headTop, passY - normalBlockHeight / 2));
    const capBottom = Math.min(height, Math.max(headBottom, failY + normalBlockHeight / 2));
    const branchInset = width - 106;

    return `M${branchInset} ${capTop} H${width - tab} V${passTop} H${width} V${passBottom} H${width - tab} V${failTop} H${width} V${failBottom} H${width - tab} V${capBottom} H${branchInset} V${headBottom} H0 V${inputBottom} H${tab} V${inputTop} H0 V${headTop} H${branchInset} Z`;
  }

  return `M0 0 H${width - tab} V${notchTop} H${width} V${notchBottom} H${width - tab} V${height} H0 V${notchBottom} H${tab} V${notchTop} H0 Z`;
}

export function conditionBranchTabs(block: SlotBlock): string {
  const width = block.width;
  return Object.entries(block.outputOffsets)
    .filter(([slotId]) => slotId === 'pass' || slotId === 'fail')
    .map(([slotId, centerY]) => {
      const top = centerY - puzzleMouthHalfHeight;
      const bottom = centerY + puzzleMouthHalfHeight;
      const tone = slotId === 'pass' ? 'pass' : 'fail';
      return `
        <path class="branch-tab-fill ${tone}" d="M${width - 20} ${top} H${width} V${bottom} H${width - 20} Z" />
        <path class="branch-tab ${tone}" d="M${width - 18} ${top} H${width} V${bottom} H${width - 18}" />
      `;
    })
    .join('');
}

export function renderShape(path: string, width: number, height: number, extraPaths = ''): string {
  return `
    <svg class="puzzle-shape" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" aria-hidden="true">
      <path class="block-body" d="${path}" />
      ${extraPaths}
    </svg>
  `;
}

export function renderSlotJoin(join: SlotJoin): string {
  return `
    <button
      type="button"
      class="slot-join ${join.tone ?? 'normal'}"
      data-join="${escapeAttr(join.id)}"
      data-from="${escapeAttr(join.from)}"
      data-to="${escapeAttr(join.to)}"
      data-branch="${join.branch}"
      aria-label="积木拼接"
      style="left:${join.x}px; top:${join.y}px; width:${join.width}px"
    ></button>
  `;
}

export function renderBlock(block: SlotBlock, recentNodeId: string | null): string {
  const branchTabs = block.kind === 'condition' ? conditionBranchTabs(block) : '';
  const conditionClass = block.kind === 'condition' && !('pass' in block.outputOffsets && 'fail' in block.outputOffsets) ? ' condition-single' : '';

  return `
    <article
      class="logic-block ${block.kind} ${block.branch}${conditionClass}${block.selected ? ' selected' : ''}${recentNodeId === block.id ? ' newly-added' : ''}"
      data-block="${escapeAttr(block.id)}"
      data-branch="${block.branch}"
      style="left:${block.x}px; top:${block.y}px; width:${block.width}px; height:${block.height}px; --condition-content-top:${Math.max(18, (block.inputY ?? 202) - 57)}px; z-index:${3000 - block.x + (block.selected ? 1000 : 0)}"
    >
      ${renderShape(puzzlePath(block), block.width, block.height, branchTabs)}
      <div class="block-topline">
        <span>${escapeHtml(block.type)}</span>
      </div>
      <h3>${escapeHtml(block.title)}</h3>
      <p>${escapeHtml(block.summary)}</p>
    </article>
  `;
}
