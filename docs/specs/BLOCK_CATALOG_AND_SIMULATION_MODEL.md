# PixelLogic Block Catalog and Simulation Model

本文是设计规格，不是实现授权。它用于指导下一阶段如何把当前 demo 的六个入口演进为可扩展的积木目录 / Block Catalog，并定义 WebUI 先行开发所需的模拟能力模型 / Simulation Model。

## Goals

- 让 PixelLogic 能承载未来大量具体积木，而不是把左侧入口永久写死为“触发器 / 条件 / 动作 / 状态 / 计时器 / 调试”六类。
- 把“分类”和“具体积木”分开：分类只负责找东西，具体积木才是可拖拽、可放置、可验证、可执行的节点。
- 避免一个万能大块承载过多配置。用户应拖入“发送聊天消息”“玩家拥有标签”“等待 30 秒”这种小而清楚的积木，而不是拖入“动作”后在表单里选择一切。
- 为 WebUI 先行开发提供模拟后端能力模型，让未接真实 Minecraft adapter 的积木也能完成配置校验、流程模拟、trace 展示和分支验证。
- 让每个积木未来都能同时拥有模拟执行器 / Simulation Executor 与真实执行器 / Minecraft Executor，而不是重写 GraphRuntime 或推翻 catalog。

## Non-goals

- 不实现大量积木。
- 不模拟完整 Minecraft。
- 不做公网多人协作。
- 不接真实 MC adapter。
- 不改 runtime、API、Graph JSON schema 或 WebUI 代码。
- 不把 WebUI 静态产物打包进 jar。

## Implementation Checkpoint: Catalog Skeleton

`feature/v1-block-catalog-skeleton` implements the first code-level slice of this design:

- Java registry lives under `core/catalog`.
- The readonly API endpoint is `GET /api/pixellogic/catalog`.
- Current demo nodes now have concrete `blockId` values while keeping `node.type`.
- Old graph JSON without `blockId` is still accepted by inferring from legacy `node.type`.
- GraphRuntime still dispatches through the existing `NodeType` path; the catalog is a compatibility and UI/data registry layer for this checkpoint.
- The WebUI left library is catalog-driven: category navigation first, then concrete blocks such as `发送聊天消息`, `设置状态`, or `等待一段时间`.
- `blockCatalogSelfCheck` verifies demo block registration, graph/document compatibility, and validation failures for unknown or mismatched `blockId`.

## Implementation Checkpoint: Catalog Form Schema + Rich Text Field

`feature/v1-catalog-form-schema-rich-text` adopts the catalog schema for the current editor path:

- Current demo blocks resolve `node.blockId` into Block Catalog `formSchema` before falling back to legacy `NodeType` field builders.
- Block definitions now expose `summaryTemplate` metadata used by the WebUI card, modal, and sidebar summaries.
- `action.message.chat` uses a `rich_text_component` field for its `message` config.
- The rich text MVP supports multiline plain text, a structured `{ version, plainText, segments }` payload, and a simple preview.
- Legacy string messages remain compatible and are interpreted as rich text plain text.
- Runtime still dispatches by `NodeType`; this is not a Minecraft executor split or real Text adapter.

## Implementation Checkpoint: Simulation Backend Boundary Docs

`docs/simulation-backend-boundary-maintainability` defines the next backend design boundary before code work:

- Simulation Backend is simulated input/context/executors on the real PixelLogic runtime path, not a fake frontend-only backend.
- It models PixelLogic-visible gameplay abstractions such as actor/player, world facts, inventory summaries, container slots, events, state, timer, trace, and action results.
- It explicitly does not model full Minecraft mechanics such as full redstone propagation, entity AI, collision, lighting, chunk lifecycle, physics, pathfinding, full NBT, full commands, or secure chat.
- Simulation, Block Catalog, GraphRuntime, WebUI, and future Minecraft Adapter keep separate responsibilities.
- Future implementation should add a small logical receiver runner and per-block simulation executors before adding many new blocks.
- Maintainability gates forbid new mega files, giant services, endless block-id switches, and new simulation business logic inside `web-ui/src/ui/app.ts`.

## Implementation Checkpoint: Simulation Backend Skeleton MVP

`feature/v1-simulation-backend-skeleton` adds the first minimal simulation backend code slice:

- `SimulationRunner` wraps one committed graph run through the real `GraphRuntime`.
- `SimulationActor` supports id, display name, online/operator flags, and tags.
- `SimulationWorld` only records a dimension id.
- `SimulationExecutionResult` reports trace id, action results, message results, state changes, actor tags, timer scheduling, and errors.
- `condition.player.has_tag` and `action.player.add_tag` are now registered catalog blocks.
- These player tag blocks include form schema, summaries, capability flags, safety flags, validation, simulation behavior, trace output, and self-check coverage.

This checkpoint still does not add a real Minecraft adapter, draft simulation, named scenarios, inventory/world/container simulation, or a new WebUI simulation panel.

## Implementation Checkpoint: Condition Output Modes

`feature/v1-condition-output-modes` makes condition outputs a per-condition config instead of forcing every condition to look and validate like a dual branch:

- `outputMode=PASS_ONLY`: 满足时继续；不满足时自然结束。
- `outputMode=FAIL_ONLY`: 不满足时继续；满足时自然结束。
- `outputMode=BRANCH`: 分成两路，保留当前 pass/fail 双分支语义。
- Old graphs without `outputMode` default to `BRANCH` for compatibility.
- New condition catalog entries default to `PASS_ONLY`, while the seeded demo graph explicitly stores `BRANCH`.
- Condition outputs are optional in validation; an unconnected selected output ends the path and writes a trace step.
- `condition.player.has_tag` now belongs to `条件判断 / 玩家条件`; `action.player.add_tag` remains `玩家操作 / 标签`.

## Product Principles

- 分类不是积木。
- 当前六类不是永久边界，只是当前 demo catalog 的初始入口示例。
- 具体积木才是用户拖到画布上的卡片、节点或拼图块。
- 具体积木保持小而明确，不做万能大类块。
- 积木目录必须 registry/data-driven：分类、标签、搜索词、可见性、能力等级都来自积木定义或目录注册项。
- 大量积木通过分类、子分类、搜索、标签、最近使用、常用推荐组织，而不是把配置堆在一个块里。
- 用户看到人话：主文案使用中文说明、示例和摘要。
- 内部 ID、schema、enum 只在存储、调试或高级视图中出现。
- WebUI / GraphRuntime 不直接绑定 Minecraft 类。
- Simulation Model 不模拟完整 Minecraft，只模拟 PixelLogic 积木可观察、可判断、可执行的抽象层。
- 未来真实 MC adapter 应替换模拟输入输出，而不是推翻 catalog。

## Block Catalog Model

Block Catalog 是“用户能放什么积木，以及这些积木如何被找到、编辑、验证、模拟和执行”的 registry。

### Category Registry

Category 是可注册目录项，不是固定 enum。它描述左侧库如何分组：

- `id`: 稳定分类 ID，例如 `message`、`player`、`state`。
- `displayName`: 用户看到的名称，例如 `消息显示`。
- `description`: 分类说明。
- `order`: 默认排序。
- `visibleByDefault`: 是否默认展示。
- `recommendedTags`: 分类页可优先显示的标签。

当前六个按钮只能视为 demo 入口，不能作为永久顶层分类写死。

### Subcategory

Subcategory 用于在一个分类下继续组织具体积木，例如：

- `消息显示 / 聊天`
- `消息显示 / 标题`
- `玩家操作 / 位置`
- `物品与背包 / 查询`

Subcategory 同样应由 registry 或 block definition 声明，不应写死在 UI 里。

### Tags

Tags 是横向检索维度，例如：

- `常用`
- `可模拟`
- `修改玩家`
- `修改世界`
- `需要玩家`
- `高级`

Tags 帮助搜索、筛选和推荐，但不改变 block id 或执行语义。

### Block Definition

Block Definition 是具体积木的完整描述。它定义一个可拖拽、可验证、可模拟、未来可真实执行的节点。

示例：

```text
action.message.chat
condition.inventory.has_item
trigger.player_join
timer.wait
```

### Block Variant

Variant 是同一积木的预设版本，不是新语义。例如 `action.message.chat` 可以有：

- 默认聊天消息。
- 欢迎语模板。
- 警告消息模板。

Variant 只提供默认配置和摘要模板，不应绕开 Block Definition。

### Deprecated / Hidden Blocks

旧积木不应直接删除。Block Definition 应能标记：

- `deprecated`: 仍可加载旧图，但不建议新建。
- `hidden`: 不在普通目录显示，但旧图或高级模式可识别。
- `replacementId`: 推荐替代积木。
- `migration`: 可自动迁移时的迁移元数据。

### Search Keywords

每个 block 可声明搜索词，包含中文同义词和常见口语：

```text
发送消息, 聊天, 提示, say, tell
给物品, 发奖励, 背包
传送, tp, 移动玩家
```

搜索结果展示用户文案，不把技术 ID 当主标题。

### Capability Flags

Capability flags 描述这个积木对模拟、真实服务器、安全和权限的要求。它们供 WebUI 提示、Doctor 检查、权限系统和未来 adapter 调度使用。

## Block Definition Fields

一个具体积木至少应包含：

- `id`: 稳定、小写、点分层 ID，例如 `action.message.chat`。
- `version`: 定义版本，用于迁移和兼容。
- `displayName`: 用户可见名称，例如 `发送聊天消息`。
- `description`: 一句话说明它做什么。
- `category`: 所属顶层目录 ID；不是永久固定枚举。
- `subcategory`: 可选二级目录。
- `tags`: 搜索和筛选标签。
- `icon / color token`: UI 图标和颜色 token，不直接写死视觉样式。
- `nodeKind`: runtime 家族，例如 trigger / condition / action / timer / debug；这是执行性质，不是目录分类。
- `slots / ports`: 输入输出槽位定义，包括 id、方向、edge type、标签、是否高级折叠、最大连接数。
- `defaultConfig`: 新建时默认配置。
- `formSchema`: 编辑表单 schema。
- `summaryTemplate`: 用户可读摘要模板。
- `validationRules`: 共享校验规则。
- `simulationCapability`: 模拟能力等级。
- `mcCapability`: 真实 Minecraft 执行能力需求。
- `safetyFlags`: 安全标记。
- `traceFormatter`: trace 文案格式化规则。
- `migration/deprecation metadata`: 迁移、替代、隐藏、弃用信息。

## Category Model

Category 不是固定 enum，而是可注册目录项。未来分类可包括但不限于：

- 流程控制
- 触发事件
- 条件判断
- 玩家操作
- 消息显示
- 世界交互
- 物品与背包
- 容器
- 区域
- 状态数据
- 时间调度
- 调试诊断
- 服务器管理
- 高级 / 集成

这些分类可以重排、隐藏、合并或拆分。一个具体积木属于哪个分类由 catalog 定义声明，不由 GraphRuntime 写死。

## Block ID Naming

建议规则：

- 稳定。
- 小写。
- 点分层。
- 不包含空格。
- 不暴露 Minecraft 原始类名给普通 UI。
- 前缀表达语义域，后缀表达具体动作或判断。

示例：

```text
trigger.manual_test
trigger.player_join
trigger.block_interact

condition.state.equals
condition.player.has_tag
condition.inventory.has_item

action.message.chat
action.message.title
action.player.add_tag
action.player.teleport
action.world.play_sound
action.world.set_block

state.set
state.add
state.clear

timer.wait
timer.start_named
timer.cancel_named

debug.log
debug.trace_marker
```

## Initial Catalog Mapping

当前 demo 积木应被迁移为具体 block id，而不是继续把“动作”本身作为可执行积木。

| Current NodeType | Catalog block id | 用户名称 | 说明 |
| --- | --- | --- | --- |
| `MANUAL_TRIGGER` | `trigger.manual_test` | 手动测试触发 | WebUI/命令测试入口 |
| `STATE_COMPARE_CONDITION` | `condition.state.equals` | 状态等于 | 读取状态并选择通过/失败 |
| `MESSAGE_ACTION` | `action.message.chat` 或 `action.message.simulated_chat` | 发送聊天消息 | 当前实现是模拟/玩家消息 |
| `STATE_SET_ACTION` | `state.set` | 设置状态 | 写入 scoped state |
| `STATE_ADD_ACTION` | `state.add` | 累加状态 | 对数字状态做增量 |
| `TIMER_START_ACTION` | `timer.wait` | 等待一段时间 | 等待后继续执行 |
| `DEBUG_LOG_ACTION` | `debug.log` | 写入调试记录 | 生成调试 trace/log |

## Example Future Blocks

以下只是候选，不代表本阶段实现清单：

- `action.message.title`: 显示标题。
- `action.message.actionbar`: 显示 actionbar。
- `action.player.add_tag`: 给玩家添加标签。
- `action.player.remove_tag`: 移除玩家标签。
- `action.player.teleport`: 传送玩家。
- `action.player.give_item`: 给予物品。
- `action.world.play_sound`: 播放声音。
- `action.world.set_block`: 设置方块。
- `condition.inventory.has_item`: 背包是否有物品。
- `condition.container.slot_matches`: 容器槽位是否匹配。
- `trigger.region.player_entered`: 玩家进入区域。
- `trigger.player_join`: 玩家加入服务器。
- `trigger.block_interact`: 玩家交互方块。

## Form Schema Direction

表单 schema 应由 Block Definition 提供，而不是 UI 按 node type 临时拼接。

规则：

- 固定选项使用 select、segmented control 或类似明确控件。
- boolean 显示为 `是 / 否`。
- internal enum 只用于存储，不做主文案。
- 文本字段要有 placeholder 和简短说明。
- 数值字段声明单位、最小值、最大值和步进。
- 危险字段需要二次确认或高级标记。
- 表单只展示该具体积木需要的字段，不展示万能动作的全部可能配置。

Current MVP supports `string`, `textarea`, `number`, `integer`, `boolean`, `select`, `segmented`, `readonly`, `hidden`, `scope`, and `rich_text_component`. The frontend uses catalog schema as the main path and reserves legacy `NodeType` builders for unknown old nodes only.

## Summary Direction

每个积木都应生成一句人话摘要，用于卡片、右侧栏、搜索结果和 trace。

示例：

```text
发送聊天消息：向「当前玩家」发送「欢迎开始游戏」
玩家拥有标签：当「当前玩家」拥有标签「runner」
设置状态：把「玩家」的 started 设置为「是」
等待：等待 30 秒后继续
```

摘要应来自 shared summary template，WebUI 和 backend trace 共享同一套语义，不各写一份互相漂移的文案。

Current MVP stores `summaryTemplate` in the built-in catalog and formats it in the WebUI. Backend trace wording still remains in `GraphRuntime`; a later executor/trace split should move trace formatting toward the same catalog metadata.

## Safety Flags

设计级安全标记：

- `READ_ONLY`: 只读查询，不改变状态或世界。
- `STATE_MUTATING`: 修改 PixelLogic 状态。
- `PLAYER_MUTATING`: 修改玩家状态、位置、背包、标签或效果。
- `WORLD_MUTATING`: 修改世界、方块、实体或环境。
- `COMMAND_LIKE`: 行为接近命令执行，需要更强提示和权限。
- `REQUIRES_PLAYER`: 需要玩家 actor。
- `REQUIRES_WORLD`: 需要世界上下文。
- `REQUIRES_LOADED_CHUNK`: 需要目标区块已加载。
- `SERVER_ADMIN_ONLY`: 只允许服务器管理员配置或运行。
- `NOT_SIMULATABLE`: 不能在纯模拟环境执行。

这些标记用于：

- WebUI 提示。
- 模拟能力判断。
- 未来权限控制。
- Doctor / validation 检查。
- 未来真实 MC adapter 的执行前保护。

## Simulation Model

Simulation Model 是 PixelLogic 的抽象测试环境，不是 Minecraft 克隆。

它应分层：

- core logic simulation: 运行 GraphRuntime 所需的 trigger、state、timer、trace。
- simulated actor/player: 模拟当前玩家 id、名字、标签、权限、位置、基础状态。
- simulated inventory/item stack: 模拟背包中“是否有某物 / 数量多少”的抽象，不模拟完整 NBT。
- simulated world: 模拟世界 id、时间、天气、少量方块查询或效果记录。
- simulated block state: 模拟“某位置是什么抽象方块状态”，不模拟红石、电路或物理。
- simulated region: 模拟区域命中、进入、离开事件源，不做真实空间扫描。
- simulated container: 模拟容器槽位和物品匹配，不实现完整容器交互。
- simulated event source: 模拟玩家加入、方块交互、手动测试等 trigger input。
- simulated permissions: 模拟执行者是否具备某类权限。

## Simulation Capability Levels

- `FULLY_SIMULATABLE`: 可在模拟环境中得到与 PixelLogic 语义一致的结果。例如状态读写、等待、调试记录、简单消息 trace。
- `APPROXIMATE_SIMULATION`: 可模拟流程走向和 trace，但结果与真实 MC 细节可能不同。例如播放声音、标题显示、简单背包查询。
- `REQUIRES_MINECRAFT_RUNTIME`: 需要真实服务器上下文才能执行。例如精确方块交互、真实实体选择、依赖加载区块的查询。
- `UNSAFE_OR_WORLD_MUTATING`: 会修改世界或有破坏性，需要强提示、权限和真实 adapter 保护。例如设置方块、批量清理实体。
- `SERVER_ADMIN_ONLY`: 只能由管理员配置或启用，例如命令类、服务器管理类、跨图高级集成。

模拟结果不等于真实 Minecraft 执行结果。模拟必须足够验证 graph 逻辑、配置合法性、trace、状态变化和分支走向，但不承诺模拟完整红石、实体 AI、碰撞/物理、区块加载或完整物品 NBT 行为。

## Simulation vs Minecraft Adapter

同一个 block id 应有共享定义和可替换执行器：

- shared block definition: id、slots、formSchema、validationRules、summaryTemplate、safetyFlags。
- simulation executor: 使用 Simulation Context 执行或近似执行。
- minecraft executor: 使用 loader/Minecraft adapter 执行真实 side effect。
- shared validation: 配置合法性尽量共享；环境依赖由 capability/safety 规则补充。
- shared summary: 卡片摘要不因执行器不同而漂移。
- shared trace format: trace 说同一件事，必要时标注“模拟结果”或“真实服务器结果”。

GraphRuntime 不应直接依赖 Minecraft 类。未来真实 MC adapter 负责把 Minecraft event 转成 PixelLogic trigger，把 PixelLogic action 请求落到真实服务器。

See also:

- `docs/specs/SIMULATION_BACKEND_BOUNDARY_AND_MAINTAINABILITY.md`
- `docs/specs/SIMULATION_CAPABILITY_MATRIX.md`

## WebUI Library UX

左侧积木库应从“六个按钮直接新增六种 demo 节点”演进为：

- 一级分类可扩展。
- 点击分类进入具体积木列表。
- 支持子分类。
- 支持搜索。
- 支持最近使用 / 常用。
- 每个具体积木有短说明和人话摘要。
- 标记可模拟 / 需真实服务器 / 会修改世界 / 仅管理员。
- 不展示技术 ID 作为主文案。
- 危险或高级积木默认弱化或折叠，避免普通用户误用。

目录中可以出现“触发事件”“条件判断”“消息显示”等分类，但用户最终拖入的是 `玩家加入服务器`、`状态等于`、`发送聊天消息` 这样的具体积木。

## Migration Plan

1. docs-only model
   完成本设计文档和初始目录草案，不改代码。

2. code-level BlockDefinition registry skeleton
   新增最小 registry 结构，先只能表达当前 demo 积木，不改变 Graph JSON 行为。

3. migrate current demo blocks into catalog
   把现有 `MANUAL_TRIGGER`、`STATE_COMPARE_CONDITION` 等映射为 catalog block definitions。

4. update WebUI library to category -> concrete block
   左侧库从六按钮改为分类页和具体积木列表，但仍只展示当前 demo 积木。

5. add simulation context model
   引入模拟 actor/player、state、timer、trace 和少量 world/inventory 抽象。

6. add first small real MC adapter
   选择一个低风险真实 adapter，例如 `trigger.player_join` 或 `action.message.chat`，验证 shared block definition + executor split。

## Risks

- category explosion: 分类过多，用户找不到积木。
- overly generic blocks: 为了省定义做万能积木，导致表单膨胀、验证复杂、trace 难读。
- simulation diverging from real MC: 模拟结果让用户误以为真实服务器一定一致。
- UI becoming bloated: 左侧库变成专业 IDE，普通服主无从下手。
- backend schema churn: 过早改 Graph JSON 导致迁移成本过高。
- hidden Minecraft coupling: executor 或 formSchema 偷偷依赖 Minecraft 类，破坏 WebUI/API 先行开发。

## Open Questions

- 第一批真实 MC adapter 应优先做玩家事件、消息显示，还是物品/背包判断？
- Block Definition 存储在 Java registry、JSON/YAML 数据文件，还是二者混合？
- 普通用户是否需要“高级 / 集成”分类，还是先隐藏到设置里？
- `action.message.chat` 是否应同时覆盖模拟聊天和真实聊天，还是拆成 `action.message.simulated_chat` 过渡？
- 积木搜索是否需要拼音/英文别名，还是先做中文关键词？
- 未来 graph JSON 中 `node.type` 是否保留 enum 兼容层，另加 `blockId`，还是一次迁移为 block id？
