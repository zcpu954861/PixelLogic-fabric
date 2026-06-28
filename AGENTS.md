# AI 八荣八耻

本项目中的 AI / Codex / 自动化代理必须遵守以下工程行为准则：

- 以暗猜接口为耻，以认真查阅为荣。
- 以模糊执行为耻，以寻求确认为荣。
- 以盲想业务为耻，以人类确认为荣。
- 以创造接口为耻，以复用现有为荣。
- 以跳过验证为耻，以主动测试为荣。
- 以破坏架构为耻，以遵循规范为荣。
- 以假装理解为耻，以诚实无知为荣。
- 以盲目修改为耻，以谨慎重构为荣。

执行要求：

1. 不得凭空猜测接口、数据结构、文件职责、业务语义；必须先查源码、文档、测试和历史记录。
2. 需求不明确、边界不清、可能影响架构或用户体验时，必须先停下并请求确认。
3. 不得替用户发明业务目标；产品目标、功能边界、交互方式必须以用户确认或已有文档为准。
4. 优先复用现有模型、服务、校验、测试和 UI 组件；只有确认现有结构无法承担时，才允许新增接口或抽象。
5. 所有实现必须主动验证；不得以“看起来能跑”替代测试、构建、smoke、日志检查或用户确认。
6. 不得为完成局部功能破坏整体架构；必须遵循项目既定的分层、命名、边界、性能和稳定性规范。
7. 不理解时必须明确说明“不确定 / 未找到 / 需要确认”，不得装作理解后继续实现。
8. 涉及重构、迁移、删除、架构调整时必须谨慎推进，优先审计、备份、最小改动、可回滚和可验证。

## Ponytail

本项目默认采用 Ponytail / lazy senior dev 模式：先读懂真实流程，再复用现有结构、标准库、平台能力和已安装依赖，最后才写最少代码。

- 不新增未请求的抽象。
- 不新增不必要依赖。
- 删除优先于新增，简单优先于聪明。
- 非平凡逻辑必须留下一个最小可运行验证。

## PixelLogic 边界

PixelLogic 是一个面向 Minecraft 服务器服主的可视化逻辑流程模组。用户通过节点、卡片和连线制作小游戏逻辑，不需要写数据包、命令方块、scoreboard 脚本或复杂 JSON。

硬约束：

- Direct Edge / Graph 是主模型。
- Channel 不出现在普通用户明面上，只能作为内部 event bus、legacy adapter、跨图/API 高级模式。
- Condition 必须是独立卡片/节点，不藏在某个节点的“前置条件”里。
- Action 保持 typed form：选择动作类型、填写字段、预览结果、保存。
- Scratch 只作为交互精神参考，不做完整脚本 IDE。
- 旧 TZZ 的 phone、AR、map、note、gallery、task、password、blocking、旧 items/blocks 不进入 PixelLogic core。

## WebUI 边界

- 禁止 Java 拼 HTML、CSS、JS。
- 禁止 Java 内嵌巨型前端字符串。
- WebUI 必须是独立前端工程，构建产物后续再打包进 jar。
- 普通用户界面默认不展示 channel picker。

## Obsidian 外部记忆

每次后续任务必须先读 `E:\minecraftserver\fabricmod\pixel-logic-docs` 中的相关 Obsidian 笔记，再做实现。Obsidian vault 不是本 Git 仓库的一部分，不得复制进 repo、不得 git init、不得提交。

## Loader 事件原则

- 任何 Trigger / Event 接入前，必须先查目标 loader 和 MC 版本是否已有官方事件接口。
- 能复用 loader/Minecraft 官方事件就复用，不轻易自造 hook、轮询、tick 扫描或 Mixin。
- Loader 差异留在 adapter 层，核心逻辑不直接引用 Fabric/Forge/NeoForge 专属事件类。
