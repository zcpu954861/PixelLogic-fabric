package com.pixelmc.pixellogic.core.catalog;

import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.EntityTargetRef;
import com.pixelmc.pixellogic.core.model.EntityTargetRequirement;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BuiltInBlockCatalog {
    public static final String TRIGGER_MANUAL_TEST = "trigger.manual_test";
    public static final String CONDITION_STATE_EQUALS = "condition.state.equals";
    public static final String ACTION_MESSAGE_CHAT = "action.message.chat";
    public static final String ACTION_MESSAGE_TITLE = "action.message.title";
    public static final String ACTION_MESSAGE_SUBTITLE = "action.message.subtitle";
    public static final String ACTION_MESSAGE_ACTIONBAR = "action.message.actionbar";
    public static final String CONDITION_ENTITY_HAS_TAG = "condition.entity.has_tag";
    public static final String CONDITION_PLAYER_IS_ADMIN = "condition.player.is_admin";
    public static final String CONDITION_PLAYER_DIMENSION_IS = "condition.player.dimension_is";
    public static final String CONDITION_PLAYER_IN_REGION = "condition.player.in_region";
    public static final String CONDITION_PLAYER_Y_COMPARE = "condition.player.y_compare";
    public static final String CONDITION_TARGET_BLOCK_IS_TYPE = "condition.target_block.is_type";
    public static final String CONDITION_TARGET_BLOCK_IN_REGION = "condition.target_block.in_region";
    public static final String CONDITION_TARGET_BLOCK_Y_COMPARE = "condition.target_block.y_compare";
    public static final String CONDITION_PLAYER_NEAR_TARGET_BLOCK = "condition.player.near_target_block";
    public static final String ACTION_ENTITY_ADD_TAG = "action.entity.add_tag";
    public static final String ACTION_ENTITY_REMOVE_TAG = "action.entity.remove_tag";
    public static final String CONTROL_LOOP_COUNT = "control.loop.count";
    public static final String CONTROL_LOOP_FOREVER = "control.loop.forever";
    public static final String CONTROL_LOOP_UNTIL = "control.loop.until";
    public static final String CONTEXT_ENTITY_EXECUTE_AS = "context.entity.execute_as";
    public static final String STATE_SET = "state.set";
    public static final String STATE_ADD = "state.add";
    public static final String TIMER_WAIT = "timer.wait";
    public static final String DEBUG_LOG = "debug.log";

    private static final BlockCatalog CATALOG = new BlockCatalog(packs(), categories(), blocks());
    private static final Map<String, BlockDefinition> BLOCKS_BY_ID = CATALOG.blocks().stream()
            .collect(Collectors.toUnmodifiableMap(BlockDefinition::id, Function.identity()));
    private static final Map<String, String> ALIASES = CATALOG.blocks().stream()
            .flatMap(block -> block.aliases().stream().map(alias -> Map.entry(alias, block.id())))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    private static final Map<NodeType, String> BLOCK_ID_BY_NODE_TYPE = Map.ofEntries(
            Map.entry(NodeType.MANUAL_TRIGGER, TRIGGER_MANUAL_TEST),
            Map.entry(NodeType.STATE_COMPARE_CONDITION, CONDITION_STATE_EQUALS),
            Map.entry(NodeType.MESSAGE_ACTION, ACTION_MESSAGE_CHAT),
            Map.entry(NodeType.ENTITY_HAS_TAG_CONDITION, CONDITION_ENTITY_HAS_TAG),
            Map.entry(NodeType.PLAYER_IS_ADMIN_CONDITION, CONDITION_PLAYER_IS_ADMIN),
            Map.entry(NodeType.PLAYER_DIMENSION_CONDITION, CONDITION_PLAYER_DIMENSION_IS),
            Map.entry(NodeType.PLAYER_IN_REGION_CONDITION, CONDITION_PLAYER_IN_REGION),
            Map.entry(NodeType.PLAYER_Y_COMPARE_CONDITION, CONDITION_PLAYER_Y_COMPARE),
            Map.entry(NodeType.TARGET_BLOCK_TYPE_CONDITION, CONDITION_TARGET_BLOCK_IS_TYPE),
            Map.entry(NodeType.TARGET_BLOCK_IN_REGION_CONDITION, CONDITION_TARGET_BLOCK_IN_REGION),
            Map.entry(NodeType.TARGET_BLOCK_Y_COMPARE_CONDITION, CONDITION_TARGET_BLOCK_Y_COMPARE),
            Map.entry(NodeType.PLAYER_NEAR_TARGET_BLOCK_CONDITION, CONDITION_PLAYER_NEAR_TARGET_BLOCK),
            Map.entry(NodeType.ENTITY_ADD_TAG_ACTION, ACTION_ENTITY_ADD_TAG),
            Map.entry(NodeType.ENTITY_REMOVE_TAG_ACTION, ACTION_ENTITY_REMOVE_TAG),
            Map.entry(NodeType.CONTROL_LOOP_COUNT, CONTROL_LOOP_COUNT),
            Map.entry(NodeType.CONTROL_LOOP_FOREVER, CONTROL_LOOP_FOREVER),
            Map.entry(NodeType.CONTROL_LOOP_UNTIL, CONTROL_LOOP_UNTIL),
            Map.entry(NodeType.CONTEXT_ENTITY_EXECUTE_AS, CONTEXT_ENTITY_EXECUTE_AS),
            Map.entry(NodeType.STATE_SET_ACTION, STATE_SET),
            Map.entry(NodeType.STATE_ADD_ACTION, STATE_ADD),
            Map.entry(NodeType.TIMER_START_ACTION, TIMER_WAIT),
            Map.entry(NodeType.DEBUG_LOG_ACTION, DEBUG_LOG)
    );

    private BuiltInBlockCatalog() {
    }

    public static BlockCatalog catalog() {
        return CATALOG;
    }

    public static Optional<BlockDefinition> block(String blockId) {
        return Optional.ofNullable(BLOCKS_BY_ID.get(canonicalBlockId(blockId)));
    }

    public static String blockIdFor(NodeType type) {
        return BLOCK_ID_BY_NODE_TYPE.getOrDefault(type, "");
    }

    public static String resolveBlockId(String blockId, NodeType type) {
        String canonical = canonicalBlockId(blockId);
        return canonical.isBlank() ? blockIdFor(type) : canonical;
    }

    public static boolean isConditionBlock(String blockId) {
        return block(blockId).map(definition -> "condition".equals(definition.nodeKind())).orElse(false);
    }

    private static String canonicalBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return "";
        }
        return ALIASES.getOrDefault(blockId, blockId);
    }

    private static List<BlockPackDefinition> packs() {
        return List.of(
                pack("events-triggers", "事件与触发", "从测试或游戏事件开始一条逻辑流。", "⚡", 10),
                pack("logic-flow", "逻辑与流程", "组织循环、等待和流程推进。", "↻", 20),
                pack("player-entity", "玩家与实体", "判断或修改玩家、实体及执行上下文。", "♟", 30),
                pack("location-region", "位置与区域", "判断维度、高度、区域和空间关系。", "⌖", 40),
                pack("block-world", "方块与世界", "读取目标方块与世界事实。", "■", 50),
                pack("presentation-feedback", "表现与反馈", "向玩家或调试视图提供文本反馈。", "✦", 60),
                pack("state-data", "状态与数据", "读取、设置和累加流程状态。", "▤", 70)
        );
    }

    private static List<BlockCategoryDefinition> categories() {
        return List.of(
                category("events-triggers.test-entry", "events-triggers", "测试入口", "从 WebUI 或本地测试开始流程。", "▶", 10),
                category("logic-flow.loops", "logic-flow", "循环", "重复执行内部积木。", "↻", 10),
                category("logic-flow.timing", "logic-flow", "等待与时序", "等待指定时间后继续。", "◷", 20),
                category("player-entity.tags", "player-entity", "标签", "判断或修改玩家与上下文实体标签。", "#", 10),
                category("player-entity.identity-permissions", "player-entity", "身份与权限", "判断玩家身份和权限。", "★", 20),
                category("player-entity.execution-context", "player-entity", "实体上下文", "切换内部积木操作的实体上下文。", "◎", 30),
                category("location-region.dimensions-heights", "location-region", "维度与高度", "判断维度或垂直高度。", "↕", 10),
                category("location-region.regions", "location-region", "区域", "判断玩家或目标方块是否位于区域内。", "▣", 20),
                category("location-region.spatial-relations", "location-region", "空间关系", "判断玩家与目标对象的空间关系。", "⇄", 30),
                category("block-world.target-block", "block-world", "目标方块", "判断测试上下文中的目标方块。", "■", 10),
                category("presentation-feedback.player-messages", "presentation-feedback", "玩家消息", "向玩家发送聊天消息。", "✉", 10),
                category("presentation-feedback.screen-prompts", "presentation-feedback", "屏幕提示", "显示标题、副标题或快捷栏消息。", "▱", 20),
                category("presentation-feedback.diagnostics", "presentation-feedback", "调试诊断", "记录模拟执行和排查信息。", "⌕", 30),
                category("state-data.conditions", "state-data", "状态条件", "读取并比较流程状态。", "?", 10),
                category("state-data.mutations", "state-data", "状态写入", "设置或累加流程状态。", "≔", 20)
        );
    }

    private static List<BlockDefinition> blocks() {
        return List.of(
                block(
                        TRIGGER_MANUAL_TEST,
                        "WebUI 测试运行",
                        "点击测试运行时进入这条流程。",
                        "events-triggers.test-entry",
                        "trigger",
                        NodeType.MANUAL_TRIGGER,
                        Map.of(),
                        List.of(readonly("triggerType", "积木类型", "手动测试触发", "测试运行从这里进入流程。")),
                        "手动测试触发入口。",
                        "",
                        List.of(),
                        List.of(out("started")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY),
                        List.of("manual.test.start")
                ),
                block(
                        CONDITION_STATE_EQUALS,
                        "判断状态是否等于",
                        "比较一个状态值，按通过或失败继续。",
                        "state-data.conditions",
                        "condition",
                        NodeType.STATE_COMPARE_CONDITION,
                        Map.of(
                                "outputMode", ConditionOutputMode.PASS_ONLY.name(),
                                "scope", "PLAYER",
                                "key", "started",
                                "valueType", "BOOLEAN",
                                "expected", "false",
                                "missing", "false"
                        ),
                        List.of(
                                conditionMode(),
                                scope("scope", "作用对象"),
                                text("key", "状态名", true, "例如 started"),
                                hidden("valueType", "BOOLEAN"),
                                bool("expected", "目标值"),
                                bool("missing", "缺失时视为")
                        ),
                        "当「{scope}」的 {key} 等于「{expected}」时走通过分支。",
                        "condition.state.equals",
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY),
                        List.of()
                ),
                block(
                        ACTION_MESSAGE_CHAT,
                        "发送聊天消息",
                        "向当前玩家或模拟玩家发送一条消息。",
                        "presentation-feedback.player-messages",
                        "action",
                        NodeType.MESSAGE_ACTION,
                        Map.of(
                                "target", "CURRENT_PLAYER",
                                "message", RichTextComponentValue.fromPlainText("新消息")
                        ),
                        List.of(
                                readonly("target", "接收者", "CURRENT_PLAYER", "当前发送给触发这条流程的玩家或 WebUI 模拟玩家。"),
                                richText("message", "消息内容", "输入要发送给玩家的文本。")
                        ),
                        "向「{target}」发送「{message.plainText}」。",
                        "action.message.chat",
                        List.of(in("input")),
                        List.of(out("done")),
                        BlockCapabilityLevel.APPROXIMATE_SIMULATION,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.PLAYER_MUTATING, BlockSafetyFlag.REQUIRES_PLAYER),
                        List.of()
                ),
                messageBlock(
                        ACTION_MESSAGE_TITLE,
                        "显示标题",
                        "向当前玩家或模拟玩家显示一条标题。",
                        "向「{target}」显示标题「{message.plainText}」。",
                        "action.message.title"
                ),
                messageBlock(
                        ACTION_MESSAGE_SUBTITLE,
                        "显示副标题",
                        "向当前玩家或模拟玩家显示一条副标题。",
                        "向「{target}」显示副标题「{message.plainText}」。",
                        "action.message.subtitle"
                ),
                messageBlock(
                        ACTION_MESSAGE_ACTIONBAR,
                        "显示快捷栏消息",
                        "向当前玩家或模拟玩家显示一条快捷栏消息。",
                        "向「{target}」显示快捷栏消息「{message.plainText}」。",
                        "action.message.actionbar"
                ),
                targetBlockWithCapabilities(
                        CONDITION_ENTITY_HAS_TAG,
                        "实体是否拥有标签",
                        "按所选实体是否拥有指定标签继续流程。",
                        "player-entity.tags",
                        "condition",
                        NodeType.ENTITY_HAS_TAG_CONDITION,
                        Map.of(
                                "outputMode", ConditionOutputMode.PASS_ONLY.name(),
                                "target", EntityTargetRef.currentEntity().toJson(),
                                "tag", "ready"
                        ),
                        List.of(
                                conditionMode("拥有标签时继续", "不拥有标签时继续", "分开执行"),
                                entityTarget("目标", EntityTargetRef.currentEntity()),
                                text("tag", "标签", false, "例如 ready")
                        ),
                        "按「{target}」是否拥有标签「{tag}」继续。",
                        "condition.entity.has_tag",
                        List.of(BlockCapability.PREDICATE),
                        "「{target}」拥有标签「{tag}」",
                        "「{target}」没有标签「{tag}」",
                        List.of(),
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY),
                        List.of(),
                        EntityTargetRequirement.ANY_ENTITY
                ),
                blockWithCapabilities(
                        CONDITION_PLAYER_IS_ADMIN,
                        "玩家是否为管理员",
                        "按当前模拟玩家是否为管理员继续流程。",
                        "player-entity.identity-permissions",
                        "condition",
                        NodeType.PLAYER_IS_ADMIN_CONDITION,
                        Map.of("outputMode", ConditionOutputMode.PASS_ONLY.name()),
                        List.of(conditionMode("是管理员时继续", "不是管理员时继续", "分开执行")),
                        "按当前玩家是否为管理员继续。",
                        "condition.player.is_admin",
                        List.of(BlockCapability.PREDICATE),
                        "玩家是管理员",
                        "玩家不是管理员",
                        List.of(),
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.APPROXIMATE_SIMULATION,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_PLAYER),
                        List.of()
                ),
                blockWithCapabilities(
                        CONDITION_PLAYER_DIMENSION_IS,
                        "玩家所在维度是否为",
                        "按当前模拟玩家所在维度继续流程。",
                        "location-region.dimensions-heights",
                        "condition",
                        NodeType.PLAYER_DIMENSION_CONDITION,
                        Map.of("outputMode", ConditionOutputMode.PASS_ONLY.name(), "dimensionId", "minecraft:overworld"),
                        List.of(
                                conditionMode("在该维度时继续", "不在该维度时继续", "分开执行"),
                                text("dimensionId", "维度 ID", false, "minecraft:overworld", "维度 ID 示例：minecraft:overworld")
                        ),
                        "按当前玩家所在维度是否为「{dimensionId}」继续。",
                        "condition.player.dimension_is",
                        List.of(BlockCapability.PREDICATE),
                        "玩家位于「{dimensionId}」",
                        "玩家不在维度「{dimensionId}」",
                        List.of(),
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_PLAYER),
                        List.of()
                ),
                blockWithCapabilities(
                        CONDITION_PLAYER_IN_REGION,
                        "玩家是否在区域内",
                        "按当前模拟玩家是否位于测试区域内继续流程。",
                        "location-region.regions",
                        "condition",
                        NodeType.PLAYER_IN_REGION_CONDITION,
                        Map.of("outputMode", ConditionOutputMode.PASS_ONLY.name(), "regionName", "出生区"),
                        List.of(
                                conditionMode("在区域内时继续", "不在区域内时继续", "分开执行"),
                                text("regionName", "区域名称", false, "例如 出生区", "区域名称需要与测试上下文中的区域名称一致。")
                        ),
                        "按当前玩家是否在区域「{regionName}」内继续。",
                        "condition.player.in_region",
                        List.of(BlockCapability.PREDICATE),
                        "玩家在区域「{regionName}」内",
                        "玩家不在区域「{regionName}」内",
                        List.of(),
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_PLAYER, BlockSafetyFlag.REQUIRES_WORLD),
                        List.of()
                ),
                block(
                        CONDITION_PLAYER_Y_COMPARE,
                        "玩家高度是否满足",
                        "按当前模拟玩家的 Y 高度继续流程。",
                        "location-region.dimensions-heights",
                        "condition",
                        NodeType.PLAYER_Y_COMPARE_CONDITION,
                        Map.of(
                                "outputMode", ConditionOutputMode.PASS_ONLY.name(),
                                "compareMode", "AT_OR_ABOVE",
                                "targetY", "64",
                                "minY", "60",
                                "maxY", "80"
                        ),
                        yCompareFields("不低于时继续", "不高于时继续", "分开执行"),
                        "按当前玩家高度是否满足条件继续。",
                        "condition.player.y_compare",
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_PLAYER),
                        List.of()
                ),
                blockWithCapabilities(
                        CONDITION_TARGET_BLOCK_IS_TYPE,
                        "目标方块是否为",
                        "按测试上下文中的目标方块类型继续流程。",
                        "block-world.target-block",
                        "condition",
                        NodeType.TARGET_BLOCK_TYPE_CONDITION,
                        Map.of("outputMode", ConditionOutputMode.PASS_ONLY.name(), "blockId", "minecraft:stone"),
                        List.of(
                                conditionMode("为该方块时继续", "不为该方块时继续", "分开执行"),
                                text("blockId", "方块 ID", false, "minecraft:stone", "方块 ID 示例：minecraft:stone")
                        ),
                        "按目标方块是否为「{blockId}」继续。",
                        "condition.target_block.is_type",
                        List.of(BlockCapability.PREDICATE),
                        "目标方块是「{blockId}」",
                        "目标方块不是「{blockId}」",
                        List.of(),
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_WORLD),
                        List.of()
                ),
                block(
                        CONDITION_TARGET_BLOCK_Y_COMPARE,
                        "目标方块高度是否满足",
                        "按测试上下文中的目标方块 Y 高度继续流程。",
                        "location-region.dimensions-heights",
                        "condition",
                        NodeType.TARGET_BLOCK_Y_COMPARE_CONDITION,
                        Map.of(
                                "outputMode", ConditionOutputMode.PASS_ONLY.name(),
                                "compareMode", "AT_OR_ABOVE",
                                "targetY", "64",
                                "minY", "60",
                                "maxY", "80"
                        ),
                        yCompareFields("不低于时继续", "不高于时继续", "分开执行"),
                        "按目标方块高度是否满足条件继续。",
                        "condition.target_block.y_compare",
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_WORLD),
                        List.of()
                ),
                block(
                        CONDITION_PLAYER_NEAR_TARGET_BLOCK,
                        "玩家是否靠近目标方块",
                        "按当前模拟玩家与目标方块的距离继续流程。",
                        "location-region.spatial-relations",
                        "condition",
                        NodeType.PLAYER_NEAR_TARGET_BLOCK_CONDITION,
                        Map.of(
                                "outputMode", ConditionOutputMode.PASS_ONLY.name(),
                                "maxDistance", "5",
                                "horizontalOnly", "true"
                        ),
                        List.of(
                                conditionMode("靠近时继续", "不靠近时继续", "分开执行"),
                                number("maxDistance", "最大距离", "格", "0.000001", "30000000", "0.5"),
                                segmented("horizontalOnly", "只计算水平距离", List.of(option("true", "是"), option("false", "否")))
                        ),
                        "按当前玩家是否靠近目标方块继续。",
                        "condition.player.near_target_block",
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_PLAYER, BlockSafetyFlag.REQUIRES_WORLD),
                        List.of()
                ),
                block(
                        CONDITION_TARGET_BLOCK_IN_REGION,
                        "目标方块是否在区域内",
                        "按测试上下文中的目标方块是否位于测试区域内继续流程。",
                        "location-region.regions",
                        "condition",
                        NodeType.TARGET_BLOCK_IN_REGION_CONDITION,
                        Map.of("outputMode", ConditionOutputMode.PASS_ONLY.name(), "regionName", "出生区"),
                        List.of(
                                conditionMode("在区域内时继续", "不在区域内时继续", "分开执行"),
                                text("regionName", "区域名称", false, "例如 出生区", "区域名称需要与测试上下文中的区域名称一致。")
                        ),
                        "按目标方块是否在区域「{regionName}」内继续。",
                        "condition.target_block.in_region",
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_WORLD),
                        List.of()
                ),
                targetBlock(
                        ACTION_ENTITY_ADD_TAG,
                        "添加实体标签",
                        "给所选实体添加一个标签。",
                        "player-entity.tags",
                        "action",
                        NodeType.ENTITY_ADD_TAG_ACTION,
                        Map.of("target", EntityTargetRef.currentEntity().toJson(), "tag", "ready"),
                        List.of(entityTarget("目标", EntityTargetRef.currentEntity()), text("tag", "标签", false, "例如 ready")),
                        "给「{target}」添加标签「{tag}」。",
                        "action.entity.add_tag",
                        List.of(),
                        List.of(in("input")),
                        List.of(out("done")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.ENTITY_MUTATING),
                        List.of(),
                        EntityTargetRequirement.ANY_ENTITY
                ),
                targetBlock(
                        ACTION_ENTITY_REMOVE_TAG,
                        "移除实体标签",
                        "从所选实体移除一个标签。",
                        "player-entity.tags",
                        "action",
                        NodeType.ENTITY_REMOVE_TAG_ACTION,
                        Map.of("target", EntityTargetRef.currentEntity().toJson(), "tag", "ready"),
                        List.of(entityTarget("目标", EntityTargetRef.currentEntity()), text("tag", "标签", false, "例如 ready")),
                        "移除「{target}」的标签「{tag}」。",
                        "action.entity.remove_tag",
                        List.of(),
                        List.of(in("input")),
                        List.of(out("done")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.ENTITY_MUTATING),
                        List.of(),
                        EntityTargetRequirement.ANY_ENTITY
                ),
                block(
                        CONTROL_LOOP_COUNT,
                        "循环次数",
                        "重复执行内部积木指定次数，然后继续后续流程。",
                        "logic-flow.loops",
                        "control",
                        NodeType.CONTROL_LOOP_COUNT,
                        Map.of("count", "3"),
                        List.of(integer("count", "循环次数", "次", "1", "100", "1")),
                        "循环 {count} 次后继续。",
                        "control.loop.count",
                        List.of("body"),
                        List.of(in("input")),
                        List.of(out("done")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY),
                        List.of()
                ),
                block(
                        CONTROL_LOOP_FOREVER,
                        "无限循环",
                        "按安全模拟上限重复执行内部积木；正常情况下不继续外部链。",
                        "logic-flow.loops",
                        "control",
                        NodeType.CONTROL_LOOP_FOREVER,
                        Map.of("intervalSeconds", "1"),
                        List.of(integer("intervalSeconds", "每轮间隔", "秒", "1", "86400", "1")),
                        "无限循环内部逻辑，每轮间隔 {intervalSeconds} 秒。",
                        "control.loop.forever",
                        List.of("body"),
                        List.of(in("input")),
                        List.of(),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY),
                        List.of()
                ),
                blockWithCapabilities(
                        CONTROL_LOOP_UNTIL,
                        "循环直到",
                        "每轮开始前检查结束条件；全部成立时退出，否则执行内部积木。",
                        "logic-flow.loops",
                        "control",
                        NodeType.CONTROL_LOOP_UNTIL,
                        Map.of(),
                        List.of(readonly(
                                "conditionRack",
                                "结束条件",
                                "在条件架中管理",
                                "双击循环直到后新增、删除或取反条件槽。"
                        )),
                        "直到全部结束条件成立，否则重复内部积木。",
                        "control.loop.until",
                        List.of(BlockCapability.PREDICATE_RACK),
                        "",
                        "",
                        List.of("body"),
                        List.of(in("input")),
                        List.of(out("done")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY),
                        List.of()
                ),
                targetBlock(
                        CONTEXT_ENTITY_EXECUTE_AS,
                        "以实体为上下文执行",
                        "在内部积木执行期间切换当前实体上下文，完成后恢复外层实体。",
                        "player-entity.execution-context",
                        "control",
                        NodeType.CONTEXT_ENTITY_EXECUTE_AS,
                        Map.of("target", EntityTargetRef.conditionSubject().toJson()),
                        List.of(entityTarget("目标", EntityTargetRef.conditionSubject())),
                        "以「{target}」为上下文执行。",
                        "context.entity.execute_as",
                        List.of("body"),
                        List.of(in("input")),
                        List.of(out("done")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY),
                        List.of(),
                        EntityTargetRequirement.ANY_ENTITY
                ),
                block(
                        STATE_SET,
                        "设置状态",
                        "把一个状态写成指定值。",
                        "state-data.mutations",
                        "state",
                        NodeType.STATE_SET_ACTION,
                        Map.of("scope", "PLAYER", "key", "started", "valueType", "BOOLEAN", "value", "true"),
                        List.of(
                                scope("scope", "作用对象"),
                                text("key", "状态名", false, "例如 started"),
                                select("valueType", "数据类型", List.of(option("BOOLEAN", "是或否"), option("INTEGER", "数字"), option("STRING", "文本"))),
                                segmented("value", "设置为", List.of(option("true", "是"), option("false", "否")))
                        ),
                        "把「{scope}」的 {key} 设置为「{value}」。",
                        "state.set",
                        List.of(in("input")),
                        List.of(out("done")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.STATE_MUTATING),
                        List.of()
                ),
                block(
                        STATE_ADD,
                        "累加状态",
                        "把数字状态增加指定数值。",
                        "state-data.mutations",
                        "state",
                        NodeType.STATE_ADD_ACTION,
                        Map.of("scope", "PLAYER", "key", "start_count", "valueType", "INTEGER", "amount", "1"),
                        List.of(
                                scope("scope", "作用对象"),
                                text("key", "状态名", false, "例如 start_count"),
                                hidden("valueType", "INTEGER"),
                                integer("amount", "增加数值", "", "-999999", "999999", "1")
                        ),
                        "把「{scope}」的 {key} 增加 {amount}。",
                        "state.add",
                        List.of(in("input")),
                        List.of(out("done")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.STATE_MUTATING),
                        List.of()
                ),
                block(
                        TIMER_WAIT,
                        "等待一段时间",
                        "等待指定秒数后继续执行。",
                        "logic-flow.timing",
                        "timer",
                        NodeType.TIMER_START_ACTION,
                        Map.of("durationSeconds", "30"),
                        List.of(integer("durationSeconds", "等待时间", "秒", "1", "86400", "1")),
                        "等待 {durationSeconds} 秒后继续。",
                        "timer.wait",
                        List.of(in("input")),
                        List.of(out("timer_completed")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY),
                        List.of()
                ),
                block(
                        DEBUG_LOG,
                        "调试记录",
                        "在模拟执行记录里写入一条调试信息。",
                        "presentation-feedback.diagnostics",
                        "debug",
                        NodeType.DEBUG_LOG_ACTION,
                        Map.of("message", "调试记录"),
                        List.of(textarea("message", "记录内容", true, "只写入模拟 trace，不发送给玩家。")),
                        "记录：{message}",
                        "debug.log",
                        List.of(in("input")),
                        List.of(out("done")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY),
                        List.of()
                )
        );
    }

    private static BlockPackDefinition pack(String id, String displayName, String description, String icon, int order) {
        return new BlockPackDefinition(id, displayName, description, icon, order);
    }

    private static BlockCategoryDefinition category(
            String id,
            String packId,
            String displayName,
            String description,
            String icon,
            int order
    ) {
        return new BlockCategoryDefinition(id, packId, displayName, description, icon, order, true);
    }

    private static BlockDefinition block(
            String id,
            String displayName,
            String description,
            String categoryId,
            String nodeKind,
            NodeType nodeType,
            Map<String, String> defaultConfig,
            List<BlockFormFieldDefinition> formSchema,
            String summaryTemplate,
            String summaryFormatter,
            List<SlotDefinition> inputSlots,
            List<SlotDefinition> outputSlots,
            BlockCapabilityLevel simulationCapability,
            BlockCapabilityLevel mcCapability,
            List<BlockSafetyFlag> safetyFlags,
            List<String> aliases
    ) {
        return block(
                id,
                displayName,
                description,
                categoryId,
                nodeKind,
                nodeType,
                defaultConfig,
                formSchema,
                summaryTemplate,
                summaryFormatter,
                List.of(),
                inputSlots,
                outputSlots,
                simulationCapability,
                mcCapability,
                safetyFlags,
                aliases
        );
    }

    private static BlockDefinition block(
            String id,
            String displayName,
            String description,
            String categoryId,
            String nodeKind,
            NodeType nodeType,
            Map<String, String> defaultConfig,
            List<BlockFormFieldDefinition> formSchema,
            String summaryTemplate,
            String summaryFormatter,
            List<String> containerSlots,
            List<SlotDefinition> inputSlots,
            List<SlotDefinition> outputSlots,
            BlockCapabilityLevel simulationCapability,
            BlockCapabilityLevel mcCapability,
            List<BlockSafetyFlag> safetyFlags,
            List<String> aliases
    ) {
        return blockWithCapabilities(
                id,
                displayName,
                description,
                categoryId,
                nodeKind,
                nodeType,
                defaultConfig,
                formSchema,
                summaryTemplate,
                summaryFormatter,
                List.of(),
                "",
                "",
                containerSlots,
                inputSlots,
                outputSlots,
                simulationCapability,
                mcCapability,
                safetyFlags,
                aliases
        );
    }

    private static BlockDefinition blockWithCapabilities(
            String id,
            String displayName,
            String description,
            String categoryId,
            String nodeKind,
            NodeType nodeType,
            Map<String, String> defaultConfig,
            List<BlockFormFieldDefinition> formSchema,
            String summaryTemplate,
            String summaryFormatter,
            List<BlockCapability> capabilities,
            String predicateSummaryTemplate,
            String predicateNegatedSummaryTemplate,
            List<String> containerSlots,
            List<SlotDefinition> inputSlots,
            List<SlotDefinition> outputSlots,
            BlockCapabilityLevel simulationCapability,
            BlockCapabilityLevel mcCapability,
            List<BlockSafetyFlag> safetyFlags,
            List<String> aliases
    ) {
        return blockWithCapabilities(
                id,
                displayName,
                description,
                categoryId,
                nodeKind,
                nodeType,
                defaultConfig,
                formSchema,
                summaryTemplate,
                summaryFormatter,
                capabilities,
                predicateSummaryTemplate,
                predicateNegatedSummaryTemplate,
                containerSlots,
                inputSlots,
                outputSlots,
                simulationCapability,
                mcCapability,
                safetyFlags,
                aliases,
                null
        );
    }

    private static BlockDefinition targetBlock(
            String id,
            String displayName,
            String description,
            String categoryId,
            String nodeKind,
            NodeType nodeType,
            Map<String, String> defaultConfig,
            List<BlockFormFieldDefinition> formSchema,
            String summaryTemplate,
            String summaryFormatter,
            List<String> containerSlots,
            List<SlotDefinition> inputSlots,
            List<SlotDefinition> outputSlots,
            BlockCapabilityLevel simulationCapability,
            BlockCapabilityLevel mcCapability,
            List<BlockSafetyFlag> safetyFlags,
            List<String> aliases,
            EntityTargetRequirement requirement
    ) {
        return blockWithCapabilities(
                id, displayName, description, categoryId, nodeKind, nodeType, defaultConfig, formSchema,
                summaryTemplate, summaryFormatter, List.of(), "", "", containerSlots, inputSlots, outputSlots,
                simulationCapability, mcCapability, safetyFlags, aliases, requirement
        );
    }

    private static BlockDefinition targetBlockWithCapabilities(
            String id,
            String displayName,
            String description,
            String categoryId,
            String nodeKind,
            NodeType nodeType,
            Map<String, String> defaultConfig,
            List<BlockFormFieldDefinition> formSchema,
            String summaryTemplate,
            String summaryFormatter,
            List<BlockCapability> capabilities,
            String predicateSummaryTemplate,
            String predicateNegatedSummaryTemplate,
            List<String> containerSlots,
            List<SlotDefinition> inputSlots,
            List<SlotDefinition> outputSlots,
            BlockCapabilityLevel simulationCapability,
            BlockCapabilityLevel mcCapability,
            List<BlockSafetyFlag> safetyFlags,
            List<String> aliases,
            EntityTargetRequirement requirement
    ) {
        return blockWithCapabilities(
                id, displayName, description, categoryId, nodeKind, nodeType, defaultConfig, formSchema,
                summaryTemplate, summaryFormatter, capabilities, predicateSummaryTemplate,
                predicateNegatedSummaryTemplate, containerSlots, inputSlots, outputSlots, simulationCapability,
                mcCapability, safetyFlags, aliases, requirement
        );
    }

    private static BlockDefinition blockWithCapabilities(
            String id,
            String displayName,
            String description,
            String categoryId,
            String nodeKind,
            NodeType nodeType,
            Map<String, String> defaultConfig,
            List<BlockFormFieldDefinition> formSchema,
            String summaryTemplate,
            String summaryFormatter,
            List<BlockCapability> capabilities,
            String predicateSummaryTemplate,
            String predicateNegatedSummaryTemplate,
            List<String> containerSlots,
            List<SlotDefinition> inputSlots,
            List<SlotDefinition> outputSlots,
            BlockCapabilityLevel simulationCapability,
            BlockCapabilityLevel mcCapability,
            List<BlockSafetyFlag> safetyFlags,
            List<String> aliases,
            EntityTargetRequirement requirement
    ) {
        return new BlockDefinition(
                id,
                1,
                displayName,
                description,
                categoryId,
                categoryId,
                List.of(),
                aliases,
                searchKeywords(id),
                capabilities,
                nodeKind,
                nodeType,
                defaultConfig,
                formSchema,
                summaryTemplate,
                summaryFormatter,
                predicateSummaryTemplate,
                predicateNegatedSummaryTemplate,
                containerSlots,
                inputSlots,
                outputSlots,
                simulationCapability,
                mcCapability,
                safetyFlags,
                false,
                false,
                BlockLibraryVisibility.BROWSE,
                requirement
        );
    }

    private static BlockDefinition messageBlock(String id, String displayName, String description, String summaryTemplate, String summaryFormatter) {
        return block(
                id,
                displayName,
                description,
                "presentation-feedback.screen-prompts",
                "action",
                NodeType.MESSAGE_ACTION,
                Map.of(
                        "target", "CURRENT_PLAYER",
                        "message", RichTextComponentValue.fromPlainText("新消息")
                ),
                List.of(
                        readonly("target", "接收者", "CURRENT_PLAYER", "当前发送给触发这条流程的玩家或 WebUI 模拟玩家。"),
                        richText("message", "消息内容", "输入要显示给玩家的文本。")
                ),
                summaryTemplate,
                summaryFormatter,
                List.of(),
                List.of(in("input")),
                List.of(out("done")),
                BlockCapabilityLevel.APPROXIMATE_SIMULATION,
                BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                List.of(BlockSafetyFlag.PLAYER_MUTATING, BlockSafetyFlag.REQUIRES_PLAYER),
                List.of()
        );
    }

    private static List<String> searchKeywords(String id) {
        return switch (id) {
            case TRIGGER_MANUAL_TEST -> List.of("手动", "测试", "触发", "manual");
            case CONDITION_STATE_EQUALS -> List.of("变量", "状态", "score", "计分", "state equals");
            case STATE_SET -> List.of("变量", "状态", "score", "计分", "set state");
            case STATE_ADD -> List.of("变量", "状态", "score", "计分", "add state");
            case ACTION_MESSAGE_CHAT -> List.of("消息", "聊天", "富文本", "chat");
            case ACTION_MESSAGE_TITLE -> List.of("消息", "屏幕提示", "title");
            case ACTION_MESSAGE_SUBTITLE -> List.of("消息", "屏幕提示", "subtitle");
            case ACTION_MESSAGE_ACTIONBAR -> List.of("消息", "屏幕提示", "actionbar");
            case CONDITION_ENTITY_HAS_TAG, ACTION_ENTITY_ADD_TAG, ACTION_ENTITY_REMOVE_TAG ->
                    List.of("实体标签", "玩家标签", "标签", "entity tag");
            case CONDITION_PLAYER_IS_ADMIN -> List.of("管理员", "权限");
            case CONDITION_PLAYER_DIMENSION_IS -> List.of("世界", "维度");
            case CONDITION_PLAYER_IN_REGION, CONDITION_TARGET_BLOCK_IN_REGION -> List.of("区域", "范围");
            case CONDITION_PLAYER_Y_COMPARE, CONDITION_TARGET_BLOCK_Y_COMPARE -> List.of("高度", "坐标", "Y");
            case CONDITION_TARGET_BLOCK_IS_TYPE -> List.of("方块", "目标方块");
            case CONDITION_PLAYER_NEAR_TARGET_BLOCK -> List.of("距离", "附近", "靠近");
            case CONTROL_LOOP_COUNT -> List.of("循环", "loop", "repeat");
            case CONTROL_LOOP_FOREVER -> List.of("循环", "loop", "while");
            case CONTROL_LOOP_UNTIL -> List.of("循环", "loop", "until");
            case CONTEXT_ENTITY_EXECUTE_AS -> List.of("实体上下文", "执行实体", "as");
            case TIMER_WAIT -> List.of("等待", "延迟", "timer", "delay");
            case DEBUG_LOG -> List.of("调试", "诊断", "日志", "log");
            default -> List.of();
        };
    }

    private static SlotDefinition in(String id) {
        return new SlotDefinition(id, SlotDirection.INPUT, EdgeType.CONTROL);
    }

    private static SlotDefinition out(String id) {
        return new SlotDefinition(id, SlotDirection.OUTPUT, EdgeType.CONTROL);
    }

    private static BlockFormFieldDefinition text(String key, String label, boolean full, String placeholder) {
        return text(key, label, full, placeholder, "");
    }

    private static BlockFormFieldDefinition text(String key, String label, boolean full, String placeholder, String description) {
        return field(key, "string", label, description, true, "", placeholder, List.of(), "", "", "", full ? "fullWidth" : "", "");
    }

    private static BlockFormFieldDefinition textarea(String key, String label, boolean full, String description) {
        return field(key, "textarea", label, description, true, "", "", List.of(), "", "", "", full ? "fullWidth textareaRows:3" : "textareaRows:3", "");
    }

    private static BlockFormFieldDefinition bool(String key, String label) {
        return field(key, "boolean", label, "", true, "", "", List.of(option("true", "是"), option("false", "否")), "", "", "", "segmented", "");
    }

    private static BlockFormFieldDefinition select(String key, String label, List<BlockFormFieldDefinition.FieldOption> options) {
        return field(key, "select", label, "", true, "", "", options, "", "", "", "", "");
    }

    private static BlockFormFieldDefinition segmented(String key, String label, List<BlockFormFieldDefinition.FieldOption> options) {
        return field(key, "segmented", label, "", true, "", "", options, "", "", "", "segmented", "");
    }

    private static BlockFormFieldDefinition conditionMode() {
        return conditionMode("满足时继续", "不满足时继续", "分成两路");
    }

    private static BlockFormFieldDefinition conditionMode(String passLabel, String failLabel, String branchLabel) {
        return field(
                ConditionOutputMode.CONFIG_KEY,
                "segmented",
                "条件用途",
                "选择条件满足、不满足或双分支时如何继续流程。",
                false,
                ConditionOutputMode.BRANCH.name(),
                "",
                List.of(
                        option(ConditionOutputMode.PASS_ONLY.name(), passLabel),
                        option(ConditionOutputMode.FAIL_ONLY.name(), failLabel),
                        option(ConditionOutputMode.BRANCH.name(), branchLabel)
                ),
                "",
                "",
                "",
                "segmented fullWidth",
                ""
        );
    }

    private static List<BlockFormFieldDefinition> yCompareFields(String passLabel, String failLabel, String branchLabel) {
        return List.of(
                conditionMode(passLabel, failLabel, branchLabel),
                select("compareMode", "判断方式", List.of(
                        option("AT_OR_ABOVE", "不低于或不高于"),
                        option("AT_OR_BELOW", "不低于或不高于"),
                        option("EQUAL", "等于"),
                        option("BETWEEN", "在范围内")
                )),
                conditionalInteger("targetY", "目标 Y", "showWhen:compareMode=AT_OR_ABOVE,AT_OR_BELOW,EQUAL"),
                conditionalInteger("minY", "最低 Y 值", "showWhen:compareMode=BETWEEN"),
                conditionalInteger("maxY", "最高 Y 值", "showWhen:compareMode=BETWEEN")
        );
    }

    private static BlockFormFieldDefinition scope(String key, String label) {
        return field(key, "scope", label, "", true, "PLAYER", "", List.of(option("PLAYER", "玩家"), option("GLOBAL", "全局"), option("SESSION", "当前会话")), "", "", "", "", "");
    }

    private static BlockFormFieldDefinition integer(String key, String label, String suffix, String min, String max, String step) {
        return field(key, "integer", label, "", true, "", "", List.of(), min, max, step, "", suffix);
    }

    private static BlockFormFieldDefinition conditionalInteger(String key, String label, String ui) {
        return field(key, "integer", label, "", false, "", "", List.of(), "-2048", "4096", "1", ui, "");
    }

    private static BlockFormFieldDefinition number(String key, String label, String suffix, String min, String max, String step) {
        return field(key, "number", label, "", true, "", "", List.of(), min, max, step, "", suffix);
    }

    private static BlockFormFieldDefinition readonly(String key, String label, String defaultValue, String description) {
        return field(key, "readonly", label, description, false, defaultValue, "", List.of(), "", "", "", "readonlyBadge", "");
    }

    private static BlockFormFieldDefinition hidden(String key, String defaultValue) {
        return field(key, "hidden", key, "", false, defaultValue, "", List.of(), "", "", "", "", "");
    }

    private static BlockFormFieldDefinition richText(String key, String label, String description) {
        return field(key, "rich_text_component", label, description, true, RichTextComponentValue.fromPlainText(""), "欢迎开始游戏\n任务开始！", List.of(), "", "", "", "fullWidth textareaRows:4", "");
    }

    private static BlockFormFieldDefinition entityTarget(String label, EntityTargetRef defaultValue) {
        return field(
                "target",
                "entity_target",
                label,
                "选择当前实体、条件主体、目标实体或指定在线玩家。",
                true,
                defaultValue.toJson(),
                "",
                List.of(),
                "",
                "",
                "",
                "fullWidth",
                ""
        );
    }

    private static BlockFormFieldDefinition field(
            String key,
            String type,
            String label,
            String description,
            boolean required,
            String defaultValue,
            String placeholder,
            List<BlockFormFieldDefinition.FieldOption> options,
            String min,
            String max,
            String step,
            String ui,
            String suffix
    ) {
        return new BlockFormFieldDefinition(key, type, label, description, required, defaultValue, placeholder, options, min, max, step, ui, suffix);
    }

    private static BlockFormFieldDefinition.FieldOption option(String value, String label) {
        return new BlockFormFieldDefinition.FieldOption(value, label);
    }
}
