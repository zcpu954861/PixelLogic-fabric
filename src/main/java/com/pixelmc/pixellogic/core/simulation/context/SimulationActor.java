package com.pixelmc.pixellogic.core.simulation.context;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class SimulationActor {
    private final UUID id;
    private final String displayName;
    private final boolean online;
    private final boolean operator;
    private final SimulationPosition position;
    private final Set<String> tags = new LinkedHashSet<>();

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
        this.id = id;
        this.displayName = displayName == null || displayName.isBlank() ? "模拟玩家" : displayName;
        this.online = online;
        this.operator = operator;
        this.position = position == null ? SimulationPosition.overworldSpawn() : position;
        if (tags != null) {
            this.tags.addAll(tags.stream().filter(tag -> tag != null && !tag.isBlank()).toList());
        }
    }

    public static SimulationActor player(UUID id, String displayName) {
        return new SimulationActor(id, displayName, true, false, Set.of());
    }

    public UUID id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean online() {
        return online;
    }

    public boolean operator() {
        return operator;
    }

    public SimulationPosition position() {
        return position;
    }

    public synchronized boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public synchronized boolean addTag(String tag) {
        return tags.add(tag);
    }

    public synchronized boolean removeTag(String tag) {
        return tags.remove(tag);
    }

    public synchronized Set<String> tags() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }
}
