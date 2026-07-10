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

This server HTTP API remains the current development/local/self-check transport. The long-term admin direction is a client-hosted WebUI: an authorized PixelLogic client starts a localhost bridge, and that bridge forwards WebUI operations to the server through Minecraft networking. The server remains authoritative for graph, catalog, validation, runtime, storage, trace, session, and capability checks. This is a future track, not the active near-term implementation roadmap; near-term work remains Simulation Backend, catalog, graph, runtime, and WebUI editor capability.

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
- Current concrete block ids include the demo set plus the player/message/context/spatial/control expansions: `trigger.manual_test`, `condition.state.equals`, `condition.player.has_tag`, `condition.player.is_admin`, `condition.player.dimension_is`, `condition.player.in_region`, `condition.player.y_compare`, `condition.target_block.is_type`, `condition.target_block.in_region`, `condition.target_block.y_compare`, `condition.player.near_target_block`, `control.loop.count`, `control.loop.forever`, `action.player.add_tag`, `action.player.remove_tag`, `action.message.chat`, `action.message.title`, `action.message.subtitle`, `action.message.actionbar`, `state.set`, `state.add`, `timer.wait`, and `debug.log`.
- Graph JSON keeps legacy `node.type` and adds `blockId`; old graph documents without `blockId` infer it from `node.type`.
- GraphRuntime still dispatches on `NodeType` in this checkpoint. The catalog is a registry and compatibility layer, not a runtime rewrite.
- `GET /api/pixellogic/catalog` exposes the readonly catalog to the independent WebUI.

The catalog form schema checkpoint keeps that runtime boundary:

- `BlockDefinition` exposes `formSchema`, `summaryTemplate`, and `summaryFormatter` metadata.
- The WebUI editor resolves known `blockId` values through catalog schema first and uses legacy `NodeType` builders only as fallback.
- The Java built-in catalog and `/api/pixellogic/catalog` are the catalog authority; the frontend fallback catalog is only an API-offline placeholder, not a duplicate registry.
- Message actions store message config as a rich text component payload while the current string-valued graph config schema is preserved. The payload now supports segments with Minecraft named colors and boolean style flags.
- `GraphRuntime` still extracts plain text and dispatches through the existing `MESSAGE_ACTION` path; no real Minecraft Text adapter is implemented. Simulation message results retain the structured payload for future preview use.

## Lifecycle / Capacity Safety

The v1 editor baseline now has spike-level safety bounds:

- In-memory state is capped at 1024 entries and fails closed when a new key would exceed the cap.
- Test reset clears the demo player's PLAYER state, the manual SESSION state, pending timers, and rebuilds the current committed runtime generation.
- Timer continuations carry a runtime generation. Reset, committed graph reload, and server stop invalidate older generations before callbacks can resume graph execution.
- Timer continuations also carry an immutable execution cursor with cumulative steps and loop frames; each continuation id can be consumed only once.
- New manual test runs cancel the replaced runtime, and forever-loop iteration counts remain capped across wait resumptions.
- The existing single simulation-result slot is refreshed after each asynchronous resume. WebUI queries it by trace/run id with one finite polling loop; late run callbacks and stale HTTP responses are ignored.
- Timer outputs are optional: an empty continuation target represents natural path/loop-frame completion rather than an invalid node.
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
- `server/admin-session`
- `client/bridge` later
- `client/tool` later
- `loader/fabric`
- `loader/forge` or `loader/neoforge` later
- `web-ui`

## Admin Client Bridge Direction

The recommended long-term management mode is:

```text
Browser
 -> 127.0.0.1:<randomPort> + one-time token
 -> PixelLogic Client Local Bridge
 -> Minecraft networking payload
 -> PixelLogic Server Core
```

The client mod defaults to no management functionality. It does not start a local bridge, does not open WebUI, does not enable selectors/overlays, and does not make tool items useful until the server grants an authorized admin session.

Future implementation candidates, when prerequisites are mature and a new prompt scopes the work:

1. handshake/status only;
2. read-only bridge for catalog, graph, and trace;
3. graph draft save / validate / commit through the bridge;
4. simulation run through the bridge;
5. capability-gated tool item selection;
6. server HTTP kept as dev/local/self-check transport until bridge parity is proven.

See:

- `docs/specs/ADMIN_CLIENT_BRIDGE_AUTHORIZED_SESSION.md`
- `docs/specs/CLIENT_HOSTED_WEBUI_PROTOCOL.md`
- `docs/audits/ADMIN_CLIENT_BRIDGE_DESIGN_AUDIT.md`

## Simulation Adapter Boundary

Simulation is an adapter-facing capability model, not a full Minecraft clone.

Core logic simulation may model actors, state, timers, trace, inventory summaries, simple world facts, regions, containers, event sources, permissions, and trace output only to the extent PixelLogic blocks need them. It must not promise full redstone, entity AI, physics, chunk loading, or complete item NBT behavior.

The intended split for future concrete blocks is:

- shared Block Definition for id, slots, form schema, validation, summary, trace format, and safety flags;
- Simulation Executor for WebUI-first testing and trace;
- Minecraft Executor for real Fabric/Minecraft side effects;
- loader adapter conversion from real events into PixelLogic triggers.

GraphRuntime and WebUI must not directly bind to Minecraft classes.

The next Simulation Backend design is recorded in:

- `docs/specs/SIMULATION_BACKEND_BOUNDARY_AND_MAINTAINABILITY.md`
- `docs/specs/SIMULATION_CAPABILITY_MATRIX.md`

The intended next slice is a small simulation skeleton: simulated event request/result types, a manual simulation receiver, a minimal actor/world/inventory/container context, and per-block simulation executors for the current demo blocks. This should be done before expanding many new block families or adding a real Minecraft adapter.

Maintainability requirements for that slice:

- `GraphRuntime` remains graph traversal/runtime core, not a Minecraft model.
- `SimulationRunner` orchestrates one simulated run; it must not own all block behavior.
- Simulation block behavior belongs in executor classes by block family or block id.
- `web-ui/src/ui/app.ts` must not absorb simulation UI business logic.
- New backend or frontend files over about 500 lines require split review; files over about 800 lines should be split unless clearly justified.

The Simulation Backend skeleton checkpoint adds the first code slice:

- `core/simulation` owns minimal actor/world/event/context/result/runner/executor types.
- `SimulationRunner` routes manual/WebUI test runs through a simulated context while still calling `GraphRuntime`.
- `RuntimeServices` provides a small optional simulation-node hook and result collectors; simulated node output uses top-level `RuntimeNodeExecutionResult` while existing services keep no-op defaults.
- Player tag condition/action are the first simulation-backed catalog expansion.
- `SimulationExecutionRegistry` owns player tag simulated behavior so `SimulationRunner` does not become a giant service.

Still out of scope: draft simulation, named scenarios, fast-forward timers, real Minecraft adapter, full inventory/world/container simulation, and new simulation UI. The current `SimulationRunOptions` only preserves the real-time timer scheduling choice.

The Simulation Test Context MVP adds the first small WebUI simulation input without widening that boundary:

- WebUI sends per-run display name, tags, and administrator flag with `POST /api/pixellogic/test/start`.
- Backend validates the request, builds a `SimulationActor`, and passes it into `SimulationRunner`.
- The run result returns initial and final actor tags so the UI can show tag changes.
- The context is not stored in graph JSON, not persisted as a scenario, and not shared with the CLI test command.
- `web-ui/src/ui/app.ts` only orchestrates the panel; the model and panel live under `model/simulationTestContext.ts` and `ui/simulation/`.

The Simulation Context Expansion v1 checkpoint keeps the same boundary and adds only small per-run facts needed by later position/block/region blocks:

- `SimulationActor` now carries an integer block position and dimension, defaulting to `minecraft:overworld (0, 64, 0)`.
- `SimulationWorld` carries a default dimension, disabled-by-default target block fact, and up to 8 normalized region facts.
- `POST /api/pixellogic/test/start` accepts these facts under `testContext.world` and returns them in the simulation result summary.
- No new blocks, named scenarios, graph writes, full world simulation, inventory/container/entity simulation, real Minecraft adapter, or Admin Client Bridge implementation are added.

Catalog Expansion v2 consumes those facts through read-only condition blocks:

- `condition.player.dimension_is` compares the simulated player dimension.
- `condition.player.in_region` checks the simulated player position against a named test region.
- `condition.target_block.is_type` compares the enabled target block's block id.
- `condition.target_block.in_region` checks the target block position against a named test region.
- Missing target block or missing region is a false condition result with trace text, not a runtime error.
- The Simulation Test Context remains per-run input and is not written to graph JSON.

Catalog Expansion v3 adds a small spatial condition slice on the same per-run facts:

- `condition.player.y_compare` compares player Y with `AT_OR_ABOVE`, `AT_OR_BELOW`, `EQUAL`, or inclusive `BETWEEN`.
- `condition.target_block.y_compare` compares target block Y and returns false when no target block is enabled.
- `condition.player.near_target_block` compares player and target block distance, horizontally or in 3D.
- Missing target block or dimension mismatch is a false condition result with trace text, not a runtime error.
- No target-block-exists block, X/Z coordinate compare, region geometry, named scenario, persistence, or real Minecraft adapter is added.

Container Control Flow v1 adds a first control-flow container slice without replacing the direct graph model:

- `control.loop.count` and `control.loop.forever` are C-shaped container blocks with one `body` slot.
- Graph JSON remains flat; body membership is stored on child nodes with `parentContainerId` and `parentSlot`.
- Loop count runs the body a bounded fixed number of times and then continues the outer `done` edge.
- Forever loop is simulation-safe: it stops at a finite test cap and does not expose a normal outer next edge.
- This is not a full coroutine loop runtime, not real persistent server loops, and not a real Minecraft adapter.

The condition output mode checkpoint keeps the same direct edge runtime and adds only a condition-local config:

- `outputMode` supports `PASS_ONLY` / 满足时继续, `FAIL_ONLY` / 不满足时继续, and `BRANCH` / 分成两路.
- Missing `outputMode` on old graphs defaults to `BRANCH`.
- New condition catalog nodes default to `PASS_ONLY`; the seeded demo graph explicitly stays `BRANCH`.
- Unconnected condition outputs mean that path ends gracefully and are not validation blockers.
- Unconnected condition inputs are allowed during editing like other loose placed blocks; they are unreachable until connected to a trigger path.
- `condition.player.has_tag` is categorized as `条件判断 / 玩家条件`; `condition.player.is_admin` uses the same player condition group; `action.player.add_tag` and `action.player.remove_tag` remain `玩家操作 / 标签`.
- Player conditions may provide block-specific `outputMode` labels so normal UI says `拥有标签时继续` or `是管理员时继续` instead of only generic labels.

## Loop Until + Condition Rack v1

`control.loop.until` extends the existing flat container model rather than introducing nested graph JSON:

- The loop keeps the static `body` slot and stores an ordered typed `conditionSlots` list on the loop node. Each entry has a stable `slotId` and slot-owned `negated` flag.
- Condition capsules remain normal graph nodes whose `parentContainerId` points to the loop and whose `parentSlot` is that stable dynamic slot id.
- Old schema-version-1 graphs without `conditionSlots` normalize to an empty list.
- Catalog `PREDICATE` and `PREDICATE_RACK` capabilities separate boolean-evaluation support from UI category/name metadata; dynamic ids never appear in static `containerSlots`.
- v1 supports only AND across configured slots plus independent per-slot NOT. The first predicate set is `condition.player.has_tag`, `condition.player.is_admin`, `condition.player.dimension_is`, `condition.player.in_region`, and `condition.target_block.is_type`.
- Ordinary condition execution and rack capsules share one raw predicate evaluator. `PASS_ONLY`, `FAIL_ONLY`, and `BRANCH` do not affect capsule truth.
- Runtime checks the rack before every body iteration. All true exits through `done`; otherwise the body runs and natural completion returns to the same loop frame.
- Missing/empty/illegal conditions and a false pre-check with an empty body fail closed with trace diagnostics. Simulation stops after 20 unsuccessful rounds.
- A body `timer.wait` reuses the existing execution cursor and continuation frames; no second continuation system is introduced.

The rack grows upward while the loop header, external anchors, and body origin stay fixed. Shared geometry supplies complete rack-aware selection, collision, drag, ghost, insertion, and nested-container bounds. Preview/animation state is not graph state.

Zero or empty condition slots remain saveable warnings so incomplete drafts are editable; duplicate ids, mismatched membership, multiple children in one condition slot, non-predicate children, and capsule control edges are structural validation failures. Runtime repeats these checks and fails closed.

Undo/redo preserves exact stable ids through graph snapshots. There is currently no copy/duplicate/import UI; this stage does not claim one. Any future fragment remapper must update node/edge ids, container membership, dynamic slot ids, and matching condition `parentSlot` atomically.

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
