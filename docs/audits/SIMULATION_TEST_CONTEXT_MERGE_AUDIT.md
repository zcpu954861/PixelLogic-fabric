# PixelLogic Simulation Test Context Merge Audit

## Verdict

- Ready to merge into mc-1.21.11: yes
- P0: none
- P1: none
- P2: none
- P3: `web-ui/src/ui/app.ts` remains the largest frontend orchestration file; acceptable for this checkpoint because the new test-player UI is split into `ui/simulation/simulationTestContextPanel.ts`.

## Source

- branch: `feature/v1-simulation-test-context`
- commits:
  - `39e9cdc feat: add simulation test context`
  - `7911827 fix: refine simulation test context UX`
- user review: user manually tested and accepted the current UX.
- browser self-check:
  - not repeated in audit stage.

## Scope Check

- split button: pass; the run control has a left run button and right arrow dropdown.
- edit modal: pass; `编辑测试玩家` opens a modal instead of a persistent side panel form.
- local draft: pass; edits stay in `simulationDraftContext` until save.
- dirty confirm: pass; closing the modal with unsaved changes uses the same confirm path as block editing.
- tag chips: pass; tags render as chips with a left-side `×` delete button.
- admin segmented control: pass; the admin field uses `是` / `否` segmented buttons, not a checkbox.
- result summary: pass; the sidebar shows the latest test result summary only.
- block editor auto-check prompt cleanup: pass; successful auto-check text stays in the bottom validation view and is not shown as a red modal error bubble.
- API/test context: pass; `POST /api/pixellogic/test/start` accepts optional `testContext.actor`.
- SimulationRunner: pass; the request actor is passed into the existing runner flow.
- docs: pass; WebUI, API, capability, roadmap, and MVP audit docs describe the temporary per-run context.

## Simulation Test Context Semantics

- per-run only: pass.
- no graph persistence: pass.
- no scenario persistence: pass.
- no auto write-back of result tags: pass.
- default actor: pass; missing context still uses `WebUI 模拟玩家`.
- CLI behavior: pass; existing `/pixellogic test start` command path remains available and uses default context.

## UX

- left run button: pass.
- right arrow dropdown: pass.
- edit test player button: pass.
- modal draft/save: pass.
- tag add/delete: pass; add trims, deduplicates, rejects invalid values, and delete is bound to `×`.
- no no-tag chip: pass.
- no flicker/reopen animation: pass; the already-open modal uses the steady overlay state during internal rerender.
- no focus stealing: pass; internal rerender does not force focus back to a different control.
- admin yes/no: pass.
- right sidebar result summary: pass.

## Backend / Runtime

- request parsing: pass; invalid test-player payloads return validation errors.
- validation: pass; display name and tags are trimmed, capped, deduplicated, and reject control characters.
- actor displayName: pass.
- actor tags: pass.
- administrator flag: pass.
- condition.player.has_tag: pass; reads current run actor tags.
- action.player.add_tag: pass; mutates only current run actor tags.
- SimulationExecutionResult: pass; returns initial/final tags, display name, admin flag, trace id, and action result details.

## Regression Checks

- Condition Output Modes: pass; self-check covers PASS_ONLY, FAIL_ONLY, BRANCH, unconnected input save, and output endings.
- Block Catalog: pass; player tag condition/action remain in the intended categories.
- Modal edit: pass; local draft, manual save, dirty confirm, and switch-disconnect confirm remain intact.
- Graph editing: pass; no changes to drag/insert/autosave storage flow in this audit.
- Simulation Backend: pass; `core/simulation` remains free of `net.minecraft` dependencies.
- rich_text_component: pass; normal UI still does not expose raw JSON.

## Validation

- git diff --check: pass.
- gradlew build: pass.
- npm install: pass, no content changes.
- npm run build: pass.
- apiWebUiSelfCheck: pass.
- graphStorageSelfCheck: pass.
- manualSimulationSelfCheck: pass.
- blockCatalogSelfCheck: pass.
- simulationBackendSelfCheck: pass.
- conditionOutputModeSelfCheck: pass.
- simulationTestContextSelfCheck: pass.
- grep UI internal terms: pass; no ordinary WebUI text exposes raw `PLAYER`, `BOOLEAN`, `true`, `false`, `PASS_ONLY`, `FAIL_ONLY`, `BRANCH`, `SimulationActor`, `operator=true`, or `tags=[]`.
- grep checkbox: pass; no checkbox remains in WebUI source.
- grep auto-check pass bubble: pass; match is limited to `validationView.ts` bottom validation text.
- grep Channel: pass; no source matches.
- grep command root: pass; match is the existing `literal("test")` under `/pixellogic test`.
- grep core/simulation MC deps: pass; no `net.minecraft` matches.
- largest files: `web-ui/src/ui/app.ts` is still largest; the new test-context UI lives in its own simulation panel module.

## Boundaries

- no named scenario: pass.
- no persistence: pass.
- no graph writes: pass.
- no multiplayer simulation: pass.
- no inventory/world/container: pass.
- no MC adapter: pass.
- no WebUI static packaging: pass.
- no Channel: pass.
- no Region: pass.
- no old TZZ: pass.
- no tag: pass.
- no release: pass.

## Known Limitations

- Test context is intentionally limited to display name, tags, and administrator flag.
- It is not saved as a scenario and is not shared between browsers.
- Simulation still covers the current lightweight graph runtime path, not a real Minecraft world adapter.

## Final Recommendation

Merge `origin/feature/v1-simulation-test-context` into `mc-1.21.11` with `--no-ff`, then rerun the same build, npm, self-check, and grep validation before pushing `mc-1.21.11`.
