# PixelLogic WebUI Cleanup v2 + Catalog Expansion v3 Merge Audit

## Verdict

- Ready to merge into mc-1.21.11: yes.
- P0: none found.
- P1: none found.
- P2: `web-ui/src/ui/app.ts` remains the largest frontend orchestration file, but it is smaller than the pre-cleanup baseline and the new extracted modules carry focused responsibilities.
- P3: Height compare `分开执行` under the merged `不低于或不高于` UI uses the current internal threshold direction as the primary `compareMode`.

## Source

- branch: `feature/v1-webui-cleanup-catalog-v3`
- commits:
  - `e1294dd refactor: split context webui orchestration`
  - `c0bc935 feat: add spatial relation condition blocks`
  - `581f5bb docs: document webui cleanup v2 and catalog v3`
  - `3777d30 fix: refine height condition editor controls`
  - `1bad4be fix: simplify height condition labels`
  - `7bb42a3 fix: merge height threshold editor option`
- user review: user hand-tested the current WebUI and accepted the final height-condition UX refinements before merge readiness.

## Phase A: WebUI Cleanup

- app.ts before: 86890 bytes.
- app.ts after: 77892 bytes.
- extracted modules:
  - `web-ui/src/ui/canvas/cardOverflow.ts`
  - `web-ui/src/ui/editor/customDropdown.ts`
  - `web-ui/src/ui/canvas/activeOutput.ts`
  - `web-ui/src/ui/simulation/simulationContextHandlers.ts`
- card overflow: title and summary overflow measurement is isolated in `cardOverflow.ts`; the UI scrolls only when rendered content actually overflows.
- custom dropdown: project-styled select/scope dropdown binding is isolated in `customDropdown.ts`.
- active output insertion: active single-output condition handling is shared through `activeOutput.ts`.
- test context handlers: simulation context modal open/close/save/discard and draft field binding moved into `ui/simulation`.
- mega-file check: no new frontend file approaches `app.ts`; the next largest files are `dragInsert.ts` and `formControls.ts`.

## Phase B: Catalog Expansion v3

- 玩家高度是否满足: `condition.player.y_compare`, read-only condition, category `条件判断 / 玩家条件`.
- 目标方块高度是否满足: `condition.target_block.y_compare`, read-only condition, category `条件判断 / 方块条件`.
- 玩家是否靠近目标方块: `condition.player.near_target_block`, read-only condition, category `条件判断 / 空间关系`.
- no target_block.exists: confirmed absent.
- no X/Z blocks: confirmed absent.
- no area geometry blocks: confirmed absent.

## User-confirmed UX Refinements

- compare mode UI: height conditions show `不低于或不高于`, `等于`, and `在范围内`.
- dynamic condition labels:
  - `不低于或不高于`: `不低于时继续`, `不高于时继续`, `分开执行`.
  - `等于`: `等于时继续`, `不等于时继续`, `分开执行`.
  - `在范围内`: `在范围内时继续`, `不在范围内时继续`, `分开执行`.
- target Y / lowest-highest labels: non-range modes show `目标 Y`; range mode shows `最低 Y 值` and `最高 Y 值`.
- live rerender: changing `compareMode` rerenders the local editor draft immediately.
- player/target height consistency: both height blocks use the same catalog helper, frontend field overrides, validator path, and simulation compare helper.
- allowed product-quality refinements: the UI merge is accepted as a user-confirmed humanized refinement without changing runtime semantics.

## Simulation Semantics

- y compare: `AT_OR_ABOVE`, `AT_OR_BELOW`, `EQUAL`, and inclusive `BETWEEN` remain the internal runtime modes.
- target block missing: target-block height and near-target checks return false with readable trace wording instead of throwing.
- distance: near-target checks `distance <= maxDistance`.
- horizontal only: `true` uses X/Z distance; `false` uses X/Y/Z distance.
- dimension mismatch: near-target returns false with readable trace wording.
- trace: no raw JSON is introduced for these conditions.

## Validation

- compareMode: validated against `AT_OR_ABOVE`, `AT_OR_BELOW`, `EQUAL`, `BETWEEN`.
- outputMode: uses existing `ConditionOutputMode` validation.
- y values: non-range validates `targetY`; range validates `minY <= maxY`.
- distance: `maxDistance` must be greater than 0.
- loose nodes: unconnected inputs and outputs remain valid edit-time graph states.

## Regression Checks

- Catalog v2: covered by existing self-checks and unchanged catalog ids.
- Simulation Context: context remains per-run input and is not graph data.
- Text Component Editor: no code changes in this audit; existing self-check remains required.
- Condition Output Modes: v3 conditions reuse the existing output-mode system.
- drag/insert: active output helper remains shared by layout and drag/insert.
- undo/redo: no graph history code changes in this audit.
- manual save: editor local draft and save path remain unchanged.

## Validation Commands

- git diff --check: pass.
- gradlew build: pass.
- npm install: pass.
- npm run build: pass.
- all self-checks: pass.
- catalogExpansionV3SelfCheck: pass.
- grep: pass. `/say` and raw JSON matches are docs-only boundary notes; source paths remain clean.
- largest files:
  - `web-ui/src/ui/app.ts`: 77993 bytes.
  - `web-ui/src/ui/canvas/dragInsert.ts`: 19139 bytes.
  - `web-ui/src/ui/editor/formControls.ts`: 17257 bytes.
  - `src/main/java/com/pixelmc/pixellogic/core/catalog/BuiltInBlockCatalog.java`: 40309 bytes.
  - `src/main/java/com/pixelmc/pixellogic/core/graph/GraphValidator.java`: 21957 bytes.
  - `src/main/java/com/pixelmc/pixellogic/core/simulation/executor/SimulationExecutionRegistry.java`: 21677 bytes.

## Boundaries

- no actions: pass.
- no target_block.exists: pass.
- no X/Z coordinate blocks: pass.
- no area geometry: pass.
- no named scenario: pass.
- no full world simulation: pass.
- no MC adapter: pass.
- no Admin Client Bridge: pass.
- no Channel: pass.
- no old TZZ: pass.
- no tag: pass.
- no release: pass.

## Known Limitations

- `app.ts` is still large and should keep shrinking in future scoped cleanup prompts, but this branch reduces it and does not create a larger replacement file.
- Built-in catalog and self-check classes remain large accumulated registries/tests; future catalog data extraction can be scoped separately.
- Height compare `分开执行` under the merged threshold UI uses the currently stored threshold direction. This is deliberate and documented; choosing a different direction should be an explicit UI action.

## Final Recommendation

Merge `feature/v1-webui-cleanup-catalog-v3` into `mc-1.21.11`.
