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

## Endpoints

```text
GET  /api/pixellogic/graphs
GET  /api/pixellogic/graphs/demo-start-flow
GET  /api/pixellogic/graphs/demo-start-flow/draft
PUT  /api/pixellogic/graphs/demo-start-flow/draft
POST /api/pixellogic/graphs/demo-start-flow/validate
POST /api/pixellogic/graphs/demo-start-flow/commit
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

## WebUI Semantics

The WebUI keeps the Slot-Based horizontal block flow. It loads the graph from the API, edits only a selected block's minimal fields, and shows draft state in the right panel/bottom dock.

User-facing distinction:

- `保存草稿` saves a draft only.
- `校验草稿` validates the draft.
- `提交生效` validates and promotes the draft to committed runtime graph.
- `测试运行` uses committed graph.

When there is a draft or unsaved edit, the UI warns:

```text
当前有未提交草稿，测试运行仍使用已提交版本。
```
