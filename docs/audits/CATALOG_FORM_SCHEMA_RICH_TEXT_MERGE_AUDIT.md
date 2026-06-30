# PixelLogic Catalog Form Schema + Rich Text Merge Audit

## Verdict
- Ready to merge into mc-1.21.11: yes
- P0: none
- P1: none
- P2: `web-ui/src/ui/app.ts` remains the largest frontend orchestration file; acceptable for this checkpoint because autosave/history/modal wiring is behavior-sensitive.
- P3: browser checks were not repeated in this audit stage by workflow; implementation-stage browser self-check remains the recorded evidence.

## Source
- branch: `feature/v1-catalog-form-schema-rich-text`
- commits:
  - `509d439 feat: adopt catalog form schema`
  - `e07a1e3 fix: make catalog form edits manual save`
- user review: manual UI/interaction review to be handled by the user; this audit does not repeat browser screenshots.
- implementation browser self-check:
  - catalog schema/rich text: `reports/catalog-form-schema-rich-text/REPORT.md`
  - manual-save fix: `reports/catalog-form-manual-save/REPORT.md`
- audit browser self-check:
  - not repeated by workflow

## Scope Check
- catalog form schema: pass; known `blockId` nodes resolve catalog `formSchema` first.
- migrated demo blocks: pass; current seven demo blocks expose schema-driven form fields.
- legacy NodeType fallback: pass; fallback remains only for old or unknown nodes.
- rich_text_component: pass; `action.message.chat.message` uses the rich text MVP field.
- manual-save modal config: pass; modal edits stay local until `保存`.
- backend validation: pass; `GraphValidator` remains the final authority and validates catalog block ids, options, scopes, numbers, and rich text required content.
- docs: pass; architecture, roadmap, API, specs, and audit notes describe the checkpoint and boundaries.

## Manual Save Fix
- modal local draft: pass; opening the editor clones the selected node into local draft/original snapshots.
- per-key autosave removed: pass; field events update `editorDraftNode`, not the graph.
- dirty confirm: pass; close/cancel/ESC/overlay route through unsaved confirmation when the draft changed.
- save creates one history entry: pass; `保存` applies the draft once through `applyGraphEdit` and then runs save/validate/commit.
- discard does not mutate graph: pass; discard drops the draft and closes the modal.
- structural graph autosave unchanged: pass; add/delete/drag/insert/disconnect still use the existing autosave path.

## Rich Text
- tellraw/text component semantics: pass; stored payload keeps `version`, `plainText`, and `segments`.
- no /say: pass; no WebUI or runtime path names the message block as `/say`.
- no raw JSON default path: pass; normal UI exposes multiline plain text and preview only.
- multiline plain text: pass.
- structured storage: pass, with legacy string compatibility.
- preview: pass; preview uses plain text extracted from the rich text payload.
- future formatting extension: pass; toolbar, hover/click, selector, score, translate, keybind, nbt, and real Minecraft Text adapter remain future work.

## Command Root Check
- /pixellogic root: pass; `PixelLogicCommandRegistrar` registers only `literal("pixellogic")` as the command root.
- /pl alias: pass; no `literal("pl")` command alias exists.
- result: the previous report wording `/pl test` was a report typo. Code and docs still use `/pixellogic test`.

## Validation
- feature git diff --check: pass.
- feature gradlew build: pass.
- feature npm install: pass; 0 vulnerabilities.
- feature npm run build: pass.
- apiWebUiSelfCheck: pass.
- graphStorageSelfCheck: pass.
- manualSimulationSelfCheck: pass.
- blockCatalogSelfCheck: pass.
- grep old labels: pass; no normal WebUI matches for `保存草稿`, `校验草稿`, `提交生效`, or `重置测试状态`.
- grep enum UI: pass; no normal WebUI matches for raw enum label text patterns.
- grep say/raw JSON: pass; matches are docs-only boundary notes.
- grep generic/万能: pass; matches are docs-only guardrails.
- grep command root: pass; only `literal("pixellogic")` and child `literal("test")` matched.
- grep Channel: pass; no `Channel`, `SignalBridge`, `SignalListener`, `ActionRelay`, `Receiver`, or `Relay` matches in runtime/WebUI source.
- largest files:
  - `web-ui/src/main.ts`: 103 bytes.
  - `web-ui/src/ui/app.ts`: 55331 bytes.
  - `web-ui/src/ui/canvas/dragInsert.ts`: 19076 bytes.
  - `web-ui/src/ui/editor/formControls.ts`: 11342 bytes.

## Boundaries
- no MC adapter: pass.
- no full rich text toolbar: pass.
- no hover/click/score/selector/nbt UI: pass.
- no WebUI static packaging: pass.
- no mass new blocks: pass; only current seven demo blocks are migrated.
- no Channel: pass.
- no Region: pass.
- no old TZZ: pass.
- no tag: pass.
- no release: pass.

## Final Recommendation

Merge `origin/feature/v1-catalog-form-schema-rich-text` into `mc-1.21.11` with a no-ff merge after committing this audit document. After merge, repeat build, WebUI build, self-checks, grep checks, and push `mc-1.21.11` only if those pass.
