import type { SlotBlock } from '../../model/graphTypes';
import { renderBlock, renderBlockShape } from './blockView';
import { visualBlocks } from './slotFlowViewModel';

export const interactionAnimationTokens = {
  previewMs: 180,
  settleMs: 170,
  containerMs: 240,
  easing: 'cubic-bezier(0.2, 0.78, 0.24, 1)',
} as const;

export type BlockRectSnapshot = {
  left: number;
  top: number;
  width: number;
  height: number;
  bodyTop?: number;
};

export type GraphTransitionSnapshot = Map<string, BlockRectSnapshot>;

type PlacementPreview = {
  key: string;
  baseBlocks: SlotBlock[];
  placementBlocks: SlotBlock[];
  draggedNodeIds: Set<string>;
};

const activeAnimations = new Set<Animation>();
const activeCleanups = new Set<() => void>();
const previewElements = new Set<HTMLElement>();
const placementGhosts = new Map<string, HTMLElement>();
const previewShells = new Map<string, HTMLElement>();
const previewShellTargets = new Map<string, HTMLElement>();
let previewKey = '';
let previewLayoutSignature = '';

installAnimationTokens();

export function captureBlockRects(extra = new Map<string, BlockRectSnapshot>()): GraphTransitionSnapshot {
  const snapshots = new Map(extra);
  document.querySelectorAll<HTMLElement>('.flow-world [data-block]').forEach((element) => {
    const nodeId = element.dataset.block;
    if (nodeId) {
      snapshots.set(nodeId, elementRectSnapshot(element));
    }
  });
  return snapshots;
}

export function playGraphTransition(first: GraphTransitionSnapshot): void {
  cancelInteractionAnimations();
  if (prefersReducedMotion()) {
    return;
  }

  const scale = flowWorldScale();
  const elements = Array.from(document.querySelectorAll<HTMLElement>('.flow-world [data-block]'));
  const afterById = new Map<string, BlockRectSnapshot>();
  elements.forEach((element) => {
    const nodeId = element.dataset.block;
    if (nodeId) {
      afterById.set(nodeId, elementRectSnapshot(element));
    }
  });
  elements.forEach((element) => {
    const nodeId = element.dataset.block;
    const before = nodeId ? first.get(nodeId) : null;
    const after = nodeId ? afterById.get(nodeId) : null;
    if (!before || !after) {
      return;
    }
    const parentId = element.dataset.embeddedParent;
    const parentBefore = parentId ? first.get(parentId) : null;
    const parentAfter = parentId ? afterById.get(parentId) : null;
    const parentDx = parentBefore && parentAfter ? (parentBefore.left - parentAfter.left) / scale : 0;
    const parentDy = parentBefore && parentAfter
      ? ((parentBefore.bodyTop ?? parentBefore.top) - (parentAfter.bodyTop ?? parentAfter.top)) / scale
      : 0;
    const dx = (before.left - after.left) / scale - parentDx;
    const dy = ((before.bodyTop ?? before.top) - (after.bodyTop ?? after.top)) / scale - parentDy;
    if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) {
      trackAnimations([
        element.animate(
          [
            { transform: `translate3d(${dx}px, ${dy}px, 0)`, opacity: 0.82 },
            { transform: 'translate3d(0, 0, 0)', opacity: 1 },
          ],
          animationOptions(interactionAnimationTokens.settleMs),
        ),
      ]);
    }
  });
}

export function showPlacementPreview(preview: PlacementPreview): void {
  const worldEl = document.querySelector<HTMLElement>('.flow-world');
  if (!worldEl) {
    return;
  }
  const layoutSignature = placementPreviewSignature(preview);
  if (preview.key === previewKey && layoutSignature === previewLayoutSignature) {
    return;
  }
  if (preview.key !== previewKey) {
    clearPlacementPreview(false);
    previewKey = preview.key;
  }
  previewLayoutSignature = layoutSignature;
  clearPreviewTransforms();

  const baseById = new Map(visualBlocks(preview.baseBlocks).map((block) => [block.id, block]));
  const placementById = new Map(visualBlocks(preview.placementBlocks).map((block) => [block.id, block]));
  if (!prefersReducedMotion()) {
    placementById.forEach((placementBlock, nodeId) => {
      if (preview.draggedNodeIds.has(nodeId)) {
        return;
      }
      const baseBlock = baseById.get(nodeId);
      const element = blockElement(nodeId);
      if (!baseBlock || !element) {
        return;
      }
      const parentBase = placementBlock.embeddedParentId
        ? baseById.get(placementBlock.embeddedParentId)
        : null;
      const parentPlacement = placementBlock.embeddedParentId
        ? placementById.get(placementBlock.embeddedParentId)
        : null;
      const parentDx = parentBase && parentPlacement ? parentPlacement.x - parentBase.x : 0;
      const parentDy = parentBase && parentPlacement ? parentPlacement.y - parentBase.y : 0;
      const dx = placementBlock.x - baseBlock.x - parentDx;
      const dy = placementBlock.y - baseBlock.y - parentDy;
      if (Math.abs(dx) <= 0.5 && Math.abs(dy) <= 0.5) {
        return;
      }
      cancelElementAnimations(element);
      element.classList.add('is-placement-preview');
      element.style.transform = `translate3d(${dx}px, ${dy}px, 0)`;
      previewElements.add(element);
    });
  }

  renderPlacementGhosts(worldEl, placementById, preview.draggedNodeIds);

  const activeShellIds = new Set<string>();
  placementById.forEach((placementBlock, nodeId) => {
    const baseBlock = baseById.get(nodeId);
    if (!baseBlock || placementBlock.kind !== 'control'
      || (baseBlock.width === placementBlock.width && baseBlock.height === placementBlock.height)) {
      return;
    }
    activeShellIds.add(nodeId);
    const shell = updateContainerShell(
      previewShells.get(nodeId) ?? null,
      worldEl,
      placementBlock,
    );
    placeShellBehindContainer(shell, nodeId);
    animateArtifactResize(shell, baseBlock, placementBlock);
    previewShells.set(nodeId, shell);
    const target = blockElement(nodeId);
    if (target) {
      target.classList.add('is-container-previewed');
      previewShellTargets.set(nodeId, target);
    }
  });
  previewShells.forEach((shell, nodeId) => {
    if (!activeShellIds.has(nodeId)) {
      shell.remove();
      previewShells.delete(nodeId);
    }
  });
  previewShellTargets.forEach((target, nodeId) => {
    if (!activeShellIds.has(nodeId)) {
      target.classList.remove('is-container-previewed');
      previewShellTargets.delete(nodeId);
    }
  });
}

export function clearPlacementPreview(animate = true): void {
  previewKey = '';
  previewLayoutSignature = '';
  previewElements.forEach((element) => {
    const from = getComputedStyle(element).transform;
    cancelElementAnimations(element);
    element.classList.remove('is-placement-preview');
    element.style.removeProperty('transform');
    if (animate && !prefersReducedMotion() && from !== 'none') {
      trackAnimations([
        element.animate(
          [{ transform: from }, { transform: 'none' }],
          animationOptions(interactionAnimationTokens.previewMs),
        ),
      ]);
    }
  });
  previewElements.clear();
  placementGhosts.forEach((ghost) => removeArtifact(ghost, animate));
  placementGhosts.clear();
  previewShells.forEach((shell) => removeArtifact(shell, animate));
  previewShells.clear();
  previewShellTargets.forEach((target) => target.classList.remove('is-container-previewed'));
  previewShellTargets.clear();
}

export function cancelInteractionAnimations(): void {
  activeAnimations.forEach((animation) => animation.cancel());
  activeAnimations.clear();
  activeCleanups.forEach((cleanup) => cleanup());
  activeCleanups.clear();
}

function clearPreviewTransforms(): void {
  previewElements.forEach((element) => {
    cancelElementAnimations(element);
    element.classList.remove('is-placement-preview');
    element.style.removeProperty('transform');
  });
  previewElements.clear();
}

function renderPlacementGhosts(
  worldEl: HTMLElement,
  placementById: Map<string, SlotBlock>,
  draggedNodeIds: Set<string>,
): void {
  placementGhosts.forEach((ghost) => ghost.remove());
  placementGhosts.clear();
  const template = document.createElement('template');
  draggedNodeIds.forEach((nodeId) => {
    const block = placementById.get(nodeId);
    if (!block || (block.embeddedParentId && draggedNodeIds.has(block.embeddedParentId))) {
      return;
    }
    template.innerHTML = renderBlock({ ...block, selected: false }, null).trim();
    const ghost = template.content.firstElementChild;
    if (!(ghost instanceof HTMLElement)) {
      return;
    }
    ghost.removeAttribute('data-block');
    ghost.classList.add('placement-ghost-block');
    ghost.setAttribute('aria-hidden', 'true');
    worldEl.append(ghost);
    placementGhosts.set(nodeId, ghost);
  });
}

function updateContainerShell(
  existing: HTMLElement | null,
  worldEl: HTMLElement,
  block: SlotBlock,
): HTMLElement {
  const element = existing ?? document.createElement('div');
  element.className = 'container-preview-shell control';
  element.setAttribute('aria-hidden', 'true');
  element.style.left = `${block.x}px`;
  element.style.top = `${block.y}px`;
  element.style.width = `${block.width}px`;
  element.style.height = `${block.height}px`;
  const shapeSignature = blockShapeSignature(block);
  if (element.dataset.shapeSignature !== shapeSignature) {
    element.innerHTML = renderBlockShape(block);
    element.dataset.shapeSignature = shapeSignature;
  }
  if (!existing) {
    worldEl.append(element);
    if (!prefersReducedMotion()) {
      trackAnimations([
        element.animate([{ opacity: 0 }, { opacity: 1 }], animationOptions(interactionAnimationTokens.previewMs)),
      ]);
    }
  }
  return element;
}

function animateArtifactResize(element: HTMLElement, from: SlotBlock, to: SlotBlock): void {
  if (prefersReducedMotion()) {
    return;
  }
  trackAnimations([
    element.animate(
      [
        { left: `${from.x}px`, top: `${from.y}px`, width: `${from.width}px`, height: `${from.height}px` },
        { left: `${to.x}px`, top: `${to.y}px`, width: `${to.width}px`, height: `${to.height}px` },
      ],
      animationOptions(interactionAnimationTokens.containerMs),
    ),
  ]);
}

function placeShellBehindContainer(shell: HTMLElement, nodeId: string): void {
  const targetZIndex = Number.parseInt(blockElement(nodeId)?.style.zIndex ?? '', 10);
  if (Number.isFinite(targetZIndex)) {
    shell.style.zIndex = `${Math.max(1, targetZIndex - 1)}`;
  }
}

function placementPreviewSignature(preview: PlacementPreview): string {
  return [
    preview.key,
    blockLayoutSignature(preview.baseBlocks),
    blockLayoutSignature(preview.placementBlocks),
  ].join(';');
}

function blockLayoutSignature(blocks: SlotBlock[]): string {
  return visualBlocks(blocks)
    .map((block) => `${block.id}:${block.kind}:${block.x}:${block.y}:${block.width}:${block.height}`)
    .join('|');
}

function blockShapeSignature(block: SlotBlock): string {
  const outputs = Object.entries(block.outputOffsets)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([slotId, offset]) => `${slotId}:${offset}`)
    .join(',');
  return `${block.kind}:${block.width}:${block.height}:${block.inputY ?? ''}:${outputs}`;
}

function removeArtifact(element: HTMLElement | null, animate: boolean): void {
  if (!element?.isConnected) {
    element?.remove();
    return;
  }
  cancelElementAnimations(element);
  if (!animate || prefersReducedMotion()) {
    element.remove();
    return;
  }
  const animation = element.animate(
    [{ opacity: getComputedStyle(element).opacity }, { opacity: 0 }],
    animationOptions(interactionAnimationTokens.previewMs),
  );
  trackAnimations([animation], () => element.remove());
}

function trackAnimations(animations: Animation[], cleanup?: () => void): void {
  let remaining = animations.length;
  let cleaned = false;
  const runCleanup = () => {
    if (cleaned) {
      return;
    }
    cleaned = true;
    if (cleanup) {
      activeCleanups.delete(runCleanup);
      cleanup();
    }
  };
  if (cleanup) {
    activeCleanups.add(runCleanup);
  }
  animations.forEach((animation) => {
    activeAnimations.add(animation);
    void animation.finished.catch(() => undefined).then(() => {
      activeAnimations.delete(animation);
      remaining -= 1;
      if (remaining === 0) {
        runCleanup();
      }
    });
  });
}

function animationOptions(duration: number): KeyframeAnimationOptions {
  return {
    duration,
    easing: interactionAnimationTokens.easing,
  };
}

function cancelElementAnimations(element: HTMLElement): void {
  element.getAnimations().forEach((animation) => animation.cancel());
}

function blockElement(nodeId: string): HTMLElement | null {
  return Array.from(document.querySelectorAll<HTMLElement>('.flow-world [data-block]'))
    .find((element) => element.dataset.block === nodeId) ?? null;
}

function flowWorldScale(): number {
  const worldEl = document.querySelector<HTMLElement>('.flow-world');
  if (!worldEl || worldEl.offsetWidth === 0) {
    return 1;
  }
  return Math.max(0.01, worldEl.getBoundingClientRect().width / worldEl.offsetWidth);
}

function rectSnapshot(rect: DOMRect): BlockRectSnapshot {
  return { left: rect.left, top: rect.top, width: rect.width, height: rect.height };
}

function elementRectSnapshot(element: HTMLElement): BlockRectSnapshot {
  const rect = element.getBoundingClientRect();
  const scale = flowWorldScale();
  const bodyOffset = Number.parseFloat(element.style.getPropertyValue('--block-body-offset')) || 0;
  return { ...rectSnapshot(rect), bodyTop: rect.top + bodyOffset * scale };
}

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function installAnimationTokens(): void {
  if (typeof document === 'undefined') {
    return;
  }
  const style = document.documentElement.style;
  style.setProperty('--interaction-preview-duration', `${interactionAnimationTokens.previewMs}ms`);
  style.setProperty('--interaction-settle-duration', `${interactionAnimationTokens.settleMs}ms`);
  style.setProperty('--interaction-container-duration', `${interactionAnimationTokens.containerMs}ms`);
  style.setProperty('--interaction-easing', interactionAnimationTokens.easing);
}
