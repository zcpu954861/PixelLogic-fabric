# PixelLogic Roadmap

## 当前主线 baseline

当前版本分支是 `mc-1.21.11`。版本开发不从 `main` 或 `master` 开始。

当前 `mc-1.21.11` 主线（以真实 Git HEAD 为准）已包含：

- Direct Edge / Graph 主模型、草稿保存/校验/提交、自动保存与 undo/redo；
- Catalog、Form Schema、Simulation Backend、Condition Output Modes；
- Simulation Test Context、Text Component Editor；
- Container Control Flow 与交互动画；
- 可跨 `timer.wait` 恢复的 Control Flow Continuation；
- Loop Until + Condition Rack；
- Contextual Entity Execution；
- Polling、Runtime/API、Catalog/Validation、拖拽性能与文档治理收口；
- Block Library Taxonomy v1 的积木包 → 一级分类 → 具体积木导航、搜索与 visibility；
- Entity Target Reference v1：四来源复合目标、共享 resolver/错误、在线玩家 UUID 选择、通用实体标签与 execute-as 接入；
- 已合并的 Health and Termination v1：伤害、恢复、设置生命、正常 kill 与禁止玩家的直接 remove；
- 当前 Status Effects and Player Game Mode v1 feature：给予/移除状态效果与设置在线玩家游戏模式；
- 独立 Vanilla TypeScript WebUI 和有界 trace/runtime 安全机制。

这些能力仍以 simulation-first 为主。Fabric adapter 已覆盖 plain chat、精确实体/标签访问、生命/终止、状态效果和玩家游戏模式；位置/区域、屏幕消息等大部分其它积木仍未接入真实服务器行为。

## 当前基础状态

Block Library Taxonomy v1、Entity Target Reference v1 与 Health and Termination v1 已合并主线；当前 feature 工作区实现 Status Effects and Player Game Mode Slice 3。确认：

- 正式 Catalog 当前为 7 个 pack、19 个 category、33 张 built-in block，且全部为 `BROWSE`；
- 当前 `build.gradle` 注册 22 个公开 Java self-check task，`web-ui/checks` 有 12 个 `*SelfCheck.mjs`；
- Simulation 能力不等于真实 Minecraft adapter 已完成；
- Entity Target Reference 已成为生命、状态效果和玩家设置动作的共同前置；
- Player & Entity Foundation v1-A 的 Slice 2 已合并；Slice 3 当前实现仍须通过自动验证与用户手测后才能收口。

Entity Target Reference Slice 1 的实现边界：

- 目标来源固定为当前执行实体、当前条件主体、当前目标实体、指定在线玩家；不存在永久运行起始实体来源；
- Graph 使用一个复合 `target` 对象，不使用松散 sibling 字段或隐式缺省解码；
- Catalog 只保留三张通用实体标签积木，六张退役标签积木及其 NodeType 已删除；
- 退役 blockId、alias、迁移器和兼容 wrapper 均不保留；
- `context.entity.execute_as` 使用同一目标控件与 resolver；
- 指定在线玩家只保存 UUID，名字仅作提示，离线时结构化失败且不按名字回退。

Health and Termination Slice 2 的实现边界：

- 五张动作均复用同一 Entity Target resolver、typed outcome 和 `done` 控制口；
- 伤害类型固定为普通、魔法、火焰、摔落、虚空，真实 Fabric adapter 走 Minecraft 正常 damage API；
- heal 只恢复到最大生命；set-health 允许 0，超过目标最大生命值直接失败且不 clamp；
- kill 走正常死亡流程；remove 只调用直接 discard，并永久拒绝玩家；
- Simulation fixture 增加 living、health、maxHealth、invulnerable、killed/removed 状态，且每次运行隔离复制。

Status Effects and Player Game Mode Slice 3 的实现边界：

- 三张动作继续复用 Entity Target resolver、typed outcome 与普通 `done` 控制口；
- 状态效果采用有界 effect id、秒数、1-based 等级、显示标记与 `VANILLA_UPDATE` / `REPLACE` 封闭策略；
- Simulation 只保存每种效果的当前可见记录，不推进倒计时，也不保留 Minecraft hidden fallback 链；
- 设置游戏模式只接受在线玩家及生存、创造、冒险、旁观四种值；
- GraphValidator 校验状态效果 ID 形状，执行 provider 负责存在性权威校验；Fabric 使用 `Registries.STATUS_EFFECT` 与 server-player game-mode API，Simulation 无 provider 时 fail-closed；
- Test Context API/UI 暂不提供效果或游戏模式输入字段，运行结果通过动作 outcome/消息观察。

设计依据见[基础积木能力矩阵](specs/FOUNDATION_BLOCK_CAPABILITY_MATRIX_V1.md)、[Entity Target Reference v1](specs/ENTITY_TARGET_REFERENCE_V1.md) 与 [Player & Entity Foundation v1-A](specs/PLAYER_ENTITY_FOUNDATION_V1A.md)。

## 近期候选方向

以下是候选方向，不是已承诺功能：

- 后续玩家与实体基础包的六张条件/Predicate Capsule（Slice 4）；
- 在现有 condition rack 中增加经过明确产品确认的 predicate capsule；
- 为真实 Minecraft adapter 建立权限、执行上下文和行为对照前置；
- Status Effects and Player Game Mode 自动验证与用户手测通过后再收口 feature；
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
