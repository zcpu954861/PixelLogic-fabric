import {
  addSimulationRegion,
  addSimulationTag,
  cloneSimulationTestContext,
  defaultSimulationTestContext,
  removeSimulationRegion,
  removeSimulationTag,
  simulationTestPayload,
  type SimulationTestContext,
  updateSimulationDisplayName,
  updateSimulationOperator,
  updateSimulationPlayerPosition,
  updateSimulationRegion,
  updateSimulationTargetBlock,
  updateSimulationTargetEnabled,
  validateSimulationTestContext,
} from '../../model/simulationTestContext';
import { state } from '../../state/appState';

type RenderApp = () => void;

export function openSimulationEditor(renderApp: RenderApp): void {
  state.simulationMenuOpen = false;
  state.simulationDraftContext = cloneSimulationTestContext(state.simulationTestContext);
  state.simulationOriginalContext = cloneSimulationTestContext(state.simulationTestContext);
  state.simulationEditorOpen = true;
  state.simulationEditorClosing = false;
  state.simulationTestContextError = '';
  renderApp();
}

export function requestCloseSimulationEditor(renderApp: RenderApp): void {
  if (hasSimulationDraftChanges()) {
    showSimulationUnsavedConfirm();
    return;
  }
  closeSimulationEditor(renderApp);
}

function closeSimulationEditor(renderApp: RenderApp): void {
  hideSimulationUnsavedConfirm();
  state.simulationEditorClosing = true;
  const overlayEl = document.querySelector<HTMLElement>('[data-sim-modal-overlay]');
  if (overlayEl) {
    overlayEl.classList.add('is-closing');
  }
  window.setTimeout(() => {
    state.simulationEditorOpen = false;
    state.simulationEditorClosing = false;
    state.simulationDraftContext = null;
    state.simulationOriginalContext = null;
    state.simulationTestContextError = '';
    renderApp();
  }, 160);
}

function showSimulationUnsavedConfirm(): void {
  const confirmEl = document.querySelector<HTMLElement>('[data-sim-unsaved-confirm]');
  confirmEl?.removeAttribute('hidden');
  document.querySelector<HTMLElement>('[data-sim-modal-action="continue-edit"]')?.focus();
}

export function hideSimulationUnsavedConfirm(): void {
  document.querySelector<HTMLElement>('[data-sim-unsaved-confirm]')?.setAttribute('hidden', '');
}

export function discardSimulationEditorDraft(renderApp: RenderApp): void {
  state.simulationDraftContext = state.simulationOriginalContext
    ? cloneSimulationTestContext(state.simulationOriginalContext)
    : null;
  closeSimulationEditor(renderApp);
}

export function bindSimulationDraftFields(renderApp: RenderApp): void {
  const nameInput = document.querySelector<HTMLInputElement>('[data-sim-draft-name]');
  nameInput?.addEventListener('input', () => {
    updateSimulationDraft(updateSimulationDisplayName(simulationDraft(), nameInput.value), false, renderApp);
  });

  const tagInput = document.querySelector<HTMLInputElement>('[data-sim-draft-tag-input]');
  const addTag = () => {
    if (!tagInput) {
      return;
    }
    const result = addSimulationTag(simulationDraft(), tagInput.value);
    if (result.error) {
      state.simulationTestContextError = result.error;
      renderApp();
      return;
    }
    tagInput.value = '';
    updateSimulationDraft(result.context, true, renderApp);
  };
  document.querySelector('[data-sim-draft-action="add-tag"]')?.addEventListener('click', addTag);
  tagInput?.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      addTag();
    }
  });

  document.querySelectorAll<HTMLButtonElement>('[data-sim-draft-admin-value]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      updateSimulationDraft(updateSimulationOperator(simulationDraft(), buttonEl.dataset.simDraftAdminValue === 'true'), true, renderApp);
    });
  });

  document.querySelectorAll<HTMLInputElement>('[data-sim-player-position-field]').forEach((inputEl) => {
    inputEl.addEventListener('input', () => {
      updateSimulationDraft(
        updateSimulationPlayerPosition(simulationDraft(), inputEl.dataset.simPlayerPositionField as 'dimensionId' | 'x' | 'y' | 'z', inputEl.value),
        false,
        renderApp,
      );
    });
  });

  document.querySelectorAll<HTMLButtonElement>('[data-sim-target-enabled]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      updateSimulationDraft(updateSimulationTargetEnabled(simulationDraft(), buttonEl.dataset.simTargetEnabled === 'true'), true, renderApp);
    });
  });

  document.querySelectorAll<HTMLInputElement>('[data-sim-target-field]').forEach((inputEl) => {
    inputEl.addEventListener('input', () => {
      updateSimulationDraft(
        updateSimulationTargetBlock(
          simulationDraft(),
          inputEl.dataset.simTargetField as 'dimensionId' | 'x' | 'y' | 'z' | 'blockId' | 'enabled',
          inputEl.value,
        ),
        false,
        renderApp,
      );
    });
  });

  document.querySelector('[data-sim-region-action="add"]')?.addEventListener('click', () => {
    const result = addSimulationRegion(simulationDraft());
    if (result.error) {
      state.simulationTestContextError = result.error;
      renderApp();
      return;
    }
    updateSimulationDraft(result.context, true, renderApp);
  });
  document.querySelectorAll<HTMLButtonElement>('[data-sim-region-action="remove"]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      updateSimulationDraft(removeSimulationRegion(simulationDraft(), Number(buttonEl.dataset.simRegionIndex)), true, renderApp);
    });
  });
  document.querySelectorAll<HTMLInputElement>('[data-sim-region-field]').forEach((inputEl) => {
    inputEl.addEventListener('input', () => {
      updateSimulationDraft(
        updateSimulationRegion(
          simulationDraft(),
          Number(inputEl.dataset.simRegionIndex),
          inputEl.dataset.simRegionField as 'name' | 'dimensionId' | 'minX' | 'minY' | 'minZ' | 'maxX' | 'maxY' | 'maxZ',
          inputEl.value,
        ),
        false,
        renderApp,
      );
    });
  });
  document.querySelector('[data-sim-draft-action="reset"]')?.addEventListener('click', () => {
    updateSimulationDraft(defaultSimulationTestContext(), true, renderApp);
  });
  document.querySelectorAll<HTMLButtonElement>('[data-sim-draft-remove-tag]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      updateSimulationDraft(removeSimulationTag(simulationDraft(), buttonEl.dataset.simDraftRemoveTag ?? ''), true, renderApp);
    });
  });
}

function simulationDraft(): SimulationTestContext {
  return state.simulationDraftContext ?? state.simulationTestContext;
}

function updateSimulationDraft(context: SimulationTestContext, render: boolean, renderApp: RenderApp): void {
  state.simulationDraftContext = context;
  state.simulationTestContextError = '';
  hideSimulationUnsavedConfirm();
  if (render) {
    renderApp();
  }
}

export function saveSimulationEditorDraft(renderApp: RenderApp): void {
  const draft = state.simulationDraftContext;
  if (!draft) {
    return;
  }
  const error = validateSimulationTestContext(draft);
  if (error) {
    state.simulationTestContextError = error;
    renderApp();
    return;
  }
  if (!hasSimulationDraftChanges()) {
    closeSimulationEditor(renderApp);
    return;
  }
  state.simulationTestContext = cloneSimulationTestContext(simulationTestPayload(draft).testContext);
  state.simulationOriginalContext = cloneSimulationTestContext(state.simulationTestContext);
  state.simulationDraftContext = cloneSimulationTestContext(state.simulationTestContext);
  state.lastAction = '测试上下文已更新';
  closeSimulationEditor(renderApp);
}

export function hasSimulationDraftChanges(): boolean {
  return Boolean(state.simulationDraftContext && state.simulationOriginalContext)
    && JSON.stringify(state.simulationDraftContext) !== JSON.stringify(state.simulationOriginalContext);
}
