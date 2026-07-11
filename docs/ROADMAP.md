# PixelLogic Roadmap

## 当前主线 baseline

当前版本分支是 `mc-1.21.11`。版本开发不从 `main` 或 `master` 开始。

主线 HEAD `52adac1` 已包含：

- Direct Edge / Graph 主模型、草稿保存/校验/提交、自动保存与 undo/redo；
- Catalog、Form Schema、Simulation Backend、Condition Output Modes；
- Simulation Test Context、Text Component Editor；
- Container Control Flow 与交互动画；
- 可跨 `timer.wait` 恢复的 Control Flow Continuation；
- Loop Until + Condition Rack；
- Contextual Entity Execution；
- Polling、Runtime/API、Catalog/Validation、拖拽性能与文档治理收口；
- 独立 Vanilla TypeScript WebUI 和有界 trace/runtime 安全机制。

这些能力仍以 simulation-first 为边界；真实 Minecraft adapter 尚未接入。

## 当前产品分支

`feature/block-library-taxonomy-v1` 基于当前主线，将左侧积木库升级为：

- 积木包 → 一级分类 → 具体积木；
- 跨包搜索与正式路径；
- visibility 与 capability 槽位过滤的交集；
- 不改变 Graph、Runtime、Simulation 或拖拽 payload。

只注册当前已有积木的领域；未来新增物品/容器等能力时再增加对应 pack，不预建空目录。收藏、最近使用和第三方包留给后续独立阶段。

## 近期候选方向

以下是候选方向，不是已承诺功能：

- 基于官方 loader/Minecraft 事件的真实实体检测和事件上下文；
- 在现有 condition rack 中增加经过明确产品确认的 predicate capsule；
- 为真实 Minecraft adapter 建立权限、执行上下文和行为对照前置；
- 评估执行位置上下文，当前仍暂缓实现。

新增方向继续复用 Catalog、GraphValidator、SimulationExecutionRegistry、Direct Edge runtime 和现有 WebUI editor，不另建平行模型。

## 真实 Minecraft 接入前置条件

- 先确认目标 loader 与 Minecraft 版本是否已有官方事件；
- loader 差异留在 adapter 层，core/simulation 不引用 Minecraft 类；
- Catalog block 的 simulation 与真实执行语义必须有明确对照；
- 服务端继续权威校验权限、Graph、上下文和执行结果；
- 在真实服务器 smoke 前保留 simulation self-check 和 fail-closed 路径。

## 明确暂缓

- If/Else Container、OR/逻辑条件组、break/continue、for-each；
- 完整事件总线或对普通用户暴露 Channel；
- Admin Client Bridge / Authorized Tool Session 的实现；
- Region 旧系统、旧 TZZ adapter、多 loader 同时接入；
- 完整专业脚本 IDE、大规模框架重写、Canvas/WebGL 全量迁移；
- named simulation scenario、持久化测试上下文和完整 Minecraft 世界模拟。

## 历史来源

- 当前行为和边界：`docs/specs/`、`docs/api/`、仍保留的阶段文档与架构 audit；
- 具体实现：对应提交和分支的 Git 历史；
- 已完成 checkpoint 的命令输出和 merge readiness 记录：Git 历史，不再在 ROADMAP 中重复维护。
