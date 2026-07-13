# Entity Execution Context + Contextual Condition Results v1

## Scope

This stage adds per-path condition results and a single-entity execute-as container without replacing PixelLogic's direct-edge graph, `GraphRuntime`, container geometry, or timer continuation system.

- A contextual condition result records the condition node/block, checked subject and kind, raw boolean, and readable fact.
- True and false evaluations both retain the checked subject. `PASS_ONLY`, `FAIL_ONLY`, and `BRANCH` keep their existing routing semantics.
- At this historical checkpoint, `context.entity.execute_as` selected one of three entity contexts for `body`; Entity Target Reference v1 now supplies the authoritative four-source target contract.
- The current condition result and current entity belong to one run and its current control path. They are never global or persisted.

This stage is execute-as only. It does not add execute-at, position/dimension/facing changes, real entity lookup, detectors, event listeners, scans, or multi-entity fan-out.

## Catalog

The new top-level category is `context` / `执行上下文`. The stage adds:

| Block id | User-facing block | Semantics |
| --- | --- | --- |
| `context.entity.execute_as` | 以实体为上下文执行 | C-shaped `input` / `body` / `done` container; default source is `当前条件对象`. |
| retired context-tag condition | 上下文实体是否拥有标签 | Historical predicate-compatible condition; replaced by the generic entity-tag condition. |
| retired context-tag add action | 为上下文实体添加标签 | Historical action; replaced by the generic entity-tag add action. |
| retired context-tag remove action | 移除上下文实体标签 | Historical action; replaced by the generic entity-tag remove action. |

The historical `entitySource` accepted only:

- `CONDITION_SUBJECT` / `当前条件对象`;
- the then-current run-bound entity source, which is no longer supported;
- `TARGET_ENTITY` / `目标实体`.

The then-current player-tag condition and `condition.player.is_admin` were the first conditions upgraded to contextual results. Entity Target Reference v1 later replaced the split player/context tag paths with one explicit generic target path.

## Runtime and Path Semantics

`RuntimeConditionResult` is carried by the active execution context/cursor. A selected true or false path receives the same checked subject plus its actual raw result and fact. A later ordinary condition replaces the current result; if that condition has no contextual result, the old value is cleared. A loop-until rack predicate may inspect the current runtime entity but does not replace the ordinary control path's condition result.

The execute-as container performs:

1. Resolve exactly the configured source.
2. Save the previous current entity.
3. Switch to the selected entity.
4. Execute the ordinary flat-graph `body` chain.
5. Restore the previous entity on natural completion.
6. Follow `done`.

An empty body is valid and immediately restores the outer entity. Nested contexts restore in stack order. Body natural completion is determined by the same graph traversal and membership rules as loop containers; no second runtime or traversal exists.

`ExecutionCursor` snapshots stable optional target/current identities, the current condition result, entity-context frames, and loop frames. `timer.wait` therefore resumes the same current entity through `context -> delay`, `context -> loop -> delay`, and mixed outer-loop/context/inner-loop arrangements. Resume validates node/block identity, body membership, entity resolvability, mixed scope nesting, runtime generation, cancellation, and one-time continuation consumption before restoring. A stale or cancelled continuation cannot mutate an entity.

## Simulation Test Context

The WebUI/API per-run test context may include one optional target entity:

```text
enabled: false
entityTypeId: minecraft:zombie
displayName: 测试僵尸
tags: []
```

The test player initializes the optional current entity; it is not retained as a second permanent target source. The target entity exists only for Simulation and is snapshotted into the cursor; it is not resolved again after a delay. Actor and target mutable tags are copied for every run, preventing request reuse or concurrent/sequential runs from sharing mutations.

Results expose test-player initial/final tags separately from target-entity initial/final tags. Running a result does not write either final tag set back to the test-context draft. The API boundary validates entity type id, display name, tag format/count, and malformed input.

## Graph, Storage, and WebUI

Graph JSON remains flat. It stores only the new block ids/config, typed edges, and ordinary membership:

```text
child.parentContainerId = context node id
child.parentSlot = body
```

Runtime condition results, subjects, target/current entity identities, mutable simulated tags, and context frames are not stored in graph JSON, drafts, autosave, undo/redo, or project storage.

The WebUI reuses the existing C-container geometry, `body` anchor semantics, nested layout, drag insertion, ghost, FLIP/reduced-motion behavior, and one pointer-up graph edit. Loop and context nodes share body-container classification. Pointer move may show preview only; it must not change base graph positions, edges, membership, history, or autosave state.

The target-entity editor follows the existing modal-local draft/save/dirty-close pattern. Normal UI uses Chinese source labels and separate actor/target result summaries; it does not expose raw JSON or runtime object dumps.

## Validation and Fail-Closed Rules

Save-time validation covers:

- at this historical checkpoint, unknown `entitySource` values were rejected and a missing value used `CONDITION_SUBJECT`; the current composite `target` contract rejects a missing target instead of decoding an implicit default;
- invalid `body` membership or parent slot;
- ancestor cycles and maximum container depth;
- contextual tag and condition-output configuration;
- an empty body as a saveable warning.

Runtime fails closed, with readable trace, when:

- `CONDITION_SUBJECT` has no current condition result;
- the condition subject is not a player/entity;
- the selected current/condition/target source is missing;
- the selected entity cannot be resolved;
- an entity-context frame or mixed loop/context scope is invalid;
- a continuation is stale, cancelled, duplicated, or belongs to an invalid generation.

Resolution never falls back from one configured source to another, never reuses a previous run's object, and never silently skips the body.

## Testing and Guards

The stage guard is `entityExecutionContextSelfCheck`, registered under Gradle `check`. Its runtime/model coverage includes contextual true/false results, `PASS_ONLY` / `FAIL_ONLY` / `BRANCH`, later-condition replacement, all three entity sources, missing/non-entity/unresolvable subjects, empty body, nesting, delay, loop, loop-until, mixed loop/context continuation, multi-run isolation, and cancelled continuation safety.

`web-ui/checks/entityExecutionContextSelfCheck.mjs` covers source labels, shared loop/context body classification, empty-body placement, nested anchor alignment, preview-only pointer move, ghost reuse, target-entity defaults/clone/validation/payload, modal/result markup, and visual category hooks.

The existing continuation, container, condition-output, loop-until, graph-storage, simulation-backend, simulation-test-context, API/WebUI, Gradle build, WebUI build, and forbidden-pattern scans remain required regressions. These automated checks are model/runtime/static checks and do not claim browser E2E or user hand-testing.

## Explicitly Not Implemented

- execute-at, position offset, dimension switch, rotation, or facing;
- real Minecraft `Entity`/player adapters or loader events;
- online-player or world-entity scans;
- detector / “when condition becomes true” modules;
- multiple-current-entity arrays, for-each, or batch fan-out;
- cross-server-restart continuation;
- broad entity health, equipment, AI, teleport, damage, spawn, particle, or sound blocks.

A future detector must create one isolated condition result and execution path/run for every matched entity. It must not place all matches into shared mutable “current entities” state.
