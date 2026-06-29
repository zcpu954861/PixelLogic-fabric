# PixelLogic v1 Minimal Vertical Spike Plan

This plan defines the first end-to-end proof for PixelLogic v1. The initial runtime spike is implemented on `feature/v1-manual-simulation-spike`; WebUI integration remains deferred.

## Goal

Prove that the no-Channel Graph Runtime can execute one useful flow designed through the slot-based horizontal block UI.

The spike validates the product and architecture, not feature breadth.

## Target Flow

```text
Manual/WebUI test run or command trigger: /pixellogic test start
-> Condition: PLAYER.started == false
   pass:
     -> Message Action: welcome start message
     -> State Set Action: PLAYER.started = true
     -> State Add Action: PLAYER.start_count += 1
     -> Timer Start Action: 30 seconds
     -> Timer completed continuation
     -> Debug Log Action: countdown finished
   fail:
     -> Debug Log Action: player already started
```

Chinese user-facing summary:

```text
手动/命令触发：/pixellogic test start 或 WebUI 测试运行
-> 条件判断：PLAYER.started == false
   通过：
     -> 发送消息：欢迎开始游戏
     -> 写入状态：PLAYER.started = true
     -> 累加状态：PLAYER.start_count += 1
     -> 启动计时器：30 秒
     -> 计时器完成后写入调试记录：倒计时结束
   失败：
     -> 写入调试记录：玩家已经开始过游戏
```

## What The Spike Must Prove

1. No Channel execution model works.
2. Direct Typed Edge traversal is enough for v1.
3. Condition works as an independent card/block.
4. Slot-Based horizontal block flow can express pass/fail branches.
5. State scopes GLOBAL / PLAYER / SESSION are enough for the v1 core path.
6. Timer continuation can cooperate with graph runtime.
7. ExecutionTrace can explain each step.
8. Validation / manual run loop is coherent.
9. Full save / simulate / run loop remains a later v1 checkpoint.

## Explicit Non-Goals

The spike does not do:

- Region.
- Old TZZ adapter.
- Multi-loader implementation.
- Forge.
- NeoForge.
- Complex rich text.
- Real large graph editor.
- Script language.
- Scoreboard bridge.
- Complex inventory, entity, item, or block state scopes.
- Bossbar lifecycle.
- Teleport, particle, effect, or sound expansion.

## Spike Graph Shape

Required nodes:

```text
N1 Command Trigger
N2 Manual/Test Trigger
N3 State Compare Condition
N4 Message Action
N5 State Set Action
N6 State Add Action
N7 Timer Start Action
N8 Debug Log Action: countdown finished
N9 Debug Log Action: player already started
```

Required slots:

```text
Trigger:
- output: started

Condition:
- input
- output: pass
- output: fail

Action:
- input
- output: done
- output: error, folded/advanced

Timer Start:
- input
- output: scheduled/done
- continuation target: timer completed
```

Required edges:

```text
N1.started -> N3.input
N2.started -> N3.input
N3.pass -> N4.input
N4.done -> N5.input
N5.done -> N6.input
N6.done -> N7.input
N7.timerCompleted -> N8.input
N3.fail -> N9.input
```

Final edge names may change during implementation, but the model must remain Typed Edge, not Channel.

## Runtime Walkthrough

### First run

Initial state:

```text
PLAYER.started = false or missing treated as false by explicit condition config
```

Expected execution:

1. Trigger `/pixellogic test start`.
2. Runtime creates ExecutionContext.
3. Runtime resolves graph entry.
4. Condition reads PLAYER.started.
5. Condition selects pass.
6. Message Action sends welcome message.
7. State Set Action writes PLAYER.started = true.
8. State Add Action increments PLAYER.start_count.
9. Timer Start Action registers 30 second continuation.
10. Timer completion resumes graph.
11. Debug Log Action writes countdown finished.
12. Trace records each step.

### Second run

Initial state:

```text
PLAYER.started = true
```

Expected execution:

1. Trigger `/pixellogic test start`.
2. Condition reads PLAYER.started.
3. Condition selects fail.
4. Debug Log Action writes player already started.
5. Trace explains the fail branch.

## Validation Requirements

Before commit or execution, validation must catch:

- missing trigger
- missing condition input
- missing pass branch
- missing fail branch
- state key without scope
- state type mismatch
- invalid timer duration
- unknown action type
- unknown condition type
- edge direction mismatch
- edge type mismatch
- loop risk or step budget risk when obvious

Validation messages must be user-readable in Chinese.

## Trace Requirements

The trace should show:

- trigger source
- condition expression and result
- selected branch
- message action result
- state write result
- timer scheduled result
- timer completed continuation
- debug log result
- errors if any

Example trace:

```text
[12:00:01] 命令触发：/pixellogic test start
[12:00:01] 条件通过：PLAYER.started == false
[12:00:01] 发送消息：欢迎开始游戏
[12:00:01] 状态写入：PLAYER.started = true
[12:00:01] 状态累加：PLAYER.start_count = 1
[12:00:01] 计时器启动：30 秒
[12:00:31] 计时器完成：继续执行
[12:00:31] 调试记录：倒计时结束
```

## WebUI Proof Requirements

The spike UI must be able to express:

- command/manual trigger card
- condition card with pass/fail branch structure
- message action card
- state set action card
- timer card
- debug log card
- validation issue list
- execution trace list
- selected card properties in right panel

Normal UI must not show Channel.

## Performance Requirements For Spike

Even the spike should keep these shapes:

- trigger entry lookup is indexed
- compiled graph is reused for execution
- outgoing edge lookup is by node and slot
- state lookup is scoped by key
- timer completion is due-time based
- max steps exists
- per execution trace is bounded

The first implementation can be small, but it must not require graph-wide scans as the main runtime design.

## Done Criteria

The spike is successful when:

- one in-memory demo graph can be represented as GraphDefinition
- graph validation can pass/fail with readable issues
- first run takes the pass branch
- second run takes the fail branch
- timer continuation runs after delay
- trace explains both runs
- no Channel core type is required
- no Region feature is required
- no old TZZ code is required

Full graph save/load, draft commit, and API-backed simulation are not part of this manual checkpoint.

## Implementation Checkpoint

Current spike implementation:

- Command root is `/pixellogic` only.
- Available commands: `/pixellogic status`, `/pixellogic test start`, `/pixellogic test reset`, `/pixellogic trace last`.
- Manual trigger is command-only for now; WebUI/API test-run integration is deferred.
- Demo graph is in-memory and built as `GraphDefinition`, then validated and compiled before execution.
- Runtime follows `triggerType -> entry` and `nodeId + slotId -> outgoing edges`; it does not use Channel routing.
- Timer is in-memory wall-clock scheduling with server-thread handoff before runtime continuation.
- State store and trace buffer are in-memory and bounded for this spike.

## Next After Spike

After this spike is confirmed:

1. Review the manual simulation spike in Minecraft.
2. Decide the persisted Project / Graph JSON schema.
3. Decide whether WebUI gets a mock-only or real API-backed test-run button next.
4. Add persistent graph save/load only after the in-memory runtime shape is accepted.
