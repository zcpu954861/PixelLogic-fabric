# PixelLogic Simulation Backend Skeleton

## Scope

- simulation context: added minimal actor, world, event, run options, and per-run result collection models under `core/simulation`.
- simulation event: first slice supports manual trigger requests through `SimulationEvent`.
- simulation runner: `SimulationRunner` wraps one graph run by preparing a simulated context and calling the real `GraphRuntime`.
- simulation result: result includes success, trace id, action results, message results, state changes, actor tags, timer scheduling, and errors.
- player tag block: this historical slice added the first player-specific tag pair; Entity Target Reference v1 later replaced it with generic target-aware tag blocks.
- existing manual test migration: `/pixellogic test start` and WebUI test-run still use the committed graph, now through `SimulationRunner`.
- follow-up in `feature/v1-condition-output-modes`: the tag condition gained condition output modes; its current generic replacement remains predicate-compatible.

## Architecture

- GraphRuntime remains core: graph traversal, state, timer, trace, and current demo block behavior still run in `GraphRuntime`.
- SimulationRunner role: request/context/result orchestration only.
- no MC classes: `core/simulation` has no `net.minecraft` imports.
- no full Minecraft simulation: actor tags are the only new gameplay-like simulated fact.
- committed graph only: draft simulation is not implemented.
- per-run context: simulated actor tags live on the request actor and result, not in graph storage.
- timer strategy: existing wall-clock timer path remains; fast-forward is only represented as an option boundary.

## Maintainability

- package split:
  - `context`
  - `event`
  - `executor`
  - `result`
  - `runner`
- no mega service: `SimulationRunner` delegates block behavior to `SimulationExecutionRegistry`.
- no giant switch over block ids: player tag behavior is registry-backed by `NodeType`.
- no WebUI simulation panel: frontend changes are limited to catalog fallback and labels.
- condition output mode remains a GraphRuntime traversal choice and does not change SimulationRunner ownership.

## Validation

- `git diff --check`: pass.
- `.\gradlew.bat build`: pass.
- `cd web-ui; npm install`: pass, 0 vulnerabilities.
- `cd web-ui; npm run build`: pass.
- `.\gradlew.bat apiWebUiSelfCheck`: pass.
- `.\gradlew.bat graphStorageSelfCheck`: pass.
- `.\gradlew.bat manualSimulationSelfCheck`: pass.
- `.\gradlew.bat blockCatalogSelfCheck`: pass.
- `.\gradlew.bat simulationBackendSelfCheck`: pass.
- forbidden-term grep in `src/main/java web-ui/src`: pass, no matches.
- old WebUI labels / raw enum labels grep: pass, no matches.
- say/raw JSON grep: source pass; matches are docs-only boundary notes.
- command root grep: pass; only existing `literal("test")` subcommand matched.
- `core/simulation` Minecraft dependency grep: pass, no matches.
- largest new simulation file: `SimulationBackendSelfCheck.java`, 194 lines.

## Known Limitations

- committed graph only.
- per-run simulated actor context only.
- no scenario persistence.
- no timer fast-forward implementation.
- no Minecraft adapter.
- no full inventory, world, container, redstone, entity, or command simulation.
- no Channel, Region, old TZZ, tag/release, or merge work in this branch.
