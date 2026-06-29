# Roadmap

## Base Version Branch

`mc-1.21.11`

Version development happens on `mc-<minecraft-version>` branches, not on `main` or `master`.

## Active Checkpoint Branch

`feature/v1-api-webui-test-run`

## Next

1. User review the API-backed WebUI test-run integration on `feature/v1-api-webui-test-run`.
2. Decide whether the next implementation target is persisted graph save/load or P2 state/timer capacity cleanup.
3. Keep Region, old TZZ migration, and full WebUI graph editing out of v1 until the direct graph runtime baseline is stable.

## Completed Checkpoints

- Bootstrap Fabric + independent WebUI environment.
- Slot-based horizontal WebUI design prototype preserved on `design/webui-slot-block-flow`.
- v1 product/core specs added on `docs/v1-product-core-spec`.
- Manual simulation runtime spike uses `/pixellogic` command root, in-memory state, bounded trace, wall-clock in-memory timer, and no Channel core model.
- User manual Minecraft smoke passed for `/pixellogic status`, reset, pass branch, timer completion, trace, and second-run fail branch.
- API-backed WebUI test-run integration connects localhost JSON endpoints to the slot-based WebUI trace panel.

## Follow-Ups

P2 before broader runtime use:

- Pending timers now have a spike-level max pending count. Broader runtime still needs a real capacity/backpressure policy.
- In-memory state should get a lifecycle, capacity, and cleanup policy before non-spike use.

## Loader Event Policy

- Check official Fabric/Minecraft event APIs before any trigger work.
- Reuse official events when possible.
- Keep Fabric/Forge/NeoForge differences in adapters.
- Delay unsafe triggers instead of forcing Mixin/tick scans too early.
