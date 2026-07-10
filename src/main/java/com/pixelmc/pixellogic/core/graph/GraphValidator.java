package com.pixelmc.pixellogic.core.graph;

import com.pixelmc.pixellogic.core.catalog.BlockDefinition;
import com.pixelmc.pixellogic.core.catalog.BlockFormFieldDefinition;
import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.catalog.RichTextComponentValue;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;
import com.pixelmc.pixellogic.core.model.StateScope;
import com.pixelmc.pixellogic.core.model.StateValueType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class GraphValidator {
    private static final int MAX_REGION_NAME_LENGTH = 64;
    private static final int MAX_CONTAINER_DEPTH = 4;
    private static final Pattern NAMESPACED_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Set<String> Y_COMPARE_MODES = Set.of("AT_OR_ABOVE", "AT_OR_BELOW", "EQUAL", "BETWEEN");

    public List<ValidationIssue> validate(GraphDefinition graph) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, NodeDefinition> nodes = new HashMap<>();

        for (NodeDefinition node : graph.nodes()) {
            if (nodes.put(node.id(), node) != null) {
                error(issues, "duplicate_node", "节点 ID 重复：" + node.id());
            }
        }

        if (graph.triggerEntries().isEmpty()) {
            error(issues, "missing_trigger", "缺少手动触发入口。");
        }

        for (Map.Entry<String, String> entry : graph.triggerEntries().entrySet()) {
            NodeDefinition trigger = nodes.get(entry.getValue());
            if (trigger == null) {
                error(issues, "missing_trigger_node", "触发入口不存在：" + entry.getValue());
            } else if (trigger.type() != NodeType.MANUAL_TRIGGER && trigger.type() != NodeType.COMMAND_TRIGGER) {
                error(issues, "invalid_trigger_node", "触发入口必须是 Trigger 节点：" + trigger.id());
            }
        }

        for (EdgeDefinition edge : graph.edges()) {
            NodeDefinition source = nodes.get(edge.sourceNodeId());
            NodeDefinition target = nodes.get(edge.targetNodeId());
            if (source == null) {
                error(issues, "edge_source_missing", "连接的起点节点不存在：" + edge.id());
                continue;
            }
            if (target == null) {
                error(issues, "edge_target_missing", "连接的终点节点不存在：" + edge.id());
                continue;
            }

            SlotDefinition sourceSlot = source.slot(edge.sourceSlotId()).orElse(null);
            SlotDefinition targetSlot = target.slot(edge.targetSlotId()).orElse(null);
            if (sourceSlot == null) {
                error(issues, "edge_source_slot_missing", "连接的起点槽位不存在：" + edge.id());
                continue;
            }
            if (targetSlot == null) {
                error(issues, "edge_target_slot_missing", "连接的终点槽位不存在：" + edge.id());
                continue;
            }
            if (sourceSlot.direction() != SlotDirection.OUTPUT || targetSlot.direction() != SlotDirection.INPUT) {
                error(issues, "edge_direction_mismatch", "连接方向错误：" + edge.id());
            }
            if (sourceSlot.edgeType() != edge.edgeType() || targetSlot.edgeType() != edge.edgeType()) {
                error(issues, "edge_type_mismatch", "连接类型不匹配：" + edge.id());
            }
        }
        validateSingleOutgoingPerSlot(graph, issues);

        validateNodes(graph, nodes, issues);
        validateContainerMembership(graph, nodes, issues);
        detectCycle(graph, issues);
        return issues;
    }

    public boolean hasErrors(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR);
    }

    private void validateNodes(GraphDefinition graph, Map<String, NodeDefinition> nodes, List<ValidationIssue> issues) {
        for (NodeDefinition node : graph.nodes()) {
            validateCatalogBlock(node, issues);
            switch (node.type()) {
                case STATE_COMPARE_CONDITION -> validateCondition(node, issues);
                case PLAYER_HAS_TAG_CONDITION -> validatePlayerTagCondition(node, issues);
                case PLAYER_IS_ADMIN_CONDITION -> validateConditionOutputMode(node, issues);
                case PLAYER_DIMENSION_CONDITION -> validatePlayerDimensionCondition(node, issues);
                case PLAYER_IN_REGION_CONDITION -> validateRegionCondition(node, issues);
                case PLAYER_Y_COMPARE_CONDITION, TARGET_BLOCK_Y_COMPARE_CONDITION -> validateYCompareCondition(node, issues);
                case TARGET_BLOCK_TYPE_CONDITION -> validateTargetBlockTypeCondition(node, issues);
                case TARGET_BLOCK_IN_REGION_CONDITION -> validateRegionCondition(node, issues);
                case PLAYER_NEAR_TARGET_BLOCK_CONDITION -> validateNearTargetBlockCondition(node, issues);
                case CONTROL_LOOP_COUNT -> validateLoopCount(node, issues);
                case CONTROL_LOOP_FOREVER -> validateLoopForever(node, issues);
                case PLAYER_ADD_TAG_ACTION, PLAYER_REMOVE_TAG_ACTION -> validatePlayerTagConfig(node, issues);
                case STATE_SET_ACTION -> validateStateAction(node, issues, true);
                case STATE_ADD_ACTION -> validateStateAction(node, issues, false);
                case TIMER_START_ACTION -> validateTimer(graph, node, issues);
                case MANUAL_TRIGGER, COMMAND_TRIGGER, MESSAGE_ACTION, DEBUG_LOG_ACTION -> {
                }
            }
        }
    }

    private void validateContainerMembership(GraphDefinition graph, Map<String, NodeDefinition> nodes, List<ValidationIssue> issues) {
        for (NodeDefinition node : graph.nodes()) {
            if (node.parentContainerId().isBlank() && node.parentSlot().isBlank()) {
                continue;
            }
            NodeDefinition parent = nodes.get(node.parentContainerId());
            if (parent == null) {
                error(issues, "container_parent_missing", "容器父积木不存在：" + node.id());
                continue;
            }
            BlockDefinition parentBlock = BuiltInBlockCatalog.block(parent.blockId()).orElse(null);
            if (parentBlock == null || !parentBlock.containerSlots().contains(node.parentSlot())) {
                error(issues, "container_slot_invalid", "容器槽位不存在：" + node.id() + "." + node.parentSlot());
            }
            if (containerDepth(node, nodes, new HashSet<>()) > MAX_CONTAINER_DEPTH) {
                error(issues, "container_depth_exceeded", "容器嵌套不能超过 " + MAX_CONTAINER_DEPTH + " 层：" + node.id());
            }
        }

        for (NodeDefinition node : graph.nodes()) {
            BlockDefinition block = BuiltInBlockCatalog.block(node.blockId()).orElse(null);
            if (block == null || block.containerSlots().isEmpty()) {
                continue;
            }
            for (String slot : block.containerSlots()) {
                boolean hasChild = graph.nodes().stream().anyMatch(child ->
                        child.parentContainerId().equals(node.id()) && child.parentSlot().equals(slot));
                if (!hasChild) {
                    warning(issues, "container_body_empty", "容器内部为空：" + node.id());
                }
            }
        }
    }

    private int containerDepth(NodeDefinition node, Map<String, NodeDefinition> nodes, Set<String> visiting) {
        if (node.parentContainerId().isBlank()) {
            return 0;
        }
        if (!visiting.add(node.id())) {
            return MAX_CONTAINER_DEPTH + 1;
        }
        NodeDefinition parent = nodes.get(node.parentContainerId());
        return parent == null ? 0 : 1 + containerDepth(parent, nodes, visiting);
    }

    private void validateCatalogBlock(NodeDefinition node, List<ValidationIssue> issues) {
        BlockDefinition block = BuiltInBlockCatalog.block(node.blockId()).orElse(null);
        if (block == null) {
            error(issues, "unknown_block_id", "未知积木 blockId：" + node.blockId() + "（" + node.id() + "）");
            return;
        }
        if (block.nodeType() != node.type()) {
            error(issues, "block_type_mismatch", "积木 blockId 与节点类型不匹配：" + node.id());
        }
        validateCatalogConfig(node, block, issues);
    }

    private void validateCatalogConfig(NodeDefinition node, BlockDefinition block, List<ValidationIssue> issues) {
        for (BlockFormFieldDefinition field : block.formSchema()) {
            String key = field.key();
            if (key == null || key.isBlank() || "hidden".equals(field.type()) || "readonly".equals(field.type())) {
                continue;
            }
            String value = node.config().get(key);
            if (value == null || value.isBlank()) {
                if (field.required()) {
                    error(issues, "config_required_missing", "积木配置缺少必填项：" + node.id() + "." + key);
                }
                continue;
            }
            if (node.type() == NodeType.STATE_SET_ACTION && "value".equals(key) && !"BOOLEAN".equals(node.config().get("valueType"))) {
                continue;
            }
            switch (field.type()) {
                case "boolean" -> validateOptionValue(node, key, value, field, issues, "config_boolean_invalid");
                case "select", "segmented" -> validateOptionValue(node, key, value, field, issues, "config_option_invalid");
                case "scope" -> validateScopeValue(node, key, value, issues);
                case "number", "integer" -> validateNumberValue(node, key, value, field, issues);
                case "rich_text_component" -> {
                    if (RichTextComponentValue.plainText(value).isBlank() && field.required()) {
                        error(issues, "config_rich_text_empty", "富文本消息不能为空：" + node.id() + "." + key);
                    }
                    for (String validationError : RichTextComponentValue.validationErrors(value)) {
                        error(issues, "config_rich_text_invalid", validationError + "（" + node.id() + "." + key + "）");
                    }
                }
                case "string", "textarea" -> {
                }
                default -> {
                }
            }
        }
    }

    private void validateOptionValue(
            NodeDefinition node,
            String key,
            String value,
            BlockFormFieldDefinition field,
            List<ValidationIssue> issues,
            String code
    ) {
        if (field.options().stream().noneMatch(option -> option.value().equals(value))) {
            error(issues, code, "积木配置选项无效：" + node.id() + "." + key);
        }
    }

    private void validateScopeValue(NodeDefinition node, String key, String value, List<ValidationIssue> issues) {
        try {
            StateScope.valueOf(value);
        } catch (IllegalArgumentException exception) {
            error(issues, "config_scope_invalid", "状态作用对象无效：" + node.id() + "." + key);
        }
    }

    private void validateNumberValue(NodeDefinition node, String key, String value, BlockFormFieldDefinition field, List<ValidationIssue> issues) {
        try {
            double numeric = "integer".equals(field.type()) ? Integer.parseInt(value) : Double.parseDouble(value);
            if (!field.min().isBlank() && numeric < Double.parseDouble(field.min())) {
                error(issues, "config_number_range", "数值低于允许范围：" + node.id() + "." + key);
            }
            if (!field.max().isBlank() && numeric > Double.parseDouble(field.max())) {
                error(issues, "config_number_range", "数值高于允许范围：" + node.id() + "." + key);
            }
        } catch (NumberFormatException exception) {
            error(issues, "config_number_invalid", "数值配置无效：" + node.id() + "." + key);
        }
    }

    private void validateCondition(NodeDefinition node, List<ValidationIssue> issues) {
        validateStateConfig(node, issues);
        if (!"BOOLEAN".equals(node.config().get("valueType"))) {
            error(issues, "condition_state_type_invalid", "State Compare Condition 当前只支持 BOOLEAN：" + node.id());
        }
        validateBooleanConfig(node, "expected", issues);
        validateBooleanConfig(node, "missing", issues);
        validateConditionOutputMode(node, issues);
    }

    private void validatePlayerTagCondition(NodeDefinition node, List<ValidationIssue> issues) {
        validatePlayerTagConfig(node, issues);
        validateConditionOutputMode(node, issues);
    }

    private void validatePlayerDimensionCondition(NodeDefinition node, List<ValidationIssue> issues) {
        validateNamespacedConfig(node, "dimensionId", "condition_dimension_id_invalid", "维度 ID 必须类似 minecraft:overworld：", issues);
        validateConditionOutputMode(node, issues);
    }

    private void validateTargetBlockTypeCondition(NodeDefinition node, List<ValidationIssue> issues) {
        validateNamespacedConfig(node, "blockId", "condition_block_id_invalid", "方块 ID 必须类似 minecraft:stone：", issues);
        validateConditionOutputMode(node, issues);
    }

    private void validateRegionCondition(NodeDefinition node, List<ValidationIssue> issues) {
        validateRegionNameConfig(node, issues);
        validateConditionOutputMode(node, issues);
    }

    private void validateYCompareCondition(NodeDefinition node, List<ValidationIssue> issues) {
        String mode = node.config().getOrDefault("compareMode", "");
        if (!Y_COMPARE_MODES.contains(mode)) {
            error(issues, "condition_y_compare_mode_invalid", "判断方式不合法：" + node.id());
        } else if ("BETWEEN".equals(mode)) {
            Integer min = parseIntegerConfig(node, "minY", "高度范围的最小值无效", issues);
            Integer max = parseIntegerConfig(node, "maxY", "高度范围的最大值无效", issues);
            if (min != null && max != null && min > max) {
                error(issues, "condition_y_range_invalid", "高度范围的最小值不能大于最大值：" + node.id());
            }
        } else {
            parseIntegerConfig(node, "targetY", "目标 Y 必须是整数", issues);
        }
        validateConditionOutputMode(node, issues);
    }

    private void validateNearTargetBlockCondition(NodeDefinition node, List<ValidationIssue> issues) {
        try {
            double maxDistance = Double.parseDouble(node.config().getOrDefault("maxDistance", ""));
            if (maxDistance <= 0) {
                error(issues, "condition_max_distance_invalid", "最大距离必须大于 0：" + node.id());
            }
        } catch (NumberFormatException exception) {
            error(issues, "condition_max_distance_invalid", "最大距离必须大于 0：" + node.id());
        }
        String horizontalOnly = node.config().getOrDefault("horizontalOnly", "");
        if (!"true".equals(horizontalOnly) && !"false".equals(horizontalOnly)) {
            error(issues, "condition_horizontal_only_invalid", "只计算水平距离必须是是或否：" + node.id());
        }
        validateConditionOutputMode(node, issues);
    }

    private void validateLoopCount(NodeDefinition node, List<ValidationIssue> issues) {
        Integer count = parseIntegerConfig(node, "count", "循环次数必须是整数", issues);
        if (count != null && (count < 1 || count > 100)) {
            error(issues, "loop_count_range", "循环次数必须在 1 到 100 之间：" + node.id());
        }
    }

    private void validateLoopForever(NodeDefinition node, List<ValidationIssue> issues) {
        Integer interval = parseIntegerConfig(node, "intervalSeconds", "每轮间隔必须是整数秒", issues);
        if (interval != null && interval < 1) {
            error(issues, "loop_interval_invalid", "每轮间隔必须大于 0 秒：" + node.id());
        }
    }

    private Integer parseIntegerConfig(NodeDefinition node, String key, String message, List<ValidationIssue> issues) {
        try {
            return Integer.parseInt(node.config().getOrDefault(key, ""));
        } catch (NumberFormatException exception) {
            error(issues, "condition_y_value_invalid", message + "：" + node.id());
            return null;
        }
    }

    private void validateNamespacedConfig(
            NodeDefinition node,
            String key,
            String code,
            String messagePrefix,
            List<ValidationIssue> issues
    ) {
        String value = node.config().getOrDefault(key, "").trim();
        if (value.isEmpty()) {
            error(issues, code, messagePrefix + node.id());
            return;
        }
        if (!NAMESPACED_ID.matcher(value).matches()) {
            error(issues, code, messagePrefix + node.id());
        }
    }

    private void validateRegionNameConfig(NodeDefinition node, List<ValidationIssue> issues) {
        String name = node.config().getOrDefault("regionName", "").trim();
        if (name.isEmpty()) {
            error(issues, "condition_region_name_missing", "区域名称不能为空：" + node.id());
            return;
        }
        if (name.length() > MAX_REGION_NAME_LENGTH) {
            error(issues, "condition_region_name_invalid", "区域名称不能超过 64 个字符：" + node.id());
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            error(issues, "condition_region_name_invalid", "区域名称不能包含换行或控制字符：" + node.id());
        }
    }

    private void validatePlayerTagConfig(NodeDefinition node, List<ValidationIssue> issues) {
        String tag = node.config().getOrDefault("tag", "");
        if (tag.isBlank()) {
            error(issues, "player_tag_missing", "玩家标签不能为空：" + node.id());
            return;
        }
        if (tag.chars().anyMatch(Character::isWhitespace)) {
            error(issues, "player_tag_invalid", "玩家标签不能包含空白字符：" + node.id());
        }
        if (tag.chars().anyMatch(Character::isISOControl)) {
            error(issues, "player_tag_invalid", "玩家标签不能包含控制字符：" + node.id());
        }
        if (tag.length() > 64) {
            error(issues, "player_tag_invalid", "玩家标签不能超过 64 个字符：" + node.id());
        }
    }

    private void validateConditionOutputMode(NodeDefinition node, List<ValidationIssue> issues) {
        if (!ConditionOutputMode.isValid(node.config().get(ConditionOutputMode.CONFIG_KEY))) {
            error(issues, "condition_output_mode_invalid", "条件用途无效：" + node.id());
        }
    }

    private void validateBooleanConfig(NodeDefinition node, String key, List<ValidationIssue> issues) {
        String value = node.config().get(key);
        if (!"true".equals(value) && !"false".equals(value)) {
            error(issues, "condition_boolean_invalid", "条件布尔配置必须是 true 或 false：" + node.id() + "." + key);
        }
    }

    private void validateStateAction(NodeDefinition node, List<ValidationIssue> issues, boolean allowAnyType) {
        validateStateConfig(node, issues);
        if (allowAnyType) {
            validateStateSetValue(node, issues);
        }
        if (!allowAnyType && !"INTEGER".equals(node.config().get("valueType"))) {
            error(issues, "state_add_type", "State Add 只能用于 INTEGER：" + node.id());
        }
        if (!allowAnyType) {
            try {
                Integer.parseInt(node.config().getOrDefault("amount", ""));
            } catch (NumberFormatException exception) {
                error(issues, "state_add_amount_invalid", "State Add 数值无效：" + node.id());
            }
        }
    }

    private void validateStateSetValue(NodeDefinition node, List<ValidationIssue> issues) {
        String type = node.config().get("valueType");
        String value = node.config().get("value");
        if (value == null) {
            error(issues, "state_set_value_missing", "State Set 缺少写入值：" + node.id());
            return;
        }
        if ("BOOLEAN".equals(type) && !("true".equals(value) || "false".equals(value))) {
            error(issues, "state_set_value_invalid", "BOOLEAN 值必须是 true 或 false：" + node.id());
        }
        if ("INTEGER".equals(type)) {
            try {
                Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                error(issues, "state_set_value_invalid", "INTEGER 值无效：" + node.id());
            }
        }
    }

    private void validateStateConfig(NodeDefinition node, List<ValidationIssue> issues) {
        try {
            StateScope.valueOf(node.config().getOrDefault("scope", ""));
            StateValueType.valueOf(node.config().getOrDefault("valueType", ""));
        } catch (IllegalArgumentException exception) {
            error(issues, "state_scope_or_type_invalid", "状态 scope/type 无效：" + node.id());
        }
        if (node.config().getOrDefault("key", "").isBlank()) {
            error(issues, "state_key_missing", "状态 key 缺失：" + node.id());
        }
    }

    private void validateTimer(GraphDefinition graph, NodeDefinition node, List<ValidationIssue> issues) {
        try {
            int seconds = Integer.parseInt(node.config().getOrDefault("durationSeconds", "0"));
            if (seconds <= 0) {
                error(issues, "timer_duration_invalid", "计时器时间必须大于 0 秒：" + node.id());
            }
        } catch (NumberFormatException exception) {
            error(issues, "timer_duration_invalid", "计时器时间无效：" + node.id());
        }
        if (!hasOutgoing(graph, node.id(), "timer_completed") && node.parentContainerId().isBlank()) {
            error(issues, "timer_missing_completed", "计时器缺少完成后的连接：" + node.id());
        }
    }

    private boolean hasOutgoing(GraphDefinition graph, String nodeId, String slotId) {
        return graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(nodeId) && edge.sourceSlotId().equals(slotId));
    }

    private void validateSingleOutgoingPerSlot(GraphDefinition graph, List<ValidationIssue> issues) {
        Set<String> seen = new HashSet<>();
        for (EdgeDefinition edge : graph.edges()) {
            String key = edge.sourceNodeId() + "." + edge.sourceSlotId();
            if (!seen.add(key)) {
                error(issues, "multiple_outgoing_edges", "同一输出槽位暂不支持多条连接：" + key);
            }
        }
    }

    private void detectCycle(GraphDefinition graph, List<ValidationIssue> issues) {
        Map<String, List<String>> outgoing = new HashMap<>();
        for (EdgeDefinition edge : graph.edges()) {
            outgoing.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>()).add(edge.targetNodeId());
        }

        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();
        for (String nodeId : outgoing.keySet()) {
            if (hasCycle(nodeId, outgoing, visited, active)) {
                error(issues, "loop_risk", "图存在明显循环风险。");
                return;
            }
        }
    }

    private boolean hasCycle(String nodeId, Map<String, List<String>> outgoing, Set<String> visited, Set<String> active) {
        if (active.contains(nodeId)) {
            return true;
        }
        if (!visited.add(nodeId)) {
            return false;
        }
        active.add(nodeId);
        for (String next : outgoing.getOrDefault(nodeId, List.of())) {
            if (hasCycle(next, outgoing, visited, active)) {
                return true;
            }
        }
        active.remove(nodeId);
        return false;
    }

    private void error(List<ValidationIssue> issues, String code, String message) {
        issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, code, message));
    }

    private void warning(List<ValidationIssue> issues, String code, String message) {
        issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, code, message));
    }
}
