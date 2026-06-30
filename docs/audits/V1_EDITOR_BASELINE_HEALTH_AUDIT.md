# PixelLogic v1 Editor Baseline Health Audit

Date: 2026-06-30

## Verdict

- Baseline stable enough for next development step: yes
- P0: none
- P1: none
- P2: state/timer lifecycle and capacity policy remain follow-up work before broader runtime use.
- P3: `web-ui/src/ui/app.ts` remains the largest frontend orchestration file at about 47 KB; acceptable for this baseline, but event binding, autosave, and history can be split later.

## Current Baseline

- graph draft/save/load: present for `demo-start-flow`, with draft, validate, and commit API separation preserved.
- modal editor: present; double-click opens focused block editing, while the sidebar stays informational.
- humanized form: present; internal enum/boolean values remain storage/runtime data but are rendered as Chinese UI labels.
- auto save/validate/apply: present; normal UI edits automatically queue save, validate, and commit.
- undo/redo: present through `上一步`, `下一步`, `Ctrl+Z`, `Ctrl+Y`, and `Ctrl+Shift+Z`.
- drag insert: present; supports block creation, free drag, downstream chain drag, condition branches, middle insert, tail append, and connect-before-target.
- WebUI structure split: present; `main.ts` and `styles.css` are tiny entry/compatibility files, with TypeScript and CSS split by responsibility.
- Apache-2.0 license: present after merging `origin/chore/apache-2-license`.

## UX Checks

- single click select: preserved.
- double click edit: preserved.
- drag threshold: preserved to prevent accidental modal open during drag.
- chain drag: preserved for visually snapped downstream chains.
- insert middle: preserved with snap-on-release edge rewrite.
- append tail: preserved for free output ports.
- green glow preview: preserved; no insert text, vertical marker, or textbox.
- condition card layout: preserved with dynamic nested branch spans and painted-outline hit testing.
- right sidebar: informational summary plus minimal connection/delete actions; no main field editor restored.

## Engineering Checks

- main.ts size: 103 bytes.
- largest frontend files: `ui/app.ts` about 47 KB, `ui/canvas/dragInsert.ts` about 19 KB, `model/graphLayout.ts` about 11 KB.
- CSS split: `styles/index.css` imports base, layout, blocks, drag-insert, forms, modal, responsive, sidebar, and trace modules.
- no new framework: yes; WebUI remains Vanilla TypeScript.
- no Channel: pass.
- no Region: pass.
- no old TZZ: pass.
- API localhost binding: WebUI/API spike remains localhost-only at `127.0.0.1:18111`.

## Validation Results

- git diff --check: pass.
- gradlew build: pass.
- npm install: pass, 0 vulnerabilities.
- npm run build: pass.
- grep old labels: pass, no old save/validate/commit/reset normal-UI labels found in `web-ui/src`.
- grep Channel: pass, no Channel or legacy signal/relay terms found in Java/WebUI source.
- command root grep: only the existing `/pixellogic test` subcommand matched; no old root command was added.

## Repository Hygiene

- LICENSE: Apache License 2.0 full text is present.
- fabric.mod.json license: `Apache-2.0`.
- .gitignore world/run: `run/` and `world/` are ignored.
- untracked files: none expected after `world/` is ignored; the local `world/` directory was not deleted.

## Next Recommendation

Keep the v1 editor baseline stable and choose one next checkpoint: state/timer lifecycle and capacity cleanup, or a first release-candidate audit after user review.
