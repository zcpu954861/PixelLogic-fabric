export type GeometryRect = { x: number; y: number; width: number; height: number };
import type { ConditionSlotDefinition } from './graphTypes';

export const normalBlockWidth = 260;
export const normalBlockHeight = 150;
export const puzzleTabDepth = 18;
export const puzzleMouthHalfHeight = 18;
export const puzzleConnectedOverlap = 14;
const containerInnerGap = 4;
const containerRailWidth = 38;
const containerBaseWidth = containerRailWidth * 2 + containerInnerGap * 2 + normalBlockWidth;

export const containerGeometry = {
  headerHeight: 96,
  leftRailWidth: containerRailWidth,
  bottomRailHeight: 38,
  bodyPadding: containerRailWidth,
  innerGap: containerInnerGap,
  childGap: 28,
  tailDropReach: containerBaseWidth / 2,
  bodyMinHeight: normalBlockHeight + containerInnerGap * 2,
} as const;

export const conditionRackGeometry = {
  rackGap: 10,
  rowHeight: 58,
  rowGap: 8,
  sideInset: 24,
  rowPadding: 7,
  toggleSize: 28,
  toggleGap: 10,
} as const;

export const containerMinimumWidth = containerBaseWidth;

export const containerMinimumHeight = containerGeometry.headerHeight
  + containerGeometry.bodyMinHeight
  + containerGeometry.bottomRailHeight;

export function containerFrame(width: number, height: number): {
  hitRect: GeometryRect;
  bodyRect: GeometryRect;
  bodyDropZone: GeometryRect;
  childOrigin: { x: number; y: number };
  bodyEntryAnchor: { x: number; y: number };
  laneY: number;
} {
  const bodyRect = {
    x: containerGeometry.leftRailWidth,
    y: containerGeometry.headerHeight,
    width: Math.max(
      normalBlockWidth + containerGeometry.innerGap * 2,
      width - containerGeometry.leftRailWidth - containerGeometry.bodyPadding,
    ),
    height: Math.max(
      containerGeometry.bodyMinHeight,
      height - containerGeometry.headerHeight - containerGeometry.bottomRailHeight,
    ),
  };
  const childOrigin = {
    x: bodyRect.x + puzzleTabDepth - puzzleConnectedOverlap,
    y: bodyRect.y + containerGeometry.innerGap,
  };
  const bodyEntryAnchor = {
    x: childOrigin.x,
    y: childOrigin.y + normalBlockHeight / 2,
  };
  return {
    hitRect: { x: 0, y: 0, width, height },
    bodyRect,
    bodyDropZone: { ...bodyRect, width: bodyRect.width + containerGeometry.tailDropReach },
    childOrigin,
    bodyEntryAnchor,
    laneY: bodyEntryAnchor.y,
  };
}

export function containerWidthForChildRight(childRight: number): number {
  return Math.max(
    containerMinimumWidth,
    childRight + containerGeometry.bodyPadding + containerGeometry.innerGap,
  );
}

export function containerHeightForChildBottom(childBottom: number): number {
  return Math.max(
    containerMinimumHeight,
    childBottom + containerGeometry.innerGap + containerGeometry.bottomRailHeight,
  );
}

export type ConditionRackRowFrame = {
  slotId: string;
  index: number;
  rowRect: GeometryRect;
  capsuleRect: GeometryRect;
  toggleRect: GeometryRect;
  dropZone: GeometryRect;
};

export function conditionRackFrame(
  width: number,
  bodyHeight: number,
  slots: ConditionSlotDefinition[],
): {
  rackHeight: number;
  fullBounds: GeometryRect;
  rows: ConditionRackRowFrame[];
} {
  const rackHeight = slots.length === 0
    ? 0
    : conditionRackGeometry.rackGap
      + slots.length * conditionRackGeometry.rowHeight
      + (slots.length - 1) * conditionRackGeometry.rowGap;
  const rowWidth = Math.max(
    normalBlockWidth,
    width - conditionRackGeometry.sideInset * 2,
  );
  const rows = slots.map((slot, index) => {
    const y = -conditionRackGeometry.rackGap
      - (index + 1) * conditionRackGeometry.rowHeight
      - index * conditionRackGeometry.rowGap;
    const rowRect = {
      x: conditionRackGeometry.sideInset,
      y,
      width: rowWidth,
      height: conditionRackGeometry.rowHeight,
    };
    const toggleRect = {
      x: rowRect.x + rowRect.width - conditionRackGeometry.rowPadding - conditionRackGeometry.toggleSize,
      y: rowRect.y + (rowRect.height - conditionRackGeometry.toggleSize) / 2,
      width: conditionRackGeometry.toggleSize,
      height: conditionRackGeometry.toggleSize,
    };
    const capsuleRect = {
      x: rowRect.x + conditionRackGeometry.rowPadding,
      y: rowRect.y + conditionRackGeometry.rowPadding,
      width: Math.max(
        80,
        toggleRect.x - conditionRackGeometry.toggleGap - rowRect.x - conditionRackGeometry.rowPadding,
      ),
      height: rowRect.height - conditionRackGeometry.rowPadding * 2,
    };
    return { slotId: slot.slotId, index, rowRect, capsuleRect, toggleRect, dropZone: { ...rowRect } };
  });
  return {
    rackHeight,
    fullBounds: { x: 0, y: -rackHeight, width, height: bodyHeight + rackHeight },
    rows,
  };
}
