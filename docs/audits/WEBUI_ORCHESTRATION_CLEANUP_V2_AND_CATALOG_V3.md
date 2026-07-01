# PixelLogic WebUI Cleanup v2 + Catalog Expansion v3

## Phase A: WebUI Orchestration Cleanup v2

- app.ts before: 86890 bytes.
- app.ts after Phase A: 76556 bytes.
- extracted modules:
  - `web-ui/src/ui/canvas/cardOverflow.ts`
  - `web-ui/src/ui/editor/customDropdown.ts`
  - `web-ui/src/ui/canvas/activeOutput.ts`
  - `web-ui/src/ui/simulation/simulationContextHandlers.ts`
- card overflow logic: title/body overflow measurement moved out of `app.ts`; behavior remains actual-overflow-only marquee.
- custom dropdown logic: project-styled dropdown binding moved out of `app.ts`; no native select UI is introduced.
- active output insertion logic: `preferredMainOutput` moved to a canvas helper and remains shared by layout and drag/insert.
- test context handlers: modal open/close/save/discard and draft field binding moved to `ui/simulation`.

## Phase B: Catalog Expansion v3

- 玩家高度是否满足: `condition.player.y_compare`, category `条件判断 / 玩家条件`.
- 目标方块高度是否满足: `condition.target_block.y_compare`, category `条件判断 / 方块条件`.
- 玩家是否靠近目标方块: `condition.player.near_target_block`, category `条件判断 / 空间关系`.
- no target_block.exists: not added.
- no X/Z coordinate blocks: not added.
- no area geometry blocks: not added.

## Simulation Semantics

- height compare: `AT_OR_ABOVE`, `AT_OR_BELOW`, `EQUAL`, and inclusive `BETWEEN`.
- target block missing: target-block conditions evaluate false and trace `未设置目标方块`.
- distance: near-target compares `distance <= maxDistance`.
- horizontal only: true uses X/Z only; false uses X/Y/Z.
- dimension mismatch: false result with readable trace.

## Validation

- compare mode: illegal values fail.
- y values: active Y fields must be integers; `BETWEEN` requires `minY <= maxY`.
- distance: `maxDistance` must be greater than 0.
- loose nodes: unconnected condition input/output semantics remain valid.

## Regression

- Catalog v2: covered by existing `catalogExpansionV2SelfCheck` and nested v3 regression call.
- Text Component Editor: covered by existing `textComponentEditorSelfCheck` and nested v3 regression call.
- Simulation Test Context: unchanged; context facts remain per-run input, not graph data.

## Validation Commands

- `git diff --check`
- `.\gradlew.bat build`
- `cd web-ui && npm install && npm run build`
- `.\gradlew.bat apiWebUiSelfCheck`
- `.\gradlew.bat graphStorageSelfCheck`
- `.\gradlew.bat manualSimulationSelfCheck`
- `.\gradlew.bat blockCatalogSelfCheck`
- `.\gradlew.bat simulationBackendSelfCheck`
- `.\gradlew.bat conditionOutputModeSelfCheck`
- `.\gradlew.bat simulationTestContextSelfCheck`
- `.\gradlew.bat catalogExpansionV1SelfCheck`
- `.\gradlew.bat textComponentEditorSelfCheck`
- `.\gradlew.bat simulationContextExpansionSelfCheck`
- `.\gradlew.bat catalogExpansionV2SelfCheck`
- `.\gradlew.bat catalogExpansionV3SelfCheck`
- grep checks for old Channel/TZZ/raw JSON/source leakage and `core/simulation` Minecraft dependencies.
