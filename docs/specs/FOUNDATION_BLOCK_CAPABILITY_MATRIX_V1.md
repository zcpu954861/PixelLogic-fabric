# Foundation Block Capability Matrix v1

## Purpose and scoring

This document inventories the merged `mc-1.21.11` baseline at merge commit `ab0bf4d`. The formal taxonomy is the existing `packs → categories → blocks` Catalog Snapshot; this document does not create a second catalog or register future empty packs.

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
| Built-in blocks | 28 |
| `BROWSE / CONTEXT_ONLY / HIDDEN` | 28 / 0 / 0 |
| `FULLY_SIMULATABLE / APPROXIMATE_SIMULATION` | 23 / 5 |
| `PREDICATE / PREDICATE_RACK` | 6 / 1 |
| Legacy aliases | 1 (`manual.test.start`) |
| Current block status | 1 COMPLETE / 27 PARTIAL |

All 28 definitions have a description, Form Schema and summary metadata. They all declare `REQUIRES_MINECRAFT_RUNTIME`; that flag is a required-environment marker, not proof that a Minecraft executor exists. There is no independent help/example metadata. Trace is currently `timestamp + nodeId + message`, and runtime errors are primarily readable strings rather than stable structured action error codes.

## Eight-domain overview

| Domain | Registered pack | Blocks | COMPLETE | PARTIAL | High-value MISSING | DEFERRED |
|---|---|---:|---:|---:|---|---|
| 事件与触发 | `events-triggers` | 1 | 1 | 0 | official gameplay event triggers | polling detectors and a general event bus |
| 逻辑与流程 | `logic-flow` | 4 | 0 | 4 | durable scheduling and selected small control primitives | universal execute pipeline and collection loops |
| 玩家与实体 | `player-entity` | 8 | 0 | 8 | target reference, health, damage, effects, game mode | selectors, scans, AI and multi-entity fan-out |
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
| 玩家与实体 | player/current-entity tags | six tag blocks | PARTIAL | `tag` | a shared typed target, changed result and fail-closed adapter | Entity Target Reference | v1-A migration candidate |
| 玩家与实体 | execute with one current entity | `context.entity.execute_as` | PARTIAL | `execute as` | explicit single source and stack-safe continuation without selector syntax | existing execution context | current foundation |
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
- There is no per-block real Minecraft executor registry yet. `RuntimeServices` is the adapter boundary and the current Fabric adapter only sends a plain chat message.
- Help currently means description, field descriptions/placeholders and summary; there is no separate example/help contract.

### Auditable field appendix

This appendix keeps the task fields separate. “No MC executor” is a current Runtime contract fact, not a statement that the block lacks Simulation execution.

| blockId | pack/category ID | kind | Current parameters | Target/context | Actual Form Schema (`key:type`) | Simulation enum | Current Runtime contract | Errors / Trace | Current help / summary | Gap | Priority |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `trigger.manual_test` | `events-triggers` / `events-triggers.test-entry` | trigger | no persisted config; schema-only trigger label | explicit manual start | `triggerType:readonly` (schema default only) | FULLY_SIMULATABLE | `RuntimeNodeExecutor` emits the `started` output | missing entry fails; `GraphRuntime` adds manual-trigger Trace | description + manual-test summary | no gameplay event | keep |
| `control.loop.count` | `logic-flow` / `logic-flow.loops` | control | count 1..100 | body membership/cursor | `count:integer` | FULLY_SIMULATABLE | `GraphRuntime` loop frame | validation codes + iteration/resume Trace | “循环 N 次后继续” | budget/durability | medium |
| `control.loop.forever` | `logic-flow` / `logic-flow.loops` | control | interval 1..86400 s | body + continuation | `intervalSeconds:integer` | FULLY_SIMULATABLE | `GraphRuntime` forever frame | 20-round Simulation cap, wait/resume Trace | “持续循环，间隔 N 秒” | durable server loop | medium |
| `control.loop.until` | `logic-flow` / `logic-flow.loops` | control + PREDICATE_RACK | ordered condition slots | rack + body + cursor | `conditionRack:readonly` | FULLY_SIMULATABLE | `GraphRuntime` pre-check loop | empty/illegal rack fail closed; slot/round Trace | rack summaries + until summary | only six predicates | medium |
| `timer.wait` | `logic-flow` / `logic-flow.timing` | timer | duration 1..86400 s | active cursor | `durationSeconds:integer` | FULLY_SIMULATABLE | `GraphRuntime` + scheduler continuation | pending/cancel/stale bounds and Trace | “等待 N 秒后继续” | no restart durability | medium |
| `condition.player.has_tag` | `player-entity` / `player-entity.tags` | condition + PREDICATE | output mode, tag | run actor | `outputMode:segmented`; `tag:string` | FULLY_SIMULATABLE | Simulation executor; no MC tag executor | validator + boolean/subject Trace | positive/negated tag summaries | fixed target, no MC adapter | high |
| `action.player.add_tag` | `player-entity` / `player-entity.tags` | action | tag | run actor | `tag:string` | FULLY_SIMULATABLE | Simulation executor; no MC tag executor | validator + action/tag-change Trace | “添加玩家标签 …” | fixed target/result shape | high |
| `action.player.remove_tag` | `player-entity` / `player-entity.tags` | action | tag | run actor | `tag:string` | FULLY_SIMULATABLE | Simulation executor; no MC tag executor | absent tag is readable no-change | “移除玩家标签 …” | fixed target/result shape | high |
| `condition.context_entity.has_tag` | `player-entity` / `player-entity.tags` | condition + PREDICATE | output mode, tag | current entity | `outputMode:segmented`; `tag:string` | FULLY_SIMULATABLE | context-entity Simulation executor; no MC executor | missing current entity fails; subject Trace | positive/negated context-tag summary | no real resolver | high |
| `action.context_entity.add_tag` | `player-entity` / `player-entity.tags` | action | tag | current entity | `tag:string` | FULLY_SIMULATABLE | context-entity Simulation executor; no MC executor | unresolvable context fails; change Trace | “为上下文实体添加标签 …” | no real resolver | high |
| `action.context_entity.remove_tag` | `player-entity` / `player-entity.tags` | action | tag | current entity | `tag:string` | FULLY_SIMULATABLE | context-entity Simulation executor; no MC executor | unresolvable/absent result Trace | “移除上下文实体标签 …” | no real resolver | high |
| `condition.player.is_admin` | `player-entity` / `player-entity.identity-permissions` | condition + PREDICATE | output mode | run actor | `outputMode:segmented` | APPROXIMATE_SIMULATION | Simulation operator flag; no permission adapter | boolean/subject Trace, string runtime errors | admin positive/negated summary | permission semantics | medium |
| `context.entity.execute_as` | `player-entity` / `player-entity.execution-context` | control | `CONDITION_SUBJECT/RUN_ENTITY/TARGET_ENTITY` | one resolvable source | `entitySource:select` | FULLY_SIMULATABLE | `GraphRuntime` context frame | missing/type/unresolvable fail; switch/restore Trace | summary plus UI/errors still say “条件对象” | production target provider; object→subject terminology | high |
| `condition.player.dimension_is` | `location-region` / `location-region.dimensions-heights` | condition + PREDICATE | output mode, dimension id | run actor position | `outputMode:segmented`; `dimensionId:string` | FULLY_SIMULATABLE | Simulation world fact; no MC position adapter | false/Trace; no contextual subject | dimension summary | adapter + subject contract | medium |
| `condition.player.y_compare` | `location-region` / `location-region.dimensions-heights` | condition | mode, target/min/max Y | run actor position | `outputMode:segmented`; `compareMode:select`; `targetY/minY/maxY:integer` | FULLY_SIMULATABLE | Simulation position fact; no MC adapter | bounds validator + observed-value Trace | dynamic Y comparison summary | not PREDICATE | medium |
| `condition.target_block.y_compare` | `location-region` / `location-region.dimensions-heights` | condition | mode, target/min/max Y | optional target block | `outputMode:segmented`; `compareMode:select`; `targetY/minY/maxY:integer` | FULLY_SIMULATABLE | Simulation block fact; no MC adapter | missing target becomes false + Trace | target-block Y summary | missing-target structure | medium |
| `condition.player.in_region` | `location-region` / `location-region.regions` | condition + PREDICATE | output mode, region name | actor + named region | `outputMode:segmented`; `regionName:string` | FULLY_SIMULATABLE | Simulation region fact; no region adapter | missing region false + Trace | WebUI may render region choices | adapter + subject contract | medium |
| `condition.target_block.in_region` | `location-region` / `location-region.regions` | condition | output mode, region name | block + named region | `outputMode:segmented`; `regionName:string` | FULLY_SIMULATABLE | Simulation facts; no MC adapter | target/region missing false + Trace | WebUI may render region choices | not PREDICATE/errors | medium |
| `condition.player.near_target_block` | `location-region` / `location-region.spatial-relations` | condition | distance, horizontal flag, output | actor + target block | `outputMode:segmented`; `maxDistance:number`; `horizontalOnly:segmented` | FULLY_SIMULATABLE | Simulation positions; no MC adapter | missing/dimension mismatch readable Trace | near-target summary | PositionRef, not PREDICATE | medium |
| `condition.target_block.is_type` | `block-world` / `block-world.target-block` | condition + PREDICATE | output mode, block id | optional target block | `outputMode:segmented`; `blockId:string` | FULLY_SIMULATABLE | Simulation block fact; no world lookup | missing target false + Trace | block-type positive/negated summary | block state/adapter | medium |
| `action.message.chat` | `presentation-feedback` / `presentation-feedback.player-messages` | action | current player, rich message | run player | `target:readonly`; `message:rich_text_component` | APPROXIMATE_SIMULATION | Simulation result + Fabric plain chat adapter | delivery not structured; message Trace | rich-text chat summary/field help | real style/delivery result | high |
| `action.message.title` | `presentation-feedback` / `presentation-feedback.screen-prompts` | action | current player, rich message | run player | `target:readonly`; `message:rich_text_component` | APPROXIMATE_SIMULATION | Simulation/Runtime record intent, result and Trace; no MC display adapter | generic string result/Trace | title summary/field help | no title adapter/timing | medium |
| `action.message.subtitle` | `presentation-feedback` / `presentation-feedback.screen-prompts` | action | current player, rich message | run player | `target:readonly`; `message:rich_text_component` | APPROXIMATE_SIMULATION | Simulation/Runtime record intent, result and Trace; no MC display adapter | generic string result/Trace | subtitle summary/field help | no subtitle adapter/timing | medium |
| `action.message.actionbar` | `presentation-feedback` / `presentation-feedback.screen-prompts` | action | current player, rich message | run player | `target:readonly`; `message:rich_text_component` | APPROXIMATE_SIMULATION | Simulation/Runtime record intent, result and Trace; no MC display adapter | generic string result/Trace | actionbar summary/field help | no actionbar adapter | medium |
| `debug.log` | `presentation-feedback` / `presentation-feedback.diagnostics` | debug | message | none | `message:textarea` | FULLY_SIMULATABLE | `RuntimeNodeExecutor` calls `services.debug`, records a generic string action result and adds Trace | bounded Trace; no typed debug outcome/structured error | Catalog says simulation record; summary echoes message | help/runtime wording drift | medium |
| `condition.state.equals` | `state-data` / `state-data.conditions` | condition | scope, key, BOOLEAN expected/missing, output | StateStore | `outputMode:segmented`; `scope:scope`; `key:string`; `valueType:hidden`; `expected/missing:boolean` | FULLY_SIMULATABLE | `RuntimeNodeExecutor` + in-memory store | missing policy and readable Trace | state comparison summary | boolean only/not PREDICATE | medium |
| `state.set` | `state-data` / `state-data.mutations` | state | scope, key, type, value | StateStore | `scope:scope`; `key:string`; `valueType:select`; `value:segmented` (WebUI switches editor by type) | FULLY_SIMULATABLE | `RuntimeNodeExecutor` + in-memory store | type/limit validation + change Trace | typed set summary | no persistence | medium |
| `state.add` | `state-data` / `state-data.mutations` | state | scope, key, integer amount | StateStore | `scope:scope`; `key:string`; `valueType:hidden`; `amount:integer` | FULLY_SIMULATABLE | `RuntimeNodeExecutor`, `Math.addExact` | overflow fails before mutation + Trace | integer add summary | no persistence/Number Source | medium |

## Predicate and contextual-result boundary

The current rack-compatible predicates are:

```text
condition.player.has_tag
condition.player.is_admin
condition.player.dimension_is
condition.player.in_region
condition.target_block.is_type
condition.context_entity.has_tag
```

Only `condition.player.has_tag`, `condition.player.is_admin` and `condition.context_entity.has_tag` currently write `RuntimeConditionResult.subject`. `RuntimeConditionResult` has no `object` or independent target field. A later ordinary condition without a contextual result clears the old result. Therefore “predicate-compatible” and “produces a condition subject” are separate capabilities and must not be inferred from one another.

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
| `tag` | six player/current-entity blocks | PARTIAL | migrate toward one EntityTargetRef and a real adapter without breaking old graphs |

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
- legacy `blockId` canonicalization and old-graph defaults;
- a future event context can enter the same run model without exposing Channel.

These features are the product advantage. New blocks should compose with them rather than imitate raw `execute` syntax.

## Shared parameter component priority

| Component | Controlled status | Current evidence | Decision |
|---|---|---|---|
| Entity Target Reference | 近期必须 | run/current/condition references exist, but no action-level unified target | design in `ENTITY_TARGET_REFERENCE_V1.md` |
| Player Target Reference | 暂缓 | current message/tag blocks hard-code run actor | use `EntityTargetRef` with `PLAYER_ONLY`; do not create a parallel system |
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
| Context Source | 已有但不足 | run/current/condition cursor semantics exist | reuse and narrow wording to real `subject` semantics |

## Foundation decisions

1. `EntityTargetRef` is the next shared foundation because at least health, effect and game-mode blocks immediately reuse it.
2. A player target is a type constraint on `EntityTargetRef`, not another reference universe.
3. The existing optional Simulation target is a test fixture that populates the formal `TARGET_ENTITY` run input; it is not a separate `TEST_TARGET_ENTITY` source, and current production providers do not populate that input yet.
4. The current runtime has a condition subject but no condition object. New specifications must not pretend otherwise.
5. Existing tag blocks are migration examples, not reasons to add duplicate “set tag” blocks in v1-A.
6. Position, inventory, selectors, scans, detector polling and advanced execute-like control remain outside v1-A.

## Evidence

- Catalog authority: `BLOCK_LIBRARY_TAXONOMY_V1.md` and `BuiltInBlockCatalog`.
- Catalog validation: `BlockCatalog` and `GraphValidator`.
- Runtime context and continuation: `RuntimeExecutionContext`, `RuntimeConditionResult`, `ExecutionCursor` and `GraphRuntime`.
- Simulation context/executors: `SimulationContext`, `SimulationExecutionRegistry` and `ContextEntityTagExecutors`.
- Runtime adapter boundary: `RuntimeServices`.
- Trace shape: `TraceStep` and `BoundedTraceBuffer`.
- Current simulation boundary: `SIMULATION_CAPABILITY_MATRIX.md`.
