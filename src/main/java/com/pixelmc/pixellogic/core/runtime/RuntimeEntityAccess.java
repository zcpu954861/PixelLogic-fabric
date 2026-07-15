package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.model.EntityDamageKind;
import com.pixelmc.pixellogic.core.model.EntityStatusEffect;
import com.pixelmc.pixellogic.core.model.PlayerGameMode;
import com.pixelmc.pixellogic.core.model.StatusEffectUpdatePolicy;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface RuntimeEntityAccess {
    RuntimeSubjectReference reference();

    default boolean living() {
        return true;
    }

    default boolean alive() {
        return true;
    }

    default boolean online() {
        return true;
    }

    default boolean removed() {
        return false;
    }

    default boolean invulnerable() {
        return false;
    }

    default double health() {
        return 20;
    }

    default double maxHealth() {
        return 20;
    }

    default boolean damage(EntityDamageKind kind, double amount) {
        throw new UnsupportedOperationException("entity damage is unavailable");
    }

    default boolean damageIsApproximate() {
        return false;
    }

    default boolean heal(double amount) {
        throw new UnsupportedOperationException("entity healing is unavailable");
    }

    default boolean setHealth(double health) {
        throw new UnsupportedOperationException("setting entity health is unavailable");
    }

    default boolean kill() {
        throw new UnsupportedOperationException("killing entity is unavailable");
    }

    default boolean remove() {
        throw new UnsupportedOperationException("removing entity is unavailable");
    }

    default Map<String, EntityStatusEffect> statusEffects() {
        return Map.of();
    }

    default Optional<EntityStatusEffect> statusEffect(String effectId) {
        return Optional.ofNullable(statusEffects().get(effectId));
    }

    default StatusEffectMutation addStatusEffect(
            EntityStatusEffect effect,
            StatusEffectUpdatePolicy policy
    ) {
        throw new UnsupportedOperationException("adding status effects is unavailable");
    }

    default StatusEffectMutation removeStatusEffect(String effectId) {
        throw new UnsupportedOperationException("removing status effects is unavailable");
    }

    default Optional<PlayerGameMode> gameMode() {
        return Optional.empty();
    }

    default boolean changeGameMode(PlayerGameMode gameMode) {
        throw new UnsupportedOperationException("changing player game mode is unavailable");
    }

    boolean hasTag(String tag);

    boolean addTag(String tag);

    boolean removeTag(String tag);

    Set<String> tags();

    enum StatusEffectMutation {
        CHANGED,
        UNCHANGED,
        UNKNOWN,
        REJECTED
    }
}
