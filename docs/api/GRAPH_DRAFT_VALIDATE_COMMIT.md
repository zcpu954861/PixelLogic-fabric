# PixelLogic Graph Draft / Validate / Commit API

This document records the v1 graph save/load checkpoint. It extends the local spike API; it is not a full project management API and is not a remote administration API.

## Scope

Implemented:

- Seed `demo-start-flow` as a committed graph when no file exists.
- Save a draft graph separately from the committed graph.
- Validate the draft with `GraphValidator`.
- Commit only valid drafts.
- Run `/api/pixellogic/test/start` against the committed graph.
- Reject unsafe graph ids that do not match `[A-Za-z0-9_-]+`.
- Return JSON for graph success and error responses.

Not implemented:

- Region.
- Channel or legacy signal routing.
- Old TZZ adapters.
- Multi-loader storage.
- Full graph editor persistence for arbitrary node create/delete/layout.
- Remote/public administration.

## Storage

Fabric runtime storage lives under the world root:

```text
<world>/pixellogic/
  graphs/
    committed/
      demo-start-flow.json
    drafts/
      demo-start-flow.json
```

The dev API self-check uses a temporary root with the same structure.

Committed and draft files are separate. Invalid drafts cannot replace committed files. Writes use a temporary file and atomic replace when the filesystem supports it.

## Graph JSON

Graph JSON is versioned and readable. It is not a Java object dump.

```json
{
  "schemaVersion": 1,
  "id": "demo-start-flow",
  "displayName": "Demo 开始流程",
  "createdAt": "2026-06-29T00:00:00Z",
  "updatedAt": "2026-06-29T00:00:00Z",
  "fingerprint": "sha256...",
  "nodes": [
    {
      "id": "manual-trigger",
      "type": "MANUAL_TRIGGER",
      "blockId": "trigger.manual_test",
      "displayName": "WebUI 测试运行",
      "config": {},
      "position": { "x": 48, "y": 205 },
      "slots": [
        { "id": "started", "direction": "OUTPUT", "edgeType": "CONTROL" }
      ]
    }
  ],
  "edges": [
    {
      "id": "e1",
      "sourceNodeId": "manual-trigger",
      "sourceSlotId": "started",
      "targetNodeId": "condition-started",
      "targetSlotId": "input",
      "type": "CONTROL"
    }
  ],
  "triggerEntries": {
    "manual.test.start": "manual-trigger"
  }
}
```

`fingerprint` is generated from the graph document with the fingerprint field blanked. v1 returns it to the UI but does not yet enforce `expectedFingerprint` on draft saves.

The WebUI now also uses a local graph version and save sequence before applying save, validate, or commit responses. This prevents stale local responses from overwriting newer edits, but it is not a replacement for future server-side optimistic conflict checks.

`blockId` is the concrete Block Catalog identity for new and normalized nodes. `type` remains in the schema as the v1 compatibility/runtime dispatch field. Old graph documents without `blockId` are still accepted and infer the catalog block from legacy `type` during normalization.

Known message config compatibility:

- Message action nodes store `config.message` as a rich text component payload string containing `version`, derived `plainText`, and `segments`.
- Segment style may include a Minecraft named `color` and boolean style flags for bold, italic, underlined, strikethrough, and obfuscated text.
- `action.message.title`, `action.message.subtitle`, and `action.message.actionbar` use the same message field and are separate blocks.
- Old graph documents with a plain string `config.message` remain valid and are treated as `plainText`.
- The normal WebUI does not expose raw JSON editing or component/segment list management for this field.

Known condition config compatibility:

- New condition nodes store `config.outputMode` as one of `PASS_ONLY`, `FAIL_ONLY`, or `BRANCH`.
- Old graph documents without `outputMode` remain valid and run as `BRANCH`.
- Unconnected condition outputs are valid and mean that path ends.
- Unconnected condition inputs are valid during editing; the block is saved but unreachable until connected into a trigger path.
- The normal WebUI renders this as `条件用途`; catalog blocks may override option labels, for example `拥有标签时继续` / `不拥有标签时继续` / `分开执行` or `是管理员时继续` / `不是管理员时继续` / `分开执行`.
- Context condition blocks use the same `outputMode` field and store only the check target:
  - `condition.player.dimension_is`: `dimensionId`.
  - `condition.player.in_region`: `regionName`.
  - `condition.player.y_compare`: `compareMode`, `targetY`, `minY`, `maxY`.
  - `condition.target_block.is_type`: `blockId`.
  - `condition.target_block.in_region`: `regionName`.
  - `condition.target_block.y_compare`: `compareMode`, `targetY`, `minY`, `maxY`.
  - `condition.player.near_target_block`: `maxDistance`, `horizontalOnly`.

Known entity-target config:

- The three generic entity-tag blocks and `context.entity.execute_as` store one real nested `config.target` object.
- `target.source` is exactly `CURRENT_ENTITY`, `CONDITION_SUBJECT`, `TARGET_ENTITY`, or `ONLINE_PLAYER`.
- `ONLINE_PLAYER` stores canonical `playerUuid` and may store `playerNameHint`; other sources reject both companion fields.
- Missing or malformed target config is invalid. There is no implicit default when decoding a saved Graph and no loose target source/UUID/name sibling fields.
- Retired tag block IDs are unknown; no alias, wrapper or graph migrator accepts them.

```json
{
  "blockId": "action.entity.add_tag",
  "config": {
    "target": { "source": "CURRENT_ENTITY" },
    "tag": "ready"
  }
}
```
- These blocks read per-run `testContext.world` facts during simulation; those facts are not stored in graph JSON.

Known container control-flow compatibility:

- Container Control Flow v1 keeps graph draft JSON flat.
- Loop body membership is saved on child nodes as `parentContainerId` and `parentSlot`.
- The v1 slot is `body`; internal ordering still uses normal control `edges`.
- Draft save, validate, commit, undo, and redo reuse the existing graph path instead of introducing nested graph blobs.
- `control.loop.count` exposes an outer `done` output; `control.loop.forever` has no normal outer next output.

## Endpoints

```text
GET  /api/pixellogic/graphs
GET  /api/pixellogic/graphs/demo-start-flow
GET  /api/pixellogic/graphs/demo-start-flow/draft
PUT  /api/pixellogic/graphs/demo-start-flow/draft
POST /api/pixellogic/graphs/demo-start-flow/validate
POST /api/pixellogic/graphs/demo-start-flow/commit
GET  /api/pixellogic/catalog
```

Success shape:

```json
{
  "ok": true,
  "graph": {},
  "validation": { "valid": true, "issues": [] },
  "fingerprint": "sha256..."
}
```

Error shape:

```json
{
  "ok": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "图验证失败，未提交。"
  }
}
```

## Runtime Semantics

- Draft save does not affect runtime.
- Validate draft does not affect runtime.
- Commit loads the draft, validates it, writes committed JSON, and swaps the runtime compiled graph only after validation passes.
- If validation fails, committed graph and current runtime remain unchanged.
- Test run uses the committed graph, even when a draft exists.
- Test run may receive a per-run `testContext.actor` with display name, tags, and administrator flag plus `testContext.world` facts for player position, optional target block, and simple regions. This context is runtime input only and is not stored in graph JSON.
- Condition runtime follows `outputMode`: `PASS_ONLY` only follows `pass` when true, `FAIL_ONLY` only follows `fail` when false, and `BRANCH` selects `pass` or `fail`.
- If the selected condition output has no edge, runtime records that no next block is connected and ends successfully.
- Context condition runtime evaluates missing target block or missing region as false and records a readable trace explanation.

## WebUI Semantics

The WebUI keeps the Slot-Based horizontal block flow. It loads the graph from the API and edits only a selected block's minimal fields.

The API layer still exposes draft, validate, and commit as separate operations, but the normal user UI does not expose that engineering sequence as separate buttons. The user-facing actions are:

- `保存`: save the current edits, validate them, and promote them only when validation passes.
- `测试运行`: if there are unsaved edits, save/validate/promote them first; then reset the demo test state, start the test run with the current `测试上下文` input, and refresh the trace.

Block fields are edited in a focused modal opened from the Slot-Based canvas. The right panel only shows selected-block information and status.

The modal is a display/control layer over the same graph JSON. It maps internal config values to user-facing Chinese labels, for example `PLAYER` -> `玩家`, `BOOLEAN` -> `是或否`, and `true` / `false` -> `是` / `否`. Saving still writes the original internal values back to the draft graph.

Validation remains fail-closed. If validation fails, the committed graph and runtime graph are not replaced, and the UI shows a Chinese validation error.

The internal draft file can still exist after a failed save so the user can repair the fields and click `保存` again.

The slot flow drag/insert checkpoint keeps the same API contract. The WebUI may now change:

- node `position` metadata after free drag or chain drag;
- `nodes` when adding or deleting a block;
- `edges` when inserting a block into an existing slot connection or disconnecting an input.
- node `blockId` when creating catalog-backed concrete blocks.
- condition node `config.outputMode`, and when confirmed by the user, removal of edges on outputs that become inactive.

These edits are still submitted as the same graph draft JSON. `保存` continues to run draft save, validation, and commit. Invalid drag/edit outcomes do not replace the committed runtime graph.

## Simulation Test Context Payload

`POST /api/pixellogic/test/start` accepts an optional body:

```json
{
  "testContext": {
    "actor": {
      "id": "webui-sim-player",
      "displayName": "WebUI 模拟玩家",
      "tags": ["runner"],
      "operator": false
    },
    "world": {
      "playerPosition": { "dimensionId": "minecraft:overworld", "x": 0, "y": 64, "z": 0 },
      "targetBlock": { "enabled": false, "dimensionId": "minecraft:overworld", "x": 0, "y": 64, "z": 0, "blockId": "minecraft:stone" },
      "regions": [
        { "name": "出生区", "dimensionId": "minecraft:overworld", "minX": 0, "minY": 64, "minZ": 0, "maxX": 10, "maxY": 80, "maxZ": 10 }
      ]
    }
  }
}
```

If the body or actor is missing, the API uses the default `WebUI 模拟玩家`.

Validation:

- display name is trimmed, required when provided, max 64 characters, and cannot contain control characters.
- tags are trimmed, deduplicated, capped at 32 values, max 64 characters each, and cannot contain control characters.
- administrator defaults to `false`.
- dimension ids and block ids must be namespaced ids such as `minecraft:overworld` and `minecraft:stone`.
- coordinates must be integers; x/z are limited to +/-30,000,000 and y to -2048..4096.
- target block defaults to disabled `minecraft:stone @ minecraft:overworld (0, 64, 0)`.
- regions are capped at 8, require a non-empty name up to 64 characters, and normalize min/max bounds.

The response includes the simulation summary:

- actor display name.
- administrator flag.
- player position.
- target block.
- region facts.
- initial actor tags.
- final actor tags after simulated actions.

Generic entity-tag actions change only the resolved entity state for the current run. They do not rewrite graph JSON or the WebUI test-context input; repeated add/remove operations report typed changed/no-change outcomes.

## Lifecycle / Capacity Safety

- Test reset clears the WebUI demo actor's PLAYER state and the manual SESSION state.
- Test reset, graph commit/reload, and server stop invalidate pending timer generations.
- Pending timers are capped at 128 in the spike scheduler.
- Trace history returned by `/api/pixellogic/traces` remains bounded by the backend ring buffer.
- Invalid draft commit remains fail-closed and does not replace the committed graph.
