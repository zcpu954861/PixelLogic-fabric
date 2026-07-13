# Initial Block Catalog Draft

> This is a historical candidate draft. The current built-in ownership matrix and library navigation contract are defined by `BLOCK_LIBRARY_TAXONOMY_V1.md`.

这个文档是初始积木目录草案，不是实现清单，不代表下一阶段一次性开发。它只列当前 v1/vNext 最有价值的核心候选，用于避免继续把“动作 / 条件 / 触发器”当成可执行万能大块。

能力等级：

- `FULLY_SIMULATABLE`: 可完整模拟 PixelLogic 语义。
- `APPROXIMATE_SIMULATION`: 可近似模拟流程和 trace。
- `REQUIRES_MINECRAFT_RUNTIME`: 需要真实服务器上下文。
- `UNSAFE_OR_WORLD_MUTATING`: 会修改世界或高风险。
- `SERVER_ADMIN_ONLY`: 管理员限定。

## Current demo block mapping

Implementation status: this mapping is now registered by the Java built-in catalog skeleton and exposed to the WebUI through `GET /api/pixellogic/catalog`. No extra catalog blocks are implemented in this checkpoint.

| Block ID | 中文名 | Category | Subcategory | Simulation level | Notes |
| --- | --- | --- | --- | --- | --- |
| `trigger.manual_test` | 手动测试触发 | 触发事件 | 测试 | `FULLY_SIMULATABLE` | 当前 WebUI/命令测试入口 |
| `condition.state.equals` | 状态等于 | 条件判断 | 状态条件 | `FULLY_SIMULATABLE` | 当前 demo 只比较 BOOLEAN；支持 满足时继续 / 不满足时继续 / 分成两路 |
| `condition.entity.has_tag` | 实体拥有标签 | 玩家与实体 | 标签 | `FULLY_SIMULATABLE` | EntityTargetRef + 模拟实体 tags；支持条件输出形态 |
| `action.message.chat` | 发送聊天消息 | 消息显示 | 聊天 | `APPROXIMATE_SIMULATION` | `message` 已采用 `rich_text_component` MVP；模拟环境记录 plain text，真实 adapter 后转换为 Minecraft Text / tellraw equivalent |
| `state.set` | 设置状态 | 状态数据 | 写入 | `FULLY_SIMULATABLE` | 写入 GLOBAL/PLAYER/SESSION state |
| `state.add` | 累加状态 | 状态数据 | 写入 | `FULLY_SIMULATABLE` | 只用于 INTEGER |
| `timer.wait` | 等待一段时间 | 时间调度 | 等待 | `FULLY_SIMULATABLE` | 当前为 wall-clock spike timer |
| `debug.log` | 写入调试记录 | 调试诊断 | Trace | `FULLY_SIMULATABLE` | 生成调试 trace/log |
| `action.entity.add_tag` | 添加实体标签 | 玩家与实体 | 标签 | `FULLY_SIMULATABLE` | 给显式目标实体写入 tag；不属于条件分类 |

## vNext candidate blocks

### 触发事件

| Block ID | 中文名 | Priority | Simulation level | Notes |
| --- | --- | --- | --- | --- |
| `trigger.manual_test` | 手动测试触发 | P0 | `FULLY_SIMULATABLE` | 保留当前测试入口 |
| `trigger.player_join` | 玩家加入服务器 | P1 | `APPROXIMATE_SIMULATION` | 适合作为第一个真实 Fabric event adapter |
| `trigger.block_interact` | 玩家交互方块 | P2 | `REQUIRES_MINECRAFT_RUNTIME` | 需先查 Fabric/MC 官方事件 |

### 条件判断

| Block ID | 中文名 | Priority | Simulation level | Notes |
| --- | --- | --- | --- | --- |
| `condition.state.equals` | 状态等于 | P0 | `FULLY_SIMULATABLE` | 当前 demo 条件 |
| `condition.entity.has_tag` | 实体拥有标签 | P1 | `FULLY_SIMULATABLE` | 位于 玩家与实体 / 标签；共享目标解析，真实 adapter 使用实体 tag |
| `condition.inventory.has_item` | 背包包含物品 | P2 | `APPROXIMATE_SIMULATION` | 先做抽象 item id + count，不做完整 NBT |

### 消息显示

| Block ID | 中文名 | Priority | Simulation level | Notes |
| --- | --- | --- | --- | --- |
| `action.message.chat` | 发送聊天消息 | P0 | `APPROXIMATE_SIMULATION` | 当前消息动作的 catalog 目标；配置语义是 vanilla text component，不是 `/say` |
| `action.message.title` | 显示标题 | P1 | `APPROXIMATE_SIMULATION` | 模拟 trace 可验证内容，真实 adapter 后显示 |
| `action.message.actionbar` | 显示快捷提示 | P2 | `APPROXIMATE_SIMULATION` | 与 title 类似，先不做 rich text |

### 玩家操作

| Block ID | 中文名 | Priority | Simulation level | Notes |
| --- | --- | --- | --- | --- |
| `action.entity.add_tag` | 给实体添加标签 | P1 | `FULLY_SIMULATABLE` | 位于 玩家与实体 / 标签，便于小游戏阶段标记 |
| `action.entity.remove_tag` | 移除实体标签 | P1 | `FULLY_SIMULATABLE` | 与 add_tag 成对，共享 EntityTargetRef |
| `action.player.teleport` | 传送玩家 | P2 | `REQUIRES_MINECRAFT_RUNTIME` | 需要世界、位置和安全检查 |

### 状态数据

| Block ID | 中文名 | Priority | Simulation level | Notes |
| --- | --- | --- | --- | --- |
| `state.set` | 设置状态 | P0 | `FULLY_SIMULATABLE` | 当前 demo |
| `state.add` | 累加状态 | P0 | `FULLY_SIMULATABLE` | 当前 demo |
| `state.clear` | 清除状态 | P1 | `FULLY_SIMULATABLE` | 需要明确 scope/owner 语义 |

### 时间调度

| Block ID | 中文名 | Priority | Simulation level | Notes |
| --- | --- | --- | --- | --- |
| `timer.wait` | 等待一段时间 | P0 | `FULLY_SIMULATABLE` | 当前 timer 行为 |
| `timer.start_named` | 启动命名计时器 | P2 | `APPROXIMATE_SIMULATION` | 需要 timer id / owner / replace policy |
| `timer.cancel_named` | 取消命名计时器 | P2 | `APPROXIMATE_SIMULATION` | 依赖命名计时器模型 |

### 世界交互

| Block ID | 中文名 | Priority | Simulation level | Notes |
| --- | --- | --- | --- | --- |
| `action.world.play_sound` | 播放声音 | P2 | `APPROXIMATE_SIMULATION` | 模拟 trace，真实 adapter 播放 |
| `action.world.set_block` | 设置方块 | P3 | `UNSAFE_OR_WORLD_MUTATING` | 需权限、区块加载、安全提示 |

### 物品与背包

| Block ID | 中文名 | Priority | Simulation level | Notes |
| --- | --- | --- | --- | --- |
| `condition.inventory.has_item` | 背包包含物品 | P2 | `APPROXIMATE_SIMULATION` | 只做抽象 item id/count |
| `action.player.give_item` | 给予物品 | P2 | `APPROXIMATE_SIMULATION` | 真实 adapter 前只改 simulated inventory |
| `action.player.take_item` | 扣除物品 | P3 | `APPROXIMATE_SIMULATION` | 涉及失败策略，后置 |

### 容器

| Block ID | 中文名 | Priority | Simulation level | Notes |
| --- | --- | --- | --- | --- |
| `condition.container.slot_matches` | 容器槽位匹配 | P3 | `REQUIRES_MINECRAFT_RUNTIME` | 需要真实 container 上下文 |
| `action.container.set_slot` | 设置容器槽位 | P3 | `UNSAFE_OR_WORLD_MUTATING` | 高风险，后置 |

### 区域

| Block ID | 中文名 | Priority | Simulation level | Notes |
| --- | --- | --- | --- | --- |
| `trigger.region.player_entered` | 玩家进入区域 | P3 | `APPROXIMATE_SIMULATION` | Region 仍非 v1 实现目标，仅保留 catalog 设计位置 |
| `trigger.region.player_left` | 玩家离开区域 | P3 | `APPROXIMATE_SIMULATION` | 同上 |

### 调试诊断

| Block ID | 中文名 | Priority | Simulation level | Notes |
| --- | --- | --- | --- | --- |
| `debug.log` | 写入调试记录 | P0 | `FULLY_SIMULATABLE` | 当前 demo |
| `debug.trace_marker` | 标记 Trace 节点 | P1 | `FULLY_SIMULATABLE` | 便于用户定位流程 |

## Do not create as generic blocks

不要创建这些万能积木：

- `generic.action`: 用户拖入后再选择所有动作类型。
- `generic.condition`: 用户拖入后再选择所有条件类型。
- `generic.minecraft_command`: 把命令方块搬进 WebUI。
- `generic.world_mutation`: 一个块修改所有世界对象。
- `raw_json_message`: 让普通用户写 JSON 文本组件。
- `script.run`: 在 v1/vNext 早期引入脚本执行。

当前 rich text MVP 不暴露 raw JSON 编辑路径。它只提供多行文本、结构化存储和预览，为后续轻量 Word-style editor 留出 style runs 扩展位。

如果某个积木需要太多模式切换，应拆成多个具体积木，或先不做。
