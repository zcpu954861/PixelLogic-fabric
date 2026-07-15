package com.pixelmc.pixellogic.loader.fabric;

import com.pixelmc.pixellogic.core.runtime.RuntimeEntityAccess;
import com.pixelmc.pixellogic.core.runtime.RuntimeEntityLookup;
import com.pixelmc.pixellogic.core.runtime.RuntimeEntityProvider;
import com.pixelmc.pixellogic.core.runtime.RuntimeOnlinePlayer;
import com.pixelmc.pixellogic.core.runtime.RuntimeOnlinePlayerList;
import com.pixelmc.pixellogic.core.runtime.RuntimeSubjectReference;
import com.pixelmc.pixellogic.core.model.EntityDamageKind;
import com.pixelmc.pixellogic.core.model.EntityStatusEffect;
import com.pixelmc.pixellogic.core.model.PlayerGameMode;
import com.pixelmc.pixellogic.core.model.StatusEffectUpdatePolicy;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class FabricRuntimeEntityProvider implements RuntimeEntityProvider {
    private final MinecraftServer server;

    FabricRuntimeEntityProvider(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public RuntimeEntityLookup resolve(RuntimeSubjectReference reference) {
        if (reference == null || !reference.isEntity()) {
            return RuntimeEntityLookup.unresolvable();
        }
        try {
            UUID id = UUID.fromString(reference.id());
            if (reference.kind() == RuntimeSubjectReference.Kind.PLAYER) {
                return resolveOnlinePlayer(id);
            }
            for (ServerWorld world : server.getWorlds()) {
                Entity entity = world.getEntity(id);
                if (entity != null && !entity.isRemoved()) {
                    return RuntimeEntityLookup.resolved(new EntityAccess(entity));
                }
            }
            return RuntimeEntityLookup.unresolvable();
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
            return RuntimeEntityLookup.resolved(new EntityAccess(player));
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

    @Override
    public boolean statusEffectExists(String effectId) {
        return statusEffectType(effectId).isPresent();
    }

    private static Optional<RegistryEntry.Reference<StatusEffect>> statusEffectType(String effectId) {
        Identifier id = Identifier.tryParse(effectId);
        return id == null ? Optional.empty() : Registries.STATUS_EFFECT.getEntry(id);
    }

    private record EntityAccess(Entity entity) implements RuntimeEntityAccess {
        @Override
        public RuntimeSubjectReference reference() {
            if (entity instanceof ServerPlayerEntity player) {
                return new RuntimeSubjectReference(
                        player.getUuid().toString(),
                        RuntimeSubjectReference.Kind.PLAYER,
                        player.getGameProfile().name()
                );
            }
            return new RuntimeSubjectReference(
                    entity.getUuid().toString(),
                    RuntimeSubjectReference.Kind.ENTITY,
                    entity.getName().getString()
            );
        }

        @Override
        public boolean living() {
            return entity instanceof LivingEntity;
        }

        @Override
        public boolean alive() {
            return entity.isAlive() && !entity.isRemoved();
        }

        @Override
        public boolean online() {
            return !entity.isRemoved();
        }

        @Override
        public boolean removed() {
            return entity.isRemoved();
        }

        @Override
        public boolean invulnerable() {
            return entity.isInvulnerable();
        }

        @Override
        public double health() {
            return livingEntity().getHealth();
        }

        @Override
        public double maxHealth() {
            return livingEntity().getMaxHealth();
        }

        @Override
        public boolean damage(EntityDamageKind kind, double amount) {
            LivingEntity living = livingEntity();
            DamageSources sources = living.getDamageSources();
            DamageSource source = switch (kind) {
                case GENERIC -> sources.generic();
                case MAGIC -> sources.magic();
                case FIRE -> sources.inFire();
                case FALL -> sources.fall();
                case VOID -> sources.outOfWorld();
            };
            return living.damage(world(), source, (float) amount);
        }

        @Override
        public boolean heal(double amount) {
            LivingEntity living = livingEntity();
            float before = living.getHealth();
            living.heal((float) amount);
            return living.getHealth() != before;
        }

        @Override
        public boolean setHealth(double health) {
            LivingEntity living = livingEntity();
            float before = living.getHealth();
            living.setHealth((float) health);
            return living.getHealth() != before;
        }

        @Override
        public boolean kill() {
            LivingEntity living = livingEntity();
            living.kill(world());
            return !living.isAlive();
        }

        @Override
        public boolean remove() {
            if (entity instanceof ServerPlayerEntity) {
                return false;
            }
            entity.discard();
            return entity.isRemoved();
        }

        @Override
        public Map<String, EntityStatusEffect> statusEffects() {
            Map<String, EntityStatusEffect> effects = new LinkedHashMap<>();
            for (StatusEffectInstance instance : livingEntity().getStatusEffects()) {
                Identifier id = Registries.STATUS_EFFECT.getId(instance.getEffectType().value());
                if (id != null) {
                    effects.put(id.toString(), statusEffect(id.toString(), instance));
                }
            }
            return Map.copyOf(effects);
        }

        @Override
        public StatusEffectMutation addStatusEffect(
                EntityStatusEffect effect,
                StatusEffectUpdatePolicy policy
        ) {
            Optional<RegistryEntry.Reference<StatusEffect>> type = statusEffectType(effect.effectId());
            if (type.isEmpty()) {
                return StatusEffectMutation.UNKNOWN;
            }
            LivingEntity living = livingEntity();
            StatusEffectInstance requested = new StatusEffectInstance(
                    type.get(),
                    effect.durationTicks(),
                    effect.amplifier(),
                    effect.ambient(),
                    effect.showParticles(),
                    effect.showIcon()
            );
            if (!living.canHaveStatusEffect(requested)) {
                return StatusEffectMutation.REJECTED;
            }
            StatusEffectInstance current = living.getStatusEffect(type.get());
            EntityStatusEffect before = current == null ? null : statusEffect(effect.effectId(), current);
            if (policy == StatusEffectUpdatePolicy.REPLACE) {
                if (effect.equals(before)) {
                    return StatusEffectMutation.UNCHANGED;
                }
                if (current == null) {
                    living.addStatusEffect(requested);
                } else {
                    living.setStatusEffect(requested, null);
                    requested.onApplied(living);
                }
            } else {
                living.addStatusEffect(requested);
            }
            StatusEffectInstance applied = living.getStatusEffect(type.get());
            if (applied == null) {
                return StatusEffectMutation.REJECTED;
            }
            EntityStatusEffect after = statusEffect(effect.effectId(), applied);
            if (policy == StatusEffectUpdatePolicy.REPLACE && !effect.equals(after)) {
                return StatusEffectMutation.REJECTED;
            }
            return after.equals(before) ? StatusEffectMutation.UNCHANGED : StatusEffectMutation.CHANGED;
        }

        @Override
        public StatusEffectMutation removeStatusEffect(String effectId) {
            Optional<RegistryEntry.Reference<StatusEffect>> type = statusEffectType(effectId);
            if (type.isEmpty()) {
                return StatusEffectMutation.UNKNOWN;
            }
            return livingEntity().removeStatusEffect(type.get())
                    ? StatusEffectMutation.CHANGED
                    : StatusEffectMutation.UNCHANGED;
        }

        @Override
        public Optional<PlayerGameMode> gameMode() {
            if (!(entity instanceof ServerPlayerEntity player)) {
                return Optional.empty();
            }
            return Optional.of(switch (player.getGameMode()) {
                case CREATIVE -> PlayerGameMode.CREATIVE;
                case ADVENTURE -> PlayerGameMode.ADVENTURE;
                case SPECTATOR -> PlayerGameMode.SPECTATOR;
                default -> PlayerGameMode.SURVIVAL;
            });
        }

        @Override
        public boolean changeGameMode(PlayerGameMode gameMode) {
            if (!(entity instanceof ServerPlayerEntity player)) {
                return false;
            }
            GameMode requested = switch (gameMode) {
                case SURVIVAL -> GameMode.SURVIVAL;
                case CREATIVE -> GameMode.CREATIVE;
                case ADVENTURE -> GameMode.ADVENTURE;
                case SPECTATOR -> GameMode.SPECTATOR;
            };
            return player.changeGameMode(requested);
        }

        @Override
        public boolean hasTag(String tag) {
            return entity.getCommandTags().contains(tag);
        }

        @Override
        public boolean addTag(String tag) {
            return entity.addCommandTag(tag);
        }

        @Override
        public boolean removeTag(String tag) {
            return entity.removeCommandTag(tag);
        }

        @Override
        public Set<String> tags() {
            return Set.copyOf(entity.getCommandTags());
        }

        private EntityStatusEffect statusEffect(String effectId, StatusEffectInstance instance) {
            return new EntityStatusEffect(
                    effectId,
                    instance.isInfinite() ? EntityStatusEffect.INFINITE_DURATION : instance.getDuration(),
                    instance.getAmplifier(),
                    instance.isAmbient(),
                    instance.shouldShowParticles(),
                    instance.shouldShowIcon()
            );
        }

        private LivingEntity livingEntity() {
            if (entity instanceof LivingEntity living) {
                return living;
            }
            throw new IllegalStateException("entity is not living");
        }

        private ServerWorld world() {
            if (entity.getEntityWorld() instanceof ServerWorld world) {
                return world;
            }
            throw new IllegalStateException("entity is not in a server world");
        }
    }
}
