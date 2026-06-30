# PixelLogic Maintainability Cleanup v1

## Scope

- app.ts split: extracted canvas slot-flow view-model building and catalog-library rendering.
- SelfCheckSupport: shared minimal `run` / `require` helper for existing self-check entrypoints.
- catalog fallback: frontend fallback catalog is now an API-offline placeholder instead of a full copy of Java `BuiltInBlockCatalog`.
- formControls legacy fallback: catalog schema remains primary, with legacy `NodeType` fallback only when no known or uniquely inferred catalog block exists.
- SimulationRunOptions: removed the unused `fastForwardTimers` future switch; current runtime option only records real-time timer scheduling.
- RuntimeServices NodeExecution: moved nested `RuntimeServices.NodeExecution` to top-level `RuntimeNodeExecutionResult`.

## Non-goals

- no GraphDocument rewrite: preserved.
- no capability metadata deletion: preserved.
- no SimulationBlockExecutor deletion: preserved.
- no docs/audits purge: preserved.
- no Text Component Editor: preserved.
- no new blocks, Region, Channel, old TZZ, MC adapter, tag, release, or merge.

## app.ts split

- before size: 66,384 bytes.
- after size: 61,788 bytes.
- modules extracted:
  - `web-ui/src/ui/canvas/slotFlowViewModel.ts`
  - `web-ui/src/ui/catalog/catalogLibrary.ts`
- largest frontend files:
  - `web-ui/src/ui/app.ts`: 61,788 bytes
  - `web-ui/src/ui/canvas/dragInsert.ts`: 19,105 bytes
  - `web-ui/src/ui/editor/formControls.ts`: 12,021 bytes
  - `web-ui/src/model/graphLayout.ts`: 11,806 bytes
  - `web-ui/src/ui/humanize/labels.ts`: 9,406 bytes

## SelfCheck cleanup

- support helper: `src/main/java/com/pixelmc/pixellogic/selfcheck/SelfCheckSupport.java`.
- tasks preserved: existing Gradle task names are unchanged.
- duplicated boilerplate removed: local `require` helpers were removed from the eight current `*SelfCheck.java` files.

## Catalog fallback

- backend catalog authority: `GET /api/pixellogic/catalog` remains the full block catalog source.
- frontend fallback reduced: `fallbackCatalog` now contains only an offline category and no block definitions.
- offline behavior: the library can render an API-disconnected placeholder instead of pretending that the catalog is complete.

## Legacy form fallback

- catalog schema primary: known `blockId` resolves through catalog schema.
- remaining fallback: old or unknown nodes still use minimal legacy `NodeType` fields; nodes without `blockId` only infer catalog schema when `nodeType` maps to exactly one catalog block.

## Runtime cleanup

- SimulationRunOptions: removed `fastForwardTimers`; no timer fast-forward behavior was added.
- NodeExecution: top-level `RuntimeNodeExecutionResult` reduces `RuntimeServices` interface noise without changing trace/output slot semantics.

## Validation

- git diff --check: pass.
- gradlew build: pass.
- npm install: pass.
- npm run build: pass.
- self-checks: pass.
  - `apiWebUiSelfCheck`
  - `graphStorageSelfCheck`
  - `manualSimulationSelfCheck`
  - `blockCatalogSelfCheck`
  - `simulationBackendSelfCheck`
  - `conditionOutputModeSelfCheck`
  - `simulationTestContextSelfCheck`
  - `catalogExpansionV1SelfCheck`
- grep:
  - Channel / SignalBridge / SignalListener / ActionRelay / Receiver / Relay: no source hits.
  - hidden draft/reset copy: no WebUI hits.
  - raw enum/true/false UI text pattern: no WebUI hits.
  - checkbox: no WebUI hits.
  - old negative/tag/title combined copy: no source hits.
  - `/say` / raw JSON: docs-only boundary/history hits.
  - command literal grep: only existing `/pixellogic test` subcommand hit.
  - `net.minecraft` in `core/simulation`: no hits.
  - `fastForwardTimers`: no hits; `realTimeTimers` remains as the current timer scheduling option.

## Known Limitations

- `web-ui/src/ui/app.ts` remains the largest frontend composition root and still owns event binding, autosave, undo/redo, and API action orchestration.
- The WebUI still formats summaries client-side from catalog metadata; backend trace formatting remains in runtime/executors.
