# WebUI Architecture

The WebUI is an independent Vite + TypeScript project.

## Current Skeleton

- Vanilla TypeScript.
- No React, Vue, or Svelte until the user confirms a framework.
- Slot-based horizontal flow surface for the v1 demo graph.
- API-backed status, test run, reset, latest trace, and recent traces.
- No graph editor persistence yet.
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

## Boundary

Java may serve built static assets later, but Java must not generate WebUI source strings.

The normal user flow must expose visual graph relationships instead of channel names.
