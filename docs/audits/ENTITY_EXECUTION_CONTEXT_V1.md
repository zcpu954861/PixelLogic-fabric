# Entity Execution Context + Contextual Condition Results v1

## Scope

This stage adds per-path condition results and a single-entity execute-as container without replacing PixelLogic's direct-edge graph, `GraphRuntime`, container geometry, or timer continuation system.

- A contextual condition result records the condition node/block, checked subject and kind, raw boolean, and readable fact.
- True and false evaluations both retain the checked subject. `PASS_ONLY`, `FAIL_ONLY`, and `BRANCH` keep their existing routing semantics.
- `context.entity.execute_as` selects the current condition subject, run entity, or optional Simulation target entity for one `body`, then restores the outer entity.
- The current condition result and current entity belong to one run and its current control path. They are never global or persisted.

This stage is execute-as only. It does not add execute-at, position/dimension/facing changes, real entity lookup, detectors, event listeners, scans, or multi-entity fan-out.

## Catalog

The new top-level category is `context` / `执行上下文`. The stage adds:

| Block id | User-facing block | Semantics |
| --- | --- | --- |
| `context.entity.execute_as` | 以实体为上下文执行 | C-shaped `input` / `body` / `done` container; default source is `当前条件对象`. |
| `condition.context_entity.has_tag` | 上下文实体是否拥有标签 | Predicate-compatible condition with the existing output-mode system and a contextual result on true/false. |
| `action.context_entity.add_tag` | 为上下文实体添加标签 | Adds one validated tag to the current entity. |
| `action.context_entity.remove_tag` | 移除上下文实体标签 | Removes one validated tag from the current entity. |

`entitySource` accepts only:

- `CONDITION_SUBJECT` / `当前条件对象`;
- `RUN_ENTITY` / `运行实体`;
- `TARGET_ENTITY` / `目标实体`.

`condition.player.has_tag` and `condition.player.is_admin` are the first existing conditions upgraded to contextual results. Existing `player.*` tag conditions/actions remain bound to the run actor; only `context_entity.*` blocks operate on the current entity.

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

`ExecutionCursor` snapshots the run entity, optional target entity, current entity, current condition result, entity-context frames, and loop frames. `timer.wait` therefore resumes the same entity through `context -> delay`, `context -> loop -> delay`, and mixed outer-loop/context/inner-loop arrangements. Resume validates node/block identity, body membership, entity resolvability, mixed scope nesting, runtime generation, cancellation, and one-time continuation consumption before restoring. A stale or cancelled continuation cannot mutate an entity.

## Simulation Test Context

The WebUI/API per-run test context may include one optional target entity:

```text
enabled: false
entityTypeId: minecraft:zombie
displayName: 测试僵尸
tags: []
```

The test player is the run entity and initial current entity. The target entity exists only for Simulation and is snapshotted into the run cursor; it is not resolved again after a delay. Actor and target mutable tags are copied for every run, preventing request reuse or concurrent/sequential runs from sharing mutations.

Results expose test-player initial/final tags separately from target-entity initial/final tags. Running a result does not write either final tag set back to the test-context draft. The API boundary validates entity type id, display name, tag format/count, and malformed input.

## Graph, Storage, and WebUI

Graph JSON remains flat. It stores only the new block ids/config, typed edges, and ordinary membership:

```text
child.parentContainerId = context node id
child.parentSlot = body
```

Runtime condition results, subjects, run/target/current entity identities, mutable simulated tags, and context frames are not stored in graph JSON, drafts, autosave, undo/redo, or project storage.

The WebUI reuses the existing C-container geometry, `body` anchor semantics, nested layout, drag insertion, ghost, FLIP/reduced-motion behavior, and one pointer-up graph edit. Loop and context nodes share body-container classification. Pointer move may show preview only; it must not change base graph positions, edges, membership, history, or autosave state.

The target-entity editor follows the existing modal-local draft/save/dirty-close pattern. Normal UI uses Chinese source labels and separate actor/target result summaries; it does not expose raw JSON or runtime object dumps.

## Validation and Fail-Closed Rules

Save-time validation covers:

- unknown `entitySource` values through catalog form options; a missing value uses the catalog/runtime default `CONDITION_SUBJECT`;
- invalid `body` membership or parent slot;
- ancestor cycles and maximum container depth;
- contextual tag and condition-output configuration;
- an empty body as a saveable warning.

Runtime fails closed, with readable trace, when:

- `CONDITION_SUBJECT` has no current condition result;
- the condition subject is not a player/entity;
- the run or target entity is missing;
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
