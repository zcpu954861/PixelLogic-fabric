# Entity Target Reference v1

## Decision

Entity Target Reference v1 answers one question for an action or condition:

> Which single entity or player does this block operate on?

It is an action/condition parameter. It does not change `currentEntity`; `context.entity.execute_as` remains the only v1 mechanism that changes execution identity for a body. The reference reuses `RuntimeExecutionContext`, `RuntimeSubjectReference`, `RuntimeConditionResult.subject` and the existing Continuation cursor instead of introducing a second execution context.

## Non-goals

v1 does not provide:

- an `@e` selector or selector parser;
- multiple entities, collections or fan-out;
- world scans, chunk loading, radius or nearest queries;
- sorting, `limit`, random selection or NBT/component matching;
- nested target expressions;
- execute-at, position, dimension, facing or rotation changes;
- offline-player mutation;
- a new condition-result model or a new execution-context container;
- Minecraft object persistence or cross-restart Continuation.

## Current context truth

The current runtime has these identities:

| Existing value | Real meaning | v1 decision |
|---|---|---|
| `RuntimeExecutionContext.currentEntity` | current execution entity; initially the run entity and temporarily replaced by execute-as | expose as `CURRENT_ENTITY`; default for generic entity blocks |
| `runEntity` | entity bound when the run starts | expose as `RUN_ENTITY` |
| `currentCondition.subject` | subject checked by the latest ordinary contextual condition on this path | expose as `CONDITION_SUBJECT` |
| `targetEntity` | optional target bound to this run | reuse the existing `TARGET_ENTITY` token, but only when the run/trigger contract supplies one |
| Simulation actor | Simulation implementation of `runEntity` | not a separate source |
| Simulation target entity | test fixture used to populate `targetEntity` | not a source named “test target” |

There is no `RuntimeConditionResult.object`. The existing UI phrase “当前条件对象” is broader than the model and should become “最近条件主体”. v1 must not alias `targetEntity` into a fictitious condition object.

The current production `RuntimeServices.targetEntity` returns empty by default. Existing `EntitySource.TARGET_ENTITY` is a formal run input, not a claim that every current trigger can produce it. A graph may only rely on it when its entry contract or test fixture provides one; otherwise resolution fails closed.

## Minimal model

The common model is intentionally flat:

```text
EntityTargetSource
  CURRENT_ENTITY
  RUN_ENTITY
  CONDITION_SUBJECT
  TARGET_ENTITY
  ONLINE_PLAYER

EntityTargetType
  ANY_ENTITY
  LIVING_ENTITY
  PLAYER_ONLY

EntityTargetRefV1
  source
  playerId          # ONLINE_PLAYER only; UUID string
  playerNameHint    # optional display only; max 64 characters; never identity
```

Implementation must extend/reuse the existing `EntitySource` token contract (or replace it with one shared superset) so `RUN_ENTITY`, `CONDITION_SUBJECT` and `TARGET_ENTITY` have one spelling and one parser. It must not keep parallel execute-as and target-source enums that map synonymous values.

Graph config remains `Map<String, String>`. A target field with prefix `target` writes:

```text
targetSource
targetPlayerId
targetPlayerNameHint
```

The Catalog exposes one `entity_target` Form Schema field that groups those existing flat keys in the editor. It is not a JSON expression, AST or new Graph record. The existing wire fields carry its complete contract:

```text
key: targetSource
type: entity_target
options: allowed source values and Chinese labels
ui: targetPrefix:target targetType:LIVING_ENTITY section:common fullWidth
```

`targetType` is one of the three required types; `options` is the authoritative allowed-source set; `targetPrefix` locates the two companion config keys. These tokens travel through the existing Form Schema `ui/options` wire. GraphValidator, WebUI and executor setup read the same field definition rather than hard-coding target type/source by blockId. Slice 1 adds parsing/validation for these exact tokens; unknown/missing target metadata fails the Catalog self-check.

Every block declares one required `EntityTargetType` in its definition/execution contract:

- `ANY_ENTITY`: a resolved `PLAYER` or `ENTITY` reference;
- `LIVING_ENTITY`: adapter/Simulation metadata must explicitly say the entity is living;
- `PLAYER_ONLY`: the resolved kind must be `PLAYER`.

Type is not inferred from a display name or string pattern. It is also not a safety flag: target type is an execution contract.

## Source availability and defaults

| Source | Resolution | Availability | UI wording |
|---|---|---|---|
| `CURRENT_ENTITY` | `context.currentEntity` | normal runs; in execute-as it is the selected entity | 当前执行实体 |
| `RUN_ENTITY` | `context.runEntity` | when the run has a bound entity | 本次运行实体 |
| `CONDITION_SUBJECT` | `context.currentCondition.subject` | only after an ordinary condition that emits a subject | 最近条件主体 |
| `TARGET_ENTITY` | `context.targetEntity` | only when the run entry/adapter provides a target | 本次运行目标实体 |
| `ONLINE_PLAYER` | UUID lookup in the online player directory | only with a valid selected UUID and an online player | 指定在线玩家 |

Generic entity actions and conditions default to `CURRENT_ENTITY`, so they naturally compose with execute-as. Player-only blocks may also default to `CURRENT_ENTITY`; type validation then catches a non-player context instead of silently bypassing it. A product implementation may choose `RUN_ENTITY` for a specifically player-oriented legacy block only as an explicit compatibility default.

Missing sources never fall back to actor, run entity or current entity.

## Resolution contract

One shared resolver is used by Simulation and Minecraft adapters:

```text
resolve(ref, requiredType, executionContext)
  1. read exactly the configured source;
  2. for ONLINE_PLAYER, parse and look up the UUID;
  3. require a PLAYER/ENTITY RuntimeSubjectReference;
  4. resolve current environment metadata by stable identity;
  5. validate ANY_ENTITY/LIVING_ENTITY/PLAYER_ONLY;
  6. return the stable reference plus minimal metadata;
  7. do not modify currentEntity.
```

The minimal environment-neutral result is:

```text
reference
entityTypeId
living
alive
online
```

Core and Continuation never retain a Minecraft `Entity`. The loader adapter may use a live object only inside one execution call.

Resolution uses existing context identity plus the target-version server's bounded per-world UUID lookup (`O(world count)`), without enumerating entities or loading chunks. A non-player entity must already be present in the run/current/condition/target context; v1 does not expose arbitrary UUID lookup as a user source.

## Identity and lifecycle

- `RuntimeSubjectReference.id` remains the stable environment-neutral identity.
- `ONLINE_PLAYER` stores the UUID as authority and the last-known name only for summaries.
- A name is never a fallback lookup key. Rename, duplicate name and offline-mode policy must not silently retarget a graph.
- Each node execution resolves its target once and passes that resolution through the block executor.
- `CURRENT_ENTITY`, `RUN_ENTITY`, `CONDITION_SUBJECT` and `TARGET_ENTITY` already travel in `ExecutionCursor`; EntityTargetRef adds no frame or continuation type. Existing source token spellings are reused where they already exist.
- On resume, existing cursor identities are revalidated before mutation. An online-player config reached after a wait is looked up again by UUID.
- An offline player fails. An unloaded/removed/unresolvable entity fails. No implementation may force-load a chunk in v1.
- “Entity is alive” can return false for a successfully resolved dead fixture. Blocks that require a living, alive target return a target precondition error instead.
- Continuations remain in-memory and single-use; server restart recovery is outside v1.

## Conditions and condition subject

Every new entity condition that successfully resolves a target records that same target in the existing `RuntimeConditionResult.subject` for both true and false results. This lets downstream `CONDITION_SUBJECT` and `context.entity.execute_as` compose without a second result channel.

Target resolution failure is not boolean false. It terminates the node/run fail-closed and must not enter a condition's fail edge, because that edge may contain side effects.

Condition Rack uses the same raw predicate evaluator and target resolver, but retains its current rule: rack evaluation does not replace the ordinary path's `currentCondition`.

v1 has no condition object. A future genuinely two-entity condition must first define subject/object semantics and lifecycle; it must not reuse `TARGET_ENTITY` as an implicit object.

## Structured errors

Slice 1 must introduce one minimal runtime/simulation error shape that can reach Trace and API results:

```text
code
nodeId
source
requiredType
referenceId
message
```

Required resolution codes:

| Code | Meaning |
|---|---|
| `ENTITY_TARGET_MISSING` | configured context source has no reference |
| `ENTITY_TARGET_UNRESOLVABLE` | identity cannot be resolved without a scan/load |
| `ENTITY_TARGET_PLAYER_OFFLINE` | configured online player is now offline |
| `ENTITY_TARGET_TYPE_MISMATCH` | resolved target violates the block type |
| `ENTITY_TARGET_NOT_ALIVE` | block requires an alive target and resolution found a dead target |

Save-time configuration codes:

```text
entity_target_source_invalid
entity_target_player_id_missing
entity_target_player_id_invalid
entity_target_player_name_hint_invalid
```

`targetPlayerNameHint` is optional, limited to 64 characters and rejects control characters. WebUI summaries and Trace render it as escaped plain text. It is never used for lookup or authorization.

The shared resolver owns the common code/message. A block adds its own domain error only after target resolution. Runtime, Simulation and API must not translate the same failure into unrelated strings.

### Integration with current result records

Slice 1 extends the existing result path; it does not add a parallel error bus:

1. Add one core `RuntimeExecutionError(code, nodeId, source, requiredType, referenceId, message)` record.
2. Add nullable `error` to the existing `RuntimeNodeExecutionResult`. Existing two/three-argument constructors continue to set it to null.
3. Add nullable `error` to the existing `RuntimeResult`. Existing constructors and successful/waiting results remain wire-compatible with null.
4. When a node result has an error, `GraphRuntime` appends its readable message/code to the existing Trace, stops traversal, and returns the same error on `RuntimeResult`; it does not throw away the code or follow an output slot.
5. Add nullable `error` to the existing `RuntimePredicateResult`; its existing two/three-argument constructors continue to set it to null. Predicate Rack checks this field before reading `value`, appends the same code/message to Trace, and terminates through the same `RuntimeResult.error` path instead of converting the failure to boolean false or a string-only exception. The ordinary predicate-to-node adapter must likewise copy this error into `RuntimeNodeExecutionResult.error` with no output slot; `GraphRuntime` then follows step 4 and never selects pass/fail from `value`.
6. Add nullable `error` to `SimulationExecutionResult`, while retaining the existing `errors: List<String>` as a derived compatibility projection containing at most the one readable message.
7. The API uses `error.code/message` when present and keeps legacy `RUNTIME_FAILED` only for unexpected failures that have no structured error. The change is additive to JSON; old clients may continue reading `message`/`errors`.

There is at most one terminal execution error per run result. `code`, `source` and `requiredType` are enums; `nodeId` uses the existing graph limit; `referenceId` is capped at 128 characters and `message` at 512 characters at the trust boundary. No arbitrary details map or stack trace crosses the API.

## Simulation

Simulation uses the same source, type and error rules:

- the actor remains the run entity and initial current entity;
- execute-as continues to change only current entity;
- the existing optional target entity is the fixture for formal `TARGET_ENTITY`—there is no `TEST_TARGET_ENTITY` source;
- an explicit online-player fixture with stable UUID is needed to simulate `ONLINE_PLAYER` without falling back to the actor;
- fixture metadata must include entity type, living/alive and online state needed by v1-A;
- missing fixture or incompatible metadata returns the same structured error code as Runtime;
- mutable health/effects/game mode are copied per run and never written back to the test-context draft;
- the same `SimulationContext` remains attached to an in-run Continuation.

A single optional online-player fixture is sufficient for v1. It is an environment lookup fixture, not a multi-target query or entity collection.

## Minecraft adapter boundary

Slice 1 adds a narrow adapter contract for:

- resolving a contextual entity reference by its already-known identity;
- looking up one configured online player UUID;
- reporting type/living/alive/online metadata;
- executing later entity operations without exposing Minecraft classes to core.

The adapter must use target Minecraft 1.21.11 APIs and remain loader-specific. It must not enumerate all world entities, force-load chunks, cache live objects across ticks, or leak Minecraft classes into Catalog/Graph/Simulation.

## Catalog and WebUI

The common control is compact:

```text
目标：[当前执行实体 ▼]
```

When `ONLINE_PLAYER` is selected, a searchable online-player combobox appears and saves UUID plus a display hint. Other source-specific fields stay hidden.

Slice 1 adds one narrow read-only endpoint backed by the loader's online-player manager:

```text
GET /api/pixellogic/runtime/online-players?query=<text>&limit=<1..50>&selectedId=<optional-uuid>

{
  "players": [
    { "id": "<uuid>", "displayName": "Steve" }
  ],
  "selected": { "id": "<uuid>", "displayName": "Alex", "available": true }
}
```

- on the current baseline it inherits the editor API's `127.0.0.1`-only development boundary; that server has no authentication, and loopback binding must not be described as authorization;
- it must never bind publicly; when the planned client-hosted bridge is implemented, this route requires the bridge's server-authorized session and a narrow player-directory read capability before production exposure;
- `query` is trimmed, at most 64 characters, and matches current display names case-insensitively;
- results are deterministic by case-insensitive display name then UUID, limited to 20 by default and 50 maximum;
- optional `selectedId` is parsed as one UUID and resolved by exact online-player lookup independently of `query`, sorting and `limit`; the response uses `selected: null` when it is omitted and `{ id, available: false }` when that UUID is offline;
- no address, permission, OP state, game mode or profile metadata is returned; `displayName` is escaped plain text capped at 64 characters;
- the WebUI queries only while the combobox is open, debounces input by 150 ms, aborts/sequence-guards stale responses and performs no background polling;
- opening the combobox refreshes results; closing cancels the request;
- a selected player going offline remains visible from the saved name hint, is marked unavailable from the exact `selectedId` result—not inferred from absence in the paged search list—and fails with `ENTITY_TARGET_PLAYER_OFFLINE` at execution;
- selecting a result stores its UUID/name hint in the flat target config. The endpoint never writes Graph state itself.

The runtime adapter contract therefore includes both exact `onlinePlayer(UUID)` resolution for execution and bounded `onlinePlayers(query, limit)` discovery for this endpoint. Simulation uses the explicit UUID fixture rather than this live endpoint.

Rules:

- options are ordered by ordinary usefulness: current, run, condition subject, run target, online player;
- a block only shows sources allowed by its target type and execution contract;
- the control displays an `任意实体 / 生物 / 仅玩家` type hint;
- a statically impossible source is disabled with a reason;
- a dynamically uncertain condition/run target shows a warning and remains runtime-authoritative;
- an unresolvable/offline UUID remains visible from its saved name hint and is marked unavailable; if the same UUID is online after a rename, it remains available and displays the current returned name without changing identity;
- keyboard navigation, focus and screen-reader labels use the existing Form Schema control patterns;
- no selector, radius, sort, limit, random or NBT form is present.

Summary examples:

```text
伤害当前执行实体 4 点
恢复最近条件主体 6 点生命
给予玩家 Steve 速度效果
```

## Existing-block compatibility

Existing `player.*` and `context_entity.*` tag blocks keep their current config/default semantics in v1-A. They are useful migration fixtures but are not silently rewritten:

- old player blocks continue to target the run actor;
- old context-entity blocks continue to target current entity;
- a later explicit migration may add EntityTargetRef defaults while preserving old graph meaning;
- no duplicate generic “set entity tag” block is added to v1-A.

## Acceptance checks for Slice 1

- all five sources resolve or fail with the specified code;
- no source fallback exists;
- all three target types are checked in Simulation and Runtime adapter tests;
- direct condition execution and Predicate Rack preserve the same structured target error code;
- online player identity is UUID-authoritative;
- true and false entity conditions retain the resolved subject;
- rack predicates do not overwrite path condition context;
- nested execute-as and wait resume preserve existing cursor semantics;
- cancellation/stale continuation cannot mutate a target;
- Graph stores only flat config strings, never a runtime entity/Minecraft object;
- WebUI shows one compact accessible selector and no selector-language controls;
- self-check covers missing, unresolvable, offline, wrong type, dead and success paths.

## Open decisions

| Decision | Recommendation | Why it still needs product confirmation |
|---|---|---|
| specified player identity | UUID + last-known name hint | binds a graph to a server identity; name-only is less stable, especially in offline mode |
| `TARGET_ENTITY` visibility | keep the existing formal source token, label it “本次运行目标实体”, disable/warn when entry cannot provide it | the current production adapters do not populate it yet |

## Evidence

- `RuntimeExecutionContext`, `RuntimeConditionResult`, `RuntimeSubjectReference`;
- `ExecutionCursor`, `ExecutionScopes` and `GraphRuntime` continuation validation;
- `RuntimeServices` adapter boundary;
- `SimulationActor`, `SimulationEntity`, `SimulationContext` and `SimulationRunner`;
- [`ENTITY_EXECUTION_CONTEXT_V1.md`](../audits/ENTITY_EXECUTION_CONTEXT_V1.md) for existing execute-as semantics.
