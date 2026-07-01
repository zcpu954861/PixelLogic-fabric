# PixelLogic Catalog Expansion v2: Context Blocks

## Scope

- 玩家所在维度是否为: `condition.player.dimension_is`
- 玩家是否在区域内: `condition.player.in_region`
- 目标方块是否为: `condition.target_block.is_type`
- 目标方块是否在区域内: `condition.target_block.in_region`

## Product Decision

- use simulation context facts: player position, target block, and test regions are read from the per-run Simulation Test Context.
- no negative blocks: there is no separate player-not-in-region or target-block-not-type block.
- no duplicate coordinates in block config: region and target coordinates stay in the test context.
- context describes facts, graph describes logic: the graph stores what to check, while the simulation context stores the current simulated world facts.

## Condition Output Labels

- dimension: 在该维度时继续 / 不在该维度时继续 / 分开执行
- region: 在区域内时继续 / 不在区域内时继续 / 分开执行
- target block type: 为该方块时继续 / 不为该方块时继续 / 分开执行
- target block region: 在区域内时继续 / 不在区域内时继续 / 分开执行

## Simulation Semantics

- player position: `condition.player.dimension_is` and `condition.player.in_region` read `SimulationActor.position()`.
- target block: target block conditions read `SimulationWorld.targetBlock()`.
- region facts: region conditions use `SimulationWorld.findRegion`.
- missing facts false: missing target block or region evaluates false and writes readable trace text.
- inclusive bounds: region checks use `min <= value <= max` for x/y/z.
- dimension match: region membership requires matching dimension id.

## Validation

- dimension id: must be a namespaced id such as `minecraft:overworld`.
- block id: must be a namespaced id such as `minecraft:stone`; real registry existence is not checked yet.
- region name: required, max 64 characters, no control characters.
- loose nodes: unconnected condition inputs and outputs remain valid editor states.

## Non-goals

- no world mutation.
- no full world simulation.
- no MC adapter.
- no Admin Client Bridge implementation.
- no named scenario or persistence.
- no graph writes for Simulation Test Context facts.
