# PixelLogic Roadmap

## 当前主线 baseline

当前版本分支是 `mc-1.21.11`。版本开发不从 `main` 或 `master` 开始。

主线 HEAD `ab0bf4d` 已包含：

- Direct Edge / Graph 主模型、草稿保存/校验/提交、自动保存与 undo/redo；
- Catalog、Form Schema、Simulation Backend、Condition Output Modes；
- Simulation Test Context、Text Component Editor；
- Container Control Flow 与交互动画；
- 可跨 `timer.wait` 恢复的 Control Flow Continuation；
- Loop Until + Condition Rack；
- Contextual Entity Execution；
- Polling、Runtime/API、Catalog/Validation、拖拽性能与文档治理收口；
- Block Library Taxonomy v1 的积木包 → 一级分类 → 具体积木导航、搜索与 visibility；
- 独立 Vanilla TypeScript WebUI 和有界 trace/runtime 安全机制。

这些能力仍以 simulation-first 为主；目前只有 plain chat 等少量真实 Minecraft adapter，大部分积木尚未接入真实服务器行为。

## 当前设计候选

Block Library Taxonomy v1 已独立合并主线。当前基础积木能力盘点确认：

- 正式 Catalog 当前为 7 个 pack、15 个 category、28 张 built-in block；
- Simulation 能力不等于真实 Minecraft adapter 已完成；
- Entity Target Reference 是生命、状态效果和玩家设置动作的共同前置；
- Player & Entity Foundation v1-A 是下一实现候选，先评审设计，再从 Slice 1 开始实现。

设计依据见[基础积木能力矩阵](specs/FOUNDATION_BLOCK_CAPABILITY_MATRIX_V1.md)、[Entity Target Reference v1](specs/ENTITY_TARGET_REFERENCE_V1.md) 与 [Player & Entity Foundation v1-A](specs/PLAYER_ENTITY_FOUNDATION_V1A.md)。

## 近期候选方向

以下是候选方向，不是已承诺功能：

- Entity Target Reference v1 的单实体解析、类型约束、结构化错误与 Simulation/Runtime 对照；
- 玩家与实体基础包的生命动作、状态效果、玩家设置和条件胶囊；
- 在现有 condition rack 中增加经过明确产品确认的 predicate capsule；
- 为真实 Minecraft adapter 建立权限、执行上下文和行为对照前置；
- Position Reference、传送和执行位置上下文留给 v1-B 或更晚阶段。

新增方向继续复用 Catalog、GraphValidator、SimulationExecutionRegistry、Direct Edge runtime 和现有 WebUI editor，不另建平行模型。

## 真实 Minecraft 接入前置条件

- 先确认目标 loader 与 Minecraft 版本是否已有官方事件；
- loader 差异留在 adapter 层，core/simulation 不引用 Minecraft 类；
- Catalog block 的 simulation 与真实执行语义必须有明确对照；
- 服务端继续权威校验权限、Graph、上下文和执行结果；
- 在真实服务器 smoke 前保留 simulation self-check 和 fail-closed 路径。

## 明确暂缓

- If/Else Container、OR/逻辑条件组、break/continue、for-each；
- 后台检测器、每 tick 监控、万能 execute-like 控制器、selector/多实体扫描；
- 完整事件总线或对普通用户暴露 Channel；
- Admin Client Bridge / Authorized Tool Session 的实现；
- Region 旧系统、旧 TZZ adapter、多 loader 同时接入；
- 完整专业脚本 IDE、大规模框架重写、Canvas/WebGL 全量迁移；
- named simulation scenario、持久化测试上下文和完整 Minecraft 世界模拟。

## 历史来源

- 当前行为和边界：`docs/specs/`、`docs/api/`、仍保留的阶段文档与架构 audit；
- 具体实现：对应提交和分支的 Git 历史；
- 已完成 checkpoint 的命令输出和 merge readiness 记录：Git 历史，不再在 ROADMAP 中重复维护。
