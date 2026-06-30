# PixelLogic Condition Output Modes Merge Audit

## Verdict

- Ready to merge into mc-1.21.11: yes
- P0: none
- P1: none
- P2: `web-ui/src/ui/app.ts` remains the largest frontend orchestration file; acceptable for this checkpoint because autosave, modal, drag, history, and API wiring are behavior-sensitive.
- P3: browser checks were not repeated in this audit stage by workflow; user manual review covered the visible behavior except the now-fixed unconnected condition input issue.

## Source

- branch: `feature/v1-condition-output-modes`
- commits:
  - `1ee745f feat: add simulation backend skeleton`
  - `21352c9 feat: add condition output modes`
  - `214b61b fix: allow unconnected condition inputs`
- user review: completed for category placement, condition output modes, single green / single red / branch visuals, and unconnected output ending; user reported one blocking issue with unconnected condition input save.
- browser self-check:
  - not repeated in audit stage

## Scope Check

- output modes: pass; conditions support `PASS_ONLY`, `FAIL_ONLY`, and `BRANCH`.
- player tag category: pass; `condition.player.has_tag` is under `条件判断 / 玩家条件`.
- unconnected output handling: pass; selected unconnected condition output ends gracefully.
- unconnected input handling: pass; condition input is no longer a hard validation blocker.
- mode switch confirmation: pass; inactive-output edge removal requires `切换并断开`.
- runtime: pass; mode selection stays in the direct edge runtime.
- validation: pass; config validation remains fail-closed while loose condition inputs are allowed.
- docs: pass; repo docs and Obsidian notes were updated.

## Condition Modes

- PASS_ONLY / 满足时继续: true follows `pass`; false ends the path.
- FAIL_ONLY / 不满足时继续: false follows `fail`; true ends the path.
- BRANCH / 分成两路: true follows `pass`; false follows `fail`; either side may be unconnected.

## Validation Semantics

- unconnected output: valid; selected missing output writes a trace message and ends successfully.
- unconnected input: valid during editing; the block is saved but unreachable until connected into a trigger path.
- loose nodes: valid when their config is valid; test-run only executes nodes reachable from the trigger entry.
- strict validation: not implemented for full graph reachability in this checkpoint.
- known limitations: timer blocks still require `timer_completed` output to be connected; this is existing timer semantics, not a condition mode rule.

## Runtime / Simulation

- PASS_ONLY: self-check covers false ending gracefully.
- FAIL_ONLY: self-check covers true ending gracefully.
- BRANCH: self-check covers old graph compatibility and one unconnected side.
- trace: path end messages are human-readable.
- self-check: `conditionOutputModeSelfCheck` covers catalog defaults, demo branch mode, unconnected output, unconnected input, invalid mode rejection, and player tag category placement.

## UX

- single green: pass by code review; single-output condition uses active `pass` slot only.
- single red: pass by code review; single-output condition uses active `fail` slot only.
- dual branch: pass by code review; `BRANCH` keeps pass/fail outputs active.
- condition mode control: pass; editor uses catalog `formSchema` segmented control with Chinese labels.
- category placement: pass; catalog and fallback catalog both place player tag condition/action correctly.
- drag/insert: pass by scope review; inactive outputs are ignored by graph layout and drag/insert candidate logic.
- undo/redo: pass by code review; config change plus confirmed edge removal goes through one `applyGraphEdit`.
- manual save: pass; modal draft still applies only when the user clicks `保存`.

## Validation

- git diff --check: pass; CRLF warnings only.
- gradlew build: pass.
- npm install: pass; 0 vulnerabilities.
- npm run build: pass.
- apiWebUiSelfCheck: pass.
- graphStorageSelfCheck: pass.
- manualSimulationSelfCheck: pass.
- blockCatalogSelfCheck: pass.
- simulationBackendSelfCheck: pass.
- conditionOutputModeSelfCheck: pass.
- grep output enum UI: pass; no raw enum HTML text matches in `web-ui/src`.
- grep Channel: pass; no matches in `src/main/java web-ui/src`.
- grep old draft labels: pass; no matches in `web-ui/src`.
- grep say/raw JSON: pass; matches are docs-only boundary notes.
- grep command root: pass; only existing `literal("test")` child under `/pixellogic` matched.
- grep core/simulation MC deps: pass; no `net.minecraft` under `core/simulation`.
- largest files:
  - `web-ui/src/main.ts`: 103 bytes.
  - `web-ui/src/ui/app.ts`: 58092 bytes.
  - `web-ui/src/ui/canvas/dragInsert.ts`: 19100 bytes.
  - `web-ui/src/model/blockCatalog.ts`: 12649 bytes.
  - `src/main/java/com/pixelmc/pixellogic/core/catalog/BuiltInBlockCatalog.java`: 22161 bytes.
  - `src/main/java/com/pixelmc/pixellogic/core/graph/GraphValidator.java`: 15930 bytes.
  - `src/main/java/com/pixelmc/pixellogic/server/api/PixelLogicApiServer.java`: 14470 bytes.

## Boundaries

- no MC adapter: pass.
- no mass new blocks: pass; only player tag condition/action from simulation skeleton plus condition mode behavior are included.
- no WebUI static packaging: pass.
- no Channel: pass.
- no Region: pass.
- no old TZZ: pass.
- no tag: pass.
- no release: pass.

## Final Recommendation

Merge `origin/feature/v1-condition-output-modes` into `mc-1.21.11` with a no-ff merge after committing the unconnected condition input fix and this audit document. After merge, repeat build, WebUI build, self-checks, grep checks, and push `mc-1.21.11` only if those pass.
