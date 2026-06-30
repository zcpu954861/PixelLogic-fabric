# PixelLogic Simulation Capability Matrix

This matrix classifies what PixelLogic should simulate in the next backend phase. It is a planning document, not an implementation list.

| Capability Area | Examples | Simulation Level | Should simulate in vNext? | Requires real MC? | Notes |
| --- | --- | --- | --- | --- | --- |
| state | `state.set`, `state.add`, state compare | `FULLY_SIMULATABLE` | yes | no | Core PixelLogic state is already simulated and should remain fully testable. |
| timer | `timer.wait`, future named timers | `FULLY_SIMULATABLE` | yes | no | Current wall-clock timer works for spike; future simulation may add fast-forward. |
| trace | debug steps, action summaries, branch path | `FULLY_SIMULATABLE` | yes | no | Trace is a PixelLogic output and must stay bounded. |
| message/chat component | `action.message.chat`, rich text plain output | `APPROXIMATE_SIMULATION` | yes | yes, for real delivery | Simulation records target and plain text; real adapter later converts to Minecraft Text/tellraw-equivalent. |
| title/actionbar | title, subtitle, actionbar | `APPROXIMATE_SIMULATION` | yes, when blocks exist | yes, for real display | Model visible output and timing summary; do not implement client rendering. |
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
