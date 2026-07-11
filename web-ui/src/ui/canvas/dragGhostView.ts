import type { SlotBlock } from '../../model/graphTypes';
import { renderBlock } from './blockView';

let ghostEl: HTMLElement | null = null;
let ghostScale = 1;

export function showCatalogDragGhost(block: SlotBlock, clientX: number, clientY: number, canvasScale: number): void {
  clearCatalogDragGhost();
  const template = document.createElement('template');
  template.innerHTML = renderBlock({ ...block, selected: false }, null).trim();
  const element = template.content.firstElementChild;
  if (!(element instanceof HTMLElement)) {
    return;
  }
  element.removeAttribute('data-block');
  element.classList.remove('selected', 'newly-added', 'is-related');
  element.classList.add('catalog-drag-ghost');
  element.setAttribute('aria-hidden', 'true');
  ghostScale = Math.min(0.86, Math.max(0.62, canvasScale));
  element.style.setProperty('--catalog-ghost-scale', String(ghostScale));
  document.body.append(element);
  ghostEl = element;
  updateCatalogDragGhost(clientX, clientY);
}

export function updateCatalogDragGhost(clientX: number, clientY: number): void {
  ghostEl?.style.setProperty('transform', `translate3d(${Math.round(clientX - ghostEl.offsetWidth * ghostScale / 2)}px, ${Math.round(clientY - ghostEl.offsetHeight * ghostScale / 2)}px, 0) scale(var(--catalog-ghost-scale))`);
}

export function catalogDragGhostRect(): DOMRect | null {
  return ghostEl?.getBoundingClientRect() ?? null;
}

export function clearCatalogDragGhost(): void {
  ghostEl?.getAnimations().forEach((animation) => animation.cancel());
  ghostEl?.remove();
  ghostEl = null;
  clearCatalogContainerTarget();
  clearCatalogConditionSlotTarget();
}

export function showCatalogContainerTarget(containerNodeId: string | null): void {
  clearCatalogContainerTarget();
  clearCatalogConditionSlotTarget();
  if (!containerNodeId) {
    return;
  }
  Array.from(document.querySelectorAll<HTMLElement>('.flow-world [data-block]'))
    .find((element) => element.dataset.block === containerNodeId)
    ?.classList.add('catalog-container-target');
}

export function showCatalogConditionSlotTarget(containerNodeId: string | null, slotId: string | null): void {
  clearCatalogContainerTarget();
  clearCatalogConditionSlotTarget();
  if (!containerNodeId || !slotId) {
    return;
  }
  document.querySelector<HTMLElement>(
    `[data-condition-container="${CSS.escape(containerNodeId)}"][data-condition-slot="${CSS.escape(slotId)}"]`,
  )?.classList.add('catalog-condition-slot-target');
}

function clearCatalogContainerTarget(): void {
  document.querySelectorAll('.catalog-container-target').forEach((element) => {
    element.classList.remove('catalog-container-target');
  });
}

function clearCatalogConditionSlotTarget(): void {
  document.querySelectorAll('.catalog-condition-slot-target').forEach((element) => {
    element.classList.remove('catalog-condition-slot-target');
  });
}
