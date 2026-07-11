# PixelLogic Roadmap

## 当前主线 baseline

当前版本分支是 `mc-1.21.11`。版本开发不从 `main` 或 `master` 开始。

主线 HEAD `e78b190` 已包含：

- Direct Edge / Graph 主模型、草稿保存/校验/提交、自动保存与 undo/redo；
- Catalog、Form Schema、Simulation Backend、Condition Output Modes；
- Simulation Test Context、Text Component Editor；
- Container Control Flow 与交互动画；
- 可跨 `timer.wait` 恢复的 Control Flow Continuation；
- Loop Until + Condition Rack；
- Contextual Entity Execution；
- 独立 Vanilla TypeScript WebUI 和有界 trace/runtime 安全机制。

这些能力仍以 simulation-first 为边界；真实 Minecraft adapter 尚未接入。

## 当前治理堆叠

治理分支从 `mc-1.21.11` 的 Contextual Entity Execution baseline 向前堆叠，尚未合并主线：

1. Polling Interaction Safety Hotfix；
2. Runtime / API Safety Hardening；
3. Cleanup A — 低风险删除与构建收缩；
4. UI Polish v1；
5. Drag Performance v1；
6. Catalog / Validation Cleanup v1；
7. Cleanup E — 文档与 Self-check 收缩（当前阶段）。

本轮治理完成后先做总收口，再决定是否合入 `mc-1.21.11`；当前不发布、不 tag。

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
