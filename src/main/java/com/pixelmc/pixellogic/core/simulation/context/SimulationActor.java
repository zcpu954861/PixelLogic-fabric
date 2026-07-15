package com.pixelmc.pixellogic.core.simulation.context;

import com.pixelmc.pixellogic.core.model.EntityStatusEffect;
import com.pixelmc.pixellogic.core.model.PlayerGameMode;
import com.pixelmc.pixellogic.core.runtime.RuntimeSubjectReference;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SimulationActor extends SimulationEntity {
    private final boolean online;
    private final boolean operator;
    private final SimulationPosition position;

    public SimulationActor(UUID id, String displayName, boolean online, boolean operator, Collection<String> tags) {
        this(id, displayName, online, operator, tags, SimulationPosition.overworldSpawn());
    }

    public SimulationActor(
            UUID id,
            String displayName,
            boolean online,
            boolean operator,
            Collection<String> tags,
            SimulationPosition position
    ) {
        this(id, displayName, online, operator, tags, position, 20, 20, false);
    }

    public SimulationActor(
            UUID id,
            String displayName,
            boolean online,
            boolean operator,
            Collection<String> tags,
            SimulationPosition position,
            double health,
            double maxHealth,
            boolean invulnerable
    ) {
        this(id, displayName, online, operator, tags, position, health, maxHealth, invulnerable,
                Map.of(), PlayerGameMode.SURVIVAL);
    }

    public SimulationActor(
            UUID id,
            String displayName,
            boolean online,
            boolean operator,
            Collection<String> tags,
            SimulationPosition position,
            double health,
            double maxHealth,
            boolean invulnerable,
            Map<String, EntityStatusEffect> statusEffects,
            PlayerGameMode gameMode
    ) {
        super(
                id,
                "minecraft:player",
                displayName == null || displayName.isBlank() ? "模拟玩家" : displayName,
                tags,
                true,
                health,
                maxHealth,
                invulnerable,
                statusEffects,
                gameMode
        );
        this.online = online;
        this.operator = operator;
        this.position = position == null ? SimulationPosition.overworldSpawn() : position;
    }

    public static SimulationActor player(UUID id, String displayName) {
        return new SimulationActor(id, displayName, true, false, Set.of());
    }

    @Override
    public boolean online() {
        return online;
    }

    public boolean operator() {
        return operator;
    }

    public SimulationPosition position() {
        return position;
    }

    @Override
    public RuntimeSubjectReference reference() {
        return new RuntimeSubjectReference(id().toString(), RuntimeSubjectReference.Kind.PLAYER, displayName());
    }

}
