# PixelLogic API + WebUI Test Run Integration Audit

## Verdict

- Ready to merge into `mc-1.21.11`: yes
- P0: 0
- P1: 0
- P2: 2
- P3: 2

## Git / Baseline

- audited branch: `feature/v1-api-webui-test-run`
- audited implementation commit: `a098c9793df8b618f5eb2da648075a2654743634`
- compared against: `origin/mc-1.21.11` at `0ad4dfbc0e3d1554b21f9da7424ee9c1c740d67f`
- worktree status before audit report: clean except ignored build/report outputs

## API Findings

- Bind address: pass. `PixelLogicApiServer.DEFAULT_HOST` is `127.0.0.1`; the default server is not bound to `0.0.0.0`.
- Lifecycle start/stop: pass for spike. Fabric registers `SERVER_STARTED` to start the API and `SERVER_STOPPING` to close the API server and spike service.
- Duplicate start: pass. `FabricPixelLogicBootstrap.startApi` returns early when `apiServer` already exists.
- Server-thread handoff: pass. API handlers call `onServerThread`, then execute runtime service operations through the supplied server executor.
- Endpoints: pass. The implementation exposes only:
  - `GET /api/pixellogic/status`
  - `POST /api/pixellogic/test/reset`
  - `POST /api/pixellogic/test/start`
  - `GET /api/pixellogic/traces/latest`
  - `GET /api/pixellogic/traces`
- JSON response shape: pass. Success and error paths return JSON; errors include `code` and `message`.
- Demo actor: pass. WebUI simulation uses the fixed `WebUI 模拟玩家` UUID and does not query real online players.
- Trace bounds: pass. `BoundedTraceBuffer` keeps 50 traces with 100 steps per trace; `/traces` returns this bounded set.

## WebUI Findings

- Real API connected: pass. The WebUI calls `/api/pixellogic/status`, `/test/reset`, `/test/start`, `/traces/latest`, and `/traces`.
- Test-run/reset/latest trace/status: pass. Buttons are wired to real API calls and render trace messages in the bottom execution dock.
- Vite proxy: pass. Dev proxy maps `/api` to `http://127.0.0.1:18111` and uses `host: 127.0.0.1`.
- UI boundary: pass. The UI presents a test-run workbench and does not claim graph persistence or full editing is complete.
- No old concepts visible: pass. WebUI source does not contain Channel, SignalBridge, Relay, Listener, Region, or old TZZ concepts.

## Runtime Boundary Findings

- No graph save/load: pass. The API only wraps the in-memory demo graph through `PixelLogicSpikeService`.
- No Region: pass. No Region runtime/API/WebUI implementation was introduced.
- No Channel: pass. No Channel model or event-bus routing was introduced.
- No old TZZ: pass. No old TZZ gameplay/API adapter code was introduced.
- Timer capacity: pass for spike. `WallClockTimerScheduler` rejects above `DEFAULT_MAX_PENDING_TIMERS = 128`.
- Timer scheduling model: pass. The scheduler remains wall-clock based and does not add per-tick scans.
- Timer/server-thread safety: pass. Timer due callbacks still hand continuation back through the server-thread executor.

## Security / Exposure Findings

- Localhost only: pass. Default API binding is `127.0.0.1:18111`.
- CORS/proxy: pass. No broad CORS header is added; local WebUI development uses Vite proxy.
- Remote management: pass. No remote/admin exposure was added.

## Maintainability Findings

- Package structure: pass. API code lives in `server/api`; loader lifecycle remains in `loader/fabric`; runtime service is reused instead of copied.
- Duplicated logic: pass. Runtime execution still routes through `PixelLogicSpikeService` and `GraphRuntime`.
- Comments: acceptable for spike. Public docs explain API boundary and demo actor semantics.
- File/function size: acceptable for spike. `PixelLogicApiServer` is the largest new Java class; if the API expands beyond the five spike endpoints, split routing/JSON serialization.

## Validation Results

- `git diff --check`: pass
- `.\gradlew.bat build`: pass
- `manualSimulationSelfCheck`: pass through `check`
- `apiWebUiSelfCheck`: pass through `check`
- `npm install`: pass, 0 vulnerabilities
- `npm run build`: pass
- command root grep: pass. Only `/pixellogic` command tree is registered; grep match is the `test` child literal under `pixellogic`.
- Channel grep: pass. No implementation matches in `src/main/java` or `web-ui/src`.

## Issues

### P2 - API server lacks an explicit closed fence for in-flight shutdown

- evidence:
  - `src/main/java/com/pixelmc/pixellogic/server/api/PixelLogicApiServer.java`
  - `src/main/java/com/pixelmc/pixellogic/loader/fabric/FabricPixelLogicBootstrap.java`
- why it matters: `SERVER_STOPPING` closes the `HttpServer` before closing the spike service, which blocks new requests. However, the API wrapper does not keep its own `closed` flag for an exchange already accepted during shutdown.
- risk: low for this localhost-only spike API, but a future remote/admin API should reject in-flight work once shutdown starts.
- blocking? no

### P2 - In-memory state lifecycle/capacity remains a broader-runtime follow-up

- evidence:
  - `src/main/java/com/pixelmc/pixellogic/core/state/InMemoryStateStore.java`
  - `docs/ROADMAP.md`
- why it matters: Reset clears the demo actor's current spike keys, but there is still no general player/session lifecycle or capacity policy.
- risk: acceptable for a fixed demo actor; not enough for broader runtime use.
- blocking? no

### P3 - TraceStep remains a minimal UI-readable schema

- evidence:
  - `src/main/java/com/pixelmc/pixellogic/core/trace/TraceStep.java`
- why it matters: Current WebUI can display messages, but filtering and rich debugging will need graph id, node type, selected slot, and structured error fields.
- blocking? no

### P3 - API JSON is manually assembled

- evidence:
  - `src/main/java/com/pixelmc/pixellogic/server/api/PixelLogicApiServer.java`
- why it matters: Manual JSON is acceptable for five fixed spike endpoints and avoids adding dependencies, but it should be replaced by a structured serializer if the API grows.
- blocking? no

## Final Recommendation

Merge into `mc-1.21.11`.

This integration is merge-ready as a localhost-only API/WebUI test-run checkpoint. It does not implement graph save/load, Region, Channel, old TZZ adapters, remote management, tags, or releases.
