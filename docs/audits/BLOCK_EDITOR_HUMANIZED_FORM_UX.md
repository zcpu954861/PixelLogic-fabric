# Block Editor Humanized Form UX

Date: 2026-06-29

Branch: `feature/v1-block-editor-humanized-form`

## Problem

The first modal editor proved the right interaction shape, but the form still looked too much like an internal admin form. Internal values such as `PLAYER`, `BOOLEAN`, and `true` leaked into the normal user-facing UI, and short fields consumed too much vertical space.

## Fix

- Keep graph JSON and runtime semantics unchanged.
- Map internal values to Chinese UI labels: `PLAYER` -> `玩家`, `GLOBAL` -> `全局`, `SESSION` -> `当前会话`, `BOOLEAN` -> `是或否`, and `true` / `false` -> `是` / `否`.
- Use segmented controls for boolean values.
- Use selects for fixed enum choices.
- Use compact two-column layout for short fields and full-width layout for long message fields.
- Display read-only block type as a badge-like value instead of an input.
- Keep the right panel as selected-block information, not the main editor.

## Principle

Internal values are storage/runtime contracts, not user copy. The UI translates them at the boundary and maps user choices back to the same internal strings before saving.

## Boundaries

- No Region.
- No Channel.
- No old TZZ adapter.
- No new action type.
- No graph JSON schema change.
- No runtime semantic change.
- No merge, tag, or release.
