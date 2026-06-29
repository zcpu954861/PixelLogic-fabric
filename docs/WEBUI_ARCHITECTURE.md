# WebUI Architecture

The WebUI is an independent Vite + TypeScript project.

## Current Skeleton

- Vanilla TypeScript.
- No React, Vue, or Svelte until the user confirms a framework.
- Slot-based horizontal flow surface for the v1 demo graph.
- API-backed status, test run, reset, latest trace, and recent traces.
- Graph API-backed draft save, draft validation, and commit for `demo-start-flow`.
- App shell communicates the product direction: cards, slots, direct execution trace, and readable user actions.

## API Test Run

The current WebUI calls:

```text
GET  /api/pixellogic/status
POST /api/pixellogic/test/reset
POST /api/pixellogic/test/start
GET  /api/pixellogic/traces/latest
GET  /api/pixellogic/traces
```

The Vite dev server proxies `/api` to `http://127.0.0.1:18111`. The UI shows the fixed `WebUI 模拟玩家` context so browser tests are not confused with real player events.

## Graph Draft Flow

The current WebUI also calls:

```text
GET  /api/pixellogic/graphs/demo-start-flow
GET  /api/pixellogic/graphs/demo-start-flow/draft
PUT  /api/pixellogic/graphs/demo-start-flow/draft
POST /api/pixellogic/graphs/demo-start-flow/validate
POST /api/pixellogic/graphs/demo-start-flow/commit
```

The UI renders the graph as the confirmed Slot-Based horizontal block flow. Clicking a block opens a focused editor modal for selected-block fields such as display name, message text, state key/value, and timer seconds.

Important user semantics:

- The backend still keeps draft, validate, and commit separated.
- The normal WebUI exposes one `保存` button instead of separate draft/validate/commit controls.
- `保存` writes pending edits, validates them, and promotes them only when validation passes.
- Invalid edits do not replace the committed runtime graph.
- `测试运行` automatically saves pending valid edits, resets the demo test state, starts the run, and refreshes the trace.
- Reset remains an internal API step, not a primary user button.
- The right panel is an information surface, not the main field editor.
- Closing the editor modal with unsaved changes requires confirmation.
- Modal open/close uses short CSS animation and respects reduced motion.
- The editor modal uses humanized Chinese form labels and controls. Internal graph values such as `PLAYER`, `BOOLEAN`, and `true` remain storage/runtime values, but normal UI renders them as labels such as `玩家`, `是或否`, and `是`.
- Short configuration fields use compact two-column layout where space allows; long text fields remain full-width.

## Slot Flow Drag / Insert

The slot-based canvas now supports a minimal direct-manipulation graph editing loop:

- Block library buttons add a new block to the visible canvas and select it.
- Short click opens the existing editor modal; pointer movement past the drag threshold starts drag.
- Dragging a block moves that block and all downstream nodes reachable from outgoing typed edges.
- Dragging a Condition moves both pass and fail downstream branches.
- Releasing on blank canvas stores new position metadata but keeps graph edges unchanged.
- Releasing on a valid join rewrites the edge as an inserted chain and keeps validation fail-closed through the existing save flow.
- Insert success shifts the target block and local downstream chain rightward to keep puzzle blocks from overlapping.
- The right panel remains informational, but now also shows selected-block connections plus minimal `断开输入` and `删除积木` controls.
- Trace text is humanized in the WebUI display layer only; runtime trace payloads remain unchanged.

## Boundary

Java may serve built static assets later, but Java must not generate WebUI source strings.

The normal user flow must expose visual graph relationships instead of channel names.
