package com.pixelmc.pixellogic.core.model;

public record EntityStatusEffect(
        String effectId,
        int durationTicks,
        int amplifier,
        boolean ambient,
        boolean showParticles,
        boolean showIcon
) {
    public static final int INFINITE_DURATION = -1;

    public EntityStatusEffect {
        effectId = effectId == null ? "" : effectId.trim();
        if (effectId.isBlank()) {
            throw new IllegalArgumentException("status effect id is required");
        }
        if (durationTicks != INFINITE_DURATION && durationTicks <= 0) {
            throw new IllegalArgumentException("status effect duration must be positive or infinite");
        }
        if (amplifier < 0 || amplifier > 255) {
            throw new IllegalArgumentException("status effect amplifier must be between 0 and 255");
        }
    }

    public boolean infinite() {
        return durationTicks == INFINITE_DURATION;
    }

    public EntityStatusEffect vanillaUpdatedBy(EntityStatusEffect incoming) {
        if (!effectId.equals(incoming.effectId)) {
            throw new IllegalArgumentException("status effect ids must match");
        }

        int nextDuration = durationTicks;
        int nextAmplifier = amplifier;
        boolean nextAmbient = ambient;
        boolean nextParticles = showParticles;
        boolean nextIcon = showIcon;
        boolean changed = false;

        if (incoming.amplifier > amplifier) {
            nextAmplifier = incoming.amplifier;
            nextDuration = incoming.durationTicks;
            changed = true;
        } else if (lastsShorterThan(incoming) && incoming.amplifier == amplifier) {
            nextDuration = incoming.durationTicks;
            changed = true;
        }

        if ((!incoming.ambient && nextAmbient) || changed) {
            nextAmbient = incoming.ambient;
            changed = true;
        }
        if (incoming.showParticles != nextParticles) {
            nextParticles = incoming.showParticles;
            changed = true;
        }
        if (incoming.showIcon != nextIcon) {
            nextIcon = incoming.showIcon;
            changed = true;
        }

        return changed
                ? new EntityStatusEffect(effectId, nextDuration, nextAmplifier, nextAmbient, nextParticles, nextIcon)
                : this;
    }

    public boolean lastsShorterThan(EntityStatusEffect other) {
        return !infinite() && (durationTicks < other.durationTicks || other.infinite());
    }
}
