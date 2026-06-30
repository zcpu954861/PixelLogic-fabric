# Roadmap

## Base Version Branch

`mc-1.21.11`

Version development happens on `mc-<minecraft-version>` branches, not on `main` or `master`.

## Project Metadata

- License: `Apache-2.0`

## Active Baseline

`mc-1.21.11` now contains the v1 editor baseline: graph draft/save/load, modal editing, humanized forms, auto save/validate/apply, undo/redo, slot-flow drag/insert, WebUI structure split, lifecycle/capacity safety cleanup, and Apache-2.0 metadata.

## Next

1. Review the first Block Catalog skeleton implementation.
2. Keep WebUI category navigation data-driven; the current demo categories are not permanent product categories.
3. Keep Region, old TZZ migration, Channel core, and full professional graph editor behavior out of v1 until the direct graph runtime baseline is stable.
4. Catalog form schema adoption and rich text component field MVP now move the current seven demo blocks onto schema-driven editing while preserving legacy fallback.
5. Catalog form manual-save fix keeps rich text and schema fields local to the editor modal until the user clicks `保存`.

## Completed Checkpoints

- Bootstrap Fabric + independent WebUI environment.
- Slot-based horizontal WebUI design prototype preserved on `design/webui-slot-block-flow`.
- v1 product/core specs added on `docs/v1-product-core-spec`.
- Manual simulation runtime spike uses `/pixellogic` command root, in-memory state, bounded trace, wall-clock in-memory timer, and no Channel core model.
- User manual Minecraft smoke passed for `/pixellogic status`, reset, pass branch, timer completion, trace, and second-run fail branch.
- API-backed WebUI test-run integration connects localhost JSON endpoints to the slot-based WebUI trace panel.
- Graph draft/validate/commit checkpoint stores `demo-start-flow` as committed JSON, saves drafts separately, validates before commit, and keeps test-run on committed graph.
- Graph Draft UX simplification hides draft/validate/commit and reset internals from normal users: `保存` performs save/validate/commit, and `测试运行` automatically resets before starting.
- Block editor modal UX moves selected-block fields out of the right panel: clicking a block opens an animated editor with unsaved-close confirmation.
- Block editor humanized form UX hides internal enum strings from normal UI, uses Chinese labels and compact controls, and keeps graph JSON/runtime semantics unchanged.
- Slot flow drag/insert UX adds block-library creation, free drag, downstream chain drag, Condition branch drag, insert-into-connection, minimal disconnect/delete actions, position metadata persistence, and trace display polish.
- WebUI structure split moves the large frontend entry and stylesheet into responsibility-based TypeScript and CSS modules without changing behavior.
- Apache-2.0 license metadata and full `LICENSE` text are included on `mc-1.21.11`.
- v1 editor baseline health audit completed with no P0/P1.
- Lifecycle/capacity safety cleanup is merged into `mc-1.21.11` with state/timer/trace/undo/autosave/API lifecycle bounds.
- Block Catalog / Simulation Model docs define the next direction: concrete catalog blocks, registry-driven categories, and simulation executors separated from future Minecraft executors.
- Block Catalog skeleton branch adds the Java built-in catalog registry, `blockId` compatibility, readonly catalog API, catalog-driven WebUI library, and `blockCatalogSelfCheck` for the current seven demo blocks.
- Catalog form schema + rich text field branch adopts `formSchema` as the editor main path for the current seven demo blocks, adds catalog summaries, upgrades message text to a structured rich text component MVP, and restores modal-local draft + manual save semantics for configuration edits.

## Follow-Ups

P2 before broader runtime use:

- Pending timers now have a spike-level max pending count plus reset/commit/stop cleanup. Broader runtime still needs a real capacity/backpressure policy.
- In-memory state now has a spike-level cap and reset/stop cleanup. Broader runtime still needs durable lifecycle and persistence policy before non-spike use.
- Draft saves return fingerprints but do not yet enforce `expectedFingerprint`; add optimistic conflict handling before multi-user or multi-tab editing.

## Loader Event Policy

- Check official Fabric/Minecraft event APIs before any trigger work.
- Reuse official events when possible.
- Keep Fabric/Forge/NeoForge differences in adapters.
- Delay unsafe triggers instead of forcing Mixin/tick scans too early.
