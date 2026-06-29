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

- Short click still opens the block editor modal.
- Pointer movement past a small threshold starts drag and prevents accidental modal open.
- Drag group is computed once on drag start from outgoing typed edges with a visited guard.
- Mouse move updates DOM positions and local insert feedback only.
- Graph JSON changes only on drag end.
- Releasing onto blank canvas moves the chain but keeps existing edges.
- Releasing onto a valid slot rewrites the target edge as `A -> dragged root -> dragged tail -> B`.
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
- Invalid isolated Condition save fails closed.
- Delete recovers to valid graph.
- Disconnect input marks the graph dirty without committing until saved.

## Boundaries

This checkpoint stays within the existing WebUI and Graph Draft API. It does not add backend APIs or change runtime graph semantics.
