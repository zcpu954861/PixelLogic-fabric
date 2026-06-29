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

The UI renders the graph as the confirmed Slot-Based horizontal block flow. The right panel edits only selected-block fields such as display name, message text, state key/value, and timer seconds.

Important user semantics:

- Save draft does not change runtime.
- Validate draft does not change runtime.
- Commit promotes only a valid draft.
- Test run always uses the committed graph.
- When a draft or unsaved edit exists, the bottom dock warns that test run still uses the committed version.

## Boundary

Java may serve built static assets later, but Java must not generate WebUI source strings.

The normal user flow must expose visual graph relationships instead of channel names.
