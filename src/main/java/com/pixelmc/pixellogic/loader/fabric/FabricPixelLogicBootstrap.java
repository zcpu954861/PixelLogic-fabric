package com.pixelmc.pixellogic.loader.fabric;

import com.pixelmc.pixellogic.PixelLogicMod;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class FabricPixelLogicBootstrap {
    private static PixelLogicSpikeService service;

    private FabricPixelLogicBootstrap() {
    }

    public static void bootstrap() {
        PixelLogicCommandRegistrar.register();
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

    private static synchronized void shutdown(MinecraftServer server) {
        if (service != null) {
            service.close();
            service = null;
        }
    }
}
