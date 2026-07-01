# PixelLogic Simulation Capability Matrix

This matrix classifies what PixelLogic should simulate in the next backend phase. It is a planning document, not an implementation list.

| Capability Area | Examples | Simulation Level | Should simulate in vNext? | Requires real MC? | Notes |
| --- | --- | --- | --- | --- | --- |
| state | `state.set`, `state.add`, state compare | `FULLY_SIMULATABLE` | yes | no | Core PixelLogic state is already simulated and should remain fully testable. |
| timer | `timer.wait`, future named timers | `FULLY_SIMULATABLE` | yes | no | Current wall-clock timer works for spike; future simulation may add fast-forward. |
| trace | debug steps, action summaries, branch path | `FULLY_SIMULATABLE` | yes | no | Trace is a PixelLogic output and must stay bounded. |
| message/chat component | `action.message.chat`, rich text plain output | `APPROXIMATE_SIMULATION` | yes | yes, for real delivery | Simulation records target and plain text; real adapter later converts to Minecraft Text/tellraw-equivalent. |
| title/actionbar | title, subtitle, actionbar | `APPROXIMATE_SIMULATION` | yes | yes, for real display | Model visible output and channel summary; do not implement client rendering, timing, fade, or combo blocks yet. |
| player tag | has/add/remove tag | `FULLY_SIMULATABLE` | yes | yes, for real server tags | Good first expansion because it is state-like and common in minigames. |
| player gamemode | check/set gamemode | `APPROXIMATE_SIMULATION` | maybe | yes | Check can be simulated; mutation must be clearly approximate until MC adapter. |
| player position | check position, teleport result | `APPROXIMATE_SIMULATION` | maybe | yes | Simulate dimension/coordinates and teleport result, not collision safety. |
| inventory simple items | has/give/take item id + count | `APPROXIMATE_SIMULATION` | yes | yes | Use item id/count/display summary; defer NBT/data-component exactness. |
| container slots | slot match, open/close/content change | `APPROXIMATE_SIMULATION` | later | yes | Simulate slot facts and events only; real container access is adapter work. |
| block type/state | block is type/state, block interact fact | `APPROXIMATE_SIMULATION` | later | yes | Simplified block table only; no physics, lighting, or neighbor updates. |
| redstone state | powered/unpowered fact, signal changed event | `APPROXIMATE_SIMULATION` | later | yes | Model a boolean or small strength value; full propagation is deferred. |
| region membership | player entered/left area | `APPROXIMATE_SIMULATION` | later | yes | Region remains outside current v1 scope, but simulation can model membership facts later. |
| world mutation | set block, play sound, spawn effect | `UNSAFE_OR_WORLD_MUTATING` | no, except recorded result | yes | Simulation may record intended mutation; real mutation needs permissions and adapter checks. |
| structure placement | paste/load structure | `UNSAFE_OR_WORLD_MUTATING` / `DEFERRED` | no | yes | Too destructive and environment-dependent for early simulation. |
| command execution | raw command, command-like actions | `SERVER_ADMIN_ONLY` / `DEFERRED` | no | yes | Do not expose raw command as normal user flow. |
| scoreboard/team | team check, scoreboard-like values | `APPROXIMATE_SIMULATION` | maybe | yes | Prefer PixelLogic state/tags first; scoreboard bridge is an adapter/integration topic. |
| permissions | OP/admin flag, capability checks | `APPROXIMATE_SIMULATION` | yes | yes | Simulation can model permission markers; real permission resolution is server-side adapter work. |
| chat signing/security | secure chat, signature validity | `REQUIRES_MINECRAFT_RUNTIME` / `DEFERRED` | no | yes | Do not simulate. Only record message intent. |
| entity AI/pathfinding | mob behavior, navigation | `REQUIRES_MINECRAFT_RUNTIME` / `DEFERRED` | no | yes | Not PixelLogic simulation scope. |
| chunk loading | loaded/unloaded real behavior | `REQUIRES_MINECRAFT_RUNTIME` / `DEFERRED` | no | yes | At most model a `chunkLoaded` fact when a block needs it. |
| NBT/data components full matching | exact item/entity/block data | `REQUIRES_MINECRAFT_RUNTIME` / `DEFERRED` | no | yes | Use simplified placeholders until a specific block requires more. |

## vNext Priority

1. Keep `state`, `timer`, and `trace` fully simulatable.
2. Keep `message/chat component` approximate until a real Minecraft Text adapter exists.
3. Add simulated actor/player tags and permissions before inventory/world features.
4. Add simple inventory only as item id/count facts.
5. Defer destructive world mutation, raw command execution, full redstone, full NBT, physics, and chunk behavior.

## Skeleton MVP Status

`feature/v1-simulation-backend-skeleton` implements the first player tag slice:

- `condition.player.has_tag`: fully simulatable against `SimulationActor.tags`.
- `action.player.add_tag`: fully simulatable by mutating the per-run simulated actor and recording action/state results.
- `action.player.remove_tag`: fully simulatable by removing a tag from the per-run simulated actor and recording action/state results.
- Real server tag read/write remains future Minecraft adapter work.

## Simulation Test Context MVP Status

`feature/v1-simulation-test-context` lets the WebUI send a per-run simulated actor into the existing test-run API:

- display name: returned in the simulation result and used by player-tag trace messages.
- tags: used as the initial actor tag set for `condition.player.has_tag`.
- administrator flag: accepted and returned for future permission blocks; no OP-sensitive behavior exists yet.
- `action.player.add_tag`: still mutates only the current run actor and returns final tags.
- no scenario persistence, multiplayer, inventory, world, container, or game mode context is implemented.

## Simulation Context Expansion v1 Status

`feature/v1-simulation-context-expansion` adds the first small world facts to that same per-run context:

- player position: dimension id plus integer x/y/z, default `minecraft:overworld (0, 64, 0)`.
- target block: disabled by default, with dimension id, integer x/y/z, and block id such as `minecraft:stone`.
- regions: up to 8 named inclusive boxes with normalized min/max coordinates.
- result summary: returns player position, target block state, and region facts so WebUI can show them after a test run.
- still no new blocks, named scenario persistence, inventory/container/entity simulation, full world map, region system, or Minecraft adapter.

## Catalog Expansion v1 Player + Message Status

- `condition.player.is_admin`: approximate simulation against the per-run actor administrator flag.
- `action.message.title`, `action.message.subtitle`, and `action.message.actionbar`: approximate simulation records message result channels `TITLE`, `SUBTITLE`, and `ACTIONBAR`.
- The rich text payload now supports selected-text color and formatting controls in WebUI. Simulation still records readable plain text for trace and preserves the structured payload in message results; real Minecraft Text delivery remains future adapter work.

## Catalog Expansion v2 Context Blocks Status

- `condition.player.dimension_is`: fully simulatable against the per-run player position dimension.
- `condition.player.in_region`: fully simulatable against named test region facts, with inclusive bounds and dimension match.
- `condition.target_block.is_type`: fully simulatable against the optional target block fact when enabled; missing target block evaluates false.
- `condition.target_block.in_region`: fully simulatable against target block position plus named region facts; missing target block or region evaluates false.
- These blocks still require a future Minecraft adapter for real server execution; current semantics are WebUI/API simulation facts only.
- No world mutation, world block map, Region system, inventory/container/entity simulation, named scenario, or persistence is added.
