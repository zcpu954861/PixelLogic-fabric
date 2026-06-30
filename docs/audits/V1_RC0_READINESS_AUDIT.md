# PixelLogic v1 RC0 Readiness Audit

Date: 2026-06-30

Scope: audit only. No release, no tag, no merge into `mc-1.21.11`, and no feature work.

## Verdict

- RC0 ready: yes
- P0: none
- P1: none
- P2:
  - WebUI static assets are not packaged into the jar yet. Current RC0 hand-test mode is Fabric/API plus Vite dev server proxying `/api` to `127.0.0.1:18111`.
- P3:
  - `web-ui/src/ui/app.ts` remains a large orchestration file at 48107 bytes. It is not blocking RC0, but future WebUI work should keep deleting or moving cohesive pieces out of it.
  - `docs/ROADMAP.md` still described lifecycle/capacity cleanup as pre-merge work; this audit branch updates that status.

## Baseline

- branch: `audit/v1-rc0-readiness`
- commit: `50d371e0a8e80852c6eb62a9e0657c048b593eac` (`merge: harden lifecycle and capacity limits`)
- license: Apache License 2.0 full text is present in `LICENSE`.
- mod metadata: `fabric.mod.json` keeps `id: pixel-logic`, `name: PixelLogic`, `license: Apache-2.0`, package entrypoint `com.pixelmc.pixellogic.PixelLogicMod`, Minecraft `1.21.11`, Java `>=21`.
- build artifact: `build/libs/pixel-logic-0.1.0.jar` and `build/libs/pixel-logic-0.1.0-sources.jar` were produced. The main jar contains `fabric.mod.json`, lang assets, API/WebUI self-check classes, and `webadmin/.gitkeep`.

## Capability Coverage

- graph draft/commit: present. Committed and draft graphs are separated; commit validates before replacing committed/runtime graph.
- modal editor: present. Single click selects/starts drag, double click opens the modal editor.
- humanized form: present. Normal UI uses Chinese labels and controls instead of raw enum/internal values.
- drag insert: present. Chain drag, middle insertion, chain-end append, free-input connection, green glow insertion gap, and visual snapped-state edge sync are implemented.
- auto save: present. Edits auto save, validate, and commit through the existing queue.
- undo/redo: present. `上一步` / `下一步`, `Ctrl+Z`, `Ctrl+Y`, and `Ctrl+Shift+Z` are present; undo/redo history caps at 80 each.
- lifecycle safety: present. State, timer, trace, autosave, reset, graph reinstall, and shutdown guards are in place.
- API/WebUI: present. Localhost API endpoints return JSON and the Vite dev server proxies `/api`.

## Security / Safety

- API bind: pass. API defaults to `127.0.0.1:18111`; no `0.0.0.0` binding found.
- invalid graph fail-closed: pass. Invalid draft validation does not replace committed graph; self-check covers this.
- autosave stale guard: pass. Frontend uses graph version plus save sequence, one in-flight save, and a latest-rerun flag.
- timer generation guard: pass. Runtime/timer continuations carry generation and stale callbacks are rejected.
- capacity limits: pass. State entries cap at 1024, pending timers at 128, trace records at 50, trace steps at 100, and frontend rendered trace steps at 100.
- repository hygiene: pass. `.gitignore` covers `.gradle/`, `build/`, `run/`, `world/`, `logs/`, `reports/`, `web-ui/node_modules/`, and `web-ui/dist/`.

## Validation

- git diff --check: pass
- gradlew build: pass
- npm install: pass
- npm run build: pass
- self-checks: pass
  - `apiWebUiSelfCheck`
  - `graphStorageSelfCheck`
  - `manualSimulationSelfCheck`
- grep old labels: pass. No matches for `保存草稿|校验草稿|提交生效|重置测试状态` in `web-ui/src`.
- grep enum UI: pass. No matches for visible `>PLAYER<|>GLOBAL<|>SESSION<|>BOOLEAN<|>INTEGER<|>STRING<|>true<|>false<` in `web-ui/src`.
- grep Channel: pass. No `Channel|SignalBridge|SignalListener|ActionRelay|Receiver|Relay` in `src/main/java` or `web-ui/src`.
- command root grep: pass. Only `literal("test")` appears under the existing `/pixellogic` root.
- build/libs:
  - `pixel-logic-0.1.0.jar`: 120910 bytes
  - `pixel-logic-0.1.0-sources.jar`: 44620 bytes
- largest files:
  - `web-ui/src/main.ts`: 103 bytes
  - `web-ui/src/ui/app.ts`: 48107 bytes
  - `web-ui/src/ui/canvas/dragInsert.ts`: 19076 bytes
  - `web-ui/src/model/graphLayout.ts`: 11271 bytes

## Manual Test

- performed: no
- summary: Codex did not run browser or in-game manual testing in this audit turn.
- not performed items:
  - Minecraft client/server hand test.
  - Browser visual screenshot/self-test.
  - API disconnect visual confirmation.
  - Drag/edit/undo hand interaction loop.

## Issues

### P0

None.

### P1

None.

### P2

- WebUI build output remains external to the jar. For RC0 hand testing, use `.\gradlew.bat apiWebUiDevServer` and `npm run dev` from `web-ui`; packaging WebUI static assets into the mod jar is still a later release-prep decision.

### P3

- `web-ui/src/ui/app.ts` is still large at 48107 bytes. It is acceptable for RC0, but future WebUI work should keep trimming it when touching nearby behavior.
- Roadmap lifecycle/capacity status was stale and is updated in this audit branch.

## Recommendation

- Ready for RC0 branch/tag/release preparation: yes
- Required fixes before RC0: none found in this audit.
- Suggested fixes after RC0:
  - Decide whether RC0 distribution should bundle WebUI static assets in the jar or remain a developer Vite proxy workflow.
  - Continue gradual WebUI orchestration cleanup only when a real adjacent change justifies it.
