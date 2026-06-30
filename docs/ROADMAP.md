# Roadmap

## Base Version Branch

`mc-1.21.11`

Version development happens on `mc-<minecraft-version>` branches, not on `main` or `master`.

## Project Metadata

- License: `Apache-2.0`

## Active Checkpoint Branch

`refactor/webui-structure-split`

## Next

1. User review the WebUI structure split on `refactor/webui-structure-split`.
2. Run audit/merge readiness before merging the drag/insert and structure split work to `mc-1.21.11`.
3. Decide whether the next implementation target is graph editor polish, trace polish, or P2 state/timer capacity cleanup.
4. Keep Region, old TZZ migration, and full professional graph editor behavior out of v1 until the direct graph runtime baseline is stable.

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

## Follow-Ups

P2 before broader runtime use:

- Pending timers now have a spike-level max pending count. Broader runtime still needs a real capacity/backpressure policy.
- In-memory state should get a lifecycle, capacity, and cleanup policy before non-spike use.
- Draft saves return fingerprints but do not yet enforce `expectedFingerprint`; add optimistic conflict handling before multi-user or multi-tab editing.

## Loader Event Policy

- Check official Fabric/Minecraft event APIs before any trigger work.
- Reuse official events when possible.
- Keep Fabric/Forge/NeoForge differences in adapters.
- Delay unsafe triggers instead of forcing Mixin/tick scans too early.
