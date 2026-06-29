package com.pixelmc.pixellogic.loader.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.trace.ExecutionTrace;
import com.pixelmc.pixellogic.core.trace.TraceStep;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class PixelLogicCommandRegistrar {
    private PixelLogicCommandRegistrar() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("pixellogic")
                .then(CommandManager.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(CommandManager.literal("test")
                        .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                        .then(CommandManager.literal("start")
                                .executes(context -> start(context.getSource())))
                        .then(CommandManager.literal("reset")
                                .executes(context -> reset(context.getSource()))))
                .then(CommandManager.literal("trace")
                        .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                        .then(CommandManager.literal("last")
                                .executes(context -> traceLast(context.getSource())))));
    }

    private static int status(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal(FabricPixelLogicBootstrap.service(source.getServer()).status()), false);
        return 1;
    }

    private static int start(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("该测试需要玩家执行。"));
            return 0;
        }

        PixelLogicSpikeService service = FabricPixelLogicBootstrap.service(source.getServer());
        RuntimeResult result = service.startManualTest(player.getUuid());
        source.sendFeedback(() -> Text.literal("PixelLogic 测试运行：" + result.message() + " trace=" + result.traceId()), false);
        return result.success() ? 1 : 0;
    }

    private static int reset(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("该测试需要玩家执行。"));
            return 0;
        }

        FabricPixelLogicBootstrap.service(source.getServer()).resetPlayer(player.getUuid());
        source.sendFeedback(() -> Text.literal("PixelLogic 测试状态已重置：PLAYER.started 已清除。"), false);
        return 1;
    }

    private static int traceLast(ServerCommandSource source) {
        PixelLogicSpikeService service = FabricPixelLogicBootstrap.service(source.getServer());
        ExecutionTrace trace = service.latestTrace().orElse(null);
        if (trace == null) {
            source.sendFeedback(() -> Text.literal("暂无 PixelLogic 执行记录。"), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("PixelLogic 最近 trace: " + trace.id()), false);
        for (TraceStep step : trace.steps()) {
            source.sendFeedback(() -> Text.literal(" - " + step.message()), false);
        }
        if (trace.truncated()) {
            source.sendFeedback(() -> Text.literal(" - 记录已截断。"), false);
        }
        return 1;
    }
}
