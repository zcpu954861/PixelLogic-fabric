# Player & Entity Foundation v1-A

正式名称：**Player & Entity Foundation v1-A / 玩家与实体基础包：目标、生命与状态**。

This is the design contract for the full v1-A pack and the implementation record for Slice 1 plus Slice 2. Status-effect, game-mode and condition blocks remain planned only.

## Product slice

v1-A recommends:

- one shared Entity Target Reference v1;
- eight action blocks;
- six condition/Predicate Capsule blocks;
- reuse of the existing direct-edge Graph, condition output modes, Predicate Rack, entity execution context, Continuation and Simulation runner.

The pack is intentionally limited to high-frequency single-entity behavior that does not require PositionRef, inventory, world scanning or a large event subsystem.

## Implemented Slice 1 reform

Stage B has replaced the six player/context-specific tag blocks, without compatibility aliases or migration, with:

```text
condition.entity.has_tag
action.entity.add_tag
action.entity.remove_tag
```

All three use the shared composite `target: EntityTargetRef`, requirement `ANY_ENTITY`, and an explicit new-node default of `CURRENT_ENTITY`. The condition remains compatible with `PASS_ONLY`, `FAIL_ONLY`, `BRANCH` and Predicate Rack, records the resolved entity as condition subject for normal true/false results, and leaves condition object empty. The retired block IDs are unknown, and their former tag NodeTypes are absent.

Slice 1 also removes the former permanent run-start source, adds the shared resolver/errors/action outcome, provides on-demand online-player UUID selection, and moves `context.entity.execute_as` onto the same target control. Slice 2 now implements the five health/termination actions below; the remaining three actions and six conditions stay planned.

## Taxonomy plan

The Catalog registers only paths with real blocks. `生命与属性` and `实体管理` are implemented by Slice 2; `状态效果` and `玩家设置` remain planned and are not registered yet.

```text
玩家与实体
├─ 生命与属性
├─ 状态效果
├─ 玩家设置
└─ 实体管理
```

`目标与上下文` remains the conceptual home of the shared target control and existing execute-as block; a parameter component is not itself a draggable block. Every new block has one formal category.

## Shared contracts

### Target

All Slice 1 tag blocks and the fourteen later blocks use `ENTITY_TARGET_REFERENCE_V1.md`. New Catalog definitions write a composite `target` object with explicit `CURRENT_ENTITY`; stored configs never receive an implicit decode fallback. Each block declares `ANY_ENTITY`, `LIVING_ENTITY` or `PLAYER_ONLY`. Target resolution failure terminates execution; a condition must not turn a missing target into its false branch.

### Results and errors

Actions extend the existing node-result path with one bounded `RuntimeActionOutcome`:

```text
status              SUCCESS / FAILURE
kind                canonical blockId/action kind
code
message
target              resolved stable reference when available
changed
affectedCount
```

The surrounding `SimulationActionResult` supplies `nodeId`. `target` contains the stable identity and bounded display name; unresolved target errors do not fabricate a target.

`RuntimeNodeExecutionResult` carries nullable `actionOutcome` beside its output, Trace and condition result. `RuntimeServices.recordActionOutcome` preserves the typed outcome; unaffected legacy actions continue to use the string recorder.

The executor returns `actionOutcome` and does not record it directly. `GraphRuntime` records a non-null outcome exactly once. `SUCCESS` may continue through the ordinary done edge. `FAILURE` carries the same stable code/message as `RuntimeResult.targetError` or `RuntimeResult.actionError` and does not select an output. Before/after health is kept in the bounded readable message rather than a free-form payload map.

The two Slice 1 tag actions are the first consumers:

- adding an existing tag returns `SUCCESS`, `changed=false`, `affectedCount=1`;
- removing an absent tag returns `SUCCESS`, `changed=false`, `affectedCount=1`;
- a real add or remove returns `SUCCESS`, `changed=true`, `affectedCount=1`;
- target resolution failure returns `FAILURE`, `changed=false`, `affectedCount=0` and the structured terminal target error.

This is not a generic object/result framework. It is the minimum extension reused by the Slice 1 tag actions and Slice 2 health/termination actions. There is no free-form payload map.

All common target errors come from EntityTargetRef. Domain errors use stable codes. Trace includes the readable target, requested operation, changed/no-change outcome, selected condition output, and error code when present; it must not dump Minecraft objects.

### Conditions

All six conditions:

- reuse `PASS_ONLY`, `FAIL_ONLY` and `BRANCH`;
- declare `PREDICATE` and use the existing Predicate Rack evaluator;
- write the successfully resolved target into `RuntimeConditionResult.subject` for true and false ordinary execution;
- do not create a second condition model;
- retain existing rack behavior: rack evaluation does not overwrite the path condition result.

### Common parameter components

v1-A justifies two small reusable components:

1. `entity_target`: one composite EntityTargetRef value and compact picker;
2. `status_effect`: one namespaced effect-id editor/validator reused by add, remove and has-effect blocks. Duration, level, visibility and minimum thresholds remain ordinary block-specific fields.

`condition.entity.type_is` uses the existing namespaced Resource ID field/validator with an “实体类型” label. It is the only v1-A consumer, so v1-A does not create an `entity_type` component; extraction waits for a second real consumer.

Literal numbers remain ordinary Form Schema number fields. v1-A does not introduce Number Source or an expression language.

### Numeric and time rules

v1-A uses fixed implementation bounds rather than adapter-dependent defaults:

- damage and heal values are decimal health points in `0.001..1_000_000`;
- set-health values are decimal health points in `0..1_000_000` and must not exceed the resolved target's current maximum health;
- health-condition values are decimal health points in `0..1_000_000`;
- status-effect duration is an integer `1..1_000_000` seconds;
- minimum remaining effect duration is an integer `0..1_000_000` seconds;
- effect level is user-facing `1..256` and maps exactly to amplifier `0..255` by subtracting one;
- seconds convert to ticks with checked integer multiplication `seconds × 20`; overflow or a non-integral/invalid config fails validation before execution;
- health/damage values must parse as finite decimal numbers; `NaN`, infinity, underflow below the declared minimum and values above the maximum are rejected;
- Runtime and Simulation convert health values to the target-version numeric type only after validation, so both report the same `changed` semantics at float precision;
- health `EQUAL` uses the same fixed `0.0001` tolerance in Simulation and Runtime.

No action saturates numeric overflow silently. Only ordinary healing stops at maximum health; set-health above the resolved maximum fails without clamping.

## Final action set

| blockId | Display name | Formal category | Target type | Simulation enum |
|---|---|---|---|---|
| `action.entity.damage` | 伤害实体 | 生命与属性 | LIVING_ENTITY | APPROXIMATE_SIMULATION |
| `action.entity.heal` | 恢复实体生命 | 生命与属性 | LIVING_ENTITY | APPROXIMATE_SIMULATION |
| `action.entity.set_health` | 设置实体生命值 | 生命与属性 | LIVING_ENTITY | APPROXIMATE_SIMULATION |
| `action.entity.kill` | 杀死实体 | 实体管理 | LIVING_ENTITY | APPROXIMATE_SIMULATION |
| `action.entity.remove` | 移除非玩家实体 | 实体管理 | ANY_ENTITY + player prohibition | APPROXIMATE_SIMULATION |
| `action.entity.add_status_effect` | 给予状态效果 | 状态效果 | LIVING_ENTITY | APPROXIMATE_SIMULATION |
| `action.entity.remove_status_effect` | 移除状态效果 | 状态效果 | LIVING_ENTITY | APPROXIMATE_SIMULATION |
| `action.player.set_game_mode` | 设置玩家游戏模式 | 玩家设置 | PLAYER_ONLY | APPROXIMATE_SIMULATION |

### `action.entity.damage` — 伤害实体

- **Responsibility:** ask the Minecraft damage pipeline to apply typed damage; it is not direct health subtraction.
- **Target/default:** `LIVING_ENTITY`, default `CURRENT_ENTITY`; target must be alive.
- **Common parameters:** target and damage amount in health points (`0.001..1_000_000`).
- **Advanced parameters:** closed `damageKind` enum: `GENERIC`, `MAGIC`, `FIRE`, `FALL`, `VOID`.
- **Explicitly unsupported:** arbitrary damage-type resource ids, attacker/projectile ownership, bypass flags, custom death messages and direct set-health behavior.
- **Form Schema:** `entity_target`, number `amount`, select `damageKind`.
- **Summary:** `对当前执行实体造成 4 点普通伤害`.
- **GraphValidator:** target config, finite positive range and known damage kind.
- **Simulation:** use one explicit deterministic approximation at target-runtime float precision: if fixture `invulnerable=true`, reject with no change; otherwise apply unmitigated damage to fixture health. Armor, resistance, damage cooldown and enchantments are not modelled. The Catalog declares `APPROXIMATE_SIMULATION`, and Trace/result message calls this an unmitigated approximation, so it validates flow/death intent rather than predicting the server's accepted amount.
- **Runtime contract:** the audited 1.21.11 adapter maps to `generic()`, `magic()`, `inFire()`, `fall()` or `outOfWorld()` and invokes `damage(ServerWorld, DamageSource, float)` so Minecraft remains authoritative.
- **Errors/result:** `ENTITY_TARGET_NOT_ALIVE`, `ENTITY_DAMAGE_REJECTED`, `ENTITY_DAMAGE_KIND_UNSUPPORTED`; result distinguishes requested amount, accepted/no-change and before/after health when observable.
- **Trace/help:** explains that the real server may reduce or reject damage; example: damage the latest condition subject after a type check.
- **Self-check:** deterministic approximation, invulnerable rejection, death, wrong type, dead target, each source and continuation cancellation; a Runtime adapter spy proves the real executor issues a damage request and never substitutes a set-health request.
- **Creation/defaults:** new Catalog nodes explicitly store `target.source=CURRENT_ENTITY` and `damageKind=GENERIC`; missing target or amount is invalid.

### `action.entity.heal` — 恢复实体生命

- **Responsibility:** add health up to the target's current maximum; it is not set-health and never resurrects.
- **Target/default:** alive `LIVING_ENTITY`, default `CURRENT_ENTITY`.
- **Parameters:** target and finite `amount` in health points (`0.001..1_000_000`).
- **Advanced parameters:** none in v1.
- **Explicitly unsupported:** resurrection, absorption hearts, hunger, status-effect healing and maximum-health mutation.
- **Form Schema:** `entity_target`, number `amount`.
- **Summary:** `恢复当前执行实体 6 点生命`.
- **GraphValidator:** target plus finite positive amount.
- **Simulation:** clamp to fixture max health and report actual restored amount.
- **Runtime contract:** use the target-version living-entity heal API; Minecraft supplies max-health and alive state.
- **Errors/result:** `ENTITY_TARGET_NOT_ALIVE`; success may have `changed=false` when already full, with before/after/actual restored amount.
- **Trace/help:** explicitly distinguishes restoring from setting health.
- **Self-check:** partial/full clamp, already full, dead, wrong type and target failure.
- **Creation/defaults:** new nodes explicitly store `CURRENT_ENTITY`; amount is required and missing target is invalid.

### `action.entity.set_health` — 设置实体生命值

- **Responsibility:** set one living entity to an explicit health value, including zero.
- **Target/default:** alive `LIVING_ENTITY`, default `CURRENT_ENTITY`.
- **Common parameters:** target and finite `health` in `0..1_000_000`, additionally bounded by the resolved target's maximum health.
- **Advanced parameters:** none in v1.
- **Explicitly unsupported:** maximum-health/attribute mutation, absorption and resurrection.
- **Form Schema:** `entity_target`, number `health`.
- **Summary:** `把当前执行实体的生命值设为 10`.
- **GraphValidator:** finite static bound; Runtime/Simulation also enforce the resolved target's current maximum.
- **Simulation:** use the fixture maximum and fail above it without clamping.
- **Runtime contract:** read current maximum then call the audited living-entity `setHealth(float)` API. Zero follows the current version's health-zero/death state, while `kill` remains the explicit normal kill operation.
- **Errors/result:** `ENTITY_TARGET_NOT_ALIVE`, `ENTITY_HEALTH_ABOVE_MAXIMUM`; result includes requested, applied and before/after health.
- **Trace/help:** warns that maximum health can vary by attributes and effects.
- **Self-check:** below/equal/zero/negative/above max, dead/wrong target and no remove alias.
- **Creation/defaults:** new nodes explicitly store `CURRENT_ENTITY`; health is required and missing target is invalid.

### `action.entity.kill` — 杀死实体

- **Responsibility:** perform Minecraft kill/death semantics on one living entity; it is not removal.
- **Target/default:** alive `LIVING_ENTITY`, default `CURRENT_ENTITY`; players are a valid target, while the server execution boundary may still reject an unauthorized operation.
- **Parameters:** target only.
- **Advanced parameters:** none.
- **Explicitly unsupported:** silent discard, custom loot/death-message flags, mass kill and arbitrary selector targets.
- **Form Schema:** `entity_target`.
- **Summary:** `杀死当前执行实体`.
- **GraphValidator:** valid target config only. Authorization is runtime/server policy, not a Graph field.
- **Simulation:** mark fixture dead and record death intent; loot/events are not simulated.
- **Runtime contract:** invoke the target-version kill/death path, preserving server events and normal consequences.
- **Errors/result:** `ENTITY_TARGET_NOT_ALIVE`, `ENTITY_KILL_REJECTED`; changed true only when a live target is killed.
- **Trace/help:** states that players may be killed and recommends explicit confirmation for player-only graphs.
- **Self-check:** living mob, player, already dead, rejection and cancellation before execution.
- **Creation/defaults:** new nodes explicitly store `CURRENT_ENTITY`; missing target is invalid.

### `action.entity.remove` — 移除非玩家实体

- **Responsibility:** discard one non-player entity without death, damage, loot or death-event semantics.
- **Target/default:** `ANY_ENTITY`, default `CURRENT_ENTITY`, plus a hard runtime prohibition on `PLAYER`.
- **Parameters:** target only.
- **Advanced parameters:** none.
- **Explicitly unsupported:** players, mass cleanup, unloaded-entity search, loot, damage and kill semantics.
- **Form Schema:** `entity_target` with a visible “玩家不可用” safety note.
- **Summary:** `直接移除当前执行实体（不触发死亡流程）`.
- **GraphValidator:** valid target; a statically player-only source/type is an error when knowable.
- **Simulation:** mark a non-player fixture removed and unresolvable for later actions; do not produce loot/death effects.
- **Runtime contract:** call the audited target-version discard/remove operation only after an adapter-level player guard.
- **Errors/result:** `ENTITY_REMOVE_PLAYER_FORBIDDEN`, `ENTITY_TARGET_UNRESOLVABLE`; changed true only on actual removal.
- **Trace/help:** prominently contrasts remove with kill.
- **Self-check:** mob removal, player rejection, later reference failure, double removal and no death-result emission.
- **Creation/defaults:** new nodes explicitly store `CURRENT_ENTITY`; missing target is invalid. Statically player-only targets fail GraphValidator, with runtime and adapter guards retained for dynamic targets.

### `action.entity.add_status_effect` — 给予状态效果

- **Responsibility:** add or update one status effect on one living entity.
- **Target/default:** alive `LIVING_ENTITY`, default `CURRENT_ENTITY`.
- **Common parameters:** target, `effectId`, integer duration seconds (`1..1_000_000`), user-facing level (`1..256`, mapped to amplifier `0..255`).
- **Display parameters:** `ambient`, `showParticles`, `showIcon`, each boolean with ordinary defaults `false/true/true`.
- **Advanced parameters:** `updatePolicy`: `VANILLA_UPDATE` (default) or `REPLACE`.
- **Explicitly unsupported:** infinite duration in v1, effect collections, arbitrary NBT/components and hidden amplifier arithmetic.
- **Form Schema:** `entity_target`, `status_effect`, integer duration/level, three booleans, policy select; display and advanced groups are collapsed by default.
- **Summary:** `给予当前执行实体 速度 II，持续 30 秒`.
- **GraphValidator:** namespaced effect id, duration/level bounds and known policy.
- **Simulation:** maintain a per-run effect map and mirror the policy table frozen by the target-version adapter audit; record before/after effect state. No executor is registered until `VANILLA_UPDATE` versus `REPLACE` behavior for weaker, stronger, shorter, longer and infinite existing effects is documented and covered by the same Simulation/Runtime self-check cases.
- **Runtime contract:** resolve the effect registry entry and use target-version status-effect APIs; `REPLACE` may remove then add only if the audited API requires it.
- **Errors/result:** `STATUS_EFFECT_UNKNOWN`, `STATUS_EFFECT_REJECTED`, target errors; result includes changed and effective duration/level.
- **Trace/help:** explains the user level/amplifier mapping and the two update policies.
- **Self-check:** new effect, stronger/weaker/longer/infinite existing effect under both policies, visibility flags, unknown id and dead target.
- **Creation/defaults:** new nodes explicitly store target, booleans and policy; effect/duration/level are required and missing target is invalid.

### `action.entity.remove_status_effect` — 移除状态效果

- **Responsibility:** remove one named effect from one living entity.
- **Target/default:** alive `LIVING_ENTITY`, default `CURRENT_ENTITY`; a resolved dead target is rejected with `ENTITY_TARGET_NOT_ALIVE`.
- **Parameters:** target and `effectId`.
- **Advanced parameters:** none.
- **Explicitly unsupported:** remove-all, effect groups and clearing hidden non-status attributes.
- **Form Schema:** `entity_target`, `status_effect` id.
- **Summary:** `移除当前执行实体的速度效果`.
- **GraphValidator:** target and namespaced effect id.
- **Simulation:** remove from the per-run effect map; absence is a successful no-change result.
- **Runtime contract:** resolve registry id and call the one-effect removal API.
- **Errors/result:** `STATUS_EFFECT_UNKNOWN`; result distinguishes removed from not present.
- **Trace/help:** example pairs it with the has-effect condition.
- **Self-check:** present, absent, unknown, wrong/dead target and idempotent no-change.
- **Creation/defaults:** new nodes explicitly store `CURRENT_ENTITY`; effect id is required and missing target is invalid.

### `action.player.set_game_mode` — 设置玩家游戏模式

- **Responsibility:** set one online player's game mode.
- **Target/default:** `PLAYER_ONLY`, default `CURRENT_ENTITY`.
- **Parameters:** target and one of `SURVIVAL`, `CREATIVE`, `ADVENTURE`, `SPECTATOR`.
- **Advanced parameters:** none.
- **Explicitly unsupported:** default game mode, offline players, permission-group changes and temporary scheduled restoration.
- **Form Schema:** `entity_target` restricted to player-capable sources and a segmented/select game-mode control.
- **Summary:** `把玩家 Steve 的游戏模式设为冒险`.
- **GraphValidator:** target config and known enum.
- **Simulation:** update the player fixture mode and record before/after; approximate because client/world side effects are not simulated.
- **Runtime contract:** UUID-authoritative online-player lookup then the audited target-version server-player game-mode API; permission remains server-authoritative.
- **Errors/result:** player offline/type mismatch plus `PLAYER_GAME_MODE_REJECTED`; same-mode request is successful no-change.
- **Trace/help:** names all four modes and warns that target must be online.
- **Self-check:** all modes, same-mode no-change, non-player, offline player and permission rejection.
- **Creation/defaults:** new nodes explicitly store `CURRENT_ENTITY`; mode is required and missing target is invalid.

## Final condition set

| blockId | Display name | Formal category | Target type | Simulation enum / Predicate role |
|---|---|---|---|---|
| `condition.entity.is_alive` | 实体是否存活 | 生命与属性 | ANY_ENTITY | FULLY_SIMULATABLE / resolved alive state |
| `condition.entity.type_is` | 实体类型是否为 | 实体管理 | ANY_ENTITY | FULLY_SIMULATABLE / exact entity type |
| `condition.entity.health_compare` | 实体生命值是否满足 | 生命与属性 | LIVING_ENTITY | FULLY_SIMULATABLE / points comparison |
| `condition.entity.has_status_effect` | 实体是否拥有状态效果 | 状态效果 | LIVING_ENTITY | FULLY_SIMULATABLE / effect + minimums |
| `condition.player.game_mode_is` | 玩家游戏模式是否为 | 玩家设置 | PLAYER_ONLY | FULLY_SIMULATABLE / exact game mode |
| `condition.entity.is_player` | 实体是否为玩家 | 实体管理 | ANY_ENTITY | FULLY_SIMULATABLE / kind check |

### `condition.entity.is_alive` — 实体是否存活

- **Responsibility:** report alive/dead for a successfully resolved entity.
- **Target/default:** `ANY_ENTITY`, default `CURRENT_ENTITY`.
- **Parameters/Form:** `entity_target`, existing `outputMode`.
- **Unsupported:** treating missing/unloaded as dead, searching the world and checking health amount.
- **Summary:** `当前执行实体是否存活`.
- **Validation:** target and output mode.
- **Simulation:** read explicit fixture `alive`; a removed/unresolvable fixture is an error, not false.
- **Runtime:** use the adapter's target-version alive/removed state.
- **Errors/Trace:** target resolution errors; normal true/false records the target as condition subject and selected output.
- **Self-check:** alive, resolved dead, removed/unresolvable, all output modes and rack evaluation.
- **Creation/defaults:** new nodes explicitly store `CURRENT_ENTITY` and the Catalog output mode; missing target is invalid.

### `condition.entity.type_is` — 实体类型是否为

- **Responsibility:** exact-match one resolved entity's registered type.
- **Target/default:** `ANY_ENTITY`, default `CURRENT_ENTITY`.
- **Parameters/Form:** target, existing namespaced Resource ID field labeled `entityTypeId`, and output mode.
- **Unsupported:** tags/type groups, inheritance, NBT, selector predicates and fuzzy names.
- **Summary:** `当前执行实体是否为 minecraft:zombie`.
- **Validation:** target, namespaced entity type id and output mode.
- **Simulation:** compare explicit fixture type id.
- **Runtime:** compare the registry identity returned by the audited adapter; unknown configured type is an error rather than ordinary false.
- **Errors/Trace:** `ENTITY_TYPE_UNKNOWN`; result retains subject for true/false.
- **Self-check:** match, mismatch, unknown id, player type, all outputs and rack.
- **Creation/defaults:** new nodes explicitly store `CURRENT_ENTITY`; type is required and missing target is invalid.

### `condition.entity.health_compare` — 实体生命值是否满足

- **Responsibility:** compare current living health in health points.
- **Target/default:** `LIVING_ENTITY`, default `CURRENT_ENTITY`; a successfully resolved dead living entity compares with health `0`, while a removed/unresolvable identity is an error.
- **Parameters:** `compareMode`: `AT_OR_ABOVE`, `AT_OR_BELOW`, `EQUAL`, `BETWEEN`; `targetHealth` or `minHealth/maxHealth`; output mode.
- **Advanced parameters:** none. Percent/max-health comparison is deferred until a real need justifies it.
- **Form Schema:** target, segmented comparison, conditional number fields and output mode, reusing the existing Y-compare editor pattern.
- **Unsupported:** absorption, maximum-health/attribute comparison and Number Source.
- **Summary:** `当前执行实体生命值不低于 10`.
- **Validation:** finite values in `0..1_000_000`, ordered range and known mode. `EQUAL` uses the common fixed `0.0001` tolerance across Simulation/Runtime.
- **Simulation/Runtime:** read fixture/live health once and use the same comparison helper.
- **Errors/Trace:** target/type errors; true/false retains subject and records observed value.
- **Self-check:** boundaries, equal tolerance, range, dead fixture, output modes and rack.
- **Creation/defaults:** new nodes explicitly store `CURRENT_ENTITY` and `AT_OR_ABOVE`; the numeric field is required and missing target is invalid.

### `condition.entity.has_status_effect` — 实体是否拥有状态效果

- **Responsibility:** check one effect with optional minimum level and remaining duration.
- **Target/default:** `LIVING_ENTITY`, default `CURRENT_ENTITY`.
- **Parameters:** target, `effectId`, integer `minimumLevel` 1..256 default 1, integer `minimumRemainingSeconds` 0..1_000_000 default 0, output mode.
- **Advanced parameters:** none.
- **Form Schema:** entity target, status-effect id, two integer/number fields and output mode.
- **Unsupported:** matching any effect, effect sets, amplifier expressions and hidden effect components.
- **Summary:** `当前执行实体是否拥有至少 II 级速度效果`.
- **Validation:** namespaced id, bounds and output mode.
- **Simulation/Runtime:** resolve one active effect and compare user level plus remaining ticks/seconds consistently. An infinite existing effect satisfies every bounded v1 `minimumRemainingSeconds`; an adapter sentinel must never be compared as a negative duration. A successfully resolved dead living entity is a normal read and may return true or false from its stored active effects; removed/unresolvable is an error.
- **Errors/Trace:** `STATUS_EFFECT_UNKNOWN`; absence or below minimum is normal false, and the subject is retained.
- **Self-check:** absent/present, finite and infinite duration boundaries, level boundaries, unknown id, all output modes and rack.
- **Creation/defaults:** new nodes explicitly store target/minimums; effect is required and missing target is invalid.

### `condition.player.game_mode_is` — 玩家游戏模式是否为

- **Responsibility:** exact-match one online player's current game mode.
- **Target/default:** `PLAYER_ONLY`, default `CURRENT_ENTITY`.
- **Parameters/Form:** player-constrained target, four-value game-mode control, output mode.
- **Unsupported:** default server mode, offline profile data and permission checks.
- **Summary:** `玩家 Steve 是否处于冒险模式`.
- **Validation:** target, mode enum and output mode.
- **Simulation:** read player fixture mode.
- **Runtime:** read the online server player's target-version game mode.
- **Errors/Trace:** offline/type errors; mismatch is normal false and retains subject.
- **Self-check:** each mode, mismatch, non-player/offline, all outputs and rack.
- **Creation/defaults:** new nodes explicitly store `CURRENT_ENTITY`; mode is required and missing target is invalid.

### `condition.entity.is_player` — 实体是否为玩家

- **Responsibility:** check whether one resolved entity is a player.
- **Target/default:** `ANY_ENTITY`, default `CURRENT_ENTITY`.
- **Parameters/Form:** entity target and output mode only.
- **Unsupported:** administrator/permission, online duration, name matching and player profile lookup.
- **Summary:** `当前执行实体是否为玩家`.
- **Validation:** target and output mode.
- **Simulation/Runtime:** compare resolved kind/metadata; no second lookup or scan.
- **Errors/Trace:** resolution failure is an error; resolved non-player is normal false and retains subject.
- **Self-check:** player, non-player, unresolved, all outputs and rack.
- **Creation/defaults:** new nodes explicitly store `CURRENT_ENTITY`; missing target is invalid.

## Minimum help examples

The first implementation supplies at least one runnable/help-center example per block without building a large Help Center:

| blockId | Minimal example |
|---|---|
| `action.entity.damage` | after “实体类型是否为 zombie”, damage the latest condition subject by 4 |
| `action.entity.heal` | heal the current execution entity by 6 after a checkpoint |
| `action.entity.set_health` | set a round participant to 20 health, failing if its current maximum is lower |
| `action.entity.kill` | kill the current execution entity when it enters an elimination path |
| `action.entity.remove` | remove a spawned non-player cleanup entity without loot/death flow |
| `action.entity.add_status_effect` | give Speed II for 30 seconds with particles and icon |
| `action.entity.remove_status_effect` | remove Speed from the latest condition subject |
| `action.player.set_game_mode` | set a selected online player to Adventure mode |
| `condition.entity.is_alive` | continue only while the current execution entity remains alive |
| `condition.entity.type_is` | branch on whether the current execution entity is a zombie |
| `condition.entity.health_compare` | enter a low-health branch at 6 health or below |
| `condition.entity.has_status_effect` | loop until the current entity no longer has Speed |
| `condition.player.game_mode_is` | allow a path only for players in Adventure mode |
| `condition.entity.is_player` | split player and non-player execution paths before player-only actions |

## Candidate decisions and deferrals

| Candidate | Decision | Reason |
|---|---|---|
| entity damage | include | high-frequency and meaningfully different from set health |
| heal | include | common minigame reward/recovery behavior |
| set health | include | deterministic setup behavior; zero is allowed, while explicit kill retains normal kill semantics |
| add/remove effect | include both | common and share one small status-effect component |
| kill | include | preserves death semantics |
| remove entity | include behind explicit no-player safety decision | necessary cleanup behavior and deliberately distinct from kill |
| set game mode | include | high-frequency player setup with a small closed enum |
| set entity velocity | defer to v1-B | needs a typed vector/direction foundation and physics semantics |
| entity tags | reform in Slice 1 | delete the six player/context variants and add three generic EntityTargetRef blocks |
| teleport | defer | depends on PositionRef, rotation, dimension and collision policy |
| summon | defer | depends on PositionRef, EntityTypeRef creation policy, ownership and world lifecycle |

## UI layering

Forms follow user behavior, not Minecraft internals:

- **Common:** target and the one main behavior value.
- **Display:** effect particles/icon/ambient settings.
- **Advanced:** explicit replacement policy or audited damage kind.

The current Form Schema has no disclosure-group model. v1-A adds only three `ui` tokens to the existing string metadata: `section:common`, `section:display` and `section:advanced`. The editor groups fields in schema order; common is always visible, while display and advanced render as accessible native disclosures, collapsed by default. Unknown section tokens fail the Catalog self-check. This needs no new `BlockFormFieldDefinition` field and does not create a layout schema.

No raw registry object, amplifier zero-base, ticks, internal enum or Minecraft class name is primary UI text.

## Implementation slices

### Slice 1 — Entity Target Foundation (implemented)

Scope:

```text
composite EntityTargetRef model, serializer and validation
four-source shared resolver; remove RUN_ENTITY
optional current-entity initialization and continuation preservation
Catalog entity_target field contract
compact accessible WebUI selector
on-demand online-player UUID provider/API and minimal Simulation fixture
minimal structured target errors and typed action outcome
replace six old tag blocks with three generic tag blocks
move context.entity.execute_as to the shared target control/resolver
migrate Catalog snapshots/fallbacks, examples, Prefabs, help and summaries; old tag IDs have zero runtime residue
Java and WebUI target self-checks
```

Recommended branch: `feature/entity-target-reference-v1` from current `mc-1.21.11` after design approval.

After all automatic checks and the explicit user hand-test gate, the implementation is committed once as `feat: add entity target references and unify entity tags`.

This slice establishes the shared target/error/result boundary reused by every later slice. Its final release gate is the explicit Stage B user hand-test matrix.

### Slice 2 — Health and Termination (implemented, awaiting user hand-test gate)

Scope: damage, heal, set health, kill and remove. The Simulation fixture extends Slice 1 metadata with bounded `health`, `maximumHealth` and `invulnerable` facts, and makes the existing per-run `alive` fact mutable for damage/kill transitions; it does not introduce a second alive field.

Implementation branch: `feature/player-entity-health-termination-v1`, based on merged Slice 1.

The audited adapter uses normal 1.21.11 damage/kill APIs and `discard()` for removal. Player removal is permanently rejected. After automatic validation, this slice remains uncommitted until the explicit user hand-test gate; its intended single commit is `feat: add entity health and termination actions`.

### Slice 3 — Status effects and player setting

Scope: add/remove status effect and set game mode. The Simulation fixture gains a per-run effect map and player game-mode fact before these executors are registered. The implementation gate is an audited, explicit `VANILLA_UPDATE`/`REPLACE` transition table—including infinite existing effects—for the target Minecraft version.

Recommended branch: `feature/player-entity-status-v1a`, based on merged Slice 2 so it reuses the finalized life-state fixture and result contract.

Suggested commits:

1. `feat: add entity status effect actions`;
2. `feat: add player game mode action`.

### Slice 4 — Condition capsules

Scope: alive, entity type, health, status effect, game mode and is-player conditions.

Recommended branch: `feature/player-entity-conditions-v1a`, based on merged Slices 2 and 3 so it reuses their health, effect, game-mode fixtures and shared components instead of changing the same Simulation model in parallel.

Suggested commit: `feat: add player entity predicate capsules`.

Each slice is independently reviewable and mergeable. Slice 3 follows merged Slice 2; Slice 4 depends on merged Slices 2 and 3. No slice should add future empty Catalog categories; only categories with its real blocks are registered.

## User hand-test gates

Slice 1:

- current entity / condition subject / target entity / online-player source selection;
- no `RUN_ENTITY` option or permanent run-start entity fallback;
- unavailable sources show a warning and fail without fallback;
- execute-as changes `CURRENT_ENTITY` target behavior;
- UUID selection survives rerender and player rename hint changes;
- delay/cancel does not mutate a stale target.
- only the three generic entity-tag blocks remain; old IDs are unknown.

Slice 2:

- damage differs from direct set health;
- heal stops at maximum; set-health accepts zero and fails above the resolved maximum without clamping; kill triggers normal death semantics;
- remove never accepts a player and does not produce death/loot semantics.

Slice 3:

- effect level/duration/display settings and update policy match summaries;
- absent effect removal is a readable no-change;
- game mode targets only an online player.

Slice 4:

- every condition supports PASS_ONLY/FAIL_ONLY/BRANCH and Predicate Rack;
- true and false preserve the resolved condition subject;
- missing/unresolved targets stop rather than entering false output;
- negated capsules show the correct summary.

## v1-A explicit exclusions

- teleport, fixed/relative/local coordinates and rotation;
- multi-entity targets, selectors, scans and entity collection loops;
- spawning entities, attributes/modifiers, equipment and inventory;
- arbitrary NBT/components or complex custom damage-source chains;
- event triggers, detectors, polling and every-tick monitoring;
- execute-at and position-context replication;
- entity velocity until a Vector/Direction foundation is designed.

## Open decisions

1. **Remove entity and players:** recommended hard prohibition for players; otherwise remove is too easy to misuse and differs from kill in unsafe ways.
2. **Damage kinds:** confirm the small closed v1 list after a Minecraft 1.21.11 adapter audit; do not expose arbitrary registry ids in v1.
3. **Effect update policy:** recommended `VANILLA_UPDATE` and explicit `REPLACE`; confirm user wording and exact adapter behavior.

## Acceptance boundary

- Slice 1 changes only TargetRef, the tag reform, execute-as integration and online-player discovery; it does not pre-register later blocks;
- Slice 1 keeps no permanent run-start source, retired tag ID, alias, migrator, wrapper or implicit target decoder;
- all blocks remain single-responsibility, target one entity, and use typed parameters;
- no command form, selector language, second Catalog, second context or speculative interface family is introduced.
