# WebUI Structure Split

Date: 2026-06-30

Branch: `refactor/webui-structure-split`

## Scope

This refactor splits the previously concentrated `web-ui/src/main.ts` and `web-ui/src/styles.css` without changing graph runtime semantics or adding editor features.

## TypeScript Structure

- `main.ts` is now only the bootstrap entry.
- `api/pixelLogicApi.ts` owns JSON API fetch, content-type checks, and API connection errors.
- `model/graphTypes.ts` owns shared API, graph, block, editor, drag, and UI state types.
- `model/demoGraph.ts` owns the seeded `demo-start-flow` graph and small graph factory helpers.
- `model/graphLayout.ts` owns pure graph layout, visual connection checks, dynamic Condition branch spans, and graph cloning helpers.
- `state/appState.ts` owns mutable app state and canvas world dimensions.
- `ui/app.ts` owns app orchestration, render assembly, event binding, API actions, autosave, and undo/redo.
- `ui/canvas/blockView.ts` owns puzzle SVG and block/join HTML.
- `ui/canvas/dragInsert.ts` owns insert/append/attach candidate rules, snapping, graph edge rewrites, and local gap creation.
- `ui/editor/blockEditorModal.ts` and `ui/editor/formControls.ts` own modal shell and humanized form controls.
- `ui/sidebar/selectionSummary.ts` owns selected-block side panel summary.
- `ui/trace/traceView.ts` owns trace list rendering.
- `ui/humanize/labels.ts` owns UI labels and trace text humanization.
- `ui/validation/validationView.ts` owns validation/draft status copy.
- `utils/dom.ts` owns HTML escaping and compact formatting helpers.

## CSS Structure

- `styles/index.css` imports the split CSS modules.
- `base.css` keeps theme variables and global element defaults.
- `layout.css` keeps shell, rails, stage, viewport, and major layout rules.
- `blocks.css` keeps puzzle block visuals.
- `drag-insert.css` keeps slot joins, insert glow, drag hints, and related animations.
- `forms.css` keeps cards, editor fields, segmented controls, and form lists.
- `modal.css` keeps editor overlay/dialog and modal animations.
- `sidebar.css` keeps right-panel layout.
- `trace.css` keeps bottom dock, issues, and trace lists.
- `responsive.css` keeps responsive and reduced-motion rules.
- `styles.css` remains as a tiny compatibility import only.

## Behavior Preserved

- Single click selects a block; double click opens the editor modal.
- Drag threshold prevents accidental modal open.
- Free drag, downstream chain drag, Condition branch drag, middle insertion, tail append, and attach-before-target behavior remain in the same code path.
- Green-glow insert preview and split/merge animation classes are preserved.
- Visual stale-edge cleanup, autosave, validate/commit, undo/redo, and trace humanization remain behaviorally unchanged.
- No React/Vue/Svelte, no graph editor library, no Channel, no Region, no runtime graph schema change.

## Validation

- `npm run build`: pass during refactor.
- Full final validation is recorded in the task report.
