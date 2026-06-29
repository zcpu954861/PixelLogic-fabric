package com.pixelmc.pixellogic.server.api;

import com.pixelmc.pixellogic.server.PixelLogicSpikeService;

public final class ApiWebUiDevServer {
    private ApiWebUiDevServer() {
    }

    public static void main(String[] args) throws Exception {
        PixelLogicSpikeService service = new PixelLogicSpikeService(
                (playerId, message) -> System.out.println("[PixelLogic message] " + message),
                Runnable::run,
                message -> System.out.println("[PixelLogic debug] " + message)
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
}
