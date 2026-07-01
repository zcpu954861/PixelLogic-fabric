export function bindCustomSelectControls(root: ParentNode = document): void {
  root.querySelectorAll<HTMLButtonElement>('[data-custom-select-toggle]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', (event) => {
      event.preventDefault();
      const selectEl = buttonEl.closest<HTMLElement>('[data-custom-select]');
      if (!selectEl) {
        return;
      }
      const willOpen = !selectEl.classList.contains('is-open');
      closeCustomSelects(selectEl);
      selectEl.classList.toggle('is-open', willOpen);
      buttonEl.setAttribute('aria-expanded', String(willOpen));
      selectEl.querySelector<HTMLElement>('.custom-select-list')?.toggleAttribute('hidden', !willOpen);
    });
  });

  root.querySelectorAll<HTMLButtonElement>('[data-custom-select-option]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      const selectEl = buttonEl.closest<HTMLElement>('[data-custom-select]');
      const triggerText = selectEl?.querySelector<HTMLElement>('.custom-select-trigger span');
      if (triggerText) {
        triggerText.textContent = buttonEl.textContent?.trim() ?? '';
      }
      selectEl?.querySelectorAll<HTMLButtonElement>('[data-custom-select-option]').forEach((item) => {
        const selected = item === buttonEl;
        item.setAttribute('aria-selected', String(selected));
        item.setAttribute('aria-pressed', String(selected));
      });
      closeCustomSelects();
    });
  });

  root.querySelectorAll<HTMLElement>('.editor-dialog').forEach((dialogEl) => {
    dialogEl.addEventListener('pointerdown', (event) => {
      if (!(event.target as HTMLElement).closest('[data-custom-select]')) {
        closeCustomSelects();
      }
    });
  });
}

export function closeCustomSelects(except?: HTMLElement): void {
  document.querySelectorAll<HTMLElement>('[data-custom-select].is-open').forEach((selectEl) => {
    if (except && selectEl === except) {
      return;
    }
    selectEl.classList.remove('is-open');
    selectEl.querySelector<HTMLButtonElement>('[data-custom-select-toggle]')?.setAttribute('aria-expanded', 'false');
    selectEl.querySelector<HTMLElement>('.custom-select-list')?.setAttribute('hidden', '');
  });
}
