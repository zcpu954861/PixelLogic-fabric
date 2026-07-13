# Foundation Block Capability Matrix v1

## Purpose and scoring

This document inventories the Stage B Entity Target Reference implementation. The formal taxonomy is the existing `packs → categories → blocks` Catalog Snapshot; this document does not create a second catalog or register future empty packs.

Status means:

- `COMPLETE`: the current responsibility is dependable end to end within its stated boundary.
- `PARTIAL`: a block exists, but a target, parameter, runtime adapter, lifecycle, error, trace, or help boundary is still incomplete.
- `MISSING`: no formal block currently provides the capability.
- `DEFERRED`: the capability needs a larger subsystem and is intentionally not near-term work.

`FULLY_SIMULATABLE` is not evidence of complete Minecraft behavior. A block without a real adapter remains `PARTIAL` even when Simulation can evaluate it exactly at PixelLogic's abstraction level.

## Current snapshot

| Metric | Current value |
|---|---:|
| Registered packs | 7 |
| Registered categories | 15 |
| Built-in blocks | 25 |
| `BROWSE / CONTEXT_ONLY / HIDDEN` | 25 / 0 / 0 |
| `FULLY_SIMULATABLE / APPROXIMATE_SIMULATION` | 20 / 5 |
| `PREDICATE / PREDICATE_RACK` | 5 / 1 |
| Legacy aliases | 1 (`manual.test.start`) |
| Current block status | 1 COMPLETE / 24 PARTIAL |

All 25 definitions have a description, Form Schema and summary metadata. They all declare `REQUIRES_MINECRAFT_RUNTIME`; that flag is a required-environment marker, not proof that every block has a Minecraft executor. There is no independent help/example metadata. Entity-target failures now use stable structured codes; unaffected older paths may still expose readable-string runtime errors.

### Slice 1 result

Slice 1 is a breaking reform with no alias or migration:

| Metric | Before Slice 1 | Stage B implementation |
|---|---:|---:|
| Built-in / BROWSE blocks | 28 / 28 | 25 / 25 |
| `player-entity` blocks | 8 | 5 |
| `FULLY_SIMULATABLE / APPROXIMATE_SIMULATION` | 23 / 5 | 20 / 5 |
| `PREDICATE / PREDICATE_RACK` | 6 / 1 | 5 / 1 |
| retired player/context tag IDs | 6 | 0 |
| generic entity tag IDs | 0 | 3 |

The arithmetic is `28 - 6 + 3 = 25`. Packs and non-empty categories remain 7 and 15. The three new tag blocks remain `PARTIAL` because general non-player runtime lookup is not implemented, so the current score is 1 COMPLETE / 24 PARTIAL.

## Eight-domain overview

| Domain | Registered pack | Blocks | COMPLETE | PARTIAL | High-value MISSING | DEFERRED |
|---|---|---:|---:|---:|---|---|
| 事件与触发 | `events-triggers` | 1 | 1 | 0 | official gameplay event triggers | polling detectors and a general event bus |
| 逻辑与流程 | `logic-flow` | 4 | 0 | 4 | durable scheduling and selected small control primitives | universal execute pipeline and collection loops |
| 玩家与实体 | `player-entity` | 5 | 0 | 5 | health, damage, effects, game mode | selectors, scans, AI and multi-entity fan-out |
| 物品与容器 | not registered | 0 | 0 | 0 | give/take/match simple item stacks | full components/NBT and arbitrary container automation |
| 位置与区域 | `location-region` | 6 | 0 | 6 | Position Reference and teleport | nested coordinate expressions and world scans |
| 方块与世界 | `block-world` | 1 | 0 | 1 | typed block state and safe setblock/fill | physics, lighting and structure simulation |
| 表现与反馈 | `presentation-feedback` | 5 | 0 | 5 | real title delivery, sound and particle | secure-chat emulation |
| 状态与数据 | `state-data` | 3 | 0 | 3 | durable typed state and broader comparisons | a universal expression language |

“物品与容器” remains a planning domain only. It must not be added to the Catalog until at least one real block is implemented.

## Capability matrix

| Domain | Capability | Current blockId | Status | Vanilla high-frequency reference | PixelLogic should exceed vanilla by | Shared component need | Suggested stage |
|---|---|---|---|---|---|---|---|
| 事件与触发 | manual test entry | `trigger.manual_test` | COMPLETE | manual command invocation | repeatable Simulation, graph entry and bounded Trace | none | current |
| 事件与触发 | gameplay event triggers | none | MISSING | join, interact, death | typed event context and official loader adapters | Event Context | later event pack |
| 事件与触发 | condition detector/tick monitor | none | DEFERRED | tick function / repeated execute | event-first scheduling, rate limits and isolated runs | detector subsystem | deferred |
| 逻辑与流程 | fixed-count loop | `control.loop.count` | PARTIAL | repeated function invocation | container composition, continuation and step budgets | existing body container | runtime hardening |
| 逻辑与流程 | forever/until loop | `control.loop.forever`, `control.loop.until` | PARTIAL | scheduled function + predicate | condition rack, bounded simulation and path context | predicate contract, durable scheduler | later |
| 逻辑与流程 | wait then continue | `timer.wait` | PARTIAL | `schedule` | one-shot continuation with nested context restoration | durable timer identity | later runtime |
| 玩家与实体 | single-entity tags | three generic entity-tag blocks | PARTIAL | `tag` | shared EntityTargetRef, typed outcomes and condition subject | Entity Target Reference | implemented foundation |
| 玩家与实体 | execute with one current entity | `context.entity.execute_as` | PARTIAL | `execute as` | explicit four-source target and stack-safe continuation without selector syntax | existing execution context | implemented foundation |
| 玩家与实体 | administrator check | `condition.player.is_admin` | PARTIAL | OP/permission check | typed predicate and explicit permission provider | Player-only target constraint | later |
| 玩家与实体 | health, damage, effects and game mode | none | MISSING | `damage`, `effect`, `kill`, `gamemode` | typed targets, structured results, Simulation and errors | EntityTargetRef, EffectSpec, EntityTypeRef | v1-A |
| 玩家与实体 | entity collections and scans | none | DEFERRED | `@e[...]` | bounded query and fan-out rather than selector text | query subsystem | deferred |
| 物品与容器 | give/take/match simple items | none | MISSING | `give`, `clear`, `item` | typed id/count, atomic result and Simulation facts | ItemStackSpec, ItemMatchSpec | later foundation pack |
| 物品与容器 | arbitrary slots/components/NBT | none | DEFERRED | `item replace/modify` | explicit slot ownership and safe component matching | SlotRef, component model | deferred |
| 位置与区域 | dimension/height/region/distance checks | six location conditions | PARTIAL | positioned `execute if` | graphical conditions, test facts and Chinese Trace | PositionRef, Resource ID editor | later hardening |
| 位置与区域 | teleport/spawn position | none | MISSING | `tp`, `teleport`, `spawnpoint` | target/destination separation, collision policy and result | PositionRef, Rotation | v1-B |
| 位置与区域 | nested relative/local expressions | none | DEFERRED | `~ ~ ~`, `^ ^ ^` | typed sources instead of command syntax | position-expression subsystem | deferred |
| 方块与世界 | target block type | `condition.target_block.is_type` | PARTIAL | `execute if block` | predicate capsule and test fact, but with explicit missing-target errors | BlockStateSpec, PositionRef | later |
| 方块与世界 | set block/fill volume | none | MISSING | `setblock`, `fill` | preview, permission, loaded-chunk and affected-count result | BlockStateSpec, PositionRef | later world pack |
| 方块与世界 | structures/physics/neighbour updates | none | DEFERRED | structure and world mechanics | record intent instead of cloning Minecraft | world subsystem | deferred |
| 表现与反馈 | chat/title/subtitle/actionbar | four message blocks | PARTIAL | `tellraw`, `title` | shared rich text and structured delivery result | existing Text Component; real adapter | near-term adapter |
| 表现与反馈 | debug trace | `debug.log` | PARTIAL | server log | bounded node-local diagnostics with one consistent Runtime/Simulation help contract | existing trace | current hardening |
| 表现与反馈 | sound/particle | none | MISSING | `playsound`, `particle` | typed spec, target/position and Simulation intent | SoundSpec, ParticleSpec, PositionRef | later |
| 表现与反馈 | secure chat/signatures | none | DEFERRED | signed chat | do not pretend to simulate trust/signature state | Minecraft adapter | deferred |
| 状态与数据 | boolean comparison | `condition.state.equals` | PARTIAL | scoreboard/storage comparison | typed scope, missing policy and condition routing | typed compare/Number Source later | near-term hardening |
| 状态与数据 | typed write and integer add | `state.set`, `state.add` | PARTIAL | scoreboard/storage write | type validation, overflow guard and Trace | durable StateStore | later runtime |
| 状态与数据 | scoreboard bridge | none | DEFERRED | scoreboard/storage | keep PixelLogic state authoritative; bridge only as integration | adapter contract | later |

## Current block detail

Common boundaries for every row:

- Graph persistence stores `blockId`, typed slots, config and membership; it does not store taxonomy or runtime subjects.
- Save-time `GraphValidator` issues have severity, code and readable message.
- There is no broad per-block Minecraft executor registry yet. `RuntimeServices` is the adapter boundary; the current Fabric adapter sends plain chat and provides exact online-player entity/tag access.
- Help currently means description, field descriptions/placeholders and summary; there is no separate example/help contract.

### Auditable field appendix

This appendix keeps the task fields separate. “No MC executor” is a current Runtime contract fact, not a statement that the block lacks Simulation execution.

Rows that say `test actor` or `request player` describe block-specific fixtures or transport metadata only. They are not EntityTargetRef sources or implicit target fallbacks.

| blockId | pack/category ID | kind | Current parameters | Target/context | Actual Form Schema (`key:type`) | Simulation enum | Current Runtime contract | Errors / Trace | Current help / summary | Gap | Priority |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `trigger.manual_test` | `events-triggers` / `events-triggers.test-entry` | trigger | no persisted config; schema-only trigger label | explicit manual start | `triggerType:readonly` (schema default only) | FULLY_SIMULATABLE | `RuntimeNodeExecutor` emits the `started` output | missing entry fails; `GraphRuntime` adds manual-trigger Trace | description + manual-test summary | no gameplay event | keep |
| `control.loop.count` | `logic-flow` / `logic-flow.loops` | control | count 1..100 | body membership/cursor | `count:integer` | FULLY_SIMULATABLE | `GraphRuntime` loop frame | validation codes + iteration/resume Trace | “循环 N 次后继续” | budget/durability | medium |
| `control.loop.forever` | `logic-flow` / `logic-flow.loops` | control | interval 1..86400 s | body + continuation | `intervalSeconds:integer` | FULLY_SIMULATABLE | `GraphRuntime` forever frame | 20-round Simulation cap, wait/resume Trace | “持续循环，间隔 N 秒” | durable server loop | medium |
| `control.loop.until` | `logic-flow` / `logic-flow.loops` | control + PREDICATE_RACK | ordered condition slots | rack + body + cursor | `conditionRack:readonly` | FULLY_SIMULATABLE | `GraphRuntime` pre-check loop | empty/illegal rack fail closed; slot/round Trace | rack summaries + until summary | only five predicates | medium |
| `timer.wait` | `logic-flow` / `logic-flow.timing` | timer | duration 1..86400 s | active cursor | `durationSeconds:integer` | FULLY_SIMULATABLE | `GraphRuntime` + scheduler continuation | pending/cancel/stale bounds and Trace | “等待 N 秒后继续” | no restart durability | medium |
| `condition.entity.has_tag` | `player-entity` / `player-entity.tags` | condition + PREDICATE | output mode, target, tag | EntityTargetRef / ANY_ENTITY | `outputMode:segmented`; `target:entity_target`; `tag:string` | FULLY_SIMULATABLE | shared Runtime/Simulation resolver and tag evaluator; Fabric provider resolves online players | structured target errors; true/false subject Trace | target-aware positive/negated summary | Fabric non-player lookup remains unavailable | high |
| `action.entity.add_tag` | `player-entity` / `player-entity.tags` | action | target, tag | EntityTargetRef / ANY_ENTITY | `target:entity_target`; `tag:string` | FULLY_SIMULATABLE | shared entity-target action path | typed SUCCESS/FAILURE, changed, affectedCount | target-aware add summary | Fabric non-player lookup remains unavailable | high |
| `action.entity.remove_tag` | `player-entity` / `player-entity.tags` | action | target, tag | EntityTargetRef / ANY_ENTITY | `target:entity_target`; `tag:string` | FULLY_SIMULATABLE | shared entity-target action path | absent tag is SUCCESS/no-change; structured target failure | target-aware remove summary | Fabric non-player lookup remains unavailable | high |
| `condition.player.is_admin` | `player-entity` / `player-entity.identity-permissions` | condition + PREDICATE | output mode | Simulation actor fixture | `outputMode:segmented` | APPROXIMATE_SIMULATION | Simulation operator flag; no permission adapter | boolean/subject Trace, string runtime errors | admin positive/negated summary | permission semantics | medium |
| `context.entity.execute_as` | `player-entity` / `player-entity.execution-context` | control | four-source target, default condition subject | EntityTargetRef / ANY_ENTITY | `target:entity_target` | FULLY_SIMULATABLE | shared resolver + `GraphRuntime` context frame | structured target failures; switch/restore Trace | target-aware summary and warnings | no position context | high |
| `condition.player.dimension_is` | `location-region` / `location-region.dimensions-heights` | condition + PREDICATE | output mode, dimension id | Simulation actor position | `outputMode:segmented`; `dimensionId:string` | FULLY_SIMULATABLE | Simulation world fact; no MC position adapter | false/Trace; no contextual subject | dimension summary | adapter + subject contract | medium |
| `condition.player.y_compare` | `location-region` / `location-region.dimensions-heights` | condition | mode, target/min/max Y | Simulation actor position | `outputMode:segmented`; `compareMode:select`; `targetY/minY/maxY:integer` | FULLY_SIMULATABLE | Simulation position fact; no MC adapter | bounds validator + observed-value Trace | dynamic Y comparison summary | not PREDICATE | medium |
| `condition.target_block.y_compare` | `location-region` / `location-region.dimensions-heights` | condition | mode, target/min/max Y | optional target block | `outputMode:segmented`; `compareMode:select`; `targetY/minY/maxY:integer` | FULLY_SIMULATABLE | Simulation block fact; no MC adapter | missing target becomes false + Trace | target-block Y summary | missing-target structure | medium |
| `condition.player.in_region` | `location-region` / `location-region.regions` | condition + PREDICATE | output mode, region name | Simulation actor + named region | `outputMode:segmented`; `regionName:string` | FULLY_SIMULATABLE | Simulation region fact; no region adapter | missing region false + Trace | WebUI may render region choices | adapter + subject contract | medium |
| `condition.target_block.in_region` | `location-region` / `location-region.regions` | condition | output mode, region name | block + named region | `outputMode:segmented`; `regionName:string` | FULLY_SIMULATABLE | Simulation facts; no MC adapter | target/region missing false + Trace | WebUI may render region choices | not PREDICATE/errors | medium |
| `condition.player.near_target_block` | `location-region` / `location-region.spatial-relations` | condition | distance, horizontal flag, output | Simulation actor + target block | `outputMode:segmented`; `maxDistance:number`; `horizontalOnly:segmented` | FULLY_SIMULATABLE | Simulation positions; no MC adapter | missing/dimension mismatch readable Trace | near-target summary | PositionRef, not PREDICATE | medium |
| `condition.target_block.is_type` | `block-world` / `block-world.target-block` | condition + PREDICATE | output mode, block id | optional target block | `outputMode:segmented`; `blockId:string` | FULLY_SIMULATABLE | Simulation block fact; no world lookup | missing target false + Trace | block-type positive/negated summary | block state/adapter | medium |
| `action.message.chat` | `presentation-feedback` / `presentation-feedback.player-messages` | action | current player, rich message | request/test player | `target:readonly`; `message:rich_text_component` | APPROXIMATE_SIMULATION | Simulation result + Fabric plain chat adapter | delivery not structured; message Trace | rich-text chat summary/field help | real style/delivery result | high |
| `action.message.title` | `presentation-feedback` / `presentation-feedback.screen-prompts` | action | current player, rich message | request/test player | `target:readonly`; `message:rich_text_component` | APPROXIMATE_SIMULATION | Simulation/Runtime record intent, result and Trace; no MC display adapter | generic string result/Trace | title summary/field help | no title adapter/timing | medium |
| `action.message.subtitle` | `presentation-feedback` / `presentation-feedback.screen-prompts` | action | current player, rich message | request/test player | `target:readonly`; `message:rich_text_component` | APPROXIMATE_SIMULATION | Simulation/Runtime record intent, result and Trace; no MC display adapter | generic string result/Trace | subtitle summary/field help | no subtitle adapter/timing | medium |
| `action.message.actionbar` | `presentation-feedback` / `presentation-feedback.screen-prompts` | action | current player, rich message | request/test player | `target:readonly`; `message:rich_text_component` | APPROXIMATE_SIMULATION | Simulation/Runtime record intent, result and Trace; no MC display adapter | generic string result/Trace | actionbar summary/field help | no actionbar adapter | medium |
| `debug.log` | `presentation-feedback` / `presentation-feedback.diagnostics` | debug | message | none | `message:textarea` | FULLY_SIMULATABLE | `RuntimeNodeExecutor` calls `services.debug`, records a generic string action result and adds Trace | bounded Trace; no typed debug outcome/structured error | Catalog says simulation record; summary echoes message | help/runtime wording drift | medium |
| `condition.state.equals` | `state-data` / `state-data.conditions` | condition | scope, key, BOOLEAN expected/missing, output | StateStore | `outputMode:segmented`; `scope:scope`; `key:string`; `valueType:hidden`; `expected/missing:boolean` | FULLY_SIMULATABLE | `RuntimeNodeExecutor` + in-memory store | missing policy and readable Trace | state comparison summary | boolean only/not PREDICATE | medium |
| `state.set` | `state-data` / `state-data.mutations` | state | scope, key, type, value | StateStore | `scope:scope`; `key:string`; `valueType:select`; `value:segmented` (WebUI switches editor by type) | FULLY_SIMULATABLE | `RuntimeNodeExecutor` + in-memory store | type/limit validation + change Trace | typed set summary | no persistence | medium |
| `state.add` | `state-data` / `state-data.mutations` | state | scope, key, integer amount | StateStore | `scope:scope`; `key:string`; `valueType:hidden`; `amount:integer` | FULLY_SIMULATABLE | `RuntimeNodeExecutor`, `Math.addExact` | overflow fails before mutation + Trace | integer add summary | no persistence/Number Source | medium |

## Predicate and contextual-result boundary

The current rack-compatible predicates are:

```text
condition.entity.has_tag
condition.player.is_admin
condition.player.dimension_is
condition.player.in_region
condition.target_block.is_type
```

`condition.entity.has_tag` and `condition.player.is_admin` currently write `RuntimeConditionResult.subject`. The generic tag condition writes the resolved EntityTargetRef target on both true and false. `RuntimeConditionResult` has no `object` field; `targetEntity` is independent runtime context. A later ordinary condition without a contextual result clears the old result. Therefore “predicate-compatible” and “produces a condition subject” are separate capabilities and must not be inferred from one another.

## Vanilla high-frequency gap

| Vanilla reference | Current coverage | Assessment | PixelLogic direction |
|---|---|---|---|
| `tp` / `teleport` | none | MISSING | separate EntityTargetRef from PositionRef; typed collision/dimension policy |
| `give` / `clear` / `item` | none | MISSING | typed stack/match/slot with atomic affected-count result |
| `effect` | none | MISSING | typed effect, duration, level and visibility settings in v1-A |
| `damage` | none | MISSING | use the damage pipeline; never model it as direct health subtraction |
| `summon` | none | DEFERRED | requires PositionRef, entity type, ownership and world lifecycle |
| `setblock` / `fill` | one read-only block-type condition | MISSING | preview intent, permissions, chunks and structured affected count |
| `title` / `tellraw` | four message blocks | PARTIAL | preserve rich Text and report delivery outcome in a real adapter |
| `playsound` / `particle` | none | MISSING | typed spec plus target/position; Simulation records intent |
| `gamemode` | none | MISSING | PLAYER_ONLY action and predicate in v1-A |
| `kill` | none | MISSING | preserve death semantics and distinguish removal |
| `spawnpoint` | none | MISSING | depends on PositionRef and player target |
| `time` / `weather` | none | MISSING, lower priority | world-scoped typed actions, not command text |
| `tag` | three generic EntityTargetRef blocks | PARTIAL | shared target, condition subject and typed outcomes are implemented; real non-player lookup remains adapter work |

One command does not imply one block. PixelLogic should expose one user behavior with typed parameters, validation, readable summary, Simulation intent, runtime contract, structured result and Trace—not raw command grammar.

## PixelLogic-native strengths

- direct typed edges and explicit condition true/false outputs;
- `PASS_ONLY`, `FAIL_ONLY` and `BRANCH` without a second condition system;
- condition subjects carried on one run/path;
- stack-safe entity execution context through nested containers and Continuation;
- typed scoped state with checked integer addition;
- containerized count/forever/until control flow;
- Simulation preview using the real graph traversal path;
- Chinese summaries, validation messages and bounded Trace;
- explicit Catalog defaults and strict unknown-block handling;
- a future event context can enter the same run model without exposing Channel.

These features are the product advantage. New blocks should compose with them rather than imitate raw `execute` syntax.

## Shared parameter component priority

| Component | Controlled status | Current evidence | Decision |
|---|---|---|---|
| Entity Target Reference | 已实现 | four sources, composite Graph target, shared resolver/editor and tag consumers | reuse before adding health/termination blocks |
| Player Target Reference | 暂缓 | message/admin blocks still have older request/test-player contracts | use `EntityTargetRef` with `PLAYER_ONLY`; request actor metadata is not a target fallback |
| Position Reference | 后续 | only Simulation position facts exist | required before teleport/spawnpoint |
| Direction / Rotation | 后续 | absent | design with PositionRef when movement is in scope |
| Item Stack Specification | 后续 | absent | required for a later item foundation pack |
| Item Match Specification | 后续 | absent | required for later item conditions |
| Inventory Slot Reference | 后续 | absent | design with the item/container pack |
| Block State Specification | 已有但不足 | only plain block id and a Simulation fact exist | strengthen before world mutation |
| Entity Type Reference | 已有但不足 | Simulation has `entityTypeId`; no shared editor/model | v1-A reuses a namespaced Resource ID field; do not extract a component for one consumer |
| Status Effect Specification | 近期必须 | absent | small shared effect field reused by three v1-A blocks |
| Sound Specification | 后续 | absent | later presentation pack |
| Particle Specification | 后续 | absent | later presentation pack |
| Resource ID Editor | 已有但不足 | backend namespaced validation; WebUI text input | reuse now; picker/autocomplete later |
| Number Source | 后续 | literal number/integer fields only | v1-A deliberately uses bounded literals |
| Text Component | 已有可复用 | versioned rich text field/editor exists | reuse; real delivery adapter remains partial |
| Context Source | 已实现基础 | current/condition/target/online-player source semantics share EntityTargetRef | keep condition subject and target entity independent |

## Foundation decisions

1. `EntityTargetRef` is the next shared foundation because at least health, effect and game-mode blocks immediately reuse it.
2. A player target is a type constraint on `EntityTargetRef`, not another reference universe.
3. The existing optional Simulation target is a test fixture that populates the formal `TARGET_ENTITY` run input; it is not a separate `TEST_TARGET_ENTITY` source, and current production providers do not populate that input yet.
4. The current runtime has a condition subject but no condition object. New specifications must not pretend otherwise.
5. Slice 1 has replaced the six retired tag blocks with exactly three generic tag blocks; retired IDs are unknown with no alias, wrapper or migrator.
6. Position, inventory, selectors, scans, detector polling and advanced execute-like control remain outside v1-A.

## Evidence

- Catalog authority: `BLOCK_LIBRARY_TAXONOMY_V1.md` and `BuiltInBlockCatalog`.
- Catalog validation: `BlockCatalog` and `GraphValidator`.
- Runtime context and continuation: `RuntimeExecutionContext`, `RuntimeConditionResult`, `ExecutionCursor` and `GraphRuntime`.
- Simulation context/executors: `SimulationContext`, `SimulationExecutionRegistry` and the shared entity-tag execution path.
- Runtime adapter boundary: `RuntimeServices`.
- Trace shape: `TraceStep` and `BoundedTraceBuffer`.
- Current simulation boundary: `SIMULATION_CAPABILITY_MATRIX.md`.
