import type { SlotBlock, SlotJoin } from '../../model/graphTypes';
import {
  containerFrame,
  containerGeometry,
  normalBlockHeight,
  puzzleMouthHalfHeight,
  puzzleTabDepth,
} from '../../model/containerGeometry';
import { escapeAttr, escapeHtml } from '../../utils/dom';

export function puzzlePath(block: SlotBlock): string {
  const { kind, width, height } = block;
  const tab = puzzleTabDepth;
  const notchTop = normalBlockHeight / 2 - puzzleMouthHalfHeight;
  const notchBottom = normalBlockHeight / 2 + puzzleMouthHalfHeight;

  if (kind === 'trigger') {
    return `M0 0 H${width - tab} V${notchTop} H${width} V${notchBottom} H${width - tab} V${height} H0 Z`;
  }

  if (kind === 'condition' && 'pass' in block.outputOffsets && 'fail' in block.outputOffsets) {
    const headHeight = normalBlockHeight;
    const inputY = block.inputY ?? height / 2;
    const passY = block.outputOffsets.pass ?? normalBlockHeight / 2;
    const failY = block.outputOffsets.fail ?? height - normalBlockHeight / 2;
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

  if (kind === 'control') {
    return `${controlOuterFillPath(block)} ${containerBodyPath(block)}`;
  }

  return `M0 0 H${width - tab} V${notchTop} H${width} V${notchBottom} H${width - tab} V${height} H0 V${notchBottom} H${tab} V${notchTop} H0 Z`;
}

function controlOuterFillPath(block: SlotBlock): string {
  const tab = puzzleTabDepth;
  const bodyOffset = block.bodyOffsetY ?? 0;
  const inputY = block.inputY ?? normalBlockHeight / 2;
  const inputTop = inputY - puzzleMouthHalfHeight - containerGeometry.innerGap;
  const inputBottom = inputY + puzzleMouthHalfHeight + containerGeometry.innerGap;
  const outputY = block.outputOffsets.done;
  const outputPath = outputY === undefined
    ? `V${block.height}`
    : `V${outputY - puzzleMouthHalfHeight} H${block.width} V${outputY + puzzleMouthHalfHeight} H${block.width - tab} V${block.height}`;
  return `M0 ${bodyOffset} H${block.width - tab} ${outputPath} H0 V${inputBottom} H${tab} V${inputTop} H0 Z`;
}

function containerBodyPath(block: SlotBlock): string {
  const tab = puzzleTabDepth;
  const bodyOffset = block.bodyOffsetY ?? 0;
  const frame = containerFrame(block.width, block.height - bodyOffset);
  const innerLeft = frame.bodyRect.x;
  const innerRight = frame.bodyRect.x + frame.bodyRect.width;
  const innerTop = frame.bodyRect.y + bodyOffset;
  const innerBottom = frame.bodyRect.y + frame.bodyRect.height + bodyOffset;
  const bodyInputY = frame.laneY + bodyOffset;
  const tabTop = bodyInputY - puzzleMouthHalfHeight - containerGeometry.innerGap;
  const tabBottom = bodyInputY + puzzleMouthHalfHeight + containerGeometry.innerGap;
  const notchTop = bodyInputY - puzzleMouthHalfHeight;
  const notchBottom = bodyInputY + puzzleMouthHalfHeight;
  return `M${innerLeft} ${innerTop} H${innerRight - tab} V${tabTop} H${innerRight} V${tabBottom} H${innerRight - tab} V${innerBottom} H${innerLeft} V${notchBottom} H${innerLeft + tab} V${notchTop} H${innerLeft} Z`;
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
      <path class="block-body" d="${path}" fill-rule="evenodd" />
      ${extraPaths}
    </svg>
  `;
}

export function renderBlockShape(block: SlotBlock): string {
  if (block.presentation === 'predicate-capsule') {
    return `
      <svg class="puzzle-shape" viewBox="0 0 ${block.width} ${block.height}" preserveAspectRatio="none" aria-hidden="true">
        <rect class="block-body" x="2" y="2" width="${Math.max(0, block.width - 4)}" height="${Math.max(0, block.height - 4)}" rx="${Math.min(20, block.height / 2)}" />
      </svg>
    `;
  }
  const branchTabs = block.kind === 'condition' ? conditionBranchTabs(block) : '';
  return renderShape(puzzlePath(block), block.width, block.height, branchTabs);
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
  if (block.presentation === 'predicate-capsule') {
    return renderPredicateCapsule(block, block.x, block.y, recentNodeId);
  }
  const conditionClass = block.kind === 'condition' && !('pass' in block.outputOffsets && 'fail' in block.outputOffsets) ? ' condition-single' : '';
  const zIndex = Math.max(10, (block.kind === 'control' ? 900 : 3000) - block.x) + (block.selected ? 1000 : 0);
  const bodyZone = block.kind === 'control' && !block.hasChildren
    ? '<div class="container-body-zone"><span>拖入积木到这里</span></div>'
    : '';
  const bodyOffset = block.bodyOffsetY ?? 0;
  const frame = block.kind === 'control' ? containerFrame(block.width, block.height - bodyOffset) : null;
  const containerStyle = frame
    ? `; --block-body-offset:${bodyOffset}px; --container-body-left:${frame.bodyRect.x}px; --container-body-top:${frame.bodyRect.y + bodyOffset}px; --container-body-right:${block.width - frame.bodyRect.x - frame.bodyRect.width}px; --container-body-bottom:${block.height - bodyOffset - frame.bodyRect.y - frame.bodyRect.height}px`
    : '';

  return `
    <article
      class="logic-block ${block.kind} ${block.branch}${conditionClass}${block.selected ? ' selected' : ''}${recentNodeId === block.id ? ' newly-added' : ''}"
      data-block="${escapeAttr(block.id)}"
      data-pack="${escapeAttr(block.packId ?? '')}"
      data-branch="${block.branch}"
      tabindex="0"
      role="button"
      aria-label="编辑积木：${escapeAttr(block.title)}"
      style="left:${block.x}px; top:${block.y}px; width:${block.width}px; height:${block.height}px; --condition-content-top:${Math.max(18, (block.inputY ?? 202) - 57)}px; z-index:${zIndex}${containerStyle}"
    >
      ${renderBlockShape(block)}
      ${renderConditionRack(block)}
      ${bodyZone}
      <div class="block-topline">
        <span>${escapeHtml(block.type)}</span>
      </div>
      <h3><span class="block-title-text">${escapeHtml(block.title)}</span></h3>
      <p><span class="block-summary-text">${escapeHtml(block.summary)}</span></p>
    </article>
  `;
}

function renderConditionRack(block: SlotBlock): string {
  if (!block.conditionRack) {
    return '';
  }
  return `<div class="condition-rack" aria-label="结束条件架">
    ${block.conditionRack.rows.map((row) => `
      <div
        class="condition-rack-row${row.capsule ? ' is-filled' : ' is-empty'}"
        data-condition-slot="${escapeAttr(row.slotId)}"
        data-condition-container="${escapeAttr(block.id)}"
        style="left:${row.x}px; top:${row.y}px; width:${row.width}px; height:${row.height}px"
      >
        <div
          class="condition-rack-slot${row.capsule ? ' is-filled' : ' is-empty'}"
          style="left:${row.slotRect.x}px; top:${row.slotRect.y}px; width:${row.slotRect.width}px; height:${row.slotRect.height}px"
        >
          ${row.capsule
            ? renderPredicateCapsule(row.capsule, 0, 0, null)
            : `<button
                type="button"
                class="condition-rack-empty"
                data-library-predicate-filter
                aria-label="打开当前条件槽可用积木"
              >拖入条件</button>`}
        </div>
        <button
          type="button"
          class="condition-negate${row.negated ? ' is-active' : ''}"
          data-condition-negate="${escapeAttr(block.id)}"
          data-condition-slot-id="${escapeAttr(row.slotId)}"
          aria-label="${row.negated ? '取消取反' : '取反此条件'}"
          aria-pressed="${row.negated}"
          title="${row.negated ? '已取反，点击取消' : '点击取反'}"
          style="left:${row.toggleRect.x}px; top:${row.toggleRect.y}px; width:${row.toggleRect.width}px; height:${row.toggleRect.height}px"
        ><span aria-hidden="true"></span></button>
      </div>
    `).join('')}
  </div>`;
}

function renderPredicateCapsule(block: SlotBlock, left: number, top: number, recentNodeId: string | null): string {
  return `
    <article
      class="logic-block condition predicate-capsule${block.selected ? ' selected' : ''}${recentNodeId === block.id ? ' newly-added' : ''}"
      data-block="${escapeAttr(block.id)}"
      data-embedded-parent="${escapeAttr(block.embeddedParentId ?? '')}"
      tabindex="0"
      role="button"
      aria-label="编辑条件：${escapeAttr(block.title)}"
      style="left:${left}px; top:${top}px; width:${block.width}px; height:${block.height}px"
      title="双击编辑条件"
    >
      ${renderBlockShape(block)}
      <span class="predicate-capsule-summary">${escapeHtml(block.title)}</span>
    </article>
  `;
}
