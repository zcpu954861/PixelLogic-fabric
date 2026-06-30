# PixelLogic Catalog Form Manual Save Fix

## Problem
- Schema-driven modal fields wrote directly into the graph on every input.
- Rich text typing triggered graph history and autosave attempts per keypress.
- Closing a modified modal could not discard local edits because the graph was already mutated.

## Fix
- The editor modal now opens with a local node draft cloned from the selected graph node.
- Field input, select changes, segmented buttons, and `rich_text_component` typing update only the modal draft.
- `保存` applies the modal draft to the graph once, creates one graph history entry, then runs the existing save / validate / commit path.
- Closing, cancel, ESC, or overlay click shows a local unsaved confirmation when the modal draft differs from its opening snapshot.
- `继续编辑` only hides the confirm layer, so the editor modal does not close and replay its open animation.
- `放弃修改` drops the modal draft and leaves the graph unchanged.
- Drag, insert, add, delete, and disconnect graph operations still use the existing autosave path.

## Validation
- gradlew build: pass.
- npm build: pass.
- self-check: pass.
- browser self-check: see `reports/catalog-form-manual-save/REPORT.md`.
- screenshots: see `reports/catalog-form-manual-save/screenshots/`.

## Boundaries
- no UI redesign.
- no MC adapter.
- no rich text toolbar.
- no graph schema break.
- no change to structural graph autosave.
