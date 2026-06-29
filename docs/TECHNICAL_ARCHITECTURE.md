# Technical Architecture

PixelLogic starts as a clean Fabric backend plus an independent WebUI project.

## Bootstrap Boundary

The initial bootstrap created the Fabric mod initializer, resource metadata, WebUI shell, and documentation.

The v1 manual simulation spike now adds the first backend runtime path:

- Fabric Command API v2 adapter under `loader/fabric`.
- `/pixellogic` command root only.
- In-memory demo `GraphDefinition`, validation, compiled graph, state, trace, and wall-clock timer.
- Timer due callbacks hand runtime continuation back to the Minecraft server thread.

It still does not implement gameplay items, blocks, old TZZ systems, Region, persistent graph storage, WebUI API integration, or Java-generated WebUI.

## Future Module Boundaries

- `core/model`
- `core/runtime`
- `core/graph`
- `core/action`
- `core/condition`
- `core/state`
- `core/timer`
- `core/region`
- `server/api`
- `server/storage`
- `server/security`
- `loader/fabric`
- `loader/forge` or `loader/neoforge` later
- `web-ui`

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
