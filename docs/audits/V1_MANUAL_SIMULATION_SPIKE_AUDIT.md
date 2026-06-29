# PixelLogic v1 Manual Simulation Spike Audit

## Verdict

- Ready for user Minecraft smoke: yes
- Ready for merge to mc-1.21.11 after user smoke: yes, with P2 follow-ups tracked
- Blocking issues: 0
- Non-blocking issues: 4

## Git / Baseline

- branch: `audit/v1-manual-simulation-spike`
- audited commit: `d259dbf7c44e4f25b8f1aada5348444f345bcc7b`
- audited branch: `feature/v1-manual-simulation-spike`
- compared against: `origin/mc-1.21.11` for spike scope, and current HEAD for implementation audit
- worktree status before report: clean except ignored build/report outputs

## Architecture Findings

### No Channel Core

Pass. New Java implementation does not introduce `Channel`, `SignalBridge`, `SignalListener`, `ActionRelay`, `Receiver`, `Relay`, channel name routing, or hidden event-bus traversal.

Evidence:

- `src/main/java/com/pixelmc/pixellogic/core/runtime/GraphRuntime.java:38-50` starts from `TriggerEvent` and resolves a compiled graph entry.
- `src/main/java/com/pixelmc/pixellogic/core/runtime/GraphRuntime.java:95-106` follows `CompiledGraph.firstTarget(current.id(), outputSlot)`.
- `src/main/java/com/pixelmc/pixellogic/core/graph/CompiledGraph.java:40-52` resolves outgoing edges by `nodeId + slotId`.

### /pixellogic Command Root

Pass. Commands are registered only under `pixellogic`.

Evidence:

- `src/main/java/com/pixelmc/pixellogic/loader/fabric/PixelLogicCommandRegistrar.java:22-35` registers `/pixellogic status`, `/pixellogic test start`, `/pixellogic test reset`, and `/pixellogic trace last`.
- `src/main/java/com/pixelmc/pixellogic/loader/fabric/PixelLogicCommandRegistrar.java:26-35` gates `test` and `trace` with GM permission.
- `src/main/java/com/pixelmc/pixellogic/loader/fabric/PixelLogicCommandRegistrar.java:43-60` returns a Chinese error for console use of player-scoped test commands.

### Graph / Runtime Shape

Pass. The demo flow is represented as a `GraphDefinition` and compiled before runtime execution. The runtime is a small typed-node dispatcher, not a hard-coded demo if/else script.

Evidence:

- `src/main/java/com/pixelmc/pixellogic/core/graph/DemoGraphFactory.java:21-69` builds a graph with nodes and edges.
- `src/main/java/com/pixelmc/pixellogic/server/PixelLogicSpikeService.java:47-80` validates, compiles, and creates `GraphRuntime`.
- `src/main/java/com/pixelmc/pixellogic/core/runtime/GraphRuntime.java:111-120` dispatches by `NodeType`.

### Condition Branch

Pass. Condition is an independent node and chooses `pass` / `fail` output slots.

Evidence:

- `src/main/java/com/pixelmc/pixellogic/core/graph/DemoGraphFactory.java:27-33` defines the standalone state compare condition.
- `src/main/java/com/pixelmc/pixellogic/core/runtime/GraphRuntime.java:123-134` returns `pass` or `fail`.
- `src/main/java/com/pixelmc/pixellogic/core/graph/GraphValidator.java:96-111` validates condition input, pass branch, fail branch, boolean config, and current BOOLEAN-only scope.

### Action Handling

Pass for spike scope. Message, State Set, State Add, Timer Start, and Debug Log all execute as typed node families.

Evidence:

- `src/main/java/com/pixelmc/pixellogic/core/runtime/GraphRuntime.java:137-186`
- `src/main/java/com/pixelmc/pixellogic/core/graph/GraphValidator.java:121-180`

### State Handling

Pass for spike scope.

Evidence:

- `src/main/java/com/pixelmc/pixellogic/core/model/StateScope.java`
- `src/main/java/com/pixelmc/pixellogic/core/state/StateKey.java:12-20` requires owner id for PLAYER and SESSION scopes.
- `src/main/java/com/pixelmc/pixellogic/core/runtime/GraphRuntime.java:123-134` handles missing `PLAYER.started` through explicit condition config.
- `src/main/java/com/pixelmc/pixellogic/core/runtime/GraphRuntime.java:144-160` checks State Set and State Add type boundaries.

### Timer Handling

Pass for smoke. Timer is in-memory, wall-clock based, and does not rely on game tick scanning. Minecraft-side effects are handed back to the server thread.

Evidence:

- `src/main/java/com/pixelmc/pixellogic/core/timer/WallClockTimerScheduler.java:9-18` uses `ScheduledExecutorService`.
- `src/main/java/com/pixelmc/pixellogic/server/PixelLogicSpikeService.java:66-77` hands due continuations to `serverThreadExecutor`.
- `src/main/java/com/pixelmc/pixellogic/server/PixelLogicSpikeService.java:108-113` closes scheduler with a closed fence.
- `src/main/java/com/pixelmc/pixellogic/loader/fabric/FabricPixelLogicBootstrap.java:16-18` registers Fabric `SERVER_STOPPING`.

### Trace Handling

Pass for smoke. Trace is bounded, readable, and includes trigger, branch, state, timer, debug, and error summaries.

Evidence:

- `src/main/java/com/pixelmc/pixellogic/core/trace/BoundedTraceBuffer.java:14-27` bounds trace count.
- `src/main/java/com/pixelmc/pixellogic/core/trace/ExecutionTrace.java:21-30` bounds per-trace steps.
- `src/main/java/com/pixelmc/pixellogic/core/runtime/GraphRuntime.java:41,55,65,79,88,100,133,140,152,160,178,186` appends readable trace messages.
- `src/main/java/com/pixelmc/pixellogic/loader/fabric/PixelLogicCommandRegistrar.java:68-83` exposes `/pixellogic trace last`.

## Performance Findings

- Graph traversal: pass. Runtime uses compiled graph indexes; it does not scan all graph edges per step.
- Indexes: pass. `CompiledGraph` stores `nodesById`, `outgoingByNodeAndSlot`, and `triggerEntries`.
- Timer scheduler: pass for smoke. Uses wall-clock scheduler, not tick scan.
- Trace buffer: pass. Max traces and max steps per trace exist.
- Obvious risks: P2 pending timer queue and in-memory state map are unbounded; P3 validation does some graph-wide scans and should be indexed before larger graph support.

## Stability Findings

- Validation fail-closed: pass for current enum-backed graph shape.
- Scheduler shutdown: pass. `SERVER_STOPPING` closes service, and service drops due continuations after close.
- Server-thread safety: pass. Timer due callback delegates runtime resume to `serverThreadExecutor`.
- Bounded memory: mixed. Trace is bounded; pending timers and state store are not bounded.
- Error handling: pass for current smoke. Runtime catches execution and edge-resolution exceptions and writes trace entries.

## Maintainability Findings

- Package structure: good for spike. `core/model`, `core/graph`, `core/runtime`, `core/state`, `core/timer`, `core/trace`, `server`, and `loader/fabric` are separated.
- Class responsibilities: mostly single-purpose.
- File/function size concerns: `GraphValidator` is the largest class at 237 lines; acceptable for spike but worth splitting once validation expands.
- Comments: minimal, but code is mostly self-explanatory.
- Duplicated logic: no large duplicated logic found.

## Scope Boundary Findings

- No old TZZ: pass. No old TZZ gameplay modules or business code found in source.
- No Region: pass. Region appears only in docs as deferred scope.
- No Channel: pass. Channel appears only in docs/Obsidian as a prohibited or future-adapter concept.
- No WebUI runtime API: pass. WebUI remains a static Vite skeleton.
- No Java-generated WebUI: pass. Java code does not generate WebUI HTML/CSS/JS.

## Validation Results

- `git diff --check`: pass
- `.\gradlew.bat build`: pass
- `npm install`: pass, 0 vulnerabilities
- `npm run build`: pass
- Minecraft smoke: not run by design

## Issues

### P2 - Pending timers and state store have no capacity/backpressure limits

- severity: P2
- evidence:
  - `src/main/java/com/pixelmc/pixellogic/core/timer/WallClockTimerScheduler.java:10-17`
  - `src/main/java/com/pixelmc/pixellogic/core/state/InMemoryStateStore.java:9-10`
- why it matters: The spike is GM-only and in-memory, so this does not block smoke. Still, repeated test runs or future graph expansion could grow pending scheduled tasks and state entries without a configured ceiling.
- suggested fix: Add spike-level max pending timers, expose scheduler rejection as fail-closed trace, and add lifecycle cleanup for PLAYER/SESSION state when v1 moves beyond manual smoke.
- blocking? no

### P2 - Product spec still contains a historical `/startgame` user-story example

- severity: P2
- evidence:
  - `docs/specs/NEW_LOGIC_MOD_PRODUCT_SPEC.md:229-240`
- why it matters: Implementation and current spike docs correctly use `/pixellogic`, but this older product-spec example can mislead future work back toward non-root commands.
- suggested fix: Rewrite the user story command to `/pixellogic test start` for spike context or clearly label `/startgame` as a future user-defined trigger name that is not a mod command root.
- blocking? no

### P3 - TraceStep schema is still a minimal summary record

- severity: P3
- evidence:
  - `src/main/java/com/pixelmc/pixellogic/core/trace/TraceStep.java:5`
- why it matters: Current trace is readable enough for smoke, but the v1 spec eventually wants graph id, node type, selected slot, and structured error fields for UI filtering and debugging.
- suggested fix: Expand `TraceStep` after smoke to include graph id, node type, selected output slot, and optional error code.
- blocking? no

### P3 - Graph validation uses simple graph-wide scans for branch checks

- severity: P3
- evidence:
  - `src/main/java/com/pixelmc/pixellogic/core/graph/GraphValidator.java:183-188`
  - `src/main/java/com/pixelmc/pixellogic/core/graph/GraphValidator.java:201-231`
- why it matters: This is fine for one demo graph, but validation should build incoming/outgoing indexes once before larger v1 graph editing to avoid preventable O(nodes * edges) patterns.
- suggested fix: Reuse compiled validation indexes or precompute incoming/outgoing maps inside `GraphValidator`.
- blocking? no

## User Minecraft Smoke Checklist

Run as a player with GM permission:

1. `/pixellogic status`
   - Expected: readiness message with `demo-start-flow`.
2. `/pixellogic test reset`
   - Expected: Chinese message confirming `PLAYER.started` was cleared.
3. `/pixellogic test start`
   - Expected pass branch: player receives `欢迎开始游戏`; command returns success with a trace id.
4. `/pixellogic trace last`
   - Expected: trace includes manual trigger, condition pass, message, state write, state add, and timer start.
5. Wait 30 seconds.
6. `/pixellogic trace last`
   - Expected: latest trace includes `计时器完成：继续执行` and `调试记录：倒计时结束`.
7. `/pixellogic test start` again.
   - Expected fail branch: no welcome message; trace records `玩家已经开始过游戏`.
8. `/pixellogic trace last`
   - Expected: latest trace explains the fail branch.

Console checks:

- `/pixellogic test start` from console should return `该测试需要玩家执行。`
- `/pixellogic test reset` from console should return `该测试需要玩家执行。`

## Final Recommendation

Proceed to user Minecraft smoke.

After smoke passes, this spike is acceptable to merge into `mc-1.21.11` as a first manual runtime checkpoint, provided the P2 items are tracked as follow-up cleanup and no Minecraft-only issue appears during user smoke.
