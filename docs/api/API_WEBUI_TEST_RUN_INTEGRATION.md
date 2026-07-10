# PixelLogic API + WebUI Test Run Integration

This document records the v1 spike API used by the WebUI test-run panel.

It is not the final graph persistence API. It only exposes the already merged manual simulation runtime spike.

## Scope

Implemented:

- Local API server bound to `127.0.0.1:18111`.
- WebUI buttons for status, test run, reset, and latest trace.
- JSON responses for success and errors.
- Vite dev proxy from `/api` to `http://127.0.0.1:18111`.
- WebUI demo actor for PLAYER-scoped state.
- Spike-level pending timer capacity limit.
- Suspended-run status lookup and finite WebUI polling for asynchronous continuation results.

Not implemented:

- Project or graph save/load.
- Arbitrary graph editing API.
- Region.
- Channel or legacy adapter.
- Remote administration.
- Multi-loader API.

## Endpoints

```text
GET  /api/pixellogic/status
POST /api/pixellogic/test/reset
POST /api/pixellogic/test/start
GET  /api/pixellogic/test/runs/{runId}
GET  /api/pixellogic/traces/latest
GET  /api/pixellogic/traces
```

All responses are JSON.

`POST /test/start` reuses the trace id as `runId` and returns `runStatus` plus `terminal`. A suspended timer returns `WAITING`; the WebUI queries the run endpoint every 750 ms and replaces its bounded trace/result snapshot. Polling stops on `COMPLETED`, `FAILED`, `CANCELLED`, a new run, page exit, or three consecutive request failures. No WebSocket, SSE, persistent run history, or unbounded registry is used.

Only the current simulation result is queryable. Reset, graph replacement, a newer run, and service stop cancel the prior non-terminal result; sequence guards prevent late callbacks and stale browser responses from replacing the current run.

Example success:

```json
{
  "ok": true,
  "message": "执行完成。",
  "traceId": "trace-id",
  "trace": {
    "id": "trace-id",
    "truncated": false,
    "steps": [
      {
        "timestamp": "2026-06-29T00:00:00Z",
        "nodeId": "condition-started",
        "message": "条件通过：PLAYER.started == false"
      }
    ]
  }
}
```

Example error:

```json
{
  "ok": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "API endpoint 不存在。"
  }
}
```

## Test Actor

WebUI test-run defaults to:

```text
WebUI 模拟玩家
```

The WebUI can send a per-run test actor with display name, tags, and administrator status in `POST /api/pixellogic/test/start`. This is a PLAYER-scope simulation identity for browser testing. It is not an online Minecraft player and does not touch real player objects.

The reset endpoint clears the WebUI demo actor's `PLAYER.started` and `PLAYER.start_count` state. The test actor context itself is not persisted as a scenario and is not written to graph JSON.

## Runtime Threading

In Fabric, HTTP request handlers hand runtime actions to the Minecraft server executor before mutating runtime state. Timer due callbacks still resume through the existing server-thread handoff.

The local dev API task uses the same runtime service with a direct executor for browser self-test outside Minecraft.

## CORS / Proxy

The WebUI dev server uses Vite proxy:

```text
/api -> http://127.0.0.1:18111
```

The API does not open broad public CORS. The server binds to localhost only.

## Timer Capacity

The wall-clock timer scheduler now rejects new timers above the spike default:

```text
max pending timers = 128
```

Rejection fails closed through the runtime trace/API error path. This is a spike limit, not the final runtime capacity policy.
