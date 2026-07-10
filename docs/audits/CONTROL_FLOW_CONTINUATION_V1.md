# PixelLogic Control Flow Continuation v1

## Scope

- `timer.wait` can suspend and resume inside `control.loop.count` and `control.loop.forever` bodies.
- Nested count loops preserve their inner and outer return paths across waits.
- Multiple waits in one body resume after the consumed wait without replaying earlier nodes.
- A wait at the end of a loop body resumes into normal iteration completion.
- `control.loop.forever.intervalSeconds` now schedules the existing wall-clock timer between completed rounds.

The initial checkpoint did not change WebUI or HTTP API behavior. The end-to-end follow-up keeps graph JSON, membership, storage, catalog forms, and Minecraft adapters unchanged while exposing suspended-run status and bounded trace polling to the existing WebUI test-run path.

## Runtime Design

- `ExecutionCursor` is an immutable snapshot containing the run/trace identity, actor/session, cumulative steps, resume node, and immutable loop-frame stack.
- `GraphRuntime` remains the only traversal engine; unchanged condition/message/state/debug behavior is isolated in the package-private `RuntimeNodeExecutor` to keep control-flow logic reviewable.
- Each loop frame records the container, current iteration, limit, body entry, completion node, and forever interval.
- `TimerContinuation` carries one cursor snapshot plus a unique continuation id and the runtime generation.
- `GraphRuntime` consumes each continuation id once. Duplicate, cancelled, stale-generation, invalid-node, or invalid-membership resumes fail closed.
- The existing scheduler remains responsible for wall-clock timing and its 128 pending-timer cap; no thread pool or polling loop was added.
- Timer output is optional. An empty resume node means natural path completion: top-level runs complete, loop-body waits complete the current iteration, and nested waits unwind their frame stack.
- `SimulationRunner` refreshes the existing single `lastSimulationResult` snapshot after every suspend/resume result. The API reuses `traceId` as `runId`; the WebUI uses finite `setTimeout` polling until `COMPLETED`, `FAILED`, or `CANCELLED`.

## Safety

- Step counts and the 20-round forever simulation cap survive every timer resume.
- New manual simulation runs cancel the replaced runtime and clear its pending scheduler tasks.
- Reset, graph install/commit, generation changes, and service stop continue to invalidate old continuations.
- Continuations are in-memory only and are not written to graph JSON or storage.
- Trace remains bounded and distinguishes iteration start/end, wait suspend/resume, loop completion, cap stop, and ignored stale continuations.

## Validation

`controlFlowContinuationSelfCheck` covers:

- count loop `A -> delay -> B` ordering;
- delay at body start and body end;
- multiple delays in one body;
- forever body delay plus inter-round interval and the 20-round cap;
- nested loops and outer/inner delays;
- cancellation, stale generation, duplicate resume, and pending continuation capacity;
- cumulative step cap across resumes;
- ordinary timer chains and existing empty-body behavior.
- disconnected timers, top-level terminal timers, nested tail waits, API status transitions, reset/new-run cancellation, and WebUI stale-response cleanup.

## Known Limits

- Pending continuations do not survive a server restart.
- Waiting for events is not implemented.
- `break`, `continue`, `loop until`, variable loop counts, and parallel branches are not implemented.
- Forever loops remain simulation-first and stop after 20 rounds.
- Real Minecraft execution remains a future adapter boundary.
