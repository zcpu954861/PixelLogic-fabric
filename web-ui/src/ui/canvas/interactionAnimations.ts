import { containerGeometry } from '../../model/containerGeometry';
import type { SlotBlock } from '../../model/graphTypes';
import { renderBlockShape } from './blockView';

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
};

export type GraphTransitionSnapshot = Map<string, BlockRectSnapshot>;

type PlacementPreview = {
  key: string;
  baseBlocks: SlotBlock[];
  placementBlocks: SlotBlock[];
  draggedNodeIds: Set<string>;
  rootNodeId: string;
};

const activeAnimations = new Set<Animation>();
const activeCleanups = new Set<() => void>();
const previewElements = new Set<HTMLElement>();
const previewShells = new Map<string, HTMLElement>();
let previewKey = '';
let placeholderEl: HTMLElement | null = null;

installAnimationTokens();

export function captureBlockRects(extra = new Map<string, BlockRectSnapshot>()): GraphTransitionSnapshot {
  const snapshots = new Map(extra);
  document.querySelectorAll<HTMLElement>('.flow-world [data-block]').forEach((element) => {
    const nodeId = element.dataset.block;
    if (nodeId) {
      snapshots.set(nodeId, rectSnapshot(element.getBoundingClientRect()));
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
  document.querySelectorAll<HTMLElement>('.flow-world [data-block]').forEach((element) => {
    const nodeId = element.dataset.block;
    const before = nodeId ? first.get(nodeId) : null;
    if (!before) {
      return;
    }
    const after = rectSnapshot(element.getBoundingClientRect());
    const dx = (before.left - after.left) / scale;
    const dy = (before.top - after.top) / scale;
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
    if (element.classList.contains('control')
      && (Math.abs(before.height - after.height) > 1 || Math.abs(before.width - after.width) > 1)) {
      animateContainerResize(element, before, after, scale);
    }
  });
}

export function showPlacementPreview(preview: PlacementPreview): void {
  const worldEl = document.querySelector<HTMLElement>('.flow-world');
  if (!worldEl) {
    return;
  }
  if (preview.key !== previewKey) {
    clearPlacementPreview(false);
    previewKey = preview.key;
  }
  clearPreviewTransforms();

  const baseById = new Map(preview.baseBlocks.map((block) => [block.id, block]));
  const placementById = new Map(preview.placementBlocks.map((block) => [block.id, block]));
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
      const dx = placementBlock.x - baseBlock.x;
      const dy = placementBlock.y - baseBlock.y;
      if (Math.abs(dx) <= 0.5 && Math.abs(dy) <= 0.5) {
        return;
      }
      cancelElementAnimations(element);
      element.classList.add('is-placement-preview');
      element.style.transform = `translate3d(${dx}px, ${dy}px, 0)`;
      previewElements.add(element);
    });
  }

  const rootBlock = placementById.get(preview.rootNodeId);
  if (rootBlock) {
    placeholderEl = updateShapeArtifact(
      placeholderEl,
      worldEl,
      'drop-placeholder',
      rootBlock,
      rootBlock.kind === 'control',
    );
  }

  const activeShellIds = new Set<string>();
  placementById.forEach((placementBlock, nodeId) => {
    const baseBlock = baseById.get(nodeId);
    if (!baseBlock || placementBlock.kind !== 'control'
      || (baseBlock.width === placementBlock.width && baseBlock.height === placementBlock.height)) {
      return;
    }
    activeShellIds.add(nodeId);
    const shell = updateShapeArtifact(
      previewShells.get(nodeId) ?? null,
      worldEl,
      'container-preview-shell',
      placementBlock,
      true,
    );
    previewShells.set(nodeId, shell);
  });
  previewShells.forEach((shell, nodeId) => {
    if (!activeShellIds.has(nodeId)) {
      shell.remove();
      previewShells.delete(nodeId);
    }
  });
}

export function clearPlacementPreview(animate = true): void {
  previewKey = '';
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
  removeArtifact(placeholderEl, animate);
  placeholderEl = null;
  previewShells.forEach((shell) => removeArtifact(shell, animate));
  previewShells.clear();
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

function updateShapeArtifact(
  existing: HTMLElement | null,
  worldEl: HTMLElement,
  className: string,
  block: SlotBlock,
  renderShape: boolean,
): HTMLElement {
  const element = existing ?? document.createElement('div');
  element.className = `${className}${block.kind === 'control' ? ' control' : ''}`;
  element.setAttribute('aria-hidden', 'true');
  element.style.left = `${block.x}px`;
  element.style.top = `${block.y}px`;
  element.style.width = `${block.width}px`;
  element.style.height = `${block.height}px`;
  element.innerHTML = renderShape ? renderBlockShape(block) : '';
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

function animateContainerResize(
  element: HTMLElement,
  before: BlockRectSnapshot,
  after: BlockRectSnapshot,
  scale: number,
): void {
  const startWidth = before.width / scale;
  const startHeight = before.height / scale;
  const endWidth = after.width / scale;
  const endHeight = after.height / scale;
  const guide = document.createElement('div');
  guide.className = 'container-resize-guide';
  guide.setAttribute('aria-hidden', 'true');
  guide.style.setProperty('--container-guide-header', `${containerGeometry.headerHeight}px`);
  guide.style.setProperty('--container-guide-rail-width', `${containerGeometry.leftRailWidth}px`);
  guide.style.setProperty('--container-guide-bottom-height', `${containerGeometry.bottomRailHeight}px`);
  guide.innerHTML = '<i class="container-resize-guide__rail"></i><i class="container-resize-guide__bottom"></i>';
  element.append(guide);

  const rail = guide.querySelector<HTMLElement>('.container-resize-guide__rail');
  const bottom = guide.querySelector<HTMLElement>('.container-resize-guide__bottom');
  if (!rail || !bottom) {
    guide.remove();
    return;
  }
  const railStart = Math.max(0, startHeight - containerGeometry.headerHeight);
  const railEnd = Math.max(0, endHeight - containerGeometry.headerHeight);
  const deltaY = startHeight - endHeight;
  const animations = [
    rail.animate(
      [{ height: `${railStart}px` }, { height: `${railEnd}px` }],
      animationOptions(interactionAnimationTokens.containerMs),
    ),
    bottom.animate(
      [
        { width: `${startWidth}px`, transform: `translate3d(0, ${deltaY}px, 0)` },
        { width: `${endWidth}px`, transform: 'translate3d(0, 0, 0)' },
      ],
      animationOptions(interactionAnimationTokens.containerMs),
    ),
  ];
  trackAnimations(animations, () => guide.remove());
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
