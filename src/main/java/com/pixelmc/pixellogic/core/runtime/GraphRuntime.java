package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.model.ConditionSlotDefinition;
import com.pixelmc.pixellogic.core.model.EntityTargetRequirement;
import com.pixelmc.pixellogic.core.model.EntityTargetSource;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.runtime.ExecutionCursor.LoopFrame;
import com.pixelmc.pixellogic.core.runtime.ExecutionCursor.LoopKind;
import com.pixelmc.pixellogic.core.runtime.ExecutionCursor.EntityContextFrame;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

public final class GraphRuntime {
    private static final int SIMULATION_LOOP_ITERATION_CAP = 20;
    private static final int MAX_PENDING_CONTINUATIONS = 128;

    private final CompiledGraph graph;
    private final BoundedTraceBuffer traces;
    private final RuntimeServices services;
    private final RuntimeNodeExecutor nodeExecutor;
    private final ExecutionScopes scopes;
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
        this.scopes = new ExecutionScopes(graph, services, limits);
        this.generation = generation;
    }

    public RuntimeResult start(TriggerEvent event) {
        String traceId = UUID.randomUUID().toString();
        traces.startTrace(traceId);
        traces.add(traceId, "trigger", "手动触发：" + event.commandText());
        if (cancelled) {
            traces.add(traceId, "trigger", "执行失败：运行实例已取消。");
            return recordResult(new RuntimeResult(false, traceId, "运行实例已取消。"));
        }

        Optional<NodeDefinition> entry = graph.entryForTrigger(event.triggerType());
        if (entry.isEmpty()) {
            traces.add(traceId, "trigger", "执行失败：找不到触发入口。");
            return recordResult(new RuntimeResult(false, traceId, "找不到触发入口。"));
        }

        RuntimeSubjectReference initialCurrentEntity = services.initialCurrentEntity(event.playerId(), event.sessionId()).orElse(null);
        RuntimeSubjectReference targetEntity = services.targetEntity(event.playerId(), event.sessionId()).orElse(null);
        return recordResult(runExecution(new ExecutionCursor(
                traceId,
                traceId,
                event.playerId(),
                event.sessionId(),
                0,
                entry.get().id(),
                List.of(),
                targetEntity,
                initialCurrentEntity,
                null,
                List.of()
        )));
    }

    public RuntimeResult resumeTimer(TimerContinuation continuation) {
        if (continuation.generation() != generation) {
            traceIgnored(continuation, "generation 已变化");
            return recordResult(new RuntimeResult(false, continuation.traceId(), "计时器已失效。"));
        }
        if (cancelled) {
            traceIgnored(continuation, "运行已取消");
            return recordResult(new RuntimeResult(false, continuation.traceId(), "运行已取消。"));
        }
        if (continuation.continuationId().isBlank() || !consumeContinuation(continuation.continuationId())) {
            traceIgnored(continuation, "continuation 已取消或消费");
            return recordResult(new RuntimeResult(false, continuation.traceId(), "continuation 已取消或消费。"));
        }
        if (!graph.graphId().equals(continuation.graphId())) {
            traceIgnored(continuation, "graph 已变化");
            return recordResult(new RuntimeResult(false, continuation.traceId(), "计时器所属 Graph 已变化。"));
        }
        if (continuation.depth() > limits.maxContinuationDepth()) {
            traces.add(continuation.traceId(), continuation.sourceNodeId(), "执行失败：计时器 continuation 深度超限。");
            return recordResult(new RuntimeResult(false, continuation.traceId(), "计时器 continuation 深度超限。"));
        }

        ExecutionCursor cursor = continuation.cursor();
        String cursorError = scopes.validateContinuation(continuation, cursor);
        if (cursorError != null) {
            traces.add(continuation.traceId(), continuation.sourceNodeId(), "执行失败：" + cursorError);
            return recordResult(new RuntimeResult(false, continuation.traceId(), cursorError));
        }

        if (continuation.reason() == TimerContinuation.Reason.LOOP_INTERVAL) {
            LoopFrame frame = cursor.loopFrames().getLast();
            traces.add(cursor.traceId(), frame.containerNodeId(), "第 " + frame.iteration() + " 轮开始。");
            traces.add(cursor.traceId(), continuation.sourceNodeId(), "循环间隔结束：继续下一轮。");
        } else {
            traces.add(cursor.traceId(), continuation.sourceNodeId(), "计时器完成：恢复等待后的执行。");
        }
        return recordResult(runExecution(cursor));
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
        ArrayList<EntityContextFrame> entityFrames = new ArrayList<>(initial.entityContextFrames());
        ExecutionContext context = new ExecutionContext(
                traceId,
                initial.playerId(),
                initial.sessionId(),
                initial.targetEntity(),
                initial.currentEntity(),
                initial.currentCondition()
        );

        int transitionBudget = Math.max(16, (limits.maxStepsPerExecution() + 1) * 4);
        for (int transition = 0; transition < transitionBudget; transition += 1) {
            if (cancelled) {
                traces.add(traceId, nodeId, "执行停止：运行已取消。");
                return new RuntimeResult(false, traceId, "运行已取消。");
            }
            if (nodeId.isBlank()) {
                if (frames.isEmpty() && entityFrames.isEmpty()) {
                    traces.add(traceId, "", "执行完成。");
                    return new RuntimeResult(true, traceId, "执行完成。");
                }

                if (!entityFrames.isEmpty() && entityFrames.getLast().loopDepth() == frames.size()) {
                    EntityContextFrame frame = entityFrames.removeLast();
                    context.currentEntity(frame.previousEntity());
                    traces.add(traceId, frame.containerNodeId(), "退出实体执行上下文，恢复"
                            + entityLabel(frame.previousEntity()) + "。");
                    nodeId = scopes.withinCurrent(frame.completionNodeId(), frames, entityFrames);
                    continue;
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
                        nodeId = scopes.withinCurrent(frame.completionNodeId(), frames, entityFrames);
                    }
                    continue;
                }

                if (frame.kind() == LoopKind.UNTIL) {
                    traces.add(traceId, frame.containerNodeId(), "第 " + frame.iteration() + " 轮完成，重新检查结束条件。");
                    nodeId = frame.containerNodeId();
                    continue;
                }

                traces.add(traceId, frame.containerNodeId(), "第 " + frame.iteration() + " 轮结束。");
                if (frame.iteration() >= SIMULATION_LOOP_ITERATION_CAP) {
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
                            entityFrames,
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
                        nodeId = scopes.withinCurrent(scopes.targetId(node.id(), "done"), frames, entityFrames);
                        continue;
                    }
                    LoopFrame frame = new LoopFrame(
                            node.id(),
                            LoopKind.COUNT,
                            1,
                            count,
                            bodyEntry.get().id(),
                            scopes.targetId(node.id(), "done"),
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
                            SIMULATION_LOOP_ITERATION_CAP,
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

            if (node.type() == NodeType.CONTROL_LOOP_UNTIL) {
                try {
                    long predicateSteps = (long) steps + node.conditionSlots().size();
                    if (predicateSteps > limits.maxStepsPerExecution()) {
                        traces.add(traceId, node.id(), "执行失败：超过最大执行步数。");
                        return new RuntimeResult(false, traceId, "超过最大执行步数。");
                    }
                    steps = (int) predicateSteps;

                    boolean recheck = !frames.isEmpty()
                            && frames.getLast().kind() == LoopKind.UNTIL
                            && frames.getLast().containerNodeId().equals(node.id());
                    boolean complete = evaluateUntilConditions(node, context, !recheck);
                    if (complete) {
                        traces.add(traceId, node.id(), "全部结束条件成立，退出循环。");
                        if (recheck) {
                            frames.removeLast();
                        }
                        nodeId = scopes.withinCurrent(scopes.targetId(node.id(), "done"), frames, entityFrames);
                        continue;
                    }

                    if (recheck) {
                        LoopFrame currentFrame = frames.getLast();
                        if (currentFrame.iteration() >= currentFrame.iterationLimit()) {
                            traces.add(traceId, node.id(), "已达到测试模拟循环上限，已停止继续模拟。");
                            return new RuntimeResult(true, traceId, "执行完成。");
                        }
                        LoopFrame nextFrame = nextIteration(currentFrame);
                        frames.set(frames.size() - 1, nextFrame);
                        traces.add(traceId, node.id(), "结束条件未全部成立，执行第 " + nextFrame.iteration() + " 轮。");
                        nodeId = nextFrame.bodyEntryNodeId();
                        continue;
                    }

                    Optional<NodeDefinition> bodyEntry = graph.bodyEntry(node.id(), "body");
                    if (bodyEntry.isEmpty()) {
                        throw new IllegalStateException("循环直到的结束条件未成立，但循环内容为空。");
                    }
                    LoopFrame frame = new LoopFrame(
                            node.id(),
                            LoopKind.UNTIL,
                            1,
                            SIMULATION_LOOP_ITERATION_CAP,
                            bodyEntry.get().id(),
                            scopes.targetId(node.id(), "done"),
                            0
                    );
                    frames.add(frame);
                    traces.add(traceId, node.id(), "结束条件未全部成立，执行第 1 轮。");
                    nodeId = frame.bodyEntryNodeId();
                    continue;
                } catch (RuntimeException exception) {
                    return fail(traceId, node.id(), exception, "循环直到执行失败。");
                }
            }

            if (node.type() == NodeType.CONTEXT_ENTITY_EXECUTE_AS) {
                try {
                    ResolvedEntityTarget selection = resolveContextEntity(node, context);
                    RuntimeSubjectReference selected = selection.reference();
                    Optional<NodeDefinition> bodyEntry = graph.bodyEntry(node.id(), "body");
                    String completion = scopes.targetId(node.id(), "done");
                    RuntimeSubjectReference previous = context.currentEntity();
                    context.currentEntity(selected);
                    traces.add(traceId, node.id(), "使用" + entitySourceLabel(selection.source()) + " "
                            + selected.displayName() + " 进入实体执行上下文。");
                    if (bodyEntry.isEmpty()) {
                        context.currentEntity(previous);
                        traces.add(traceId, node.id(), "实体执行上下文内部为空，恢复"
                                + entityLabel(previous) + "并继续外部流程。");
                        nodeId = scopes.withinCurrent(completion, frames, entityFrames);
                        continue;
                    }
                    entityFrames.add(new EntityContextFrame(
                            node.id(),
                            bodyEntry.get().id(),
                            completion,
                            previous,
                            selected,
                            frames.size()
                    ));
                    nodeId = bodyEntry.get().id();
                    continue;
                } catch (RuntimeException exception) {
                    return fail(traceId, node.id(), exception, "实体执行上下文解析失败。");
                }
            }

            if (node.type() == NodeType.TIMER_START_ACTION) {
                try {
                    int seconds = parsePositiveInt(node.config().getOrDefault("durationSeconds", "30"), "等待时间");
                    Optional<NodeDefinition> target = graph.firstTarget(node.id(), "timer_completed");
                    String resumeNodeId = scopes.withinCurrent(
                            target.map(NodeDefinition::id).orElse(""),
                            frames,
                            entityFrames
                    );
                    traces.add(traceId, node.id(), "计时器启动：" + seconds + " 秒");
                    services.recordActionResult(node.id(), "timer", "计时器启动：" + seconds + " 秒");
                    return suspend(
                            runId,
                            traceId,
                            context,
                            steps,
                            resumeNodeId,
                            frames,
                            entityFrames,
                            node.id(),
                            Duration.ofSeconds(seconds),
                            TimerContinuation.Reason.DELAY
                    );
                } catch (RuntimeException exception) {
                    return fail(traceId, node.id(), exception, "计时器调度失败。");
                }
            }

            RuntimeNodeExecutionResult execution;
            try {
                execution = nodeExecutor.execute(node, context);
            } catch (RuntimeException exception) {
                recordFailedAction(node, exception);
                return fail(traceId, node.id(), exception, "运行时错误。");
            }
            if (execution.actionOutcome() != null) {
                services.recordActionOutcome(node.id(), execution.actionOutcome());
            }
            if (BuiltInBlockCatalog.isConditionBlock(node.blockId())) {
                context.currentCondition(execution.conditionResult());
            }
            String outputSlot = execution.outputSlot();
            if (outputSlot == null) {
                nodeId = "";
                continue;
            }

            try {
                Optional<NodeDefinition> next = graph.firstTarget(node.id(), outputSlot);
                if (next.isEmpty() && frames.isEmpty() && entityFrames.isEmpty()) {
                    traces.add(traceId, node.id(), "未连接后续积木，流程在此结束。");
                }
                nodeId = scopes.withinCurrent(next.map(NodeDefinition::id).orElse(""), frames, entityFrames);
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
            List<EntityContextFrame> entityFrames,
            String sourceNodeId,
            Duration delay,
            TimerContinuation.Reason reason
    ) {
        int depth = frames.size() + entityFrames.size() + 1;
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
                frames,
                context.targetEntity(),
                context.currentEntity(),
                context.currentCondition(),
                entityFrames
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

    private boolean evaluateUntilConditions(NodeDefinition loop, ExecutionContext context, boolean announceCheck) {
        if (announceCheck) {
            traces.add(context.traceId(), loop.id(), "循环直到：条件检查开始。");
        }
        if (loop.conditionSlots().isEmpty()) {
            throw new IllegalStateException("循环直到缺少结束条件。");
        }

        boolean allMatched = true;
        Set<String> slotIds = new HashSet<>();
        for (int index = 0; index < loop.conditionSlots().size(); index += 1) {
            ConditionSlotDefinition slot = loop.conditionSlots().get(index);
            if (slot.slotId().isBlank() || !slotIds.add(slot.slotId())) {
                throw new IllegalStateException("结束条件 " + (index + 1) + " 的槽位 ID 无效。");
            }
            List<NodeDefinition> conditions = graph.childrenInSlot(loop.id(), slot.slotId());
            if (conditions.isEmpty()) {
                throw new IllegalStateException("结束条件 " + (index + 1) + " 尚未设置。");
            }
            if (conditions.size() != 1) {
                throw new IllegalStateException("结束条件 " + (index + 1) + " 包含多个条件积木。");
            }

            NodeDefinition condition = conditions.getFirst();
            Optional<RuntimePredicateResult> evaluated = services.evaluatePredicate(condition, context.snapshot());
            if (evaluated.isEmpty()) {
                throw new IllegalStateException("结束条件 " + (index + 1) + " 不支持布尔求值。");
            }
            RuntimePredicateResult predicate = evaluated.get();
            boolean matched = slot.negated() ? !predicate.value() : predicate.value();
            String detail = predicate.traceMessage().isBlank() ? "" : predicate.traceMessage() + " ";
            traces.add(context.traceId(), condition.id(), "结束条件 " + (index + 1) + "：" + detail
                    + "原始结果 " + predicate.value() + "，应用取反后 " + matched + "。");
            allMatched &= matched;
        }
        return allMatched;
    }

    private ResolvedEntityTarget resolveContextEntity(NodeDefinition node, ExecutionContext context) {
        String rawTarget = node.config().get("target");
        return EntityTargetResolver.resolve(
                node.id(),
                "target",
                rawTarget,
                entityTargetRequirement(node),
                context.snapshot(),
                services.entityProvider()
        );
    }

    private String entitySourceLabel(EntityTargetSource source) {
        return switch (source) {
            case CURRENT_ENTITY -> "当前执行实体";
            case CONDITION_SUBJECT -> "当前条件主体";
            case TARGET_ENTITY -> "当前目标实体";
            case ONLINE_PLAYER -> "指定在线玩家";
        };
    }

    private EntityTargetRequirement entityTargetRequirement(NodeDefinition node) {
        return BuiltInBlockCatalog.block(node.blockId())
                .map(block -> block.entityTargetRequirement())
                .orElseThrow(() -> new IllegalStateException("积木缺少实体目标类型约束。"));
    }

    private String entityLabel(RuntimeSubjectReference entity) {
        return entity == null || entity.displayName().isBlank() ? "外层实体上下文" : "实体 " + entity.displayName();
    }

    private RuntimeResult fail(String traceId, String nodeId, RuntimeException exception, String fallback) {
        String message = exception.getMessage() == null ? fallback : exception.getMessage();
        EntityTargetError targetError = exception instanceof EntityTargetException targetException
                ? targetException.error()
                : null;
        EntityActionError actionError = exception instanceof EntityActionException actionException
                ? actionException.error()
                : null;
        String errorCode = targetError != null
                ? targetError.code().id()
                : actionError == null ? "" : actionError.code().id();
        traces.add(traceId, nodeId, "执行失败" + (errorCode.isBlank() ? "" : " [" + errorCode + "]") + "：" + message);
        return new RuntimeResult(false, traceId, message, false, targetError, actionError);
    }

    private void recordFailedAction(NodeDefinition node, RuntimeException exception) {
        if (exception instanceof EntityTargetException targetException
                && (node.type() == NodeType.ENTITY_ADD_TAG_ACTION
                || node.type() == NodeType.ENTITY_REMOVE_TAG_ACTION
                || EntityHealthExecution.supports(node.type())
                || EntityStatusExecution.supports(node.type()))) {
            services.recordActionOutcome(node.id(), RuntimeActionOutcome.failure(node.blockId(), targetException.error()));
        } else if (exception instanceof EntityActionException actionException
                && (EntityHealthExecution.supports(node.type())
                || EntityStatusExecution.supports(node.type()))) {
            services.recordActionOutcome(node.id(), RuntimeActionOutcome.failure(node.blockId(), actionException.error()));
        }
    }

    private RuntimeResult recordResult(RuntimeResult result) {
        services.recordRuntimeResult(result);
        return result;
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
