# PixelLogic Simulation Backend Boundary and Maintainability

This is a docs-only design specification. It does not authorize Java, WebUI, API, GraphRuntime, Minecraft adapter, tag, release, or merge work by itself.

## Goals

PixelLogic will grow from the current seven demo blocks into many concrete catalog blocks. The simulation backend exists so new blocks can be configured, validated, executed in a controlled test context, traced, and reviewed before every block has a real Minecraft adapter.

Simulation Backend should let PixelLogic verify graph logic, block config, trace output, state changes, timer behavior, branch selection, and action results without bootstrapping the full real Minecraft interaction chain. It should be close enough to real gameplay semantics that server owners can trust the flow shape, but small enough that PixelLogic does not become a partial Minecraft engine.

This matters now because waiting until the WebUI is "finished" before modeling real gameplay concepts would push too much complexity into the frontend and current demo runtime. Deep Minecraft integration is also too early: loader events, permissions, world mutation, item data, and server lifecycle need stable adapter boundaries first.

## Non-Goals

- Do not simulate full Minecraft.
- Do not replace real Minecraft testing.
- Do not implement public multiplayer collaboration.
- Do not perform real world mutation.
- Do not parse real BlockEntity or full NBT payloads.
- Do not execute real Minecraft commands.
- Do not implement full item/component matching.
- Do not add Channel, SignalBridge, SignalListener, ActionRelay, Receiver, or Relay as a core execution model.
- Do not make SimulationRunner a replacement for GraphRuntime.
- Do not let WebUI directly simulate Minecraft rules.

## Core Principles

1. Simulation Backend is not a fake backend. It is simulated input, simulated context, and simulated execution adapters running through the real PixelLogic graph/runtime path.
2. Simulation Backend does not simulate the whole Minecraft engine.
3. It simulates only Minecraft abstractions that PixelLogic blocks can observe, decide on, or act on.
4. Simulation and Minecraft Adapter stay separate.
5. WebUI does not directly simulate Minecraft.
6. GraphRuntime does not depend on Minecraft classes.
7. Catalog describes blocks; Simulation executes simulated behavior; Minecraft Adapter executes real server behavior.
8. Future blocks should be able to receive a Simulation executor first, then a Minecraft executor later.
9. Files and packages must split by responsibility. A new large service or mega file is a design failure, not a milestone.
10. Maintainability is a first-class goal for this phase.

## Simulation Scope

Simulation should model PixelLogic-visible abstractions, not Minecraft internals.

### Actor / Player

- Stable simulated id or UUID.
- Display name.
- Online/offline state.
- OP or permission markers.
- Tags.
- Team membership.
- Gamemode.
- Alive/dead state.
- Position and dimension.
- Facing direction when a block needs it.
- Inventory summary.

### World

- Dimension id.
- Simplified block table.
- Simplified block state.
- Region membership facts.
- Simplified redstone state.
- Simplified containers.
- Recorded world-change results for unsafe actions.

### Inventory and Items

- Item id.
- Count.
- Display name.
- Simplified lore/text component.
- Placeholder for custom data or future data components.

### Container

- Container id.
- Slots.
- Open/close event facts.
- Content change facts.

### Event

- Manual trigger.
- Player join.
- Player leave.
- Player right-click block.
- Player enter region.
- Player leave region.
- Redstone state change.
- Container open/close/content change.

### Action Result

- Message output.
- Future title/actionbar output.
- State changes.
- Tag changes.
- Item changes.
- Teleport result.
- World mutation record.
- Trace.

## Out of Scope

Simulation must not implement:

- Full redstone propagation.
- Entity AI.
- Collision boxes.
- Fluid updates.
- Lighting.
- Real chunk loading/unloading behavior.
- Random ticks.
- Entity physics.
- Pathfinding.
- Full NBT interpreter.
- Full command system.
- Full server permission system.
- Full chat signing or secure chat mechanics.

If a future block needs one of these concepts, model the smallest PixelLogic-visible fact or result needed by that block. If that is not enough, mark the block `REQUIRES_MINECRAFT_RUNTIME`, `UNSAFE_OR_WORLD_MUTATING`, or `DEFERRED`.

## Layered Architecture

```text
Block Catalog
  -> describes blocks, form schema, summary, capability, safety flags

GraphRuntime Core
  -> executes graph traversal, conditions, actions, state, timers, trace
  -> must not depend on Minecraft classes

Simulation Model
  -> SimulatedActor, SimulatedWorld, SimulatedInventory, SimulatedEvent, SimulationActionResult

Simulation Receiver / Runner
  -> receives simulated events, builds execution context, calls GraphRuntime, returns results

Simulation Executors
  -> execute blockId/NodeType-specific simulated behavior such as message, state, timer, debug, inventory, world, player

Minecraft Adapter
  -> future loader-side mapping to real ServerPlayer, ServerWorld, BlockState, Inventory, and events
```

Block Catalog is data/metadata. It must not call executors.

GraphRuntime remains the direct graph traversal engine. Simulation Runner should prepare the request and collect results; it must not become a second runtime.

Simulation Executors are the adapter layer for block behavior. The current `GraphRuntime` still contains a `NodeType` switch for the demo blocks; the future split should move behavior out gradually, one current block family at a time, with self-checks.

Minecraft Adapter is a future boundary. It should translate real loader/Minecraft inputs into PixelLogic trigger or execution requests and translate PixelLogic action requests into real side effects.

## Proposed Backend Package Structure

This is a target shape, not an instruction to create every file at once.

```text
src/main/java/com/pixelmc/pixellogic/core/simulation/
  context/
    SimulationContext.java
    SimulationActor.java
    SimulationWorld.java
    SimulationInventory.java
    SimulationItemStack.java
    SimulationBlockState.java
    SimulationContainer.java
    SimulationRegion.java

  event/
    SimulationEvent.java
    SimulationEventType.java
    ManualTriggerEvent.java
    PlayerJoinEvent.java
    BlockInteractEvent.java
    RegionEnterEvent.java
    ContainerChangeEvent.java

  runner/
    SimulationRunner.java
    SimulationExecutionRequest.java
    SimulationExecutionResult.java
    SimulationRunOptions.java

  receiver/
    SimulationEventReceiver.java
    ManualSimulationReceiver.java
    PlayerSimulationReceiver.java
    WorldSimulationReceiver.java

  executor/
    SimulationBlockExecutor.java
    SimulationExecutionRegistry.java
    MessageSimulationExecutor.java
    StateSimulationExecutor.java
    TimerSimulationExecutor.java
    DebugSimulationExecutor.java

  result/
    SimulationActionResult.java
    SimulationMessageResult.java
    SimulationStateChangeResult.java
    SimulationWorldChangeResult.java

  trace/
    SimulationTraceFormatter.java
```

Prefer adding only the packages needed by the next implementation slice. Empty architecture folders are not useful by themselves.

## WebUI Structure Boundary

Future simulation UI should split into dedicated frontend modules:

```text
web-ui/src/model/simulation/
  simulationTypes.ts
  simulationEvents.ts
  simulationResults.ts

web-ui/src/api/simulationApi.ts

web-ui/src/ui/simulation/
  simulationPanel.ts
  simulationActorEditor.ts
  simulationEventPicker.ts
  simulationResultView.ts
  simulationWorldStateView.ts
```

Do not add simulation business logic to `web-ui/src/ui/app.ts`. `app.ts` may orchestrate screen assembly and bind high-level actions, but simulated actor/world/event editing belongs in `ui/simulation`, and API details belong in `api/simulationApi.ts`.

WebUI can display and edit simulation inputs. It must not become the source of truth for Minecraft rules.

## Logical Receiver Runner

The logical receiver runner is the backend path that turns a simulated event into one graph run.

Responsibilities:

1. Receive one simulated event.
2. Select the graph and trigger block for the event.
3. Build the execution context.
4. Provide actor, world, inventory, container, state, timer, and trace services to runtime/executors.
5. Call GraphRuntime.
6. Capture action outputs into `SimulationActionResult`.
7. Include state, timer, and trace updates in the result.
8. Return a bounded result to WebUI/API.

It is not:

1. A new runtime replacing GraphRuntime.
2. A Channel/event-bus model.
3. A real Minecraft event listener.
4. A place for UI display formatting.

## Maintainability Rules

### File Size and Responsibility

1. New mega files are forbidden.
2. A file over about 500 lines requires review for possible split.
3. A file over about 800 lines should be split unless there is a documented reason.
4. `web-ui/src/ui/app.ts` must not absorb new simulation UI business logic.
5. `SimulationRunner` must not contain all player, world, item, container, and action logic.
6. Catalog must not own execution logic.
7. Executors must not own WebUI copy.
8. WebUI must not own backend simulation rules.

These are review gates, not compiler limits. They exist to stop the next large feature from becoming one more central file.

### Package Responsibility

- `context`: simulated state model only.
- `event`: simulated event model only.
- `runner`: orchestration of one simulation run only.
- `receiver`: event receiving and trigger entry selection only.
- `executor`: per-block simulated execution only.
- `result`: output result model only.
- `trace`: trace formatting helpers only.

### Dependency Direction

```text
catalog -> no dependency on simulation executor
simulation -> may depend on catalog and core runtime
minecraft adapter -> may depend on catalog/core runtime, but must not pollute core simulation
web-ui -> communicates through API only
core runtime -> no WebUI dependency and no Minecraft classes
```

### New Block Extension Checklist

Every new concrete block needs:

1. `BlockDefinition`.
2. `formSchema`.
3. Summary.
4. Validation.
5. Simulation capability level.
6. Safety flags.
7. Simulation executor, or an explicit `NOT_SIMULATABLE` / `REQUIRES_MINECRAFT_RUNTIME` reason.
8. Trace formatter.
9. Self-check.
10. Docs.

Do not add only UI. Do not add only runtime dispatch. Do not add a block with no capability/safety story.

### Forbidden Patterns

1. One universal `SimulationService` that owns every behavior.
2. One large switch over every `blockId` that grows indefinitely.
3. One giant `app.ts` that manages all simulation UI.
4. One giant `blockCatalog.ts` full of behavior.
5. WebUI hardcoding real Minecraft behavior.
6. Core model importing real Minecraft classes for simulation convenience.
7. Raw command or raw JSON as the normal user escape hatch.
8. Channel, SignalBridge, SignalListener, ActionRelay, Receiver, or Relay as the core execution model.

## Suggested Implementation Path

1. Add simulation backend skeleton with request/result/context types and one manual receiver.
2. Add simulated actor/player/world/inventory/container models with only fields needed by current and next candidate blocks.
3. Route current demo blocks through simulation executors without changing user-visible behavior.
4. Keep state, timer, trace, and graph validation fail-closed.
5. Add a small simulation-backed catalog expansion, such as player tag condition/action.
6. Only after the simulation model is stable, build one small Minecraft adapter proof.

## Open Questions Before Implementation

- Should the first simulation API operate on committed graphs only, or also support draft-only simulation?
- Should simulation state be isolated per run, per browser session, or persisted as a named test scenario?
- Which first non-demo block proves the model best: player tag, player join, inventory item check, or title/actionbar?
- Should timer simulation use real wall-clock delay, fast-forward, or both?
- How should simulation results be stored: ephemeral API response, trace history, or named scenario artifact?
