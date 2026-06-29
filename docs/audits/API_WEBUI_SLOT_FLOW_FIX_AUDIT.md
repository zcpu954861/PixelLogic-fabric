# PixelLogic API WebUI Slot Flow Fix Audit

## Verdict

- Ready to merge into `mc-1.21.11`: yes
- P0: 0
- P1: 0

## Problem

After the API-backed WebUI test-run checkpoint was merged, two regressions were found:

- The WebUI could receive Vite fallback HTML from `/api` and then fail with a raw JSON parse error such as `Unexpected token '<'`.
- The WebUI presentation had drifted away from the user-confirmed Slot-Based horizontal block flow.

## Fix

- Restored the WebUI to the user-confirmed Slot-Based horizontal block flow baseline: large puzzle-like blocks, left-to-right layout, a tall Condition block, pass/fail branch slots, left rail, right properties panel, and bottom trace dock.
- Kept the WebUI scope to status, test run, reset, and latest trace. No graph save/load or commit API was added.
- Updated the frontend API wrapper so it sends `Accept: application/json`, checks `Content-Type` before `response.json()`, catches JSON parse failures, and displays a Chinese `API 未连接` error instead of exposing a raw parse exception.
- Moved the JDK `HttpServer` context from `/api/pixellogic` to `/api` so `/api` and unknown `/api/*` paths return JSON error envelopes instead of default HTML.
- Extended `ApiWebUiSelfCheck` to assert that `GET /api` returns a JSON `NOT_FOUND` envelope.

## Boundary Audit

- No graph save/load added.
- No Region added.
- No Channel / SignalBridge / SignalListener / ActionRelay / Receiver / Relay added.
- No old TZZ adapter added.
- No runtime semantics changed.
- API remains localhost-only through `127.0.0.1:18111`.
- Vite proxy remains `/api -> http://127.0.0.1:18111`.

## Validation

- `git diff --check`: pass
- `.\gradlew.bat build`: pass
- `npm install`: pass
- `npm run build`: pass
- Command root grep: pass. Only `/pixellogic` is registered; `test` is a child command.
- Channel grep: pass. No Channel/Relay legacy concepts found in Java or WebUI source.
- Browser self-test: pass. Screenshots and report are under `reports/fix-api-webui-slot-flow/`.

## Browser Self-Test Coverage

- API connected state.
- Test run updates trace.
- Reset then run reaches pass branch.
- Second run reaches fail branch.
- Slot-Based horizontal block flow remains visible.
- API disconnected state shows Chinese error and no `Unexpected token` / JSON parse console error.

## Follow-Up

The next product checkpoint remains graph save/load plus draft/validate/commit. This fix deliberately does not start that work.
