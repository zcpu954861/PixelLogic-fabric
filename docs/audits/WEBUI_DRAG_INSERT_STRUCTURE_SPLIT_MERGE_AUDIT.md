# PixelLogic WebUI Drag Insert + Structure Split Merge Audit

Date: 2026-06-30

## Verdict

- Ready to merge into mc-1.21.11: yes
- P0: none
- P1: none
- P2: none blocking merge
- P3: `web-ui/src/ui/app.ts` remains the largest orchestration file at about 47 KB. This is acceptable for this checkpoint because API actions, autosave, undo/redo, and event binding remain behavior-sensitive, but a later refactor can split those areas further.

## Branch / Commit

- source branch: `refactor/webui-structure-split`
- source commit: `54c4473`
- pushed: yes, `origin/refactor/webui-structure-split`
- includes `feature/v1-slot-flow-drag-insert`: yes
- includes `origin/mc-1.21.11`: yes

## User Manual Test

- user stated manual test completed: yes
- screenshots required in this stage: no
- known limitations: no new browser screenshot was captured in this audit; this follows the user's hand-test direction.

## Drag Insert UX

- single click select: preserved
- double click edit: preserved
- drag threshold: preserved
- free drag: preserved
- chain drag: preserved
- condition branch drag: preserved
- insert middle: preserved
- append tail: preserved
- magnetic snap: preserved
- stale edge cleanup: preserved
- green glow preview: preserved; no insert text/vertical marker/textbox
- condition puzzle layout: preserved, including visible-outline hit area and dynamic nested branch spans

## Auto Save / Undo

- auto save: preserved
- auto validate: preserved
- auto apply: preserved after validation passes
- undo: preserved through `上一步` and `Ctrl+Z`
- redo: preserved through `下一步`, `Ctrl+Y`, and `Ctrl+Shift+Z`

## Structure Split

- main.ts: bootstrap only, 103 bytes
- TS modules: API, graph types, demo graph, graph layout, state, canvas block view, drag insert, editor modal/forms, sidebar summary, trace view, humanize labels, validation view, and DOM utils
- CSS modules: `styles/index.css` imports base, layout, blocks, drag-insert, forms, modal, responsive, sidebar, and trace modules
- no new framework: yes
- no new giant file: no blocking giant file; largest file is `ui/app.ts` as the app orchestration layer, with drag/insert and graph layout split out

## Validation

- source `git diff --check`: pass
- source `gradlew build`: pass
- source `npm install`: pass, 0 vulnerabilities
- source `npm run build`: pass
- grep checks:
  - Channel / legacy signal terms: no matches
  - old save-step labels in WebUI source: no matches
  - raw enum/boolean strings as primary UI copy: no matches
  - command root grep: only existing `/pixellogic test` subcommand matched; no old root command was added
- largest file check:
  - `web-ui/src/main.ts`: 103 bytes
  - `web-ui/src/styles.css`: 29 bytes
  - largest module: `web-ui/src/ui/app.ts`, about 47 KB

## Architecture Boundaries

- no Channel: pass
- no Region: pass
- no old TZZ: pass
- no runtime semantic drift: pass; WebUI refactor only
- no graph schema break: pass
- no Apache license merge: pass; `chore/apache-2-license` remains separate

## Merge Recommendation

Merge `refactor/webui-structure-split` into `mc-1.21.11` with `--no-ff`, then run the post-merge build and grep checks again before pushing `mc-1.21.11`.
