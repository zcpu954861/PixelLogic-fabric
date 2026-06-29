# Roadmap

## Base Version Branch

`mc-1.21.11`

Version development happens on `mc-<minecraft-version>` branches, not on `main` or `master`.

## Active Checkpoint Branch

`feature/v1-manual-simulation-spike`

## Next

1. Review the manual simulation spike on `feature/v1-manual-simulation-spike`.
2. Run Minecraft smoke for `/pixellogic test start`, `/pixellogic trace last`, and the second-run fail branch.
3. Decide whether the next step is persisted graph save/load or WebUI/API test-run integration.
4. Keep Region, old TZZ migration, and full WebUI graph editing out of v1 until the direct graph runtime is accepted.

## Completed Checkpoints

- Bootstrap Fabric + independent WebUI environment.
- Slot-based horizontal WebUI design prototype preserved on `design/webui-slot-block-flow`.
- v1 product/core specs added on `docs/v1-product-core-spec`.
- Manual simulation runtime spike uses `/pixellogic` command root, in-memory state, bounded trace, wall-clock in-memory timer, and no Channel core model.

## Loader Event Policy

- Check official Fabric/Minecraft event APIs before any trigger work.
- Reuse official events when possible.
- Keep Fabric/Forge/NeoForge differences in adapters.
- Delay unsafe triggers instead of forcing Mixin/tick scans too early.
