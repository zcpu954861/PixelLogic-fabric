# Slot Flow Drag Insert UX

Date: 2026-06-29

Branch: `feature/v1-slot-flow-drag-insert`

## Scope

Implemented:

- Click block library items to add a block onto the visible canvas.
- Drag a single block or the downstream chain rooted at that block.
- Drag a Condition block with both pass and fail downstream branches.
- Highlight a nearby insertable slot while dragging.
- Insert a block or single-tail chain into an existing control connection.
- Preserve position metadata in graph draft/commit storage.
- Show selected-block connection information in the right panel.
- Provide minimal `断开输入` and `删除积木` actions.
- Humanize trace display strings such as `PLAYER.started`, `true`, and `false`.

Not implemented:

- Region.
- Channel.
- Old TZZ adapters.
- Full infinite-canvas editor behavior.
- Multi-user editing.
- Complex graph auto-layout.

## Interaction Decisions

- Single click selects the block; double click opens the existing block editor modal.
- Manual save is removed from the primary UI; every graph edit is recorded in history and queued for automatic draft save, validation, and commit.
- Undo/redo is available through `上一步` / `下一步`, `Ctrl+Z`, `Ctrl+Y`, and `Ctrl+Shift+Z`.
- Pointer movement past a small threshold starts drag and prevents accidental modal open.
- Drag group is computed once on drag start from outgoing typed edges that are still visually snapped, with a visited guard.
- Snap detection and connected-state detection are separated: snap can be forgiving, but connected edges require tight visual alignment.
- Candidate detection uses in-flight preview positions, so dragging away and then back can reconnect to the just-separated port before release.
- Old-port reconnection uses a smaller snap radius than new-target snapping to avoid sticky pull-back while separating a chain.
- Condition branch lanes reserve recursive downstream visual span above and below each input anchor, while the Condition card outline only draws the input head and normal-height branch caps around pass/fail mouths instead of drawing the full reserved lane.
- Painted SVG paths and visible text are the only block hit targets, so transparent Condition lane space does not select or drag that block.
- Connected downstream blocks are vertically realigned to the dynamic pass/fail output centers so nested branch lanes do not overlap.
- Mouse move updates DOM positions and local insert feedback only.
- Graph JSON changes only on drag end.
- Releasing onto blank canvas moves the chain and removes stale edges that are no longer visually snapped.
- Releasing near a valid slot magnetically snaps the dragged chain and rewrites the target edge as `A -> dragged root -> dragged tail -> B`.
- Valid middle-slot hover previews insertion by temporarily cutting that join, animating the two resulting connected components apart, and showing a green glow-only insertion gap.
- Releasing near an empty chain-end output magnetically appends the dragged root to that output.
- Releasing a dragged tail near an empty input magnetically connects the dragged chain before that target block or chain.
- Insert success creates a local horizontal gap for the target block and its downstream chain so the slot-based blocks do not overlap.

## Validation

Browser self-test evidence:

```text
reports/graph-editor-drag-insert/REPORT.md
reports/graph-editor-drag-insert/screenshots/
```

Key checks covered:

- API connected.
- Slot-based horizontal flow retained.
- Added message block appears on canvas.
- Free/chain drag updates positions.
- Inserted message blocks execute in trace.
- Condition drag moves pass and fail branches.
- Isolated Condition blocks can be saved like other placed blocks; they only run after being connected into a trigger path.
- Delete recovers to valid graph.
- Disconnect input is undoable and then enters the automatic save/validation queue.

## Boundaries

This checkpoint stays within the existing WebUI and Graph Draft API. It does not add backend APIs or change runtime graph semantics.
