# Loop Until + Condition Rack v1

## Scope

This stage adds `control.loop.until` without replacing the flat direct-edge graph or the existing container/continuation architecture.

- The loop has the existing static `body` container slot plus an instance-owned dynamic condition rack.
- The rack uses ordered, typed `conditionSlots`; each entry has a stable `slotId` and a slot-owned `negated` flag.
- A condition capsule remains a normal flat graph node. Its membership is `parentContainerId=<loop id>` and `parentSlot=<stable condition slot id>`.
- Catalog metadata declares `PREDICATE` on predicate-compatible conditions and `PREDICATE_RACK` on `control.loop.until`; dynamic slot ids are not copied into static `containerSlots`.
- v1 combines configured conditions with AND only. Each slot may independently apply NOT.

This stage does not add OR, grouped expressions, asynchronous predicates, break/continue, event waits, persistent cross-restart continuation, or a real Minecraft executor.

## Predicate Capsule Set

Only these existing block ids are predicate-compatible in v1:

- `condition.player.has_tag`
- `condition.player.is_admin`
- `condition.player.dimension_is`
- `condition.player.in_region`
- `condition.target_block.is_type`

The capsule and ordinary condition-card paths share the same raw predicate evaluator. `PASS_ONLY`, `FAIL_ONLY`, and `BRANCH` remain stored on the condition node but do not change predicate truth while the node is in a rack. The rack applies its own `negated` flag after raw evaluation.

Catalog predicate summary metadata provides compact human-readable capsule text. Neither the UI nor runtime infers predicate support from Chinese names, categories, coordinates, or condition output mode.

## Graph and Storage

Graph JSON remains flat and keeps schema version 1. The new node field is additive:

```text
loop node.conditionSlots = [{ slotId, negated }, ...]
condition node.parentContainerId = loop node id
condition node.parentSlot = stable slotId
body node.parentContainerId = loop node id
body node.parentSlot = body
```

Missing `conditionSlots` in an old graph normalizes to an empty list. Save/load and undo/redo preserve list order, stable slot ids, negation state, and membership. Removing a middle slot does not renumber or reassign the remaining slots.

Undo/redo restores an exact graph snapshot and therefore does not remap ids. The current editor has no copy, duplicate, or import UI and this stage does not claim one. A future fragment copy/import feature must centrally remap node ids, edge ids, container membership, dynamic slot ids, and the matching condition-node `parentSlot`; it must not treat the static `body` name as a dynamic id.

## Geometry and Interaction

The condition rack grows upward from the loop header anchor:

- adding rows does not move the loop title, external input/done anchors, or body origin;
- shared rack geometry owns row height/gap, capsule and empty-slot rectangles, NOT toggle hit rectangles, rack top offset, and complete visual bounds;
- selection, marquee, collision, drag group, ghost, insertion placeholder, nesting, and container size calculations use the complete bounds including the rack;
- dragging the loop moves its body descendants and condition capsules as one structural group;
- condition-slot drop is a rack-local candidate for `PREDICATE` nodes only and does not replace the existing body candidate scope;
- animation and preview transforms remain visual only and do not write graph membership before pointer release.

The loop editor uses a modal-local draft. Adding/removing slots and changing NOT remain local until save; deleting a filled slot deletes its owned condition node only in the single saved graph edit. Cancel leaves graph, history, and storage unchanged.

## Runtime

`control.loop.until` is a pre-check loop:

1. Validate that at least one condition slot exists and every slot has exactly one legal predicate node.
2. Evaluate each raw predicate synchronously and without side effects.
3. Apply the slot-local NOT flag.
4. Exit through `done` when all results are true.
5. Otherwise run `body`; natural body completion returns to the same loop frame for another pre-check.

An initially true condition executes the body zero times. Missing slots, empty slots, invalid predicate membership/evaluation, or a false condition with an empty body fail closed with a readable diagnostic and do not enter a busy loop.

`timer.wait` in the body reuses the existing immutable execution cursor and loop-frame continuation. Resume does not reset cumulative steps or the loop-until iteration counter. Simulation stops safely after 20 unsuccessful rounds and records the cap in trace.

## Validation and Guards

Save-time editing states are intentionally distinguishable from runnable states:

- zero slots, empty slots, mixed filled/empty slots, and an empty body are warnings and remain saveable;
- blank/duplicate stable slot ids, a missing dynamic parent slot, multiple nodes in one condition slot, a non-`PREDICATE` child, or any control edge incident to a rack capsule are structural errors;
- runtime repeats the rack checks and fails closed even if invalid external data bypasses normal editing;
- ordinary condition cards retain the existing optional-input/output and output-mode rules.

The stage guard is `loopUntilConditionRackSelfCheck`, registered under Gradle `check`, plus `web-ui/checks/loopUntilConditionRackSelfCheck.mjs`. Coverage includes catalog capability metadata, old/new graph roundtrip, stable ids and NOT, validator warnings/errors, the five shared predicates, AND/NOT, initial exit, body mutation, delay continuation, empty states, cap 20, nested loops, upward geometry, complete bounds, drag grouping, accessibility, modal-draft semantics, and deep-cloned undo snapshots.

These checks are deterministic model/runtime/static checks. They do not claim browser E2E or user hand-testing.
