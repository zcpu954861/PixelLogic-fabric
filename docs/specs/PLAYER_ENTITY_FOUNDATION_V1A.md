# Player & Entity Foundation v1-A

正式名称：**Player & Entity Foundation v1-A / 玩家与实体基础包：目标、生命与状态**。

This is a design and implementation contract, not implementation in the current branch.

## Product slice

v1-A recommends:

- one shared Entity Target Reference v1;
- eight action blocks;
- six condition/Predicate Capsule blocks;
- reuse of the existing direct-edge Graph, condition output modes, Predicate Rack, entity execution context, Continuation and Simulation runner.

The pack is intentionally limited to high-frequency single-entity behavior that does not require PositionRef, inventory, world scanning or a large event subsystem.

## Taxonomy plan

These are planned paths only. The Catalog must not register them until real blocks are implemented.

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

All fourteen blocks use `ENTITY_TARGET_REFERENCE_V1.md`. Generic blocks default to `CURRENT_ENTITY`. Each block declares `ANY_ENTITY`, `LIVING_ENTITY` or `PLAYER_ONLY`. Target resolution failure terminates execution; a condition must not turn a missing target into its false branch.

### Results and errors

Actions extend the existing node-result path with one bounded `RuntimeActionOutcome`:

```text
nodeId
blockId
targetReference
changed
message
beforeSummary       optional
afterSummary        optional
```

`message` is required readable text capped at 512 characters. `RuntimeNodeExecutionResult` gains nullable `actionOutcome` beside its existing output/trace/condition fields and the structured error defined by EntityTargetRef. `RuntimeServices` gains an overload that records this outcome; the old string overload stays as a compatibility adapter. Existing `SimulationActionResult` is extended additively with `blockId`, `targetId`, `changed`, `beforeSummary` and `afterSummary` while retaining its current `nodeId/kind/message` fields and old constructor. The API continues returning the same action-results array with additive fields.

For v1-A, the executor only returns `actionOutcome`; it does not also call `RuntimeServices.recordActionResult`. `GraphRuntime` is the single recorder: after a successful node result, it forwards a non-null outcome exactly once before selecting the next edge. The new `RuntimeServices.recordActionResult(RuntimeActionOutcome)` default delegates one way to the existing string overload using `blockId` as `kind` and the bounded `message`; the old overload never calls back into the new one. Simulation overrides the new overload to retain all additive fields. Existing blocks may keep their current direct string recording until explicitly migrated, so no result is duplicated.

This is not a generic object/result framework. It is the minimum extension of current records immediately reused by eight v1-A actions. There is no free-form payload map: block-specific facts are summarized into the bounded before/after strings, each capped at 256 characters.

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

1. `entity_target`: flat EntityTargetRef fields and compact picker;
2. `status_effect`: one namespaced effect-id editor/validator reused by add, remove and has-effect blocks. Duration, level, visibility and minimum thresholds remain ordinary block-specific fields.

`condition.entity.type_is` uses the existing namespaced Resource ID field/validator with an “实体类型” label. It is the only v1-A consumer, so v1-A does not create an `entity_type` component; extraction waits for a second real consumer.

Literal numbers remain ordinary Form Schema number fields. v1-A does not introduce Number Source or an expression language.

### Numeric and time rules

v1-A uses fixed implementation bounds rather than adapter-dependent defaults:

- damage, heal and set-health values are decimal health points in `0.001..1_000_000`;
- health-condition values are decimal health points in `0..1_000_000`;
- status-effect duration is an integer `1..1_000_000` seconds;
- minimum remaining effect duration is an integer `0..1_000_000` seconds;
- effect level is user-facing `1..256` and maps exactly to amplifier `0..255` by subtracting one;
- seconds convert to ticks with checked integer multiplication `seconds × 20`; overflow or a non-integral/invalid config fails validation before execution;
- health/damage values must parse as finite decimal numbers; `NaN`, infinity, underflow below the declared minimum and values above the maximum are rejected;
- runtime conversion to the target-version numeric type occurs only after validation and must reject a non-finite conversion;
- health `EQUAL` uses the same fixed `0.0001` tolerance in Simulation and Runtime.

No action saturates numeric overflow silently. The only clamp is the explicit `CLAMP_TO_MAX` set-health policy and ordinary heal-to-maximum behavior.

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
- **Advanced parameters:** closed `damageKind` enum. Recommended v1 values are `GENERIC`, `MAGIC`, `FIRE`, `FREEZE`, `DROWN`; the Minecraft 1.21.11 adapter audit must verify exact registry/API mapping before implementation.
- **Explicitly unsupported:** arbitrary damage-type resource ids, attacker/projectile ownership, bypass flags, custom death messages and direct set-health behavior.
- **Form Schema:** `entity_target`, number `amount`, select `damageKind`.
- **Summary:** `对当前执行实体造成 4 点普通伤害`.
- **GraphValidator:** target config, finite positive range and known damage kind.
- **Simulation:** use one explicit deterministic approximation: if fixture `invulnerable=true`, reject with no change; otherwise `effectiveDamage=min(requestedAmount,currentHealth)` and reduce fixture health by that value. Armor, resistance, damage cooldown and enchantments are not modelled. The Catalog declares `APPROXIMATE_SIMULATION`, and Trace/result message calls this an unmitigated approximation, so it validates flow/death intent rather than predicting the server's accepted amount.
- **Runtime contract:** loader adapter constructs the audited 1.21.11 damage source and invokes the normal damage path so armor, resistance, invulnerability, events and death semantics remain Minecraft-authoritative.
- **Errors/result:** `ENTITY_TARGET_NOT_ALIVE`, `ENTITY_DAMAGE_REJECTED`, `ENTITY_DAMAGE_KIND_UNAVAILABLE`; result distinguishes requested amount, accepted/no-change and before/after health when observable.
- **Trace/help:** explains that the real server may reduce or reject damage; example: damage the latest condition subject after a type check.
- **Self-check:** deterministic approximation, invulnerable rejection, death, wrong type, dead target, each source and continuation cancellation; a Runtime adapter spy proves the real executor issues a damage request and never substitutes a set-health request.
- **Compatibility:** new v1 block; missing target uses `CURRENT_ENTITY`, missing damage kind uses `GENERIC`; missing/invalid amount is not silently defaulted on old data.

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
- **Compatibility:** new block; target defaults current, amount is required.

### `action.entity.set_health` — 设置实体生命值

- **Responsibility:** set one living entity to an explicit positive health value.
- **Target/default:** alive `LIVING_ENTITY`, default `CURRENT_ENTITY`.
- **Common parameters:** target and finite `health` in `0.001..1_000_000`.
- **Advanced parameters:** `aboveMaximum`: `CLAMP_TO_MAX` (default) or `FAIL`.
- **Explicitly unsupported:** value zero as an indirect kill, maximum-health/attribute mutation, absorption and resurrection.
- **Form Schema:** `entity_target`, number `health`, segmented/select `aboveMaximum`.
- **Summary:** `把当前执行实体的生命值设为 10`.
- **GraphValidator:** positive finite bound and known policy.
- **Simulation:** use fixture maximum and the selected clamp/fail policy.
- **Runtime contract:** read current maximum then set through the audited living-entity API; setting death through zero is prohibited so kill remains distinct.
- **Errors/result:** `ENTITY_TARGET_NOT_ALIVE`, `ENTITY_HEALTH_ABOVE_MAXIMUM`; result includes requested, applied and before/after health.
- **Trace/help:** warns that maximum health can vary by attributes and effects.
- **Self-check:** below/equal/above max, both policies, dead/wrong target and no zero-kill alias.
- **Compatibility:** new block; target and policy have defaults, health is required.

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
- **Compatibility:** new block; target defaults current.

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
- **Compatibility:** new block; target defaults current. Player prohibition is an open product decision and an implementation gate.

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
- **Compatibility:** new block; target, booleans and policy have defaults; effect/duration/level are required.

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
- **Compatibility:** new block; target defaults current, effect id required.

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
- **Compatibility:** new block; target defaults current, mode required.

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
- **Compatibility:** new block; target defaults current, missing output mode follows the existing condition compatibility default.

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
- **Compatibility:** new block; target defaults current, type required.

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
- **Compatibility:** new block; target defaults current; mode default `AT_OR_ABOVE`, numeric field required.

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
- **Compatibility:** new block; target/minimums default, effect required.

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
- **Compatibility:** new block; target defaults current, mode required.

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
- **Compatibility:** new block; target defaults current.

## Minimum help examples

The first implementation supplies at least one runnable/help-center example per block without building a large Help Center:

| blockId | Minimal example |
|---|---|
| `action.entity.damage` | after “实体类型是否为 zombie”, damage the latest condition subject by 4 |
| `action.entity.heal` | heal the current execution entity by 6 after a checkpoint |
| `action.entity.set_health` | set a round participant to 20 health with clamp-to-maximum |
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
| set health | include | deterministic setup behavior; zero is prohibited to keep kill distinct |
| add/remove effect | include both | common and share one small status-effect component |
| kill | include | preserves death semantics |
| remove entity | include behind explicit no-player safety decision | necessary cleanup behavior and deliberately distinct from kill |
| set game mode | include | high-frequency player setup with a small closed enum |
| set entity velocity | defer to v1-B | needs a typed vector/direction foundation and physics semantics |
| set entity tag | do not duplicate | six tag blocks already exist; migrate them later to EntityTargetRef |
| teleport | defer | depends on PositionRef, rotation, dimension and collision policy |
| summon | defer | depends on PositionRef, EntityTypeRef creation policy, ownership and world lifecycle |

## UI layering

Forms follow user behavior, not Minecraft internals:

- **Common:** target and the one main behavior value.
- **Display:** effect particles/icon/ambient settings.
- **Advanced:** explicit replacement/clamp policy or audited damage kind.

The current Form Schema has no disclosure-group model. v1-A adds only three `ui` tokens to the existing string metadata: `section:common`, `section:display` and `section:advanced`. The editor groups fields in schema order; common is always visible, while display and advanced render as accessible native disclosures, collapsed by default. Unknown section tokens fail the Catalog self-check. This needs no new `BlockFormFieldDefinition` field and does not create a layout schema.

No raw registry object, amplifier zero-base, ticks, internal enum or Minecraft class name is primary UI text.

## Implementation slices

### Slice 1 — Entity Target Foundation

Scope:

```text
flat EntityTargetRef model and validation
Catalog entity_target field contract
compact accessible WebUI selector
shared Simulation resolver and one online-player fixture
Minecraft adapter resolution contract
minimal structured target errors and additive action-result records
target self-check
```

Recommended branch: `feature/entity-target-reference-v1` from current `mc-1.21.11` after design approval.

Suggested commits:

1. `feat: add entity target reference foundation`;
2. `feat: add structured entity action outcomes`.

This slice should be the next implementation task. It has immediate reuse across all later slices and prevents each action from inventing target/error semantics.

### Slice 2 — Health and entity termination

Scope: damage, heal, set health, kill and remove. The Simulation fixture extends Slice 1 metadata with bounded `health`, `maximumHealth` and `invulnerable` facts, and makes the existing per-run `alive` fact mutable for damage/kill transitions; it does not introduce a second alive field.

Recommended branch: `feature/player-entity-health-v1a`, based on merged Slice 1.

Suggested commits:

1. `feat: add entity health actions`;
2. `feat: add entity kill and remove actions`.

Implementation gate: target-version damage/kill/discard APIs and the “remove players” product decision must be settled first.

### Slice 3 — Status effects and player setting

Scope: add/remove status effect and set game mode. The Simulation fixture gains a per-run effect map and player game-mode fact before these executors are registered. The implementation gate is an audited, explicit `VANILLA_UPDATE`/`REPLACE` transition table—including infinite existing effects—for the target Minecraft version.

Recommended branch: `feature/player-entity-status-v1a`, based on merged Slice 1 (it need not wait for Slice 2 if shared result/error contracts are already merged).

Suggested commits:

1. `feat: add entity status effect actions`;
2. `feat: add player game mode action`.

### Slice 4 — Condition capsules

Scope: alive, entity type, health, status effect, game mode and is-player conditions.

Recommended branch: `feature/player-entity-conditions-v1a`, based on merged Slices 2 and 3 so it reuses their health, effect, game-mode fixtures and shared components instead of changing the same Simulation model in parallel.

Suggested commit: `feat: add player entity predicate capsules`.

Each slice is independently reviewable and mergeable. Slices 2 and 3 depend on Slice 1 and may proceed in parallel; Slice 4 depends on merged Slices 2 and 3. No slice should add future empty Catalog categories; only categories with its real blocks are registered.

## User hand-test gates

Slice 1:

- current/run/condition/run-target/online-player source selection;
- unavailable sources show a warning and fail without fallback;
- execute-as changes `CURRENT_ENTITY` target behavior;
- UUID selection survives rerender and player rename hint changes;
- delay/cancel does not mutate a stale target.

Slice 2:

- damage differs from direct set health;
- heal clamps, set-health policy is visible, kill triggers death semantics;
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
- complete execute-as/at replication;
- entity velocity until a Vector/Direction foundation is designed.

## Open decisions

1. **Specified player identity:** recommended UUID + name hint; confirm offline-mode/server-binding expectations.
2. **Remove entity and players:** recommended hard prohibition for players; otherwise remove is too easy to misuse and differs from kill in unsafe ways.
3. **Damage kinds:** confirm the small closed v1 list after a Minecraft 1.21.11 adapter audit; do not expose arbitrary registry ids in v1.
4. **Effect update policy:** recommended `VANILLA_UPDATE` and explicit `REPLACE`; confirm user wording and exact adapter behavior.
5. **Condition subject/object convention:** v1 records the resolved target as `subject` and adds no object; confirm this terminology before expanding to genuine two-entity conditions.

## Acceptance boundary for this design

- no product code, Catalog entries, Graph schema, Runtime executor or WebUI is changed by the design branch;
- the next implementation starts with Slice 1 only;
- all blocks remain single-responsibility, target one entity, and use typed parameters;
- no command form, selector language, second Catalog, second context or speculative interface family is introduced.
