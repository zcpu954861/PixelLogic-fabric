# PixelLogic

PixelLogic is a visual logic flow mod for Minecraft servers.

Server owners build minigame logic with nodes, cards, and direct edges instead of datapacks, command blocks, scoreboard scripts, or complex JSON.

## Current Target

- Minecraft: `1.21.11`
- Branch: `mc-1.21.11`
- Mod ID: `pixel-logic`
- Package: `com.pixelmc.pixellogic`
- Java: `21`

## Product Rules

- Direct Edge / Graph is the primary model.
- Channel is not a normal-user concept and is not a core execution model.
- Conditions are standalone cards/nodes.
- Actions are typed forms, not a scripting language.
- Scratch is only an interaction reference.
- Old TZZ phone, AR, map, note, gallery, task, password, blocking, items, and blocks are not PixelLogic core.

## Current Spike Commands

The v1 manual simulation spike is exposed under `/pixellogic` only:

```text
/pixellogic status
/pixellogic test start
/pixellogic test reset
/pixellogic trace last
```

`/pixellogic test start` runs the in-memory demo graph for the executing player. Console execution returns a Chinese error because the spike uses PLAYER state.

## v1 Specs

- [PixelLogic v1 Product Spec](docs/specs/NEW_LOGIC_MOD_PRODUCT_SPEC.md)
- [PixelLogic v1 Core Architecture Spec](docs/specs/CORE_ARCHITECTURE_SPEC.md)
- [PixelLogic v1 Minimal Vertical Spike Plan](docs/specs/V1_VERTICAL_SPIKE_PLAN.md)

## Builds

Java / Fabric build:

```powershell
.\gradlew.bat build
```

WebUI build:

```powershell
cd web-ui
npm install
npm run build
```

The Gradle build does not require Node. WebUI build output is intentionally separate for this bootstrap.

## External Project Memory

Project memory lives in Obsidian at:

```text
E:\minecraftserver\fabricmod\pixel-logic-docs
```

Before future implementation work, read the relevant Obsidian notes first. Do not copy the vault into this repo.
