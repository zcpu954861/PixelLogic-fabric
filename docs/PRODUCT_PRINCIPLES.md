# Product Principles

PixelLogic is a visual logic flow mod for Minecraft servers.

Users build minigame logic with nodes, cards, and edges. They should not need datapacks, command blocks, scoreboard scripts, or complex JSON.

## Highest Principles

1. 性能为大
   性能不是后期优化，而是架构第一天的设计目标。Runtime、WebUI、大图编辑、状态查询、条件判断、动作执行都不得留下明显全量扫描、O(n²)、主线程阻塞隐患。

2. 维护性为先
   架构清晰、模块规范、前后端分离、API 明确、测试可执行。禁止 Java 字符串生成 WebUI。禁止巨型文件、巨型函数、补丁套补丁。

3. 稳定性至上
   保存、运行、校验、错误处理必须 fail-closed。Runtime 必须可追踪、可限流、可调试、可恢复。宁可第一版功能少，也不能行为不稳定。

4. 人性化看重
   任何设计以好用为第一准则。任何配置以实用为首先目的。内容必须简单、简单、再简单。不必要展示出来的不展示。能快捷操作的就快捷操作。

## Product Model

- Direct Edge / Graph is the primary model.
- Channel is not a normal-user concept.
- Condition is a standalone card/node.
- Action is a typed form, not a scripting language.
- Scratch is an interaction reference only.
- Old TZZ content is not PixelLogic core.
