# Technical Architecture

PixelLogic starts as a clean Fabric backend plus an independent WebUI project.

## Bootstrap Boundary

The initial bootstrap created the Fabric mod initializer, resource metadata, WebUI shell, and documentation.

The v1 manual simulation spike now adds the first backend runtime path:

- Fabric Command API v2 adapter under `loader/fabric`.
- `/pixellogic` command root only.
- In-memory demo `GraphDefinition`, validation, compiled graph, state, trace, and wall-clock timer.
- Timer due callbacks hand runtime continuation back to the Minecraft server thread.

It still does not implement gameplay items, blocks, old TZZ systems, Region, persistent graph storage, full graph editing, or Java-generated WebUI.

The v1 API + WebUI test-run checkpoint adds a localhost-only spike API:

- JDK `HttpServer` under `server/api`.
- API lifecycle owned by the Fabric server adapter.
- Runtime actions handed to the Minecraft server executor before state/trace mutation.
- Vite dev proxy from `/api` to `127.0.0.1:18111`.
- WebUI renders real status, reset, test run, latest trace, and recent trace responses.

The v1 graph draft checkpoint adds the first persistent graph loop:

- `server/storage` owns versioned JSON graph documents and world-local graph files.
- Committed graph and draft graph are separate files under `pixellogic/graphs/`.
- Startup seeds `demo-start-flow` as committed JSON when missing.
- Draft save parses schema but does not affect runtime.
- Commit runs `GraphValidator`; only valid drafts replace committed JSON and the compiled runtime graph.
- Test-run endpoints continue to execute the committed graph.

It still does not implement arbitrary graph create/delete editing, Region, old TZZ adapters, multi-loader storage, or Java-generated WebUI.

The v1 block catalog skeleton separates Block Catalog definitions from the current demo node families:

- `core/catalog` owns the built-in registry records for categories, subcategories, concrete blocks, form fields, simulation capability, Minecraft capability, and safety flags.
- Only the current demo concrete block ids are registered: `trigger.manual_test`, `condition.state.equals`, `action.message.chat`, `state.set`, `state.add`, `timer.wait`, and `debug.log`.
- Graph JSON keeps legacy `node.type` and adds `blockId`; old graph documents without `blockId` infer it from `node.type`.
- GraphRuntime still dispatches on `NodeType` in this checkpoint. The catalog is a registry and compatibility layer, not a runtime rewrite.
- `GET /api/pixellogic/catalog` exposes the readonly catalog to the independent WebUI.

## Lifecycle / Capacity Safety

The v1 editor baseline now has spike-level safety bounds:

- In-memory state is capped at 1024 entries and fails closed when a new key would exceed the cap.
- Test reset clears the demo player's PLAYER state, the manual SESSION state, pending timers, and rebuilds the current committed runtime generation.
- Timer continuations carry a runtime generation. Reset, committed graph reload, and server stop invalidate older generations before callbacks can resume graph execution.
- Pending timers are capped at 128 and can be cleared as a group during reset, graph install, and shutdown.
- Trace storage remains a bounded ring buffer: 50 traces, 100 steps per trace.
- `PixelLogicSpikeService.close()` stops timers and clears in-memory state.

These bounds are intentionally small and local to the spike. Before broader runtime use, state ownership, lifecycle, persistence, and per-scope capacity policy still need a product decision.

## Future Module Boundaries

- `core/model`
- `core/runtime`
- `core/graph`
- `core/action`
- `core/condition`
- `core/state`
- `core/timer`
- `core/catalog`
- `core/simulation`
- `core/region`
- `server/api`
- `server/storage`
- `server/security`
- `loader/fabric`
- `loader/forge` or `loader/neoforge` later
- `web-ui`

## Simulation Adapter Boundary

Simulation is an adapter-facing capability model, not a full Minecraft clone.

Core logic simulation may model actors, state, timers, trace, inventory summaries, simple world facts, regions, containers, event sources, permissions, and trace output only to the extent PixelLogic blocks need them. It must not promise full redstone, entity AI, physics, chunk loading, or complete item NBT behavior.

The intended split for future concrete blocks is:

- shared Block Definition for id, slots, form schema, validation, summary, trace format, and safety flags;
- Simulation Executor for WebUI-first testing and trace;
- Minecraft Executor for real Fabric/Minecraft side effects;
- loader adapter conversion from real events into PixelLogic triggers.

GraphRuntime and WebUI must not directly bind to Minecraft classes.

## WebUI Rule

Java / Fabric owns mod initialization, runtime, API, permission, storage, validation, audit/debug, and static resource serving.

`web-ui` owns TypeScript UI, graph/node/edge editing, card interactions, and build output.

Forbidden:

- Java-generated HTML
- Java-generated CSS
- Java-generated JS
- Giant frontend strings in Java
- WebUI logic inside Java script modules

## Loader Event API Reuse

1. 以暗猜接口为耻，以认真查阅为荣。Any Trigger / Event integration must first check the target loader and MC version for official event APIs.
2. Reuse loader/Minecraft official events when available.
3. Keep loader differences in adapter layers.
4. Core logic must not depend on Fabric/Forge/NeoForge event classes.
5. If Fabric lacks a safe event, evaluate Fabric API, official interfaces, Mixin necessity, performance cost, and stability before adding it.
6. Do not sacrifice clarity for forced uniformity.

Future design space:

- `loader/fabric/events`
- `loader/forge/events`
- `loader/neoforge/events`
- `core/events/TriggerEvent`
