# PixelLogic Simulation Test Context MVP

## Scope

- WebUI test actor: a per-run simulated player context sent with `POST /api/pixellogic/test/start`.
- Display name: editable in the WebUI and returned in `SimulationExecutionResult`.
- Tags: editable as simple chips, trimmed/deduplicated, and used by the current generic entity-tag condition when targeting the test actor.
- Administrator flag: editable and returned with the run result; no OP-only block exists yet.
- Run payload: optional `testContext.actor`; old no-body requests still use `WebUI 模拟玩家`.
- Result summary: returns initial tags, final tags, display name, admin flag, trace id, and status.

## Architecture

- Test context is per-run input.
- It is not graph data and is not written into committed or draft graph JSON.
- It is not named scenario persistence.
- `SimulationRunner` still calls the real `GraphRuntime`.
- The current generic add-tag action mutates only the explicitly resolved test entity for the current run result.
- `/pixellogic test start` keeps the default simulated player path.
- No Minecraft adapter is added.

## UX

- The `测试运行` control is a split button: left side runs the test, right arrow opens a dropdown.
- The dropdown contains short helper copy and `编辑测试玩家`.
- `编辑测试玩家` opens a modal that follows the block editor pattern: local draft, `保存`, close animation, and unsaved-close confirmation.
- Labels are Chinese: `玩家名`, `标签`, `管理员`, `恢复默认`.
- Tags use chips with a left-side `×` remove button, plus a small input and `添加` button.
- The right panel keeps a read-only run result summary with `本次玩家`, `初始标签`, `结束标签`, `标签变化`, and `管理员`.
- Result tags are not written back into the test context inputs.

## Validation

- `.\gradlew.bat simulationTestContextSelfCheck`: covers default request, custom display name, request tags, empty tags, add-tag result, non-persistence, admin flag, tag trim/dedupe, and invalid tag rejection.
- `.\gradlew.bat build`: required before merge readiness.
- `npm install` and `npm run build`: required before merge readiness.
- Existing self-checks remain required: API/WebUI, graph storage, manual simulation, block catalog, simulation backend, and condition output modes.

## Known Limitations

- No named scenarios.
- No persistence.
- No multiplayer simulation.
- No inventory, world, or container context.
- No game mode in this MVP.
- No real OP/permission-sensitive block behavior yet.
