package com.pixelmc.pixellogic.core.catalog;

import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.ConditionOutputMode;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.SlotDirection;

import java.util.EnumMap;
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
    public static final String CONDITION_PLAYER_HAS_TAG = "condition.player.has_tag";
    public static final String CONDITION_PLAYER_IS_ADMIN = "condition.player.is_admin";
    public static final String CONDITION_PLAYER_DIMENSION_IS = "condition.player.dimension_is";
    public static final String CONDITION_PLAYER_IN_REGION = "condition.player.in_region";
    public static final String CONDITION_PLAYER_Y_COMPARE = "condition.player.y_compare";
    public static final String CONDITION_TARGET_BLOCK_IS_TYPE = "condition.target_block.is_type";
    public static final String CONDITION_TARGET_BLOCK_IN_REGION = "condition.target_block.in_region";
    public static final String CONDITION_TARGET_BLOCK_Y_COMPARE = "condition.target_block.y_compare";
    public static final String CONDITION_PLAYER_NEAR_TARGET_BLOCK = "condition.player.near_target_block";
    public static final String ACTION_PLAYER_ADD_TAG = "action.player.add_tag";
    public static final String ACTION_PLAYER_REMOVE_TAG = "action.player.remove_tag";
    public static final String STATE_SET = "state.set";
    public static final String STATE_ADD = "state.add";
    public static final String TIMER_WAIT = "timer.wait";
    public static final String DEBUG_LOG = "debug.log";

    private static final BlockCatalog CATALOG = new BlockCatalog(categories(), subcategories(), blocks());
    private static final Map<String, BlockDefinition> BLOCKS_BY_ID = CATALOG.blocks().stream()
            .collect(Collectors.toUnmodifiableMap(BlockDefinition::id, Function.identity()));
    private static final Map<String, String> ALIASES = CATALOG.blocks().stream()
            .flatMap(block -> block.aliases().stream().map(alias -> Map.entry(alias, block.id())))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    private static final Map<NodeType, String> BLOCK_ID_BY_NODE_TYPE = blockIdByNodeType();

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

    public static Optional<NodeType> nodeTypeFor(String blockId) {
        return block(blockId).map(BlockDefinition::nodeType);
    }

    private static String canonicalBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return "";
        }
        return ALIASES.getOrDefault(blockId, blockId);
    }

    private static Map<NodeType, String> blockIdByNodeType() {
        EnumMap<NodeType, String> result = new EnumMap<>(NodeType.class);
        CATALOG.blocks().forEach(block -> result.putIfAbsent(block.nodeType(), block.id()));
        return Map.copyOf(result);
    }

    private static List<BlockCategoryDefinition> categories() {
        return List.of(
                category("trigger", "触发事件", "从玩家操作或测试入口开始一条逻辑流。", 10),
                category("condition", "条件判断", "按状态或上下文决定走哪条分支。", 20),
                category("player", "玩家操作", "修改当前玩家的模拟属性。", 30),
                category("message", "消息显示", "向玩家或调试视图展示文本反馈。", 40),
                category("state", "状态数据", "读取或修改流程运行时状态。", 50),
                category("timer", "时间调度", "等待一段时间后继续流程。", 60),
                category("debug", "调试诊断", "记录测试和排查信息。", 70)
        );
    }

    private static List<BlockSubcategoryDefinition> subcategories() {
        return List.of(
                subcategory("trigger.manual", "trigger", "手动测试", "用于 WebUI 和本地验证的测试入口。", 10),
                subcategory("condition.state", "condition", "状态条件", "基于玩家、全局或会话状态做判断。", 10),
                subcategory("condition.player", "condition", "玩家条件", "基于当前模拟玩家做判断。", 20),
                subcategory("condition.region", "condition", "区域条件", "基于测试上下文中的区域事实做判断。", 30),
                subcategory("condition.block", "condition", "方块条件", "基于测试上下文中的目标方块事实做判断。", 40),
                subcategory("condition.spatial", "condition", "空间关系", "基于玩家和目标方块之间的位置关系做判断。", 50),
                subcategory("player.tag", "player", "标签", "写入当前模拟玩家的标签。", 10),
                subcategory("message.player", "message", "玩家消息", "面向玩家的文本反馈。", 10),
                subcategory("message.screen", "message", "屏幕提示", "显示标题、副标题或快捷栏消息。", 20),
                subcategory("state.write", "state", "写入状态", "设置或累加状态值。", 10),
                subcategory("timer.basic", "timer", "基础等待", "等待后继续执行。", 10),
                subcategory("debug.basic", "debug", "调试输出", "记录模拟执行信息。", 10)
        );
    }

    private static List<BlockDefinition> blocks() {
        return List.of(
                block(
                        TRIGGER_MANUAL_TEST,
                        "WebUI 测试运行",
                        "点击测试运行时进入这条流程。",
                        "trigger",
                        "trigger.manual",
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
                        "condition",
                        "condition.state",
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
                        "message",
                        "message.player",
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
                block(
                        CONDITION_PLAYER_HAS_TAG,
                        "玩家是否拥有标签",
                        "按当前模拟玩家是否拥有指定标签继续流程。",
                        "condition",
                        "condition.player",
                        "condition",
                        NodeType.PLAYER_HAS_TAG_CONDITION,
                        Map.of("outputMode", ConditionOutputMode.PASS_ONLY.name(), "tag", "runner"),
                        List.of(conditionMode("拥有标签时继续", "不拥有标签时继续", "分开执行"), text("tag", "标签", false, "例如 runner")),
                        "按当前玩家是否拥有标签「{tag}」继续。",
                        "condition.player.has_tag",
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_PLAYER),
                        List.of()
                ),
                block(
                        CONDITION_PLAYER_IS_ADMIN,
                        "玩家是否为管理员",
                        "按当前模拟玩家是否为管理员继续流程。",
                        "condition",
                        "condition.player",
                        "condition",
                        NodeType.PLAYER_IS_ADMIN_CONDITION,
                        Map.of("outputMode", ConditionOutputMode.PASS_ONLY.name()),
                        List.of(conditionMode("是管理员时继续", "不是管理员时继续", "分开执行")),
                        "按当前玩家是否为管理员继续。",
                        "condition.player.is_admin",
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.APPROXIMATE_SIMULATION,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_PLAYER),
                        List.of()
                ),
                block(
                        CONDITION_PLAYER_DIMENSION_IS,
                        "玩家所在维度是否为",
                        "按当前模拟玩家所在维度继续流程。",
                        "condition",
                        "condition.player",
                        "condition",
                        NodeType.PLAYER_DIMENSION_CONDITION,
                        Map.of("outputMode", ConditionOutputMode.PASS_ONLY.name(), "dimensionId", "minecraft:overworld"),
                        List.of(
                                conditionMode("在该维度时继续", "不在该维度时继续", "分开执行"),
                                text("dimensionId", "维度 ID", false, "minecraft:overworld", "维度 ID 示例：minecraft:overworld")
                        ),
                        "按当前玩家所在维度是否为「{dimensionId}」继续。",
                        "condition.player.dimension_is",
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_PLAYER),
                        List.of()
                ),
                block(
                        CONDITION_PLAYER_IN_REGION,
                        "玩家是否在区域内",
                        "按当前模拟玩家是否位于测试区域内继续流程。",
                        "condition",
                        "condition.region",
                        "condition",
                        NodeType.PLAYER_IN_REGION_CONDITION,
                        Map.of("outputMode", ConditionOutputMode.PASS_ONLY.name(), "regionName", "出生区"),
                        List.of(
                                conditionMode("在区域内时继续", "不在区域内时继续", "分开执行"),
                                text("regionName", "区域名称", false, "例如 出生区", "区域名称需要与测试上下文中的区域名称一致。")
                        ),
                        "按当前玩家是否在区域「{regionName}」内继续。",
                        "condition.player.in_region",
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
                        "condition",
                        "condition.player",
                        "condition",
                        NodeType.PLAYER_Y_COMPARE_CONDITION,
                        Map.of(
                                "outputMode", ConditionOutputMode.PASS_ONLY.name(),
                                "compareMode", "AT_OR_ABOVE",
                                "targetY", "64",
                                "minY", "60",
                                "maxY", "80"
                        ),
                        yCompareFields("满足高度时继续", "不满足高度时继续", "分开执行"),
                        "按当前玩家高度是否满足条件继续。",
                        "condition.player.y_compare",
                        List.of(in("input")),
                        List.of(out("pass"), out("fail")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.READ_ONLY, BlockSafetyFlag.REQUIRES_PLAYER),
                        List.of()
                ),
                block(
                        CONDITION_TARGET_BLOCK_IS_TYPE,
                        "目标方块是否为",
                        "按测试上下文中的目标方块类型继续流程。",
                        "condition",
                        "condition.block",
                        "condition",
                        NodeType.TARGET_BLOCK_TYPE_CONDITION,
                        Map.of("outputMode", ConditionOutputMode.PASS_ONLY.name(), "blockId", "minecraft:stone"),
                        List.of(
                                conditionMode("为该方块时继续", "不为该方块时继续", "分开执行"),
                                text("blockId", "方块 ID", false, "minecraft:stone", "方块 ID 示例：minecraft:stone")
                        ),
                        "按目标方块是否为「{blockId}」继续。",
                        "condition.target_block.is_type",
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
                        "condition",
                        "condition.block",
                        "condition",
                        NodeType.TARGET_BLOCK_Y_COMPARE_CONDITION,
                        Map.of(
                                "outputMode", ConditionOutputMode.PASS_ONLY.name(),
                                "compareMode", "AT_OR_ABOVE",
                                "targetY", "64",
                                "minY", "60",
                                "maxY", "80"
                        ),
                        yCompareFields("满足高度时继续", "不满足高度时继续", "分开执行"),
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
                        "condition",
                        "condition.spatial",
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
                        "condition",
                        "condition.region",
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
                block(
                        ACTION_PLAYER_ADD_TAG,
                        "添加玩家标签",
                        "给当前模拟玩家添加一个标签。",
                        "player",
                        "player.tag",
                        "action",
                        NodeType.PLAYER_ADD_TAG_ACTION,
                        Map.of("tag", "runner"),
                        List.of(text("tag", "标签", false, "例如 runner")),
                        "给当前玩家添加标签「{tag}」。",
                        "action.player.add_tag",
                        List.of(in("input")),
                        List.of(out("done")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.PLAYER_MUTATING, BlockSafetyFlag.REQUIRES_PLAYER),
                        List.of()
                ),
                block(
                        ACTION_PLAYER_REMOVE_TAG,
                        "移除玩家标签",
                        "从当前模拟玩家移除一个标签。",
                        "player",
                        "player.tag",
                        "action",
                        NodeType.PLAYER_REMOVE_TAG_ACTION,
                        Map.of("tag", "runner"),
                        List.of(text("tag", "标签", false, "例如 runner")),
                        "移除当前玩家的标签「{tag}」。",
                        "action.player.remove_tag",
                        List.of(in("input")),
                        List.of(out("done")),
                        BlockCapabilityLevel.FULLY_SIMULATABLE,
                        BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                        List.of(BlockSafetyFlag.PLAYER_MUTATING, BlockSafetyFlag.REQUIRES_PLAYER),
                        List.of()
                ),
                block(
                        STATE_SET,
                        "设置状态",
                        "把一个状态写成指定值。",
                        "state",
                        "state.write",
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
                        "state",
                        "state.write",
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
                        "timer",
                        "timer.basic",
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
                        "debug",
                        "debug.basic",
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

    private static BlockCategoryDefinition category(String id, String displayName, String description, int order) {
        return new BlockCategoryDefinition(id, displayName, description, order, true);
    }

    private static BlockSubcategoryDefinition subcategory(String id, String categoryId, String displayName, String description, int order) {
        return new BlockSubcategoryDefinition(id, categoryId, displayName, description, order);
    }

    private static BlockDefinition block(
            String id,
            String displayName,
            String description,
            String categoryId,
            String subcategoryId,
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
        return new BlockDefinition(
                id,
                1,
                displayName,
                description,
                categoryId,
                subcategoryId,
                List.of(),
                nodeKind,
                nodeType,
                defaultConfig,
                formSchema,
                summaryTemplate,
                summaryFormatter,
                inputSlots,
                outputSlots,
                simulationCapability,
                mcCapability,
                safetyFlags,
                false,
                false,
                aliases
        );
    }

    private static BlockDefinition messageBlock(String id, String displayName, String description, String summaryTemplate, String summaryFormatter) {
        return block(
                id,
                displayName,
                description,
                "message",
                "message.screen",
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
                List.of(in("input")),
                List.of(out("done")),
                BlockCapabilityLevel.APPROXIMATE_SIMULATION,
                BlockCapabilityLevel.REQUIRES_MINECRAFT_RUNTIME,
                List.of(BlockSafetyFlag.PLAYER_MUTATING, BlockSafetyFlag.REQUIRES_PLAYER),
                List.of()
        );
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
                        option("AT_OR_ABOVE", "不低于"),
                        option("AT_OR_BELOW", "不高于"),
                        option("EQUAL", "等于"),
                        option("BETWEEN", "在范围内")
                )),
                conditionalInteger("targetY", "目标 Y", "showWhen:compareMode=AT_OR_ABOVE,AT_OR_BELOW,EQUAL"),
                conditionalInteger("minY", "最小 Y", "showWhen:compareMode=BETWEEN"),
                conditionalInteger("maxY", "最大 Y", "showWhen:compareMode=BETWEEN")
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
