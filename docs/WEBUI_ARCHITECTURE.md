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
- The normal WebUI does not expose a manual save button; graph edits enter an automatic save, validate, and commit queue.
- Automatic save promotes edits only when validation passes, and stale save responses do not overwrite newer local edits.
- Invalid edits do not replace the committed runtime graph.
- `上一步` / `下一步` plus `Ctrl+Z`, `Ctrl+Y`, and `Ctrl+Shift+Z` roll back and reapply graph operations such as naming, configuration, add/delete, drag, and connection edits.
- `测试运行` waits for pending automatic save, resets the demo test state, starts the run, and refreshes the trace.
- Reset remains an internal API step, not a primary user button.
- The right panel is an information surface, not the main field editor.
- Closing the editor modal is just closing the form; field changes are already part of the graph history and automatic save queue.
- Modal open/close uses short CSS animation and respects reduced motion.
- The editor modal uses humanized Chinese form labels and controls. Internal graph values such as `PLAYER`, `BOOLEAN`, and `true` remain storage/runtime values, but normal UI renders them as labels such as `玩家`, `是或否`, and `是`.
- Short configuration fields use compact two-column layout where space allows; long text fields remain full-width.

## Slot Flow Drag / Insert

The slot-based canvas now supports a minimal direct-manipulation graph editing loop:

- Block library buttons add a new block to the visible canvas and select it.
- Single click selects the block; pointer movement past the drag threshold starts drag; double click opens the existing editor modal.
- Dragging a block moves that block and all downstream nodes reachable from outgoing typed edges.
- Dragging a Condition moves both pass and fail downstream branches.
- Dragging follows only edges whose puzzle mouths are still visually snapped together; visually separated stale edges are ignored.
- Magnetic snap has a wider hit area, but connected-state detection uses a tight snapped-position tolerance so near-misses are not treated as one chain.
- During an active drag, candidate detection uses preview positions so a just-separated old port can be reconnected before pointer release.
- Reconnecting to the just-separated original port uses a smaller snap radius than connecting to a new target, so slight separation does not immediately pull the block back.
- Condition branch lanes reserve recursive downstream visual span above and below each input anchor so nested Conditions do not overlap, while the Condition card outline only draws the input head and normal-height branch caps around pass/fail mouths instead of drawing the full reserved lane.
- Block hit testing follows the painted puzzle outline and visible text, not the oversized layout rectangle used for dynamic branch lanes.
- Connected downstream nodes are vertically realigned to those dynamic ports; this keeps the puzzle mouths snapped while preventing nested branch overlap.
- Releasing on blank canvas stores new position metadata and drops edges that are no longer visually snapped.
- Automatic save and test-run also sync graph edges to the current visual snapped state before persisting.
- Releasing near a valid join magnetically snaps the dragged chain into place, rewrites the edge as an inserted chain, and keeps validation fail-closed through the existing save flow.
- While hovering near a valid middle join, the two connected components produced by temporarily cutting that join animate apart, leaving a green glow-only insertion gap.
- Releasing near a chain-end output magnetically appends the dragged chain to that output.
- Releasing a dragged chain tail near a free input magnetically connects the dragged chain before that block or chain.
- Insert success shifts the target block and local downstream chain rightward to keep puzzle blocks from overlapping.
- The right panel remains informational, but now also shows selected-block connections plus minimal `断开输入` and `删除积木` controls.
- Trace text is humanized in the WebUI display layer only; runtime trace payloads remain unchanged.

## Boundary

Java may serve built static assets later, but Java must not generate WebUI source strings.

The normal user flow must expose visual graph relationships instead of channel names.
