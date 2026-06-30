# PixelLogic Lifecycle / Capacity / Safety Merge Audit

Date: 2026-06-30

## Verdict

- Ready to merge into mc-1.21.11: yes
- P0: none
- P1: none
- P2: server-side optimistic fingerprint enforcement remains future work before multi-user or multi-tab editing.
- P3: browser hand checks are still recommended for repeated drag autosave, undo/redo, API offline, and refresh behavior.

## Source

- branch: `feature/v1-lifecycle-capacity-safety-cleanup`
- commit: `4decf719cdc4445ee8f3c8a0b47a62c5c2ff1722`
- user review: completed; no blocking issue reported for merge readiness.

## Scope Check

- state lifecycle: in scope; bounded in-memory state plus player/session reset and shutdown cleanup.
- timer cleanup: in scope; pending timer cap, generation invalidation, reset/commit/server-stop cleanup.
- trace capacity: in scope; bounded backend trace storage plus bounded WebUI rendering.
- undo/redo capacity: in scope; both stacks capped at 80 entries.
- autosave ordering: in scope; single in-flight save path plus local version and save sequence guards.
- API lifecycle: in scope; local API remains lifecycle-owned by Fabric/dev server wrappers and returns JSON errors.
- frontend listener cleanup: in scope; global keyboard and beforeunload handlers are assigned rather than stacked.

## Capacity Limits

- state entries: 1024.
- pending timers: 128.
- trace records: 50.
- trace steps: backend 100 per trace; frontend renders last 100.
- undo stack: 80 graph snapshots.
- redo stack: 80 graph snapshots.
- autosave queue: one in-flight save plus one latest pending rerun flag.

## Safety Checks

- generation guard: pass; timer continuations carry runtime generation and stale generations do not resume graph execution.
- stale save response guard: pass; WebUI applies save/validate/commit responses only when graph version and save sequence still match.
- invalid graph fail-closed: pass; invalid drafts do not replace committed graph or runtime.
- server stop cleanup: pass; service close stops timers and clears in-memory state.
- graph commit/reset cleanup: pass; reset and committed graph install clear pending timers and advance runtime generation.
- API offline handling: pass; non-JSON or unreachable API paths surface Chinese API connection errors instead of raw JSON parse errors.

## Regression Checks

- graph draft: pass; draft, validate, and commit API contract remains.
- modal editor: pass; double-click opens the block editor modal.
- humanized form: pass; internal enum/boolean values are mapped to Chinese labels in normal UI.
- drag insert: pass; drag/insert files remain and no interaction rewrite was introduced.
- auto save: pass; auto save/validate/commit remains the normal user flow.
- undo/redo: pass; controls and keyboard shortcuts remain.
- right sidebar: pass; it remains summary/status/connection actions, not the main field editor.
- trace: pass; trace rendering remains and now has a visible step bound.

## Validation

- git diff --check: pass.
- gradlew build: pass.
- npm install: pass, 0 vulnerabilities.
- npm run build: pass.
- grep old labels: pass, no old save/validate/commit/reset main UI labels in `web-ui/src`.
- grep enum UI: pass, no raw enum/boolean button text matches in `web-ui/src`.
- grep Channel: pass, no Channel/legacy signal terms in Java or WebUI source.
- command root grep: only existing `/pixellogic test` subcommand matched.
- largest files: `web-ui/src/main.ts` 103 bytes; largest frontend file is `web-ui/src/ui/app.ts` at 48037 bytes.

## Boundaries

- no new feature: yes.
- no Channel: yes.
- no Region: yes.
- no old TZZ: yes.
- no tag: yes.
- no release: yes.

## Final Recommendation

Merge `origin/feature/v1-lifecycle-capacity-safety-cleanup` into `mc-1.21.11` with a no-ff merge commit, then rerun the same build and grep checks on `mc-1.21.11` before pushing.
