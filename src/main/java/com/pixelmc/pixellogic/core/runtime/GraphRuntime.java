package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.runtime.ExecutionCursor.LoopFrame;
import com.pixelmc.pixellogic.core.runtime.ExecutionCursor.LoopKind;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

public final class GraphRuntime {
    private static final int FOREVER_SIMULATION_ITERATION_CAP = 20;
    private static final int MAX_PENDING_CONTINUATIONS = 128;

    private final CompiledGraph graph;
    private final BoundedTraceBuffer traces;
    private final RuntimeServices services;
    private final RuntimeNodeExecutor nodeExecutor;
    private final RuntimeLimits limits;
    private final long generation;
    private final Set<String> pendingContinuationIds = new HashSet<>();
    private volatile boolean cancelled;

    public GraphRuntime(
            CompiledGraph graph,
            InMemoryStateStore stateStore,
            BoundedTraceBuffer traces,
            RuntimeServices services,
            RuntimeLimits limits
    ) {
        this(graph, stateStore, traces, services, limits, 0L);
    }

    public GraphRuntime(
            CompiledGraph graph,
            InMemoryStateStore stateStore,
            BoundedTraceBuffer traces,
            RuntimeServices services,
            RuntimeLimits limits,
            long generation
    ) {
        this.graph = graph;
        this.traces = traces;
        this.services = services;
        this.nodeExecutor = new RuntimeNodeExecutor(stateStore, traces, services);
        this.limits = limits;
        this.generation = generation;
    }

    public RuntimeResult start(TriggerEvent event) {
        String traceId = UUID.randomUUID().toString();
        traces.startTrace(traceId);
        traces.add(traceId, "trigger", "手动触发：" + event.commandText());
        if (cancelled) {
            traces.add(traceId, "trigger", "执行失败：运行实例已取消。");
            return new RuntimeResult(false, traceId, "运行实例已取消。");
        }

        Optional<NodeDefinition> entry = graph.entryForTrigger(event.triggerType());
        if (entry.isEmpty()) {
            traces.add(traceId, "trigger", "执行失败：找不到触发入口。");
            return new RuntimeResult(false, traceId, "找不到触发入口。");
        }

        return runExecution(new ExecutionCursor(
                traceId,
                traceId,
                event.playerId(),
                event.sessionId(),
                0,
                entry.get().id(),
                List.of()
        ));
    }

    public RuntimeResult resumeTimer(TimerContinuation continuation) {
        if (continuation.generation() != generation) {
            traceIgnored(continuation, "generation 已变化");
            return new RuntimeResult(false, continuation.traceId(), "计时器已失效。");
        }
        if (cancelled) {
            traceIgnored(continuation, "运行已取消");
            return new RuntimeResult(false, continuation.traceId(), "运行已取消。");
        }
        if (continuation.continuationId().isBlank() || !consumeContinuation(continuation.continuationId())) {
            traceIgnored(continuation, "continuation 已取消或消费");
            return new RuntimeResult(false, continuation.traceId(), "continuation 已取消或消费。");
        }
        if (!graph.graphId().equals(continuation.graphId())) {
            traceIgnored(continuation, "graph 已变化");
            return new RuntimeResult(false, continuation.traceId(), "计时器所属 Graph 已变化。");
        }
        if (continuation.depth() > limits.maxContinuationDepth()) {
            traces.add(continuation.traceId(), continuation.sourceNodeId(), "执行失败：计时器 continuation 深度超限。");
            return new RuntimeResult(false, continuation.traceId(), "计时器 continuation 深度超限。");
        }

        ExecutionCursor cursor = continuation.cursor();
        String cursorError = validateCursor(continuation, cursor);
        if (cursorError != null) {
            traces.add(continuation.traceId(), continuation.sourceNodeId(), "执行失败：" + cursorError);
            return new RuntimeResult(false, continuation.traceId(), cursorError);
        }

        if (continuation.reason() == TimerContinuation.Reason.LOOP_INTERVAL) {
            LoopFrame frame = cursor.loopFrames().getLast();
            traces.add(cursor.traceId(), frame.containerNodeId(), "第 " + frame.iteration() + " 轮开始。");
            traces.add(cursor.traceId(), continuation.sourceNodeId(), "循环间隔结束：继续下一轮。");
        } else {
            traces.add(cursor.traceId(), continuation.sourceNodeId(), "计时器完成：恢复等待后的执行。");
        }
        return runExecution(cursor);
    }

    public void cancelPendingContinuations() {
        cancelled = true;
        synchronized (pendingContinuationIds) {
            pendingContinuationIds.clear();
        }
    }

    int pendingContinuations() {
        synchronized (pendingContinuationIds) {
            return pendingContinuationIds.size();
        }
    }

    private RuntimeResult runExecution(ExecutionCursor initial) {
        String runId = initial.runId();
        String traceId = initial.traceId();
        int steps = initial.steps();
        String nodeId = initial.nodeId();
        ArrayList<LoopFrame> frames = new ArrayList<>(initial.loopFrames());
        ExecutionContext context = new ExecutionContext(traceId, initial.playerId(), initial.sessionId());

        int transitionBudget = Math.max(16, (limits.maxStepsPerExecution() + 1) * 4);
        for (int transition = 0; transition < transitionBudget; transition += 1) {
            if (cancelled) {
                traces.add(traceId, nodeId, "执行停止：运行已取消。");
                return new RuntimeResult(false, traceId, "运行已取消。");
            }
            if (nodeId.isBlank()) {
                if (frames.isEmpty()) {
                    return new RuntimeResult(true, traceId, "执行完成。");
                }

                LoopFrame frame = frames.getLast();
                if (frame.kind() == LoopKind.COUNT) {
                    traces.add(traceId, frame.containerNodeId(), "第 " + frame.iteration() + " 次循环结束。");
                    if (frame.iteration() < frame.iterationLimit()) {
                        LoopFrame nextFrame = nextIteration(frame);
                        frames.set(frames.size() - 1, nextFrame);
                        traces.add(traceId, frame.containerNodeId(), "第 " + nextFrame.iteration() + " 次循环开始。");
                        nodeId = nextFrame.bodyEntryNodeId();
                    } else {
                        traces.add(traceId, frame.containerNodeId(), "循环完成，继续外部链。");
                        frames.removeLast();
                        nodeId = nodeWithinCurrentBody(frame.completionNodeId(), frames);
                    }
                    continue;
                }

                traces.add(traceId, frame.containerNodeId(), "第 " + frame.iteration() + " 轮结束。");
                if (frame.iteration() >= FOREVER_SIMULATION_ITERATION_CAP) {
                    traces.add(traceId, frame.containerNodeId(), "已达到测试模拟循环上限，已停止继续模拟。");
                    return new RuntimeResult(true, traceId, "执行完成。");
                }
                LoopFrame nextFrame = nextIteration(frame);
                frames.set(frames.size() - 1, nextFrame);
                traces.add(traceId, frame.containerNodeId(), "等待 " + frame.intervalSeconds() + " 秒后进入下一轮。");
                try {
                    return suspend(
                            runId,
                            traceId,
                            context,
                            steps,
                            nextFrame.bodyEntryNodeId(),
                            frames,
                            frame.containerNodeId(),
                            Duration.ofSeconds(frame.intervalSeconds()),
                            TimerContinuation.Reason.LOOP_INTERVAL
                    );
                } catch (RuntimeException exception) {
                    return fail(traceId, frame.containerNodeId(), exception, "循环间隔调度失败。");
                }
            }

            Optional<NodeDefinition> current = graph.node(nodeId);
            if (current.isEmpty()) {
                traces.add(traceId, nodeId, "执行失败：恢复目标节点不存在。");
                return new RuntimeResult(false, traceId, "恢复目标节点不存在。");
            }
            NodeDefinition node = current.get();
            steps += 1;
            if (steps > limits.maxStepsPerExecution()) {
                traces.add(traceId, node.id(), "执行失败：超过最大执行步数。");
                return new RuntimeResult(false, traceId, "超过最大执行步数。");
            }

            if (node.type() == NodeType.CONTROL_LOOP_COUNT) {
                try {
                    int count = parsePositiveInt(node.config().getOrDefault("count", "3"), "循环次数");
                    Optional<NodeDefinition> bodyEntry = graph.bodyEntry(node.id(), "body");
                    traces.add(traceId, node.id(), "进入循环次数：共 " + count + " 次。");
                    if (bodyEntry.isEmpty()) {
                        traces.add(traceId, node.id(), "循环内部为空，直接继续外部流程。");
                        nodeId = nodeWithinCurrentBody(targetId(node.id(), "done"), frames);
                        continue;
                    }
                    LoopFrame frame = new LoopFrame(
                            node.id(),
                            LoopKind.COUNT,
                            1,
                            count,
                            bodyEntry.get().id(),
                            targetId(node.id(), "done"),
                            0
                    );
                    frames.add(frame);
                    traces.add(traceId, node.id(), "第 1 次循环开始。");
                    nodeId = frame.bodyEntryNodeId();
                    continue;
                } catch (RuntimeException exception) {
                    return fail(traceId, node.id(), exception, "循环执行失败。");
                }
            }

            if (node.type() == NodeType.CONTROL_LOOP_FOREVER) {
                try {
                    int intervalSeconds = parsePositiveInt(node.config().getOrDefault("intervalSeconds", "1"), "每轮间隔");
                    Optional<NodeDefinition> bodyEntry = graph.bodyEntry(node.id(), "body");
                    traces.add(traceId, node.id(), "进入无限循环：每轮间隔 " + intervalSeconds + " 秒。");
                    if (bodyEntry.isEmpty()) {
                        traces.add(traceId, node.id(), "无限循环内部为空，已停止模拟。");
                        return new RuntimeResult(true, traceId, "执行完成。");
                    }
                    LoopFrame frame = new LoopFrame(
                            node.id(),
                            LoopKind.FOREVER,
                            1,
                            FOREVER_SIMULATION_ITERATION_CAP,
                            bodyEntry.get().id(),
                            "",
                            intervalSeconds
                    );
                    frames.add(frame);
                    traces.add(traceId, node.id(), "第 1 轮开始。");
                    nodeId = frame.bodyEntryNodeId();
                    continue;
                } catch (RuntimeException exception) {
                    return fail(traceId, node.id(), exception, "无限循环执行失败。");
                }
            }

            if (node.type() == NodeType.TIMER_START_ACTION) {
                try {
                    int seconds = parsePositiveInt(node.config().getOrDefault("durationSeconds", "30"), "等待时间");
                    Optional<NodeDefinition> target = graph.firstTarget(node.id(), "timer_completed");
                    if (target.isEmpty() && frames.isEmpty()) {
                        throw new IllegalStateException("计时器缺少完成后的目标。");
                    }
                    String resumeNodeId = nodeWithinCurrentBody(target.map(NodeDefinition::id).orElse(""), frames);
                    traces.add(traceId, node.id(), "计时器启动：" + seconds + " 秒");
                    services.recordActionResult(node.id(), "timer", "计时器启动：" + seconds + " 秒");
                    return suspend(
                            runId,
                            traceId,
                            context,
                            steps,
                            resumeNodeId,
                            frames,
                            node.id(),
                            Duration.ofSeconds(seconds),
                            TimerContinuation.Reason.DELAY
                    );
                } catch (RuntimeException exception) {
                    return fail(traceId, node.id(), exception, "计时器调度失败。");
                }
            }

            String outputSlot;
            try {
                outputSlot = nodeExecutor.execute(node, context);
            } catch (RuntimeException exception) {
                return fail(traceId, node.id(), exception, "运行时错误。");
            }
            if (outputSlot == null) {
                nodeId = "";
                continue;
            }

            try {
                Optional<NodeDefinition> next = graph.firstTarget(node.id(), outputSlot);
                if (next.isEmpty() && frames.isEmpty()) {
                    traces.add(traceId, node.id(), "未连接后续积木，流程在此结束。");
                }
                nodeId = nodeWithinCurrentBody(next.map(NodeDefinition::id).orElse(""), frames);
            } catch (RuntimeException exception) {
                return fail(traceId, node.id(), exception, "连接解析失败。");
            }
        }
        traces.add(traceId, nodeId, "执行失败：超过内部控制流转换预算。");
        return new RuntimeResult(false, traceId, "超过内部控制流转换预算。");
    }

    private RuntimeResult suspend(
            String runId,
            String traceId,
            ExecutionContext context,
            int steps,
            String resumeNodeId,
            List<LoopFrame> frames,
            String sourceNodeId,
            Duration delay,
            TimerContinuation.Reason reason
    ) {
        int depth = frames.size() + 1;
        if (depth > limits.maxContinuationDepth()) {
            throw new IllegalStateException("计时器 continuation 深度超限。");
        }

        String continuationId = UUID.randomUUID().toString();
        ExecutionCursor cursor = new ExecutionCursor(
                runId,
                traceId,
                context.playerId(),
                context.sessionId(),
                steps,
                resumeNodeId,
                frames
        );
        TimerContinuation continuation = new TimerContinuation(
                graph.graphId(),
                resumeNodeId,
                traceId,
                cursor.playerId(),
                cursor.sessionId(),
                depth,
                generation,
                continuationId,
                sourceNodeId,
                reason,
                cursor
        );
        if (!registerContinuation(continuationId)) {
            throw new RejectedExecutionException("待恢复执行过多，请稍后再试。");
        }
        try {
            services.scheduleTimer(delay, continuation);
            services.recordTimerScheduled(sourceNodeId, delay, continuation);
        } catch (RuntimeException exception) {
            consumeContinuation(continuationId);
            throw exception;
        }
        traces.add(traceId, sourceNodeId, "等待已调度，执行暂停。");
        return new RuntimeResult(true, traceId, "执行已暂停，等待计时器。", true);
    }

    private String validateCursor(TimerContinuation continuation, ExecutionCursor cursor) {
        if (cursor == null) {
            return "continuation 缺少恢复快照。";
        }
        if (continuation.reason() == null || continuation.sourceNodeId().isBlank()) {
            return "continuation 等待来源无效。";
        }
        if (cursor.runId().isBlank() || cursor.steps() < 0 || cursor.steps() > limits.maxStepsPerExecution()) {
            return "continuation 运行预算快照无效。";
        }
        if (!cursor.traceId().equals(continuation.traceId())
                || !Objects.equals(cursor.playerId(), continuation.playerId())
                || !cursor.sessionId().equals(continuation.sessionId())
                || !cursor.nodeId().equals(continuation.targetNodeId())) {
            return "continuation 恢复快照不一致。";
        }
        List<LoopFrame> frames = cursor.loopFrames();
        for (int index = 0; index < frames.size(); index += 1) {
            LoopFrame frame = frames.get(index);
            if (frame.kind() == null) {
                return "循环 frame 类型无效。";
            }
            Optional<NodeDefinition> container = graph.node(frame.containerNodeId());
            Optional<NodeDefinition> bodyEntry = graph.node(frame.bodyEntryNodeId());
            NodeType expected = frame.kind() == LoopKind.COUNT
                    ? NodeType.CONTROL_LOOP_COUNT
                    : NodeType.CONTROL_LOOP_FOREVER;
            if (container.isEmpty() || container.get().type() != expected) {
                return "循环容器不存在或类型已变化。";
            }
            if (bodyEntry.isEmpty() || !graph.isInBody(bodyEntry.get(), frame.containerNodeId(), "body")) {
                return "循环 body membership 已失效。";
            }
            if (frame.iteration() < 1 || frame.iteration() > frame.iterationLimit()) {
                return "循环迭代快照无效。";
            }
            if (frame.kind() == LoopKind.COUNT) {
                String completion;
                try {
                    completion = targetId(frame.containerNodeId(), "done");
                } catch (RuntimeException exception) {
                    return "循环 done 输出已失效。";
                }
                if (!completion.equals(frame.completionNodeId())) {
                    return "循环返回位置已失效。";
                }
            } else if (!frame.completionNodeId().isBlank() || frame.intervalSeconds() <= 0) {
                return "无限循环间隔快照无效。";
            }
            if (index > 0 && !graph.isInBody(container.get(), frames.get(index - 1).containerNodeId(), "body")) {
                return "嵌套循环返回路径已失效。";
            }
            if (index > 0 && !frame.completionNodeId().isBlank()) {
                Optional<NodeDefinition> completion = graph.node(frame.completionNodeId());
                if (completion.isEmpty()
                        || !graph.isInBody(completion.get(), frames.get(index - 1).containerNodeId(), "body")) {
                    return "嵌套循环完成位置已失效。";
                }
            }
        }
        if (!cursor.nodeId().isBlank()) {
            Optional<NodeDefinition> target = graph.node(cursor.nodeId());
            if (target.isEmpty()) {
                return "resume node 不存在。";
            }
            if (!frames.isEmpty() && !graph.isInBody(target.get(), frames.getLast().containerNodeId(), "body")) {
                return "resume node 不属于当前循环 body。";
            }
        }
        Optional<NodeDefinition> source = graph.node(continuation.sourceNodeId());
        if (source.isEmpty()) {
            return "等待节点不存在。";
        }
        if (continuation.reason() == TimerContinuation.Reason.DELAY
                && source.get().type() != NodeType.TIMER_START_ACTION) {
            return "等待节点类型已变化。";
        }
        if (continuation.reason() == TimerContinuation.Reason.LOOP_INTERVAL
                && (frames.isEmpty() || !frames.getLast().containerNodeId().equals(source.get().id()))) {
            return "循环间隔 frame 已失效。";
        }
        return null;
    }

    private boolean registerContinuation(String continuationId) {
        synchronized (pendingContinuationIds) {
            if (cancelled || pendingContinuationIds.size() >= MAX_PENDING_CONTINUATIONS) {
                return false;
            }
            return pendingContinuationIds.add(continuationId);
        }
    }

    private boolean consumeContinuation(String continuationId) {
        synchronized (pendingContinuationIds) {
            return pendingContinuationIds.remove(continuationId);
        }
    }

    private LoopFrame nextIteration(LoopFrame frame) {
        return new LoopFrame(
                frame.containerNodeId(),
                frame.kind(),
                frame.iteration() + 1,
                frame.iterationLimit(),
                frame.bodyEntryNodeId(),
                frame.completionNodeId(),
                frame.intervalSeconds()
        );
    }

    private String targetId(String nodeId, String outputSlot) {
        return graph.firstTarget(nodeId, outputSlot).map(NodeDefinition::id).orElse("");
    }

    private String nodeWithinCurrentBody(String targetNodeId, List<LoopFrame> frames) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            return "";
        }
        if (frames.isEmpty()) {
            return targetNodeId;
        }
        Optional<NodeDefinition> target = graph.node(targetNodeId);
        return target.filter(node -> graph.isInBody(node, frames.getLast().containerNodeId(), "body"))
                .map(NodeDefinition::id)
                .orElse("");
    }

    private RuntimeResult fail(String traceId, String nodeId, RuntimeException exception, String fallback) {
        String message = exception.getMessage() == null ? fallback : exception.getMessage();
        traces.add(traceId, nodeId, "执行失败：" + message);
        return new RuntimeResult(false, traceId, message);
    }

    private void traceIgnored(TimerContinuation continuation, String reason) {
        String nodeId = continuation.sourceNodeId().isBlank() ? continuation.targetNodeId() : continuation.sourceNodeId();
        traces.add(continuation.traceId(), nodeId, "等待恢复已忽略：" + reason + "。");
    }

    private int parsePositiveInt(String raw, String label) {
        int value = Integer.parseInt(raw);
        if (value <= 0) {
            throw new IllegalArgumentException(label + "必须大于 0。");
        }
        return value;
    }

}
