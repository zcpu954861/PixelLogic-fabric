# PixelLogic Simulation Context Expansion v1

## Scope

- player position: per-run simulated block position with dimension id and integer x/y/z.
- dimension: defaults to `minecraft:overworld`.
- target block: optional per-run fact with enabled flag, position, and block id.
- region facts: up to 8 named axis-aligned boxes with normalized min/max bounds.
- WebUI test context modal: expands `编辑测试玩家` into `编辑测试上下文`.

## Non-goals

- no new blocks.
- no named scenarios.
- no persistence.
- no graph writes.
- no full world simulation.
- no inventory/container/entity simulation.
- no MC adapter.
- no Admin Client Bridge implementation.

## Data Model

- actor position: `SimulationActor.position()` and `SimulationContext.actorPosition()`.
- `SimulationWorld`: default dimension, target block fact, and region facts.
- target block: `SimulationBlockFact`, disabled by default.
- regions: `SimulationRegionFact`, inclusive bounds and dimension match helper.
- validation: API boundary validates namespaced ids, integer coordinates, coordinate bounds, region count, and readable region names.

## WebUI

- edit test context: split-button dropdown opens `编辑测试上下文`.
- local draft: modal edits stay local until `保存`.
- player section: name, tags, and administrator segmented control are preserved.
- position section: dimension plus compact X/Y/Z fields.
- target block section: yes/no enable, block id, dimension, and compact X/Y/Z fields.
- region section: add/delete named region facts; no empty-region bubble.
- result summary: shows player, administrator, player position, target block, regions, tags, and tag changes.

## Runtime

- `SimulationRunner` still delegates to `GraphRuntime`.
- existing player tag/admin/message nodes are unchanged.
- new context facts prepare later position/block/region catalog blocks.

## Validation

- `gradlew build`: required before merge/readiness.
- `npm build`: required before merge/readiness.
- `simulationContextExpansionSelfCheck`: covers default context, custom position, target block, region normalization, invalid ids/count, and existing message/tag/admin result fields.
