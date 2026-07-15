package com.pixelmc.pixellogic.core.simulation.context;

import com.pixelmc.pixellogic.core.runtime.RuntimeSubjectReference;
import com.pixelmc.pixellogic.core.runtime.RuntimeEntityAccess;
import com.pixelmc.pixellogic.core.model.EntityDamageKind;
import com.pixelmc.pixellogic.core.model.EntityStatusEffect;
import com.pixelmc.pixellogic.core.model.PlayerGameMode;
import com.pixelmc.pixellogic.core.model.StatusEffectUpdatePolicy;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SimulationEntity implements RuntimeEntityAccess {
    private final UUID id;
    private final String entityTypeId;
    private final String displayName;
    private final Set<String> tags = new LinkedHashSet<>();
    private final Map<String, EntityStatusEffect> statusEffects = new LinkedHashMap<>();
    private final boolean living;
    private final boolean invulnerable;
    private final double maxHealth;
    private PlayerGameMode gameMode;
    private double health;
    private boolean killed;
    private boolean removed;

    public SimulationEntity(UUID id, String entityTypeId, String displayName, Collection<String> tags) {
        this(id, entityTypeId, displayName, tags, true, 20, 20, false);
    }

    public SimulationEntity(
            UUID id,
            String entityTypeId,
            String displayName,
            Collection<String> tags,
            boolean living,
            double health,
            double maxHealth,
            boolean invulnerable
    ) {
        this(id, entityTypeId, displayName, tags, living, health, maxHealth, invulnerable, Map.of(), null);
    }

    public SimulationEntity(
            UUID id,
            String entityTypeId,
            String displayName,
            Collection<String> tags,
            boolean living,
            double health,
            double maxHealth,
            boolean invulnerable,
            Map<String, EntityStatusEffect> statusEffects
    ) {
        this(id, entityTypeId, displayName, tags, living, health, maxHealth, invulnerable, statusEffects, null);
    }

    public SimulationEntity(
            UUID id,
            String entityTypeId,
            String displayName,
            Collection<String> tags,
            boolean living,
            double health,
            double maxHealth,
            boolean invulnerable,
            Map<String, EntityStatusEffect> statusEffects,
            PlayerGameMode gameMode
    ) {
        this.id = id;
        this.entityTypeId = entityTypeId == null || entityTypeId.isBlank() ? "minecraft:pig" : entityTypeId;
        this.displayName = displayName == null || displayName.isBlank() ? "模拟实体" : displayName;
        if (!Double.isFinite(maxHealth) || maxHealth <= 0 || maxHealth > 1_000_000
                || !Double.isFinite(health) || health < 0 || health > maxHealth) {
            throw new IllegalArgumentException("simulation entity health is invalid");
        }
        double runtimeHealth = minecraftNumber(health);
        double runtimeMaxHealth = minecraftNumber(maxHealth);
        if (runtimeMaxHealth <= 0 || runtimeHealth < 0 || runtimeHealth > runtimeMaxHealth) {
            throw new IllegalArgumentException("simulation entity health is invalid at runtime precision");
        }
        this.living = living;
        this.health = runtimeHealth;
        this.maxHealth = runtimeMaxHealth;
        this.invulnerable = invulnerable;
        this.gameMode = "minecraft:player".equals(this.entityTypeId)
                ? (gameMode == null ? PlayerGameMode.SURVIVAL : gameMode)
                : null;
        if (tags != null) {
            this.tags.addAll(tags.stream().filter(tag -> tag != null && !tag.isBlank()).toList());
        }
        if (statusEffects != null) {
            statusEffects.forEach((effectId, effect) -> {
                if (!living || effect == null || !effect.effectId().equals(effectId)) {
                    throw new IllegalArgumentException("simulation status effect is invalid");
                }
                this.statusEffects.put(effectId, effect);
            });
        }
    }

    public UUID id() {
        return id;
    }

    public String entityTypeId() {
        return entityTypeId;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public RuntimeSubjectReference reference() {
        RuntimeSubjectReference.Kind kind = "minecraft:player".equals(entityTypeId)
                ? RuntimeSubjectReference.Kind.PLAYER
                : RuntimeSubjectReference.Kind.ENTITY;
        return new RuntimeSubjectReference(id.toString(), kind, displayName);
    }

    @Override
    public boolean living() {
        return living;
    }

    @Override
    public synchronized boolean alive() {
        return !removed && (!living || health > 0);
    }

    @Override
    public synchronized boolean removed() {
        return removed;
    }

    @Override
    public boolean invulnerable() {
        return invulnerable;
    }

    @Override
    public synchronized double health() {
        return health;
    }

    @Override
    public double maxHealth() {
        return maxHealth;
    }

    @Override
    public synchronized boolean damage(EntityDamageKind kind, double amount) {
        if (!living || !alive() || invulnerable || kind == null || amount <= 0) {
            return false;
        }
        health = minecraftNumber(Math.max(0, health - amount));
        if (health == 0) {
            killed = true;
        }
        return true;
    }

    @Override
    public boolean damageIsApproximate() {
        return true;
    }

    @Override
    public synchronized boolean heal(double amount) {
        if (!living || !alive() || amount <= 0) {
            return false;
        }
        double before = health;
        health = minecraftNumber(Math.min(maxHealth, health + amount));
        return health != before;
    }

    @Override
    public synchronized boolean setHealth(double health) {
        if (!living || removed || health < 0 || health > maxHealth) {
            return false;
        }
        double runtimeHealth = minecraftNumber(health);
        boolean changed = this.health != runtimeHealth;
        this.health = runtimeHealth;
        return changed;
    }

    @Override
    public synchronized boolean kill() {
        if (!living || !alive()) {
            return false;
        }
        health = 0;
        killed = true;
        return true;
    }

    @Override
    public synchronized boolean remove() {
        if (removed) {
            return false;
        }
        removed = true;
        return true;
    }

    @Override
    public synchronized Map<String, EntityStatusEffect> statusEffects() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(statusEffects));
    }

    @Override
    public synchronized StatusEffectMutation addStatusEffect(
            EntityStatusEffect effect,
            StatusEffectUpdatePolicy policy
    ) {
        if (!living || !alive() || effect == null || policy == null) {
            return StatusEffectMutation.REJECTED;
        }
        EntityStatusEffect current = statusEffects.get(effect.effectId());
        if (current == null) {
            statusEffects.put(effect.effectId(), effect);
            return StatusEffectMutation.CHANGED;
        }
        EntityStatusEffect updated = policy == StatusEffectUpdatePolicy.REPLACE
                ? effect
                : current.vanillaUpdatedBy(effect);
        // ponytail: Simulation has no effect-expiry clock, so Vanilla's hidden fallback chain is not separately modelled.
        if (updated.equals(current)) {
            return StatusEffectMutation.UNCHANGED;
        }
        statusEffects.put(effect.effectId(), updated);
        return StatusEffectMutation.CHANGED;
    }

    @Override
    public synchronized StatusEffectMutation removeStatusEffect(String effectId) {
        if (!living || !alive()) {
            return StatusEffectMutation.REJECTED;
        }
        return statusEffects.remove(effectId) == null
                ? StatusEffectMutation.UNCHANGED
                : StatusEffectMutation.CHANGED;
    }

    @Override
    public synchronized Optional<PlayerGameMode> gameMode() {
        return Optional.ofNullable(gameMode);
    }

    @Override
    public synchronized boolean changeGameMode(PlayerGameMode gameMode) {
        if (this.gameMode == null || !online() || removed || gameMode == null || this.gameMode == gameMode) {
            return false;
        }
        this.gameMode = gameMode;
        return true;
    }

    public synchronized boolean killed() {
        return killed;
    }

    @Override
    public synchronized boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    @Override
    public synchronized boolean addTag(String tag) {
        return tags.add(tag);
    }

    @Override
    public synchronized boolean removeTag(String tag) {
        return tags.remove(tag);
    }

    @Override
    public synchronized Set<String> tags() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    private static double minecraftNumber(double value) {
        return Double.parseDouble(Float.toString((float) value));
    }
}
