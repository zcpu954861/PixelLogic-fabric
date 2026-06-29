# Block Editor Modal UX

Date: 2026-06-29

Branch: `feature/v1-block-editor-modal`

## User Feedback

The right-side property editor made block configuration feel like a side panel workflow. The expected interaction is closer to opening a focused editor window from the selected block.

## Decision

- Clicking a block opens a block editor modal.
- The modal contains the editable typed fields and the single `保存` action.
- The right panel no longer contains field inputs or draft/validate/commit controls.
- Closing the modal with unsaved edits asks for confirmation.
- The modal uses short CSS open/close animations.
- Reduced motion disables animation.

## Save Semantics

The modal reuses the existing simplified save flow:

```text
PUT draft -> POST validate -> POST commit
```

Invalid edits remain fail-closed: they do not replace the committed graph or runtime graph, and the modal stays open with a Chinese validation message.

## Boundaries

- No Region.
- No Channel.
- No old TZZ adapter.
- No new action type.
- No runtime semantic change.
- No merge, tag, or release.
