# PixelLogic v1 Core Architecture Spec

This document defines the v1 core architecture for PixelLogic. It now also records the first manual simulation spike implementation checkpoint.

## Architecture Summary

PixelLogic v1 uses a direct graph execution model:

```text
TriggerEvent
-> ExecutionContext
-> Graph entry block
-> CompiledGraph
-> Typed Edge traversal
-> Condition branch selection
-> Action side effects
-> Timer continuation
-> ExecutionTrace
```

There is no Channel execution path in core.

## Manual Simulation Spike Checkpoint

The `feature/v1-manual-simulation-spike` implementation keeps the first runtime proof intentionally small:

- `core/model`: graph, node, slot, edge, node type, edge type, state scope, and state value type.
- `core/graph`: validation, compilation, compiled graph indexes, and the in-memory demo graph.
- `core/runtime`: trigger event, execution context, graph runtime, runtime limits, runtime result, and a main-based self-check.
- `core/state`: scoped in-memory state store with Boolean, Integer, and String values.
- `core/timer`: wall-clock in-memory timer scheduler and immutable timer continuation.
- `core/trace`: bounded in-memory execution trace buffer.
- `server`: Fabric-facing spike service that owns the demo graph, state, trace, runtime, and scheduler.
- `loader/fabric`: Fabric Command API v2 and server lifecycle adapter.

The spike command root is `/pixellogic`; no `/pl` root is registered. The current commands are `/pixellogic status`, `/pixellogic test start`, `/pixellogic test reset`, and `/pixellogic trace last`.

The current timer uses `ScheduledExecutorService` for wall-clock delay. The due callback only schedules runtime continuation back onto the Minecraft server thread through the Fabric adapter service.

The API + WebUI test-run checkpoint adds a localhost-only spike API for the same demo graph. The API uses a fixed `WebUI 模拟玩家` actor for PLAYER-scoped browser simulation and does not implement graph save/load.

The graph draft/validate/commit checkpoint adds versioned JSON persistence for `demo-start-flow`. The runtime starts from the committed graph, draft saves are isolated, and commit swaps the compiled runtime graph only after `GraphValidator` passes.

## Layer Boundaries

### Core

Core owns pure product and runtime concepts:

- Project
- Graph
- Node / Block
- Slot / Port
- Typed Edge
- Trigger
- Condition
- Action
- State
- Timer
- ExecutionContext
- ExecutionTrace
- ValidationIssue
- RuntimePlan / CompiledGraph

Core must not depend on Fabric, Forge, NeoForge, HTTP server classes, WebUI DOM types, or old TZZ models.

### Server

Server owns:

- API shape
- request validation
- save/load
- permissions
- audit
- trace retrieval
- admin session and capability checks for future client-hosted WebUI
- static WebUI asset serving later

### Loader Adapter

Loader adapter owns conversion from loader/Minecraft events to PixelLogic TriggerEvent.

Fabric is the only v1 loader target. Forge and NeoForge remain future adapter boundaries only.

### WebUI

WebUI owns the independent frontend:

- slot-based horizontal flow canvas
- graph editing
- block library driven by Block Catalog definitions
- selected block properties
- validation display
- execution trace display

Java must not generate WebUI HTML, CSS, or JavaScript.

The current six WebUI library buttons are temporary demo entries. The next architecture step should move to category registry plus concrete block definitions rather than a hardcoded category enum.

### Admin Client Bridge

The long-term admin transport is client-hosted WebUI with an authorized client-local bridge:

```text
Browser -> client localhost bridge -> Minecraft networking -> PixelLogic server core
```

This does not change the core rule: the server remains authority for graph, validation, runtime, storage, trace, and permissions. A client mod installing PixelLogic does not grant management power. The server must authorize a session and check capability on every bridge request. The current server HTTP API remains dev/local/self-check transport. Admin Client Bridge is a long-term track, not the active near-term implementation roadmap.

## No Channel Core Model

The following are explicitly forbidden in core:

- Channel domain object.
- Channel name as execution relationship.
- SignalBridge.
- SignalListener.
- ActionRelay.
- Receiver / Relay.
- Hidden event bus as the primary graph traversal model.

Legacy adapters may translate external broadcasts into TriggerEvent or API operations later, but that translation must stop at the adapter boundary.

## Core Model

### Project

A Project is the PixelLogic workspace for a server/world.

It contains:

- project id
- schema version
- graphs
- global settings
- permission/audit policy
- createdAt / updatedAt
- fingerprint

### Graph

A Graph is one named logic flow.

It contains:

- graph id
- display name
- nodes / blocks
- slots / ports
- typed edges
- metadata
- validation result
- draft/committed state

Graph execution is based on compiled indexes, not graph-wide scans.

### Block / Node

User term: card, block, puzzle piece, 积木.

Internal term: Node is acceptable in core code.

Future catalog term: Block Definition describes the concrete user-placeable block. Category is only library navigation and must not be treated as an executable node.

v1 node families:

- Trigger
- Condition
- Action
- Control container
- Execution-context container
- State
- Timer
- Debug

Each node has:

- id
- type
- typed config
- input slots
- output slots
- position metadata for WebUI
- Chinese summary data or summary template

The current families are demo/runtime families, not permanent catalog categories. A future Block Catalog should map concrete block ids such as `trigger.manual_test`, `condition.state.equals`, `action.message.chat`, `state.set`, `timer.wait`, and `debug.log` onto the underlying runtime node behavior.

Catalog definitions should own form schema, default config, summary template, validation rules, capability levels, safety flags, and migration/deprecation metadata. Runtime execution should continue to use compiled graph indexes and typed edges.

### Slot / Port

A Slot is an input or output connection point on a node.

Examples:

```text
Condition:
- input
- pass output
- fail output

Action:
- input
- done output
- error output, optional/folded in normal UI
```

Slot definitions must include:

- id
- direction: input or output
- edge type
- cardinality
- user-facing label
- advanced/folded flag when needed

### Typed Edge

A Typed Edge is a direct connection from one output Slot to one input Slot.

It contains:

- edge id
- source node id
- source slot id
- target node id
- target slot id
- edge type

Typed Edge is the execution relationship. It must not be replaced by Channel routing.

### Trigger

A Trigger is a graph entry.

v1 supports:

- command trigger
- manual/test trigger

Trigger emits TriggerEvent through loader adapter or WebUI/API simulation.

### Condition

A Condition is a read-only branch node.

Rules:

- It evaluates state or context.
- It does not write state.
- It does not perform side effects.
- It does not emit events.
- It returns a raw boolean and may also return a contextual condition result.
- It chooses one outgoing slot.
- A contextual result keeps the checked subject on both true and false evaluations; false must not degrade to a subject-less boolean.
- `PASS_ONLY`, `FAIL_ONLY`, and `BRANCH` decide routing only and do not change the raw result or contextual subject.

v1 required condition:

- State Compare Condition.

### Action

An Action is a controlled side-effect node.

Rules:

- It must have typed schema.
- It must have backend validation.
- It must produce a trace result.
- It should have a Chinese summary.
- Unknown or invalid action config fails closed.

v1 actions:

- Message Action.
- State Set Action.
- State Add Action.
- Timer Start Action.
- Debug Log Action.

### State

State is typed and scoped.

v1 scopes:

- GLOBAL
- PLAYER
- SESSION

v1 value types:

- BOOLEAN
- INTEGER
- STRING

State keys must be validated by scope and type. Runtime state lookups must be indexed by scoped key.

### Timer

Timer is a delayed continuation.

v1 needs:

- start timer action
- due time
- continuation graph id
- continuation node/slot reference
- trace correlation id
- immutable execution cursor and control-frame stack
- single-consumption continuation identity
- max continuation depth/budget

Timer scheduling must not scan every timer every tick when avoidable. Prefer a due queue, min heap, or indexed next-due structure.

The manual simulation spike uses JDK wall-clock scheduling instead of tick scanning. It is in-memory and intentionally does not recover timers across server restart.

Control Flow Continuation v1 keeps cumulative steps, count/forever iteration state, and nested loop return frames in the cursor snapshot so a timer resumes after the waiting node instead of restarting or completing the containing loop.

Timer output is optional. No downstream edge is a valid natural completion cursor: a top-level path completes after the wait, a loop body completes its current iteration, and nested loop frames unwind normally. Disconnected timer nodes remain valid but unreachable.

### ExecutionContext

ExecutionContext represents one execution.

It includes:

- trigger source
- actor/player or session metadata when present, for transport/security/audit only
- optional target entity reference
- optional current entity context
- current contextual condition result
- session id when present
- graph id
- trace id
- runtime budget
- state view
- current node
- current continuation depth

### Contextual Condition Result and Entity Execution Context

A contextual condition result is temporary runtime state for one run and its current control path. It contains enough information to identify the condition node/block, checked subject and subject kind, raw boolean, and readable fact. `condition.entity.has_tag` and `condition.player.is_admin` currently produce contextual subjects. Their true and false evaluations both retain the same checked object; a later ordinary condition replaces the current result, and an ordinary condition without a contextual result clears it.

This state must not use a static/global last result, cross run boundaries, or be persisted in graph JSON, storage, draft, autosave, or history. Loop-until rack predicates consume the current runtime context for raw evaluation but do not overwrite the ordinary control path's current condition result.

`context.entity.execute_as` is a flat single-body container with `input`, `done`, and `containerSlots=["body"]`. Its shared composite `target` accepts exactly:

- `CURRENT_ENTITY`: the optional current execution entity;
- `CONDITION_SUBJECT`: the current path's latest condition subject;
- `TARGET_ENTITY`: the optional target supplied by the current path/trigger contract;
- `ONLINE_PLAYER`: a selected currently online player resolved by canonical UUID.

Entry resolves exactly the selected source through EntityTargetRef, saves the previous current entity, switches to the resolved entity, executes the body, restores the previous entity on natural completion, and then follows `done`. An empty body is a valid no-op that still restores the outer context. Nested entity contexts restore in stack order. This is execute-as, not execute-at: position, dimension, rotation, and facing are unchanged and are not part of the v1 entity frame.

`ExecutionCursor` and `TimerContinuation` snapshot only stable optional target/current identities, the current condition result, entity-context frames, and loop frames. A delay inside any loop/context nesting therefore resumes with the same subject and current entity. Resume validates entity resolvability, condition-node identity, body membership, mixed loop/context nesting, runtime generation, cancellation, and single consumption before state is restored. There is no second permanent start-of-run entity source and no Minecraft object in the cursor.

### ExecutionTrace

ExecutionTrace is a readable record for users and debugging.

Each trace step should include:

- timestamp
- trace id
- graph id
- node id
- node type
- input summary
- result summary
- selected output slot
- error if any

Trace powers WebUI bottom execution record and runtime diagnosis.

### Simulation Model

Simulation is the WebUI-first execution model for testing graph logic before every block has a real Minecraft adapter.

It may model PixelLogic-visible abstractions such as actor/player, state, timers, trace, simple inventory facts, simple world facts, region events, container slots, event source, and permissions. It must not simulate complete Minecraft mechanics such as redstone, entity AI, physics, chunk loading, or full item NBT behavior.

The intended executor split is:

- shared validation, slots, form schema, summary, and trace format in Block Definition;
- simulation executor for `FULLY_SIMULATABLE` or `APPROXIMATE_SIMULATION` blocks;
- Minecraft executor for real server side effects;
- capability and safety flags to warn, restrict, or require real runtime.

The detailed Simulation Backend boundary and maintainability rules are now defined in:

- `docs/specs/SIMULATION_BACKEND_BOUNDARY_AND_MAINTAINABILITY.md`
- `docs/specs/SIMULATION_CAPABILITY_MATRIX.md`

Future Simulation Backend work should add a logical receiver runner that accepts a simulated event, builds a simulation context, calls GraphRuntime, collects action results, and returns bounded trace/state/timer/result data. The runner is not a replacement for GraphRuntime, not a Channel/event bus, and not a real Minecraft listener.

Maintenance gates:

- Do not place all simulation behavior in one service.
- Do not grow an endless switch over all block ids.
- Do not put simulated Minecraft rules into WebUI.
- Do not import Minecraft classes into core runtime or core simulation.
- Review any new file over about 500 lines for splitting; split files over about 800 lines unless there is a documented reason.

### ValidationIssue

ValidationIssue is a user-facing problem found before commit or simulation.

It includes:

- severity
- node/edge reference when applicable
- Chinese message
- machine code
- suggested fix when obvious

## Runtime Execution

Runtime execution flow:

```text
1. Loader/API creates TriggerEvent.
2. Runtime creates ExecutionContext.
3. Runtime resolves graph entry by triggerType index.
4. Runtime loads CompiledGraph from graphId cache.
5. Runtime executes node.
6. Condition selects pass or fail output and replaces the current path's contextual result when applicable.
7. Entity-context container resolves exactly one configured source, enters body, and restores the outer entity on completion.
8. Action performs controlled side effect against its declared actor/current-entity semantics.
9. Runtime follows outgoing Typed Edge by nodeId and slotId index.
10. Timer action registers a continuation containing loop and entity-context state.
11. Runtime appends trace step.
12. Runtime stops on completion, budget exhaustion, error, or validation failure.
```

## Runtime Performance Requirements

v1 runtime must define and enforce:

1. `triggerType -> entry block` index.
2. `graphId -> compiled graph` cache.
3. `nodeId -> outgoing edges` index.
4. `state scope + key -> state value` lookup.
5. Timer due queue, min heap, or equivalent next-due index.
6. Max steps per execution.
7. Max nested continuations.
8. Per tick runtime budget.
9. Loop guard.
10. Fail-closed error handling.

Implementation may start simple, but the structure must not make these requirements impossible without rewriting core.

## Storage Spec

Recommended v1 world storage:

```text
world/pixellogic/
  project.json
  graphs/
    committed/
      <graph-id>.json
    drafts/
      <graph-id>.json
  state/
    global.json
    players/
      <player-id>.json
    sessions/
      <session-id>.json
  audit/
  traces/
```

Each persisted config file must include:

- schemaVersion
- id
- createdAt
- updatedAt
- fingerprint

Graph drafts and committed graphs should be distinguishable so invalid drafts do not replace a valid committed graph.

The current implemented graph checkpoint uses:

```text
<world>/pixellogic/
  graphs/
    committed/demo-start-flow.json
    drafts/demo-start-flow.json
```

`demo-start-flow` is seeded on first startup. Writes use a temporary file and atomic replace where available. Graph ids are limited to `[A-Za-z0-9_-]+` so API paths cannot escape the PixelLogic storage root.

Entity execution graphs persist block id/config—including the nested EntityTargetRef object—typed edges, and flat container membership (`parentContainerId` / `parentSlot=body`). Runtime condition results, subject references, current/target entity state, mutable simulation tags, and scope frames are ephemeral and must never enter graph or project storage.

## Validation Spec

v1 validation must cover:

- graph structure
- missing graph entry
- disconnected required path
- slot direction mismatch
- edge type mismatch
- missing required fields
- state scope mismatch
- state type mismatch
- timer parameter validity
- unknown block type
- unknown action type
- unknown condition type
- missing, malformed, or unknown composite entity `target`
- invalid entity-context membership or ancestor cycle
- container nesting above the configured maximum
- loop risk
- budget risk when statically obvious

Unknown block or edge types fail closed.

An empty `context.entity.execute_as` body is a saveable warning, not an error. Runtime source resolution remains strict even for a structurally valid graph: missing current/condition/target source, offline or unresolvable online player, non-entity subject, type mismatch, invalid entity frame, or stale continuation fails closed and does not fall back to another entity source.

The spike model uses enums for known node and edge types, then validates required slots, state config, timer duration, timer completion edge, and obvious loop risk before compilation/execution.

## API Draft

API shape is a draft. Before implementation, re-check requirements and confirm endpoints.

```text
GET    /api/project
GET    /api/graphs
POST   /api/graphs
GET    /api/graphs/{id}
PUT    /api/graphs/{id}/draft
POST   /api/graphs/{id}/validate
POST   /api/graphs/{id}/commit
POST   /api/graphs/{id}/simulate
GET    /api/traces
GET    /api/events/stream
```

### Implemented Graph Draft API

```text
GET  /api/pixellogic/graphs
GET  /api/pixellogic/graphs/demo-start-flow
GET  /api/pixellogic/graphs/demo-start-flow/draft
PUT  /api/pixellogic/graphs/demo-start-flow/draft
POST /api/pixellogic/graphs/demo-start-flow/validate
POST /api/pixellogic/graphs/demo-start-flow/commit
```

Rules:

- All responses are JSON envelopes.
- `PUT draft` parses versioned graph JSON and writes only the draft file.
- `validate` runs the existing `GraphValidator` against the draft.
- `commit` validates again; invalid drafts return issues and do not replace committed graph.
- Test-run endpoints execute the committed graph, not the draft.

### Implemented Spike API

The first implemented API is deliberately smaller than the draft:

```text
GET  /api/pixellogic/status
POST /api/pixellogic/test/reset
POST /api/pixellogic/test/start
GET  /api/pixellogic/test/runs/{runId}
GET  /api/pixellogic/traces/latest
GET  /api/pixellogic/traces
```

Rules:

- It is bound to `127.0.0.1:18111`.
- It serves the in-memory `demo-start-flow` only.
- It defaults to `WebUI 模拟玩家` and may accept a per-run test actor plus one optional simulated target entity for WebUI simulation.
- It returns JSON success/error envelopes.
- It exposes the current bounded simulation snapshot as `WAITING`, `COMPLETED`, `FAILED`, or `CANCELLED`; `traceId` is reused as the run identity.
- It is not the final project/graph persistence API.
- It does not introduce Channel, Region, or old TZZ concepts.

API rules:

- Validate all inbound graph drafts server-side.
- Commit only valid graphs.
- Simulate should not mutate committed state unless explicitly designed later.
- Trace endpoints must avoid unbounded result size.
- Events stream is optional for UI refresh; it is not a Channel model.

## Loader Adapter Spec

v1 targets Fabric only.

Rules:

1. Core runtime does not reference Fabric/Forge/NeoForge event classes.
2. Loader adapter converts loader/Minecraft events into PixelLogic TriggerEvent.
3. Before any event integration, check the target loader and MC version for existing official event APIs.
4. Reuse official loader/Minecraft events when available.
5. Do not lightly add custom hooks, polling, tick scans, or Mixin.
6. If Fabric lacks a safe event, document alternatives, performance cost, and stability risk before implementation.

Forge and NeoForge are future adapter boundaries, not v1 implementation targets.

## Required v1 CompiledGraph Indexes

CompiledGraph should prepare:

- nodes by id
- slots by node id
- outgoing edges by node id and output slot id
- incoming edges by node id when validation/debug needs it
- trigger entries by trigger type
- state references by node for validation
- timer continuations by node for runtime registration

This keeps execution focused on current node traversal rather than graph scans.

## Fail-Closed Rules

Runtime must stop safely when:

- graph is missing
- compiled graph is stale or invalid
- node id is unknown
- slot id is unknown
- edge type mismatches
- action config is invalid
- condition config is invalid
- configured entity source is unknown
- the selected run, target, or condition-subject entity is missing, not an entity, or cannot be resolved
- entity-context or mixed loop/context continuation frames are invalid
- state type mismatches
- execution exceeds budget
- timer continuation target is missing
- timer continuation generation or contextual snapshot is stale

Each stop should write a trace step when possible.
