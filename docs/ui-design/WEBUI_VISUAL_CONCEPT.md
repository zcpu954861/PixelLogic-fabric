# PixelLogic WebUI Visual Concept

This is a design-only static prototype for user review. It does not implement backend runtime, saving, execution, storage, permissions, or Java-generated WebUI.

## Implemented Direction

Recommended prototype: **Card Studio with light Pixel Craft accents**.

Reason:

- PixelLogic is a graph-first logic workbench, so card readability and direct edges matter more than decorative theme.
- Server owners need a professional tool, not a toy-like editor.
- A small pixel/craft accent can signal Minecraft context without turning the UI into a game skin.

Final visual direction remains a user decision.

## Included States

- App shell with top project/status/actions.
- Left rail with graph list, node library, and first-use actions.
- Graph canvas with Command Trigger, Condition, Message Action, State Action, Timer, and Debug Log cards.
- Typed edges for trigger, pass, fail, done, and completed paths.
- Right selected-card property panel for a Condition node.
- Bottom validation errors and mock execution trace.

## Non-Goals

- No real graph persistence.
- No real runtime execution.
- No backend API calls.
- No Java backend changes.
- No React/Vue/Svelte or large UI framework.

## Quality Notes

- The primary user flow is visible as cards and edges.
- Condition is an independent card with pass/fail outputs.
- Action cards expose done/error outputs.
- Normal-user UI does not include a Channel entry point.
- The current prototype prioritizes 1366px and wider desktop review. Small screens stack panels for rough usability, but mobile editing needs a later dedicated design pass.
