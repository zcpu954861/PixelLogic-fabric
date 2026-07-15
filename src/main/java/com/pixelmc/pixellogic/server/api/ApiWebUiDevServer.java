package com.pixelmc.pixellogic.server.api;

import com.pixelmc.pixellogic.core.runtime.RuntimeEntityLookup;
import com.pixelmc.pixellogic.core.runtime.RuntimeEntityProvider;
import com.pixelmc.pixellogic.core.runtime.RuntimeOnlinePlayer;
import com.pixelmc.pixellogic.core.runtime.RuntimeOnlinePlayerList;
import com.pixelmc.pixellogic.core.runtime.RuntimeSubjectReference;
import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ApiWebUiDevServer {
    static final UUID DEV_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");

    private ApiWebUiDevServer() {
    }

    public static void main(String[] args) throws Exception {
        PixelLogicSpikeService service = new PixelLogicSpikeService(
                (playerId, message) -> System.out.println("[PixelLogic message] " + message),
                Runnable::run,
                message -> System.out.println("[PixelLogic debug] " + message),
                Duration.ofSeconds(30),
                Path.of("world", "pixellogic"),
                devEntityProvider()
        );
        PixelLogicApiServer server = PixelLogicApiServer.start(service, Runnable::run);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            service.close();
        }, "PixelLogic-API-Dev-Shutdown"));

        System.out.println("PixelLogic API dev server listening on http://" +
                PixelLogicApiServer.DEFAULT_HOST + ":" + server.port());
        Thread.currentThread().join();
    }

    static RuntimeEntityProvider devEntityProvider() {
        SimulationActor player = SimulationActor.player(DEV_PLAYER_ID, "DevPlayer");
        return new RuntimeEntityProvider() {
            @Override
            public RuntimeEntityLookup resolve(RuntimeSubjectReference reference) {
                return reference != null
                        && reference.kind() == RuntimeSubjectReference.Kind.PLAYER
                        && DEV_PLAYER_ID.toString().equals(reference.id())
                        ? RuntimeEntityLookup.resolved(player)
                        : RuntimeEntityLookup.unresolvable();
            }

            @Override
            public RuntimeEntityLookup resolveOnlinePlayer(UUID playerUuid) {
                return DEV_PLAYER_ID.equals(playerUuid)
                        ? RuntimeEntityLookup.resolved(player)
                        : RuntimeEntityLookup.unresolvable();
            }

            @Override
            public RuntimeOnlinePlayerList listOnlinePlayers(String query, int limit) {
                String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
                return new RuntimeOnlinePlayerList(
                        true,
                        limit > 0 && "devplayer".contains(needle)
                                ? List.of(new RuntimeOnlinePlayer(DEV_PLAYER_ID, player.displayName()))
                                : List.of()
                );
            }

            @Override
            public boolean statusEffectExists(String effectId) {
                // ponytail: the standalone dev fixture only needs the catalog default; Fabric uses the real registry.
                return "minecraft:speed".equals(effectId);
            }
        };
    }
}
