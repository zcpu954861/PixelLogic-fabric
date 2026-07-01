# Roadmap

## Base Version Branch

`mc-1.21.11`

Version development happens on `mc-<minecraft-version>` branches, not on `main` or `master`.

## Project Metadata

- License: `Apache-2.0`

## Active Baseline

`mc-1.21.11` now contains the v1 editor baseline: graph draft/save/load, modal editing, humanized forms, auto save/validate/apply, undo/redo, slot-flow drag/insert, WebUI structure split, lifecycle/capacity safety cleanup, catalog expansion v1, and Apache-2.0 metadata.

## Next

Near-term track:

1. Move more current demo block behavior toward per-block simulation executors only when it reduces real duplication.
2. Build the next catalog expansion on the Simulation Context Expansion v1 branch when adding position, target-block, or region-aware blocks.
3. Keep WebUI category navigation data-driven; the current demo categories are not permanent product categories.
4. Continue graph/editor/runtime improvements only where they support the current visual editor and simulation loop.
5. Keep Region, old TZZ migration, Channel core, and full professional graph editor behavior out of v1 until the direct graph runtime baseline is stable.

Long-term architecture track:

- Admin Client Bridge / Authorized Tool Session.
- Client-hosted WebUI.
- Capability-gated tool items.
- These are not current active implementation items; future prompts should scope them only after prerequisite Simulation Backend, catalog, graph, runtime, and editor capabilities are mature.

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
- Simulation Backend boundary docs define the next backend direction: simulated actor/world/inventory/container/event abstractions, a logical receiver runner, per-block simulation executors, capability matrix, and maintenance rules against mega files.
- Simulation Backend skeleton branch adds minimal `core/simulation` context/event/runner/result/executor code, routes manual/WebUI test-run through `SimulationRunner`, and registers the first simulation-backed player tag condition/action blocks.
- Condition output mode branch adds `满足时继续` / `不满足时继续` / `分成两路`, makes unconnected condition outputs end gracefully, allows unconnected condition inputs during editing, moves `condition.player.has_tag` to 条件判断 / 玩家条件, and keeps the demo graph as explicit dual-branch.
- Simulation Test Context MVP lets WebUI test runs send a temporary simulated player display name, tags, and administrator flag; results show initial/final tags without writing the context into graph JSON or saving scenarios.
- Simulation Context Expansion v1 extends that temporary test context with player position, optional target block, and simple region facts for future block expansion, still without named scenarios, graph writes, or a real MC adapter.
- Catalog Expansion v1 adds `玩家是否拥有标签`, `玩家是否为管理员`, `移除玩家标签`, and the title/subtitle/actionbar message blocks; message color and formatting toolbar remains a follow-up.
- Maintainability Cleanup v1 reduces frontend catalog duplication, extracts slot-flow/catalog rendering helpers from `app.ts`, consolidates self-check support, and removes runtime future-switch naming noise without changing product behavior.
- Text Component Editor v1 adds the shared rich text component editor for message/title/subtitle/actionbar fields.
- Admin Client Bridge design audit records the long-term direction: authorized client-hosted WebUI, local bridge transport, server-authoritative capability checks, and capability-gated future tool items.

## Follow-Ups

P2 before broader runtime use:

- The current simulation runtime now has a small `SimulationRunner` wrapper, but most demo behavior still lives in `GraphRuntime`; move behavior out gradually only when the executor split is useful.
- `web-ui/src/ui/app.ts` remains the composition root and largest frontend file; keep extracting cohesive helpers only when new work would otherwise make it larger.
- Rich text message blocks now have the v1 shared text component editor with selected-text color and formatting controls; hover/click events, translate/score/nbt, variables, and a real Minecraft Text adapter remain future work.
- Pending timers now have a spike-level max pending count plus reset/commit/stop cleanup. Broader runtime still needs a real capacity/backpressure policy.
- In-memory state now has a spike-level cap and reset/stop cleanup. Broader runtime still needs durable lifecycle and persistence policy before non-spike use.
- Draft saves return fingerprints but do not yet enforce `expectedFingerprint`; add optimistic conflict handling before multi-user or multi-tab editing.
- Server HTTP API is still the current dev/local/self-check WebUI transport. The long-term recommended admin flow is client-hosted WebUI through an authorized PixelLogic client session, but that track is deferred until a future scoped prompt.

## Loader Event Policy

- Check official Fabric/Minecraft event APIs before any trigger work.
- Reuse official events when possible.
- Keep Fabric/Forge/NeoForge differences in adapters.
- Delay unsafe triggers instead of forcing Mixin/tick scans too early.
