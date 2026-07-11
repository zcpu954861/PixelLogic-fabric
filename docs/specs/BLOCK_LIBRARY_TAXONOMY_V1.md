# Block Library Taxonomy v1

## 用户目标

积木库使用唯一的三级导航：

```text
积木包 → 一级分类 → 具体积木
```

积木包与分类只负责发现和导航。Graph 仍只保存 `blockId`，Runtime、Simulation、GraphValidator 和拖拽落位规则不读取 taxonomy。

## 数据模型

- `BlockPackDefinition`：`id / displayName / description / icon / order`。
- `BlockCategoryDefinition`：`id / packId / displayName / description / icon / order`；旧 `visibleByDefault` 字段保留为兼容只读值。
- `BlockDefinition.categoryId`：唯一正式归属；pack 通过 category 推导，不在 block 重复保存。
- `BlockDefinition.aliases`：保留既有旧 `blockId` canonicalization 语义；当前只有 `manual.test.start`。
- `BlockDefinition.searchKeywords`：用户搜索词；`manual / delay / repeat / while / until / as` 等不会扩大 Graph `blockId` 信任边界。
- `BlockLibraryVisibility`：`BROWSE / CONTEXT_ONLY / HIDDEN`。
- `BlockCatalog`：正式权威仍是单一 `packs / categories / blocks` Snapshot，沿用 `GET /api/pixellogic/catalog`。
- 既有 wire 字段 `subcategories / subcategoryId / tags / hidden / visibleByDefault` 保留；它们全部从正式 category/visibility 派生，WebUI 不把它们当第二套 taxonomy。

Snapshot 在构造时验证引用、唯一性、可见归属、空分组、可达 context 与搜索元数据，并按 `order + id`（block 按 pack/category order 与稳定 id）排序。新增可见积木但没有正式分类会明确失败，不会落入“其他”。

## 可见性

- `BROWSE`：普通三级浏览可见；兼容槽位中仍需通过 capability filter。
- `CONTEXT_ONLY`：普通浏览隐藏，只能在兼容创建上下文中出现。
- `HIDDEN`：积木库任何模式都不显示。
- `deprecated` 独立于 visibility；已弃用积木不能新建。

当前 28 张 built-in 均为 `BROWSE`。当前没有 context-only 或 hidden built-in；相关语义由 synthetic self-check fixture 固定。

## 初始包与分类

只注册当前确实有积木的 7 个 pack，不预建“物品与容器”等空领域：

1. 事件与触发；
2. 逻辑与流程；
3. 玩家与实体；
4. 位置与区域；
5. 方块与世界；
6. 表现与反馈；
7. 状态与数据。

## 当前积木分类矩阵

| blockId | 中文名 | nodeType | visibility | pack | category | 备注 |
|---|---|---|---|---|---|---|
| `trigger.manual_test` | WebUI 测试运行 | `MANUAL_TRIGGER` | BROWSE | 事件与触发 | 测试入口 | `manual.test.start` 仅为 legacy ID |
| `condition.state.equals` | 判断状态是否等于 | `STATE_COMPARE_CONDITION` | BROWSE | 状态与数据 | 状态条件 | 状态比较，不是 rack predicate |
| `action.message.chat` | 发送聊天消息 | `MESSAGE_ACTION` | BROWSE | 表现与反馈 | 玩家消息 | 旧 `MESSAGE_ACTION` 默认 fallback |
| `action.message.title` | 显示标题 | `MESSAGE_ACTION` | BROWSE | 表现与反馈 | 屏幕提示 | 依靠 blockId 区分共享 NodeType |
| `action.message.subtitle` | 显示副标题 | `MESSAGE_ACTION` | BROWSE | 表现与反馈 | 屏幕提示 | 依靠 blockId 区分共享 NodeType |
| `action.message.actionbar` | 显示快捷栏消息 | `MESSAGE_ACTION` | BROWSE | 表现与反馈 | 屏幕提示 | 依靠 blockId 区分共享 NodeType |
| `condition.player.has_tag` | 玩家是否拥有标签 | `PLAYER_HAS_TAG_CONDITION` | BROWSE | 玩家与实体 | 标签 | `PREDICATE` |
| `condition.player.is_admin` | 玩家是否为管理员 | `PLAYER_IS_ADMIN_CONDITION` | BROWSE | 玩家与实体 | 身份与权限 | `PREDICATE` |
| `condition.player.dimension_is` | 玩家所在维度是否为 | `PLAYER_DIMENSION_CONDITION` | BROWSE | 位置与区域 | 维度与高度 | `PREDICATE` |
| `condition.player.in_region` | 玩家是否在区域内 | `PLAYER_IN_REGION_CONDITION` | BROWSE | 位置与区域 | 区域 | `PREDICATE` |
| `condition.player.y_compare` | 玩家高度是否满足 | `PLAYER_Y_COMPARE_CONDITION` | BROWSE | 位置与区域 | 维度与高度 | 当前不是 rack predicate |
| `condition.target_block.is_type` | 目标方块是否为 | `TARGET_BLOCK_TYPE_CONDITION` | BROWSE | 方块与世界 | 目标方块 | `PREDICATE` |
| `condition.target_block.y_compare` | 目标方块高度是否满足 | `TARGET_BLOCK_Y_COMPARE_CONDITION` | BROWSE | 位置与区域 | 维度与高度 | 当前不是 rack predicate |
| `condition.player.near_target_block` | 玩家是否靠近目标方块 | `PLAYER_NEAR_TARGET_BLOCK_CONDITION` | BROWSE | 位置与区域 | 空间关系 | 当前不是 rack predicate |
| `condition.target_block.in_region` | 目标方块是否在区域内 | `TARGET_BLOCK_IN_REGION_CONDITION` | BROWSE | 位置与区域 | 区域 | 当前不是 rack predicate |
| `action.player.add_tag` | 添加玩家标签 | `PLAYER_ADD_TAG_ACTION` | BROWSE | 玩家与实体 | 标签 | 修改当前玩家标签 |
| `action.player.remove_tag` | 移除玩家标签 | `PLAYER_REMOVE_TAG_ACTION` | BROWSE | 玩家与实体 | 标签 | 修改当前玩家标签 |
| `control.loop.count` | 循环次数 | `CONTROL_LOOP_COUNT` | BROWSE | 逻辑与流程 | 循环 | 固定次数容器 |
| `control.loop.forever` | 无限循环 | `CONTROL_LOOP_FOREVER` | BROWSE | 逻辑与流程 | 循环 | 有模拟安全上限 |
| `control.loop.until` | 循环直到 | `CONTROL_LOOP_UNTIL` | BROWSE | 逻辑与流程 | 循环 | `PREDICATE_RACK` 宿主 |
| `context.entity.execute_as` | 以实体为上下文执行 | `CONTEXT_ENTITY_EXECUTE_AS` | BROWSE | 玩家与实体 | 实体上下文 | C 型执行上下文 |
| `condition.context_entity.has_tag` | 上下文实体是否拥有标签 | `CONTEXT_ENTITY_HAS_TAG_CONDITION` | BROWSE | 玩家与实体 | 标签 | `PREDICATE` |
| `action.context_entity.add_tag` | 为上下文实体添加标签 | `CONTEXT_ENTITY_ADD_TAG_ACTION` | BROWSE | 玩家与实体 | 标签 | 修改当前实体上下文 |
| `action.context_entity.remove_tag` | 移除上下文实体标签 | `CONTEXT_ENTITY_REMOVE_TAG_ACTION` | BROWSE | 玩家与实体 | 标签 | 修改当前实体上下文 |
| `state.set` | 设置状态 | `STATE_SET_ACTION` | BROWSE | 状态与数据 | 状态写入 | typed state 写入 |
| `state.add` | 累加状态 | `STATE_ADD_ACTION` | BROWSE | 状态与数据 | 状态写入 | checked INTEGER 累加 |
| `timer.wait` | 等待一段时间 | `TIMER_START_ACTION` | BROWSE | 逻辑与流程 | 等待与时序 | continuation 等待 |
| `debug.log` | 调试记录 | `DEBUG_LOG_ACTION` | BROWSE | 表现与反馈 | 调试诊断 | 只写模拟 trace |

未注册进 Catalog 的 `COMMAND_TRIGGER` 不是 hidden block；它继续保持无 built-in fallback 的 fail-closed 边界。

## 搜索

前端按 Catalog Snapshot 身份缓存只读索引：`packById / categoryById / categoriesByPackId / blocksByCategoryId / searchDocumentByBlockId`。搜索文档只在 Snapshot 变化时重建；输入时只替换结果区域，保留搜索 input、IME 与原生文本撤销历史。

搜索覆盖名称、说明、blockId、旧 aliases、searchKeywords、category 和 pack。输入会 trim、英文小写化、折叠连续空白；多个词必须全部命中同一文档。排序为：完整名称或 alias、名称前缀、普通包含，随后按正式 pack/category 顺序与 blockId。

搜索始终跨 pack；结果显示完整正式路径。query 不修改 browse location，因此清空后自然恢复。

## 槽位兼容过滤

正式分类回答“它属于哪里”，capability/slot contract 回答“现在能不能放”。最终集合为：

```text
visibility ∩ capability filter ∩ search query
```

点击空 Condition Rack 槽位进入 `PREDICATE` 过滤，退出后恢复进入前的位置。筛选状态记录本次触发槽位，使键盘 Enter/Space 创建也走现有 rack placement；鼠标拖拽仍按实际落点判定。过滤只影响左侧发现与计数；拖拽仍只传 `blockId`，命中和后端保存仍由现有 Condition Rack contract 与 GraphValidator 决定。

## 不变边界

- Graph JSON 与 `node.blockId` 不变；不保存 pack/category/visibility/search metadata。
- Runtime、Simulation、Continuation、Entity Execution Context 不读取 taxonomy。
- 不修改 drag candidate、preview cache、container geometry、autosave、history 或 polling。
- API endpoint 不变，只扩展同一 Snapshot。

## v1 不支持

- 收藏、最近使用、推荐和服务端个人偏好；
- 第三方/可下载积木包、市场或动态卸载；
- fuzzy、拼音搜索和搜索历史；
- 虚拟列表与移动端专项布局；
- Help Center 全面重构。

后续 `Library Productivity v1` 可在本 Snapshot 与只读索引上增加收藏/最近使用；第三方 pack 仍需独立的授权、兼容和生命周期设计。
