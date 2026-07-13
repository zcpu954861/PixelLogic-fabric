package com.pixelmc.pixellogic.core.runtime;

import java.util.UUID;

public interface RuntimeEntityProvider {
    RuntimeEntityProvider UNAVAILABLE = new RuntimeEntityProvider() {
        @Override
        public RuntimeEntityLookup resolve(RuntimeSubjectReference reference) {
            return RuntimeEntityLookup.providerUnavailable();
        }

        @Override
        public RuntimeEntityLookup resolveOnlinePlayer(UUID playerUuid) {
            return RuntimeEntityLookup.providerUnavailable();
        }

        @Override
        public RuntimeOnlinePlayerList listOnlinePlayers(String query, int limit) {
            return RuntimeOnlinePlayerList.unavailable();
        }
    };

    RuntimeEntityLookup resolve(RuntimeSubjectReference reference);

    RuntimeEntityLookup resolveOnlinePlayer(UUID playerUuid);

    RuntimeOnlinePlayerList listOnlinePlayers(String query, int limit);
}
