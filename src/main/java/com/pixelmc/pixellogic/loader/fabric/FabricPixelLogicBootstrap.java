package com.pixelmc.pixellogic.loader.fabric;

import com.pixelmc.pixellogic.PixelLogicMod;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;
import com.pixelmc.pixellogic.server.api.PixelLogicApiServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;

public final class FabricPixelLogicBootstrap {
    private static PixelLogicSpikeService service;
    private static PixelLogicApiServer apiServer;

    private FabricPixelLogicBootstrap() {
    }

    public static void bootstrap() {
        PixelLogicCommandRegistrar.register();
        ServerLifecycleEvents.SERVER_STARTED.register(FabricPixelLogicBootstrap::startApi);
        ServerLifecycleEvents.SERVER_STOPPING.register(FabricPixelLogicBootstrap::shutdown);
    }

    public static synchronized PixelLogicSpikeService service(MinecraftServer server) {
        if (service == null) {
            service = new PixelLogicSpikeService(
                    (playerId, message) -> {
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                        if (player != null) {
                            player.sendMessage(Text.literal(message));
                        }
                    },
                    server::executeSync,
                    PixelLogicMod.LOGGER::info
            );
        }
        return service;
    }

    private static synchronized void startApi(MinecraftServer server) {
        if (apiServer != null) {
            return;
        }
        try {
            apiServer = PixelLogicApiServer.start(service(server), server::execute);
            PixelLogicMod.LOGGER.info("PixelLogic local API listening on http://{}:{}",
                    PixelLogicApiServer.DEFAULT_HOST,
                    apiServer.port());
        } catch (IOException exception) {
            PixelLogicMod.LOGGER.error("PixelLogic local API failed to start on {}:{}",
                    PixelLogicApiServer.DEFAULT_HOST,
                    PixelLogicApiServer.DEFAULT_PORT,
                    exception);
        }
    }

    private static synchronized void shutdown(MinecraftServer server) {
        if (apiServer != null) {
            apiServer.close();
            apiServer = null;
        }
        if (service != null) {
            service.close();
            service = null;
        }
    }
}
