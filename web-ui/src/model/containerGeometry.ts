export type GeometryRect = { x: number; y: number; width: number; height: number };

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
