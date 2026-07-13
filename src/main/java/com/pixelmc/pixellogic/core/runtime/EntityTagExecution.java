package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.EntityTargetRequirement;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;

import java.util.Optional;

final class EntityTagExecution {
    private EntityTagExecution() {
    }

    static Optional<RuntimeNodeExecutionResult> execute(
            NodeDefinition node,
            RuntimeExecutionContext context,
            RuntimeServices services
    ) {
        if (node.type() == NodeType.ENTITY_HAS_TAG_CONDITION) {
            RuntimePredicateResult predicate = evaluate(node, context, services);
            ConditionOutputMode mode = ConditionOutputMode.fromConfig(node.config());
            return Optional.of(new RuntimeNodeExecutionResult(
                    mode.outputSlot(predicate.value()),
                    predicate.traceMessage() + contextualPathTrace(mode, predicate.value()),
                    predicate.conditionResult()
            ));
        }
        if (node.type() != NodeType.ENTITY_ADD_TAG_ACTION
                && node.type() != NodeType.ENTITY_REMOVE_TAG_ACTION) {
            return Optional.empty();
        }

        ResolvedEntityTarget target = resolve(node, context, services);
        String tag = tag(node);
        boolean adding = node.type() == NodeType.ENTITY_ADD_TAG_ACTION;
        boolean changed = adding ? target.entity().addTag(tag) : target.entity().removeTag(tag);
        String message = actionMessage(adding, changed, target.reference(), tag);
        services.recordEntityTagState(node.id(), target.reference(), target.entity().tags());
        return Optional.of(new RuntimeNodeExecutionResult(
                "done",
                message,
                null,
                RuntimeActionOutcome.success(node.blockId(), message, target.reference(), changed)
        ));
    }

    static Optional<RuntimePredicateResult> evaluatePredicate(
            NodeDefinition node,
            RuntimeExecutionContext context,
            RuntimeServices services
    ) {
        return node.type() == NodeType.ENTITY_HAS_TAG_CONDITION
                ? Optional.of(evaluate(node, context, services))
                : Optional.empty();
    }

    private static RuntimePredicateResult evaluate(
            NodeDefinition node,
            RuntimeExecutionContext context,
            RuntimeServices services
    ) {
        ResolvedEntityTarget target = resolve(node, context, services);
        String tag = tag(node);
        boolean passed = target.entity().hasTag(tag);
        String fact = "实体 " + target.reference().displayName() + (passed ? " 拥有" : " 没有")
                + "标签「" + tag + "」";
        return new RuntimePredicateResult(
                passed,
                fact + "。",
                new RuntimeConditionResult(node.id(), node.blockId(), target.reference(), passed, fact)
        );
    }

    private static ResolvedEntityTarget resolve(
            NodeDefinition node,
            RuntimeExecutionContext context,
            RuntimeServices services
    ) {
        EntityTargetRequirement requirement = BuiltInBlockCatalog.block(node.blockId())
                .map(block -> block.entityTargetRequirement())
                .orElseThrow(() -> new IllegalStateException("积木缺少实体目标类型约束。"));
        return EntityTargetResolver.resolve(
                node.id(),
                "target",
                node.config().get("target"),
                requirement,
                context,
                services.entityProvider()
        );
    }

    private static String tag(NodeDefinition node) {
        String tag = node.config().getOrDefault("tag", "");
        if (tag.isBlank()) {
            throw new IllegalStateException("标签不能为空。");
        }
        return tag;
    }

    private static String actionMessage(
            boolean adding,
            boolean changed,
            RuntimeSubjectReference target,
            String tag
    ) {
        if (adding) {
            return changed
                    ? "为实体 " + target.displayName() + " 添加标签「" + tag + "」。"
                    : "实体 " + target.displayName() + " 已有标签「" + tag + "」，未发生变化。";
        }
        return changed
                ? "移除实体 " + target.displayName() + " 的标签「" + tag + "」。"
                : "实体 " + target.displayName() + " 没有标签「" + tag + "」，未发生变化。";
    }

    private static String contextualPathTrace(ConditionOutputMode mode, boolean passed) {
        return switch (mode) {
            case PASS_ONLY -> passed ? "进入“满足”路径。" : "流程在此结束。";
            case FAIL_ONLY -> passed ? "流程在此结束。" : "进入“不满足”路径。";
            case BRANCH -> passed ? "进入“满足”路径。" : "进入“不满足”路径。";
        };
    }
}
