import type { SlotBlock } from '../../model/graphTypes';

export const readableZoomThreshold = 0.72;
export const readableEditScale = 0.82;
export function initialReadableTransform(
  viewport: { width: number; height: number },
  blocks: SlotBlock[],
  triggerIds: string[],
): { scale: number; offsetX: number; offsetY: number } | null {
  const entry = blocks.find((block) => triggerIds.includes(block.id)) ?? blocks[0];
  if (!entry) {
    return null;
  }
  const primary = blocks.filter((block) =>
    block.x >= entry.x - 40 && block.x <= entry.x + 920
      && block.y >= entry.y - 360 && block.y <= entry.y + 520,
  );
  const visible = primary.length > 0 ? primary : [entry];
  const minX = Math.min(...visible.map((block) => block.x));
  const minY = Math.min(...visible.map((block) => block.y));
  const maxX = Math.max(...visible.map((block) => block.x + block.width));
  const maxY = Math.max(...visible.map((block) => block.y + block.height));
  const marginX = 44;
  const marginY = 36;
  const fit = Math.min(
    1,
    (viewport.width - marginX * 2) / Math.max(1, maxX - minX),
    (viewport.height - marginY * 2) / Math.max(1, maxY - minY),
  );
  const scale = Math.max(readableZoomThreshold, fit);
  return { scale, offsetX: marginX - minX * scale, offsetY: marginY - minY * scale };
}
