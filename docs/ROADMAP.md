# Roadmap

## Current Branch

`mc-1.21.11`

Version development happens on `mc-<minecraft-version>` branches, not on `main` or `master`.

## Next

1. Write `NEW_LOGIC_MOD_PRODUCT_SPEC.md`.
2. Design the v1 minimal vertical spike.
3. Define the first graph model shape.
4. Define loader event adapter boundaries before implementing triggers.

## Loader Event Policy

- Check official Fabric/Minecraft event APIs before any trigger work.
- Reuse official events when possible.
- Keep Fabric/Forge/NeoForge differences in adapters.
- Delay unsafe triggers instead of forcing Mixin/tick scans too early.
