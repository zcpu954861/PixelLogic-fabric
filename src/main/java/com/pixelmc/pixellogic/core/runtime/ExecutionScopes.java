package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.graph.CompiledGraph;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.runtime.ExecutionCursor.EntityContextFrame;
import com.pixelmc.pixellogic.core.runtime.ExecutionCursor.LoopFrame;
import com.pixelmc.pixellogic.core.runtime.ExecutionCursor.LoopKind;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class ExecutionScopes {
    private final CompiledGraph graph;
    private final RuntimeServices services;
    private final RuntimeLimits limits;

    ExecutionScopes(CompiledGraph graph, RuntimeServices services, RuntimeLimits limits) {
        this.graph = graph;
        this.services = services;
        this.limits = limits;
    }

    String validateContinuation(TimerContinuation continuation, ExecutionCursor cursor) {
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
        if ((cursor.currentEntity() != null && !validEntity(cursor.currentEntity()))
                || (cursor.targetEntity() != null && !validEntity(cursor.targetEntity()))) {
            return "continuation 实体上下文无效。";
        }
        if ((cursor.currentEntity() != null && !entityResolvable(cursor.currentEntity()))
                || (cursor.targetEntity() != null && !entityResolvable(cursor.targetEntity()))) {
            return "continuation 实体无法解析。";
        }
        if (cursor.currentCondition() != null) {
            RuntimeConditionResult condition = cursor.currentCondition();
            Optional<NodeDefinition> conditionNode = graph.node(condition.conditionNodeId());
            if (condition.subject() == null || condition.subject().id().isBlank()
                    || condition.subject().kind() == null
                    || conditionNode.isEmpty()
                    || !conditionNode.get().blockId().equals(condition.blockId())) {
                return "continuation 条件结果上下文无效。";
            }
            if (condition.subject().isEntity() && !entityResolvable(condition.subject())) {
                return "continuation 条件对象无法解析。";
            }
        }

        List<LoopFrame> frames = cursor.loopFrames();
        for (int index = 0; index < frames.size(); index += 1) {
            LoopFrame frame = frames.get(index);
            if (frame.kind() == null) {
                return "循环 frame 类型无效。";
            }
            Optional<NodeDefinition> container = graph.node(frame.containerNodeId());
            Optional<NodeDefinition> bodyEntry = graph.node(frame.bodyEntryNodeId());
            NodeType expected = switch (frame.kind()) {
                case COUNT -> NodeType.CONTROL_LOOP_COUNT;
                case FOREVER -> NodeType.CONTROL_LOOP_FOREVER;
                case UNTIL -> NodeType.CONTROL_LOOP_UNTIL;
            };
            if (container.isEmpty() || container.get().type() != expected) {
                return "循环容器不存在或类型已变化。";
            }
            if (bodyEntry.isEmpty() || !graph.isInBody(bodyEntry.get(), frame.containerNodeId(), "body")) {
                return "循环 body membership 已失效。";
            }
            if (frame.iteration() < 1 || frame.iteration() > frame.iterationLimit()) {
                return "循环迭代快照无效。";
            }
            if (frame.kind() == LoopKind.COUNT || frame.kind() == LoopKind.UNTIL) {
                String completion;
                try {
                    completion = targetId(frame.containerNodeId(), "done");
                } catch (RuntimeException exception) {
                    return "循环 done 输出已失效。";
                }
                if (!completion.equals(frame.completionNodeId())) {
                    return "循环返回位置已失效。";
                }
                if (frame.intervalSeconds() != 0) {
                    return "循环间隔快照无效。";
                }
            } else if (!frame.completionNodeId().isBlank() || frame.intervalSeconds() <= 0) {
                return "无限循环间隔快照无效。";
            }
            String enclosingContainerId = enclosingContainerForLoop(index, frames, cursor.entityContextFrames());
            if (!enclosingContainerId.isBlank()
                    && !graph.isInBody(container.get(), enclosingContainerId, "body")) {
                return "嵌套循环返回路径已失效。";
            }
            if (!enclosingContainerId.isBlank() && !frame.completionNodeId().isBlank()) {
                Optional<NodeDefinition> completion = graph.node(frame.completionNodeId());
                if (completion.isEmpty()
                        || !graph.isInBody(completion.get(), enclosingContainerId, "body")) {
                    return "嵌套循环完成位置已失效。";
                }
            }
        }

        List<EntityContextFrame> entityFrames = cursor.entityContextFrames();
        for (int index = 0; index < entityFrames.size(); index += 1) {
            EntityContextFrame frame = entityFrames.get(index);
            Optional<NodeDefinition> container = graph.node(frame.containerNodeId());
            Optional<NodeDefinition> bodyEntry = graph.node(frame.bodyEntryNodeId());
            if (container.isEmpty() || container.get().type() != NodeType.CONTEXT_ENTITY_EXECUTE_AS) {
                return "实体上下文容器不存在或类型已变化。";
            }
            if (bodyEntry.isEmpty() || !graph.isInBody(bodyEntry.get(), frame.containerNodeId(), "body")) {
                return "实体上下文 body membership 已失效。";
            }
            if ((frame.previousEntity() != null && !validEntity(frame.previousEntity()))
                    || !validEntity(frame.selectedEntity())
                    || frame.loopDepth() < 0 || frame.loopDepth() > frames.size()) {
                return "实体上下文 frame 无效。";
            }
            if ((frame.previousEntity() != null && !entityResolvable(frame.previousEntity()))
                    || !entityResolvable(frame.selectedEntity())) {
                return "实体上下文 frame 中的实体无法解析。";
            }
            String completion;
            try {
                completion = targetId(frame.containerNodeId(), "done");
            } catch (RuntimeException exception) {
                return "实体上下文 done 输出已失效。";
            }
            if (!completion.equals(frame.completionNodeId())) {
                return "实体上下文返回位置已失效。";
            }
            String enclosingContainerId = enclosingContainerForEntityFrame(index, frame, frames, entityFrames);
            if (!enclosingContainerId.isBlank()
                    && !graph.isInBody(container.get(), enclosingContainerId, "body")) {
                return "嵌套实体上下文路径已失效。";
            }
            if (!enclosingContainerId.isBlank() && !frame.completionNodeId().isBlank()) {
                Optional<NodeDefinition> completionNode = graph.node(frame.completionNodeId());
                if (completionNode.isEmpty()
                        || !graph.isInBody(completionNode.get(), enclosingContainerId, "body")) {
                    return "嵌套实体上下文完成位置已失效。";
                }
            }
        }
        if (!entityFrames.isEmpty() && !Objects.equals(entityFrames.getLast().selectedEntity(), cursor.currentEntity())) {
            return "continuation 当前实体与 frame 不一致。";
        }
        if (!cursor.nodeId().isBlank()) {
            Optional<NodeDefinition> target = graph.node(cursor.nodeId());
            if (target.isEmpty()) {
                return "resume node 不存在。";
            }
            if (!contains(target.get(), frames, entityFrames)) {
                return "resume node 不属于当前容器 body。";
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
                && (frames.isEmpty()
                || frames.getLast().kind() != LoopKind.FOREVER
                || !frames.getLast().containerNodeId().equals(source.get().id()))) {
            return "循环间隔 frame 已失效。";
        }
        return null;
    }

    String targetId(String nodeId, String outputSlot) {
        return graph.firstTarget(nodeId, outputSlot).map(NodeDefinition::id).orElse("");
    }

    String withinCurrent(
            String targetNodeId,
            List<LoopFrame> frames,
            List<EntityContextFrame> entityFrames
    ) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            return "";
        }
        if (frames.isEmpty() && entityFrames.isEmpty()) {
            return targetNodeId;
        }
        return graph.node(targetNodeId)
                .filter(node -> contains(node, frames, entityFrames))
                .map(NodeDefinition::id)
                .orElse("");
    }

    private boolean contains(
            NodeDefinition node,
            List<LoopFrame> frames,
            List<EntityContextFrame> entityFrames
    ) {
        if (!entityFrames.isEmpty() && entityFrames.getLast().loopDepth() == frames.size()) {
            return graph.isInBody(node, entityFrames.getLast().containerNodeId(), "body");
        }
        return frames.isEmpty() || graph.isInBody(node, frames.getLast().containerNodeId(), "body");
    }

    private String enclosingContainerForLoop(
            int loopIndex,
            List<LoopFrame> frames,
            List<EntityContextFrame> entityFrames
    ) {
        for (int index = entityFrames.size() - 1; index >= 0; index -= 1) {
            EntityContextFrame frame = entityFrames.get(index);
            if (frame.loopDepth() == loopIndex) {
                return frame.containerNodeId();
            }
        }
        return loopIndex > 0 ? frames.get(loopIndex - 1).containerNodeId() : "";
    }

    private String enclosingContainerForEntityFrame(
            int entityFrameIndex,
            EntityContextFrame frame,
            List<LoopFrame> frames,
            List<EntityContextFrame> entityFrames
    ) {
        for (int index = entityFrameIndex - 1; index >= 0; index -= 1) {
            EntityContextFrame candidate = entityFrames.get(index);
            if (candidate.loopDepth() == frame.loopDepth()) {
                return candidate.containerNodeId();
            }
        }
        return frame.loopDepth() > 0 ? frames.get(frame.loopDepth() - 1).containerNodeId() : "";
    }

    private static boolean validEntity(RuntimeSubjectReference entity) {
        return entity != null && entity.isEntity() && !entity.id().isBlank();
    }

    private boolean entityResolvable(RuntimeSubjectReference entity) {
        RuntimeEntityLookup lookup = services.entityProvider().resolve(entity);
        if (lookup == null || lookup.status() != RuntimeEntityLookup.Status.RESOLVED
                || lookup.entity() == null || lookup.entity().reference() == null) {
            return false;
        }
        RuntimeSubjectReference resolved = lookup.entity().reference();
        return entity.id().equals(resolved.id())
                && entity.kind() == resolved.kind()
                && (resolved.kind() != RuntimeSubjectReference.Kind.PLAYER || lookup.entity().online());
    }
}
