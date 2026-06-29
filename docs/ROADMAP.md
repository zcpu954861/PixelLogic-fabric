# Roadmap

## Base Version Branch

`mc-1.21.11`

Version development happens on `mc-<minecraft-version>` branches, not on `main` or `master`.

## Active Checkpoint Branch

`feature/v1-manual-simulation-spike`

## Next

1. Decide whether the next implementation target is persisted graph save/load or WebUI/API test-run integration.
2. Keep Region, old TZZ migration, and full WebUI graph editing out of v1 until the direct graph runtime baseline is stable.
3. Address the merge-readiness P2 follow-ups before broader runtime use.

## Completed Checkpoints

- Bootstrap Fabric + independent WebUI environment.
- Slot-based horizontal WebUI design prototype preserved on `design/webui-slot-block-flow`.
- v1 product/core specs added on `docs/v1-product-core-spec`.
- Manual simulation runtime spike uses `/pixellogic` command root, in-memory state, bounded trace, wall-clock in-memory timer, and no Channel core model.
- User manual Minecraft smoke passed for `/pixellogic status`, reset, pass branch, timer completion, trace, and second-run fail branch.

## Follow-Ups

P2 before broader runtime use:

- Pending timers should get a max pending count, rejection trace, or equivalent backpressure policy.
- In-memory state should get a lifecycle, capacity, and cleanup policy before non-spike use.

## Loader Event Policy

- Check official Fabric/Minecraft event APIs before any trigger work.
- Reuse official events when possible.
- Keep Fabric/Forge/NeoForge differences in adapters.
- Delay unsafe triggers instead of forcing Mixin/tick scans too early.
