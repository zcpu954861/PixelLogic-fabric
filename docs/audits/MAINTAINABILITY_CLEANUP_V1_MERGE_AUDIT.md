# PixelLogic Maintainability Cleanup v1 Merge Audit

## Verdict
- Ready to merge into mc-1.21.11: yes
- P0: none
- P1: none
- P2: app.ts is still the largest frontend file and remains a future split target.
- P3: legacy NodeType fallback labels remain for old or unknown graph nodes.

## Source
- branch: `feature/v1-maintainability-cleanup`
- commits: `9fad8dc refactor: improve maintainability cleanup v1`
- user review: user hand-tested this feature version and reported no obvious issue.
- browser self-check:
  - not repeated in audit stage

## Scope Check
- app.ts split: pass; slot-flow view model and catalog library rendering were extracted.
- SelfCheckSupport: pass; helper only wraps `run` and `require`.
- catalog fallback: pass; frontend fallback is an API-offline placeholder, not a copied registry.
- formControls legacy fallback: pass; catalog schema is primary, unique legacy nodeType inference is guarded, and fallback remains for old or unknown nodes.
- SimulationRunOptions: pass; the unused `fastForwardTimers` switch was removed.
- RuntimeNodeExecutionResult: pass; runtime result structure was lifted out of `RuntimeServices`.
- docs: pass; architecture/spec/audit docs describe the cleanup scope.

## app.ts Split
- before size: 66384 bytes
- after size: 61788 bytes
- extracted modules:
  - `web-ui/src/ui/canvas/slotFlowViewModel.ts`: 2546 bytes
  - `web-ui/src/ui/catalog/catalogLibrary.ts`: 2229 bytes
- largest frontend files:
  - `web-ui/src/ui/app.ts`: 61788 bytes
  - `web-ui/src/ui/canvas/dragInsert.ts`: 19105 bytes
  - `web-ui/src/ui/editor/formControls.ts`: 12021 bytes
  - `web-ui/src/model/graphLayout.ts`: 11806 bytes
  - `web-ui/src/ui/humanize/labels.ts`: 9406 bytes
- mega-file risk: no new 50KB+ replacement file was introduced.

## SelfCheck Cleanup
- support helper: `SelfCheckSupport` is intentionally tiny and not a new test framework.
- preserved task names:
  - `apiWebUiSelfCheck`
  - `graphStorageSelfCheck`
  - `manualSimulationSelfCheck`
  - `blockCatalogSelfCheck`
  - `simulationBackendSelfCheck`
  - `conditionOutputModeSelfCheck`
  - `simulationTestContextSelfCheck`
  - `catalogExpansionV1SelfCheck`
- individual entry points: pass; every `*SelfCheck.java` still has its own `main`.
- build.gradle: pass; task names are unchanged and still attached to `check`.
- failure behavior: pass; failed requirements throw and return a non-zero Gradle task.

## Catalog Fallback
- backend catalog authority: pass; Java `BuiltInBlockCatalog` and `/api/pixellogic/catalog` remain authoritative.
- frontend fallback reduced: pass; `fallbackCatalog` contains one offline category and no blocks.
- offline API behavior: pass; UI can show the API disconnected/offline placeholder instead of a fake full catalog.
- no complex generation: pass; no catalog generation system was added.

## Legacy Form Fallback
- catalog schema primary: pass.
- unique nodeType inference: pass; nodes without `blockId` infer schema only when exactly one catalog block matches the legacy type.
- remaining legacy fallback: pass; old or unknown nodes still render editable legacy fields.
- old graph compatibility: pass; self-checks and docs keep old graph compatibility intact.

## Runtime Cleanup
- SimulationRunOptions: only `realTimeTimers` remains.
- fastForwardTimers: removed from runtime source; docs mention fast-forward only as future/out-of-scope context.
- timer behavior: unchanged wall-clock behavior; generation guard and pending-timer bounds remain covered by self-checks.
- RuntimeNodeExecutionResult: top-level record with `outputSlot` and `traceMessage`.
- GraphRuntime semantics: unchanged; simulation hook still returns optional node execution result before normal runtime dispatch.

## Regression Checks
- test run split button: preserved by code scope; not browser-repeated in this audit stage.
- edit test player modal: preserved by code scope; not browser-repeated in this audit stage.
- condition output modes: pass by `conditionOutputModeSelfCheck`.
- catalog expansion v1: pass by `catalogExpansionV1SelfCheck`.
- rich_text_component: pass; schema-driven and legacy message fields still use the rich text control.
- drag/insert: code scope preserved; `dragInsert.ts` was not rewritten.
- undo/redo: code scope preserved; app state/history behavior was not rewritten.
- manual save: code scope preserved; no draft/save wording regression in source grep.

## Validation
- git diff --check: pass
- gradlew build: pass
- npm install: pass, 0 vulnerabilities
- npm run build: pass
- apiWebUiSelfCheck: pass
- graphStorageSelfCheck: pass
- manualSimulationSelfCheck: pass
- blockCatalogSelfCheck: pass
- simulationBackendSelfCheck: pass
- conditionOutputModeSelfCheck: pass
- simulationTestContextSelfCheck: pass
- catalogExpansionV1SelfCheck: pass
- grep old terms: pass; no old negative/title wording in source.
- grep fastForwardTimers/realTimeTimers: pass; `fastForwardTimers` absent from runtime source, `realTimeTimers` remains as current option.
- grep Channel: pass; no Channel/Relay legacy terms in Java or WebUI source.
- grep core/simulation MC deps: pass; no `net.minecraft` under `core/simulation`.
- largest files: reviewed; no new mega-file risk.

## Explicit Non-goals Preserved
- no GraphDocument rewrite: pass
- no BlockCapability/Safety deletion: pass
- no SimulationBlockExecutor deletion: pass
- no docs/audits purge: pass
- no Text Component Editor: pass
- no MC adapter: pass
- no inventory/world/container: pass
- no Channel: pass
- no Region: pass
- no old TZZ: pass
- no WebUI static packaging: pass
- no tag: pass
- no release: pass

## Known Limitations
- app.ts still large: accepted as the remaining composition root and future split target.
- formControls fallback remaining: accepted for old/unknown graph compatibility.
- catalog fallback remaining: accepted as an API-offline placeholder only.

## Final Recommendation

Merge `feature/v1-maintainability-cleanup` into `mc-1.21.11` with a no-ff merge after the audit-doc commit is pushed.
