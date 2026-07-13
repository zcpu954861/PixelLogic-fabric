package com.pixelmc.pixellogic.core.runtime;

import java.util.List;

public record RuntimeOnlinePlayerList(boolean providerAvailable, List<RuntimeOnlinePlayer> players) {
    public RuntimeOnlinePlayerList {
        players = players == null ? List.of() : List.copyOf(players);
    }

    public static RuntimeOnlinePlayerList unavailable() {
        return new RuntimeOnlinePlayerList(false, List.of());
    }
}
