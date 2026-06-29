# Roadmap

## Current Branch

`mc-1.21.11`

Version development happens on `mc-<minecraft-version>` branches, not on `main` or `master`.

## Next

1. Review and confirm `docs/specs/NEW_LOGIC_MOD_PRODUCT_SPEC.md`.
2. Review and confirm `docs/specs/CORE_ARCHITECTURE_SPEC.md`.
3. Review and confirm `docs/specs/V1_VERTICAL_SPIKE_PLAN.md`.
4. Write the v1 minimal vertical spike implementation prompt.
5. Confirm loader/Minecraft official event APIs before implementing triggers.

## Loader Event Policy

- Check official Fabric/Minecraft event APIs before any trigger work.
- Reuse official events when possible.
- Keep Fabric/Forge/NeoForge differences in adapters.
- Delay unsafe triggers instead of forcing Mixin/tick scans too early.
