# PixelLogic Simulation Capability Matrix

This matrix classifies current Simulation capabilities and the boundary of later Minecraft adapters. A `FULLY_SIMULATABLE` entry describes PixelLogic's simulation semantics, not proof of equivalent real-server behavior.

| Capability Area | Examples | Simulation Level | Should simulate in vNext? | Requires real MC? | Notes |
| --- | --- | --- | --- | --- | --- |
| state | `state.set`, `state.add`, state compare | `FULLY_SIMULATABLE` | yes | no | Core PixelLogic state is already simulated and should remain fully testable. |
| timer | `timer.wait`, future named timers | `FULLY_SIMULATABLE` | yes | no | Wall-clock waits preserve loop and nested-loop continuation state; output is optional and the WebUI polls the bounded run snapshot until terminal. |
| control loop | `control.loop.count`, `control.loop.forever`, `control.loop.until` | `FULLY_SIMULATABLE` | yes | yes, for durable server loops | Count/forever/until bodies resume after waits with cumulative safety caps; until uses predicate pre-check, AND + per-slot NOT, and a 20-round simulation cap. Persistent cross-restart scheduling still requires Minecraft runtime. |
| trace | debug steps, action summaries, branch path | `FULLY_SIMULATABLE` | yes | no | Trace is a PixelLogic output and must stay bounded. |
| message/chat component | `action.message.chat`, rich text plain output | `APPROXIMATE_SIMULATION` | yes | yes, for real delivery | Simulation records target and plain text; real adapter later converts to Minecraft Text/tellraw-equivalent. |
| title/actionbar | title, subtitle, actionbar | `APPROXIMATE_SIMULATION` | yes | yes, for real display | Model visible output and channel summary; do not implement client rendering, timing, fade, or combo blocks yet. |
| entity tag | target-aware has/add/remove tag | `FULLY_SIMULATABLE` | yes | yes, for real server tags | Uses the shared four-source EntityTargetRef and one generic execution path. |
| entity health and termination | damage, heal, set health, kill, direct remove | `APPROXIMATE_SIMULATION` | yes | yes | Uses the same target resolver/outcome path. Simulation models unmitigated health math, death/removal and invulnerability; Fabric uses audited normal damage/heal/set/kill/discard APIs. |
| entity status effects | add/update/remove one effect | `APPROXIMATE_SIMULATION` | yes | yes | Simulation keeps only each effect's current visible record; Fabric uses registry-backed status APIs and may retain Minecraft's hidden fallback chain. |
| contextual condition result | checked subject + raw result + readable fact | `FULLY_SIMULATABLE` | yes | yes, for real-world subjects | True and false evaluations retain the checked object on the current run/path; the result is temporary cursor state, not graph or global state. |
| entity execution context | execute as current/condition/target/online-player source | `FULLY_SIMULATABLE` | yes | yes, for real entities | Simulation switches one current entity and restores it across nesting/delay/loop; v1 is not execute-at and does not scan or fan out entities. |
| simulated target entity | optional type id, display name, tags, living/health/max/invulnerable facts | `FULLY_SIMULATABLE` | yes | yes, for real entity lookup | Per-run Test Context fact only; mutable health, alive, killed and removed state remain isolated to the copied run fixture. |
| player game mode | set one online player's mode | `APPROXIMATE_SIMULATION` | yes | yes | Simulation mutates the per-run player fact; the Fabric adapter uses the audited server-player game-mode API. The condition remains Slice 4. |
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

The original skeleton implemented the first player-tag slice. Entity Target Reference v1 now exposes the current generic form:

- `condition.entity.has_tag`: fully simulatable against the explicitly resolved target tags.
- `action.entity.add_tag`: fully simulatable with typed changed/no-change outcomes.
- `action.entity.remove_tag`: fully simulatable with typed changed/no-change outcomes.
- The Fabric provider supports exact online-player access and UUID lookup of currently loaded entities without scanning entity collections or loading chunks.

## Simulation Test Context MVP Status

`feature/v1-simulation-test-context` lets the WebUI send a per-run simulated actor into the existing test-run API:

- display name: returned in the simulation result and used by player-tag trace messages.
- tags: used as the initial current-entity tag set when the generic tag condition targets `CURRENT_ENTITY`.
- administrator flag: accepted and returned for future permission blocks; no OP-sensitive behavior exists yet.
- health, maximum health and invulnerability: bounded per-run actor facts used by health actions and returned as initial/final state.
- the generic add-tag action mutates only the resolved entity for the current run and returns final tags/outcome.
- status-effect and game-mode state exist only as internal per-run entity facts (empty effects and `SURVIVAL` by default); the Test Context API/UI does not expose inputs or dedicated result fields for them.
- no scenario persistence, multiplayer collection, inventory, world map or container context is implemented.

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

## Catalog Expansion v3 Spatial Conditions Status

- `condition.player.y_compare`: fully simulatable against the per-run player Y coordinate.
- `condition.target_block.y_compare`: fully simulatable against the optional target block Y coordinate; missing target block evaluates false.
- `condition.player.near_target_block`: fully simulatable against player and target block positions, with horizontal-only or 3D distance.
- Dimension mismatch evaluates false for near-target checks.
- These blocks still require a future Minecraft adapter for real server execution.
- No target-block-exists block, X/Z coordinate comparisons, region geometry, world map, named scenario, or persistence is added.

## Container Control Flow v1 Status

- `control.loop.count` is simulatable with a fixed integer count and bounded validation.
- `control.loop.forever` is simulation-safe with a finite iteration cap and no outer next edge.
- Container membership is stored on flat graph nodes and remains editor/runtime metadata.
- `timer.wait` inside count/forever and nested loop bodies resumes from the saved cursor without replaying earlier body nodes.
- Forever inter-round intervals use the wall-clock continuation path and the simulation cap remains 20 rounds across resumes.
- Real persistent server loop scheduling, break/continue, for-each, variable counts, and if/else are deferred.

## Loop Until + Condition Rack v1 Status

- `control.loop.until` pre-checks an ordered condition rack before every body iteration.
- v1 combines all slot results with AND and applies each slot's independent NOT after raw predicate evaluation.
- The predicate capsule set includes `condition.entity.has_tag`, `condition.player.is_admin`, `condition.player.dimension_is`, `condition.player.in_region`, and `condition.target_block.is_type`.
- These blocks reuse their existing Simulation Test Context facts and the same raw evaluator used by ordinary condition execution.
- Zero slots, empty slots, illegal predicate membership/evaluation, and a false condition with an empty body fail closed at runtime; incomplete graph states remain saveable warnings where structurally safe.
- `timer.wait` in the body reuses the existing continuation cursor/frame and does not reset the 20-round loop-until cap.
- OR, condition groups, asynchronous predicates, cross-restart continuation, and real Minecraft condition/runtime adapters remain deferred.

## Entity Target Reference + Execution Context v1 Status

- `condition.entity.has_tag` and `condition.player.is_admin` return the checked subject, raw boolean, and readable fact on true and false evaluations. Existing `PASS_ONLY`, `FAIL_ONLY`, and `BRANCH` routing is unchanged.
- The latest ordinary contextual result is scoped to the current run/control path. A later condition replaces it, and a condition without a contextual result clears it; no static/global last-result state is used. Loop-until predicate evaluation does not overwrite the ordinary path result.
- `context.entity.execute_as` resolves exactly one of `CURRENT_ENTITY`, `CONDITION_SUBJECT`, `TARGET_ENTITY`, or `ONLINE_PLAYER`, switches the current entity for its body, and restores the outer entity on empty or natural body completion.
- `ExecutionCursor` carries optional target/current stable identities, the current condition result, entity-context frames, and loop frames through `timer.wait`. Invalid, cancelled, duplicate, or stale resumes fail closed before entity mutation.
- The three generic tag blocks operate on the resolved EntityTargetRef; there is no split player/context tag executor or permanent start-of-run target fallback.
- Simulation Test Context optionally supplies one target entity (`minecraft:zombie`, `测试僵尸`, 20/20 health, living and vulnerable by default). Each run copies actor/target tags and life facts; results expose initial/final tags, health, alive and removed state separately.
- Graph/storage persists one nested target object plus block/config, edges, and flat `body` membership. Runtime subjects, condition facts, entity state, and scope frames are not persisted.
- Save-time validation rejects unknown sources and invalid/deep/cyclic membership while allowing an empty body as a warning. Runtime fails closed on missing/non-entity/unresolvable subjects or targets and never falls back to another source.
- Not implemented: execute-at, unloaded/offline entity lookup, world-entity scanning, offline-player actions, detector/event blocks, multi-entity arrays or fan-out, and cross-restart continuation. A future detector must produce one isolated path/run and condition result per matched entity.

## Health and Termination v1 Status

- `action.entity.damage` supports the closed `GENERIC/MAGIC/FIRE/FALL/VOID` list. Simulation applies deterministic unmitigated damage unless the fixture is invulnerable; Runtime calls the audited Minecraft damage pipeline and never substitutes set-health.
- `action.entity.heal` restores up to fixture/live maximum health and reports full-health success with `changed=false`.
- `action.entity.set_health` accepts zero, rejects negative/non-finite/static-overflow values, and fails above the resolved maximum without clamping.
- `action.entity.kill` marks the Simulation fixture killed/dead and calls the normal living-entity kill path in Fabric. `action.entity.remove` marks it removed/unresolvable and calls `discard()` only for non-players.
- All five reuse the four-source target resolver, stable Continuation identities, typed outcomes and structured domain/target errors. They add no success/failure graph ports.

## Status Effects and Player Game Mode v1 Status

- `action.entity.add_status_effect` accepts a namespaced effect id, finite duration in seconds, user level 1..256, `ambient`/particles/icon flags and `VANILLA_UPDATE` or `REPLACE`. `action.entity.remove_status_effect` removes one known effect; a known absent effect succeeds with `changed=false`.
- `VANILLA_UPDATE` follows the frozen target-version visible-state rules: stronger replaces visible level/duration, equal-and-longer extends, equal-and-not-longer preserves, and weaker does not replace the visible effect. Visible ambient/particle/icon updates follow the detailed table in `PLAYER_ENTITY_FOUNDATION_V1A.md`.
- `REPLACE` fully covers visible level, duration, ambient, particles and icon. Identical state is a successful no-change; a finite v1 request replaces either a finite or infinite existing effect. Infinite incoming effects exist only in the audited lower-level transition model/self-check, not Graph v1 configuration.
- Simulation stores only one current visible record per effect id. It has no duration countdown, expiry clock or Minecraft hidden fallback chain, so a weaker-longer vanilla update can leave the simulated visible record unchanged even though real Minecraft retains a fallback.
- `action.player.set_game_mode` supports `SURVIVAL`, `CREATIVE`, `ADVENTURE` and `SPECTATOR`. Simulation mutates the isolated per-run player fixture; same-mode requests succeed with `changed=false`.
- Graph validation checks the namespaced id shape, but the execution provider owns existence. The Fabric provider resolves `Registries.STATUS_EFFECT`; Simulation delegates to its supplied provider and fails closed without one. Fabric then uses audited update/replacement/removal APIs and changes game mode through the online server-player API. Unknown effects, rejected effects, dead/wrong targets, offline players and rejected mode changes fail with structured errors.
- The standalone `ApiWebUiDevServer` is a deliberately narrow hand-test fixture: its online-player picker exposes only `DevPlayer`, and its effect authority recognizes only the Catalog default `minecraft:speed`; hand tests on that server must use Speed, while every other effect id fails closed. Production Fabric still uses the complete `Registries.STATUS_EFFECT` authority.
- The current Test Context request/UI still has no status-effect or game-mode fields, and `SimulationExecutionResult` has no dedicated effect-map/game-mode result fields. These actions are observable through their typed action outcome, readable message and `changed` flag.
