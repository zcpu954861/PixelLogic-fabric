# Entity Target Reference v1

## Status and decision

This specification is the implemented Slice 1 contract. It answers one question for an action or condition:

> Which single entity or online player does this block operate on?

`EntityTargetRef` selects a target. It never changes execution identity, position, condition subject or the run target. `context.entity.execute_as` remains the explicit container that changes `currentEntity` for its body and restores the outer value on exit.

The approved reform is intentionally breaking:

- remove `RUN_ENTITY` and the permanent “run-start entity” concept;
- keep only one optional current execution entity;
- replace six player/context tag blocks with three generic entity tag blocks;
- remove old block IDs, NodeTypes, aliases and implicit defaults;
- do not add a migrator, deprecated Catalog entry or compatibility wrapper.

Slice 1 produced a 25-block Catalog (`28 - 6 + 3`) and the shared target model/resolver. Health and Termination Slice 2 now reuses it for five additional actions, bringing the current feature Catalog to 30 blocks. User hand-testing remains the final release gate.

## Non-goals

v1 does not provide:

- selectors, multiple entities, collections or fan-out;
- all-online-player actions, world/entity scans, chunk loading, sorting, random choice or `limit` semantics in Graph;
- NBT/component predicates or nested target expressions;
- offline-player mutation or name-based rebinding;
- execute-at, position, dimension, facing or rotation changes;
- entity-valued variables, a second execution context or a second condition-result model;
- Minecraft object persistence or cross-restart Continuation.

## Runtime context reform

The runtime identities are:

| Value | Meaning |
|---|---|
| `currentEntity` | optional current execution entity; initialized once and temporarily replaced by execute-as |
| `currentCondition.subject` | entity checked by the latest ordinary contextual condition on this path |
| `targetEntity` | optional target supplied by the current trigger/path contract |
| request actor/player ID and session ID | security, API ownership and audit metadata only; never an entity-target fallback |

There is no stored `runEntity`, `initialEntity` or equivalent permanent source. A run may start with no entity. A trigger may provide one optional initial current identity, but the runtime stores it only as `currentEntity`.

`RuntimeConditionResult` continues to contain a subject and no object. `targetEntity` remains independent from the condition subject.

`ExecutionCursor` and Continuation retain only stable, environment-neutral identities for:

- current entity;
- target entity;
- condition subject through the existing condition result;
- outer execute-as frames.

They never retain a Minecraft `Entity`, `ServerPlayer` or cross-thread world object.

## Data and wire model

The shared model is:

```text
EntityTargetSource
  CURRENT_ENTITY
  CONDITION_SUBJECT
  TARGET_ENTITY
  ONLINE_PLAYER

EntityTargetRequirement
  ANY_ENTITY
  LIVING_ENTITY
  PLAYER_ONLY

EntityTargetRef
  source
  playerUuid       # ONLINE_PLAYER only
  playerNameHint   # optional display hint only
```

Graph JSON stores one composite value under the block config key `target`:

```json
{
  "target": {
    "source": "CURRENT_ENTITY"
  },
  "tag": "ready"
}
```

For a selected online player:

```json
{
  "target": {
    "source": "ONLINE_PLAYER",
    "playerUuid": "00000000-0000-0000-0000-000000000000",
    "playerNameHint": "Steve"
  }
}
```

The existing string-backed in-memory config path uses one formal `entity_target` serializer so persisted Graph JSON still contains a real nested object. The editor, Graph parser, validator, summaries and executors consume that one logical value. Long-lived `targetSource/targetUuid/targetName` sibling fields are not permitted.

Catalog fields declare:

```text
key: target
type: entity_target
default: { source: CURRENT_ENTITY }
ui metadata: requirement and allowed sources
```

There is no implicit target during decode. New block definitions explicitly write `CURRENT_ENTITY`; missing or malformed stored `target` is invalid. Canonical JSON omits `playerUuid` and `playerNameHint` for every source except `ONLINE_PLAYER`; explicitly supplied companion keys, including `null`, are invalid for other sources.

## Source contract

| Source | Resolution | UI wording |
|---|---|---|
| `CURRENT_ENTITY` | current runtime entity | 当前执行实体 |
| `CONDITION_SUBJECT` | latest ordinary condition subject | 当前条件主体 |
| `TARGET_ENTITY` | current path/trigger target | 当前目标实体 |
| `ONLINE_PLAYER` | exact configured UUID in the online-player provider | 指定在线玩家 |

`TARGET_ENTITY` is always selectable as an advanced source. The UI warns: `当前执行路径可能不提供目标实体。`

Missing sources never fall back to current entity, request actor, player name or any other source.

## Requirement contract

- `ANY_ENTITY`: any resolved `PLAYER` or `ENTITY` reference.
- `LIVING_ENTITY`: a living entity plus the block-specific alive/dead precondition.
- `PLAYER_ONLY`: a currently online player.

The requirement is declared by Block Definition/executor metadata. Individual executors must not invent private requirement strings or type rules.

## Configuration validation

Save-time validation is authoritative:

- `target` object and `source` are required;
- source must be one of the four v1 values;
- `ONLINE_PLAYER` requires one canonical UUID;
- `playerNameHint` is optional, at most 64 characters, contains no control characters and is escaped as plain text;
- non-player sources must not carry `playerUuid` or `playerNameHint`;
- UUID/name fields are normalized only within the same selected identity;
- no static validator claims that a dynamic condition subject or target entity will exist at runtime.

Required validation codes:

```text
entity_target_invalid_config
entity_target_source_invalid
entity_target_player_uuid_missing
entity_target_player_uuid_invalid
entity_target_player_name_hint_invalid
```

## Shared resolver and errors

Runtime and Simulation use one resolver contract:

```text
resolve(nodeId, fieldPath, EntityTargetRef, EntityTargetRequirement, executionContext)
  1. validate the decoded target object;
  2. read exactly the configured source;
  3. resolve current environment metadata by stable identity;
  4. enforce ANY_ENTITY/LIVING_ENTITY/PLAYER_ONLY;
  5. return reference, display name, entity type, living/alive/online facts;
  6. never mutate currentEntity or condition context.
```

Required execution codes:

```text
entity_target_invalid_config
entity_target_missing
entity_target_unresolvable
entity_target_offline
entity_target_type_mismatch
entity_target_not_alive
entity_target_provider_unavailable
```

Errors contain:

```text
code
nodeId
fieldPath          # for example node-1.target
source
requirement
referenceId
Chinese message
```

The common error shape is bounded at the API boundary and reaches Trace, `RuntimeResult` and `SimulationExecutionResult`. A target error terminates an action, ordinary condition, Predicate Capsule or execute-as entry. It is never converted into condition false and never selects an output edge.

The ordinary predicate adapter and Predicate Rack both propagate the same structured error. Rack evaluation continues not to overwrite the ordinary path condition context.

## Conditions

Every generic entity condition that resolves successfully records that entity in `RuntimeConditionResult.subject` for both true and false results. `condition object` remains empty in v1.

The generic tag condition supports `PASS_ONLY`, `FAIL_ONLY`, `BRANCH` and `PREDICATE`. Missing target, offline player, wrong type and provider failure terminate instead of entering the fail edge.

## `context.entity.execute_as`

The container uses the same `target: EntityTargetRef`, shared editor, validator and resolver.

- Catalog default is explicit `CONDITION_SUBJECT`.
- Allowed sources are all four v1 sources.
- `CURRENT_ENTITY` means “与当前上下文相同”.
- successful resolution sets the body current entity;
- nested bodies and delays retain the selected stable identity;
- body completion restores the outer current entity;
- resolution failure does not enter the body.

No position context is added.

## Online-player provider and API

`ONLINE_PLAYER` stores a UUID as the only identity. The name is a display hint and never a lookup fallback. In offline-mode servers, a rename that changes UUID is a new identity.

Each execution preserves the provider result one-to-one:

- an unavailable or closed provider returns `entity_target_provider_unavailable`;
- an available provider that cannot resolve the UUID returns `entity_target_unresolvable`;
- a known UUID whose player is not currently online returns `entity_target_offline`;
- an exact online UUID match continues with that resolved player.

Runtime and Simulation must not collapse these states. A saved name hint is display data only and never changes an unresolvable result into offline or success.

The loader adapter provides:

- exact online-player resolution by UUID;
- exact UUID lookup of currently loaded non-player entities across server worlds, without enumerating entity collections or loading chunks;
- bounded listing/search of current online players;
- type/living/alive/online plus health/action access required by current consumers;
- no world-entity enumeration, chunk loading or live-object storage in core/cursors.

The WebUI loads the directory only when the player picker opens or the user presses refresh. It performs no periodic polling and exposes no manual UUID input.

```text
GET /api/pixellogic/runtime/online-players?query=<text>&limit=<1..50>&selectedUuid=<optional-uuid>

{
  "players": [{ "uuid": "<uuid>", "name": "Steve" }],
  "selected": { "uuid": "<uuid>", "name": "Alex", "availability": "ONLINE" }
}
```

`selected` has four exact response shapes:

```json
{ "selected": null }
```

when `selectedUuid` is omitted;

```json
{ "selected": { "uuid": "<uuid>", "name": "Alex", "availability": "ONLINE" } }
```

for an exact online match;

```json
{ "selected": { "uuid": "<uuid>", "name": null, "availability": "OFFLINE" } }
```

for a known but currently offline UUID; `name` may instead contain a trustworthy cached name when the provider has one; and

```json
{ "selected": { "uuid": "<uuid>", "name": null, "availability": "UNRESOLVABLE" } }
```

when an available provider reports that the UUID is unknown or unresolvable. The WebUI uses the saved `playerNameHint` when the response has no current name and never infers availability from absence in the paged `players` list.

These four shapes apply only to a successful request while the provider is available. An unavailable, closed or timed-out provider returns the existing bounded API error response and no `selected` payload; it is never represented as `availability: UNRESOLVABLE`.

- `query` is at most 64 characters; default result limit is 20 and maximum is 50;
- `selectedUuid` is resolved exactly and independently of the result list;
- `OFFLINE` and `UNRESOLVABLE` selections preserve the configured UUID and saved name hint and are marked `当前不在线`; this shared UI wording does not merge their Runtime error codes;
- a renamed player with the same UUID remains selected and displays the current name;
- list refresh never clears the saved selection;
- the endpoint returns no address, permission, OP, game-mode or profile metadata;
- the existing loopback-only API, request bounds, server-thread timeout and close fence remain authoritative;
- loopback is not authentication, and this task does not invent the future Authorized Session.

## Simulation

Simulation follows the same four-source resolver and error codes.

- the test actor may initialize `currentEntity`, but is not exposed as `RUN_ENTITY`;
- a scenario may deliberately initialize no current entity;
- the existing optional target fixture supplies `TARGET_ENTITY`;
- an `ONLINE_PLAYER` UUID matching the test actor resolves to the existing per-run actor copy;
- the first different UUID is resolved lazily through the delegate provider and binds the run's only additional online-player fixture; a successful lookup copies that exact player's identity, display name, tags, health, maximum health and invulnerability into Simulation;
- later resolutions of that same UUID, including after an in-run Continuation, reuse the bound lookup and mutable simulation copy without querying or mutating the provider entity;
- after that fixture slot is bound, a second different UUID is unresolvable and fails closed; no online-player collection or world scan is introduced.

## Breaking tag-block reform

The following IDs are the formal removed-ID history for this breaking change:

```text
condition.player.has_tag
action.player.add_tag
action.player.remove_tag
condition.context_entity.has_tag
action.context_entity.add_tag
action.context_entity.remove_tag
```

The Catalog now contains:

```text
condition.entity.has_tag
action.entity.add_tag
action.entity.remove_tag
```

All three use `target: EntityTargetRef`, requirement `ANY_ENTITY`, explicit new-node default `CURRENT_ENTITY` and the existing authoritative tag lexical validation. The six removed IDs become unknown blocks; their old NodeTypes are deleted, and no alias, migration or legacy NodeType fallback remains.

All three are classified under `player-entity/tags` (`玩家与实体 > 标签`) and use exactly three generic NodeTypes:

```text
ENTITY_HAS_TAG_CONDITION
ENTITY_ADD_TAG_ACTION
ENTITY_REMOVE_TAG_ACTION
```

Names may follow the existing enum style, but the one-condition/two-action split is normative. The implementation retains one generic condition/predicate path and one entity-target action path for each operation; it does not retain separate player and context-entity executors or evaluators.

Tag action outcomes are:

- adding an existing tag: `SUCCESS`, `changed=false`, `affectedCount=1`;
- removing an absent tag: `SUCCESS`, `changed=false`, `affectedCount=1`;
- making a real change: `SUCCESS`, `changed=true`, `affectedCount=1`;
- target resolution failure: `FAILURE`, `changed=false`, `affectedCount=0`, with the shared structured target error.

The condition uses the same evaluator for ordinary execution and Predicate Rack, records the resolved entity as condition subject for normal true and false results, and leaves condition object empty.

## Repository-internal migration

The lack of external old-Graph compatibility does not permit stale repository fixtures. Stage B removes or updates every retired tag-specific:

- Block Definition and NodeType;
- executor and predicate evaluator;
- summary/help helper and Form Schema branch;
- example Graph, Prefab and test graph;
- Catalog fallback, snapshot and taxonomy fixture;
- Java/WebUI self-check branch and documentation reference.

Outside formal historical “removed ID” documentation, repository searches for the six old block IDs and removed `RUN_ENTITY` source must return zero. Supplying an old block ID is validated as unknown; no alias, wrapper, deprecated entry or migration decoder is used.

## Slice 1 acceptance

- exactly four sources and three requirements exist; `RUN_ENTITY` is absent from runtime code and WebUI;
- global runs can have no current entity and fail closed when a block targets it;
- initial current entity, nested execute-as, loop/delay Continuation and outer restoration preserve only stable identities;
- Graph JSON contains one `target` object and no loose sibling target fields;
- UUID/name validation, provider unavailable, offline, missing, wrong-type and not-alive paths use stable errors;
- ordinary conditions and Predicate Rack do not convert resolution errors to false;
- online-player discovery is on demand, manually refreshable and non-polling;
- the three generic tag blocks fully replace the six old blocks;
- old IDs are unknown and old NodeTypes are deleted, with no alias or fallback;
- the generic tag NodeTypes use no duplicated player/context executor or predicate path;
- tag actions preserve the specified `SUCCESS`/`FAILURE`, `changed` and `affectedCount` semantics;
- repository examples, Prefabs, help, snapshots, fallbacks and self-checks contain no live old-tag reference;
- selected-player responses preserve configured offline/unresolvable selections without using paged-list absence as availability evidence;
- Runtime and Simulation distinguish provider unavailable, unresolvable UUID and known-but-offline UUID with the specified one-to-one error codes;
- Simulation snapshots at most one non-actor online player per run, reuses it through Continuation, isolates tag mutations from the provider entity and rejects a second UUID;
- WebUI uses one compact accessible target editor for tag blocks and execute-as;
- no selector, collection, position context, offline player or success/failure graph port is added.

## Current consumer slice

**Health and Termination** now consumes this contract for damage, heal, set health, kill and non-player removal. It adds no target source or selector behavior. Status effects, player game mode and later entity conditions remain future slices and must continue reusing this reference instead of creating parallel target models.

## Implementation evidence

- `RuntimeExecutionContext`, `RuntimeConditionResult`, `RuntimeSubjectReference`;
- `ExecutionCursor`, `EntityContextFrame`, `ExecutionScopes` and `GraphRuntime`;
- `RuntimeServices` and loader-specific online-player access;
- `SimulationActor`, `SimulationEntity`, `SimulationContext` and `SimulationRunner`;
- `BuiltInBlockCatalog`, `GraphValidator` and the current Form Schema editor;
- [`ENTITY_EXECUTION_CONTEXT_V1.md`](../audits/ENTITY_EXECUTION_CONTEXT_V1.md).
