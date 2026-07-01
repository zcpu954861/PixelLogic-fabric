export function updateBlockOverflowMotion(root: ParentNode = document): void {
  root.querySelectorAll<HTMLElement>('.logic-block h3').forEach((titleEl) => {
    const textEl = titleEl.querySelector<HTMLElement>('.block-title-text');
    const distance = textEl ? Math.ceil(textEl.scrollWidth - titleEl.clientWidth) : 0;
    titleEl.classList.toggle('is-overflowing', distance > 2);
    if (distance > 2) {
      titleEl.style.setProperty('--marquee-x', `${-distance}px`);
      titleEl.style.setProperty('--marquee-duration', `${Math.min(18, Math.max(8, distance / 7))}s`);
    }
  });

  root.querySelectorAll<HTMLElement>('.logic-block p').forEach((summaryEl) => {
    const textEl = summaryEl.querySelector<HTMLElement>('.block-summary-text');
    const distance = textEl ? Math.ceil(textEl.scrollHeight - summaryEl.clientHeight) : 0;
    summaryEl.classList.toggle('is-overflowing', distance > 4);
    if (distance > 4) {
      summaryEl.style.setProperty('--marquee-y', `${-distance}px`);
      summaryEl.style.setProperty('--marquee-duration', `${Math.min(18, Math.max(9, distance / 3))}s`);
    }
  });
}
