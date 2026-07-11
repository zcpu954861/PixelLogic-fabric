# PixelLogic Lifecycle / Capacity / Safety Cleanup

Date: 2026-06-30

## Verdict

- Ready for review: yes
- P0: none
- P1: none
- P2: server-side optimistic fingerprint enforcement is still future work; this pass adds WebUI local sequence guards but does not implement multi-user conflict control.
- P3: WebUI still has no browser test harness; frontend stale-response and listener behavior is covered by build plus code review, with manual checks recommended.

## Scope

- state: added a fixed-cap in-memory state store and owner/session cleanup for test reset and server stop.
- timer: added pending timer cancellation, scheduler generation invalidation, runtime generation guards, and reset/commit/server-stop cleanup.
- trace: existing backend ring buffer remains at 50 traces and 100 steps per trace; frontend rendering now caps displayed steps at 100 as a defensive UI bound.
- undo/redo: undo and redo stacks are both capped at 80 graph snapshots.
- autosave: autosave remains debounced and single-flight; save/validate/commit responses now use local graph version plus save sequence guards before applying.
- API lifecycle: local API remains bound to `127.0.0.1`; Fabric server stop closes API and service state/timers.
- frontend listeners: no new global listeners were added; existing document/window handlers are assigned, not accumulated through repeated `addEventListener`.

## Capacity Limits

- state: 1024 in-memory state entries; new keys fail closed after the cap.
- pending timers: 128 pending timers.
- trace: 50 trace records, 100 steps per trace, 100 rendered frontend steps.
- undo stack: 80 entries.
- redo stack: 80 entries.
- autosave queue: one in-flight save plus one latest pending rerun flag.

## Safety Guards

- generation/runId: timer continuations carry a runtime generation; reset, graph commit/reload, and server stop invalidate old generations.
- save sequence: WebUI save operations carry a local sequence in addition to `graphVersion`.
- stale response guard: stale draft/validate/commit responses are ignored if graph version or save sequence no longer match.
- invalid graph fail-closed: existing `GraphStorageService.commitDraft` behavior remains; invalid drafts do not replace committed graph or runtime.
- server stop cleanup: service close invalidates timers, stops scheduler callbacks, and clears in-memory state.
- API task lifecycle: server-thread work transitions atomically through `QUEUED`, `RUNNING`, `COMPLETED`, or `CANCELLED`. A queued timeout cancels before execution; a running timeout never claims the operation was not executed.
- close fence: API and service close reject new work, queued tasks recheck the fence before side effects, and service mutations serialize with close at their existing operation boundary.
- integer arithmetic: INTEGER state addition uses checked arithmetic; overflow fails before the state map is changed and its Chinese diagnostic flows into runtime result/trace output.

## Validation

- gradlew build: pass.
- npm build: pass.
- grep checks: pass.
- tests/self-check:
  - `ManualSimulationSelfCheck` covers state cap/owner clear, trace bounds, timer capacity, timer clear, and stale timer generation.
  - `GraphStorageSelfCheck` covers invalid draft fail-closed and reset invalidating pending timer callbacks.
  - `ApiWebUiSelfCheck` still covers localhost JSON API, reset/start/trace, graph draft/validate/commit, and JSON error shape.
  - `RuntimeApiSafetySelfCheck` covers real save/commit/reset/start timeout cancellation, task start/cancel races, close fences, pending timer shutdown, checked INTEGER overflow, original-value preservation, and runtime trace diagnostics.

## Known Limitations

- This remains an in-memory spike safety layer, not durable state lifecycle or persistence.
- Reset currently invalidates all pending timers for the spike service, not only timers owned by one actor.
- Server-side `expectedFingerprint` conflict enforcement is still not implemented.
- Browser manual checks are still recommended for repeated drag autosave, undo/redo, API offline, and page refresh behavior.
