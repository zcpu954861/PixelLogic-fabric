package com.pixelmc.pixellogic.loader.fabric;

import com.pixelmc.pixellogic.core.runtime.RuntimeEntityAccess;
import com.pixelmc.pixellogic.core.runtime.RuntimeEntityLookup;
import com.pixelmc.pixellogic.core.runtime.RuntimeEntityProvider;
import com.pixelmc.pixellogic.core.runtime.RuntimeOnlinePlayer;
import com.pixelmc.pixellogic.core.runtime.RuntimeOnlinePlayerList;
import com.pixelmc.pixellogic.core.runtime.RuntimeSubjectReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class FabricRuntimeEntityProvider implements RuntimeEntityProvider {
    private final MinecraftServer server;

    FabricRuntimeEntityProvider(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public RuntimeEntityLookup resolve(RuntimeSubjectReference reference) {
        if (reference == null || reference.kind() != RuntimeSubjectReference.Kind.PLAYER) {
            return RuntimeEntityLookup.unresolvable();
        }
        try {
            return resolveOnlinePlayer(UUID.fromString(reference.id()));
        } catch (IllegalArgumentException exception) {
            return RuntimeEntityLookup.unresolvable();
        }
    }

    @Override
    public RuntimeEntityLookup resolveOnlinePlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return RuntimeEntityLookup.unresolvable();
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
        if (player != null) {
            return RuntimeEntityLookup.resolved(new PlayerAccess(player));
        }
        return server.getApiServices().nameToIdCache().getByUuid(playerUuid)
                .map(profile -> RuntimeEntityLookup.offline(profile.name()))
                .orElseGet(RuntimeEntityLookup::unresolvable);
    }

    @Override
    public RuntimeOnlinePlayerList listOnlinePlayers(String query, int limit) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("query 不能超过 64 个字符。");
        }
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        List<RuntimeOnlinePlayer> players = server.getPlayerManager().getPlayerList().stream()
                .map(player -> new RuntimeOnlinePlayer(player.getUuid(), player.getGameProfile().name()))
                .filter(player -> normalized.isEmpty() || player.name().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing(RuntimeOnlinePlayer::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(player -> player.uuid().toString()))
                .limit(boundedLimit)
                .toList();
        return new RuntimeOnlinePlayerList(true, players);
    }

    private record PlayerAccess(ServerPlayerEntity player) implements RuntimeEntityAccess {
        @Override
        public RuntimeSubjectReference reference() {
            return new RuntimeSubjectReference(
                    player.getUuid().toString(),
                    RuntimeSubjectReference.Kind.PLAYER,
                    player.getGameProfile().name()
            );
        }

        @Override
        public boolean alive() {
            return player.isAlive();
        }

        @Override
        public boolean hasTag(String tag) {
            return player.getCommandTags().contains(tag);
        }

        @Override
        public boolean addTag(String tag) {
            return player.addCommandTag(tag);
        }

        @Override
        public boolean removeTag(String tag) {
            return player.removeCommandTag(tag);
        }

        @Override
        public Set<String> tags() {
            return Set.copyOf(player.getCommandTags());
        }
    }
}
