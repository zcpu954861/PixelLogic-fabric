package com.pixelmc.pixellogic.core.simulation.context;

import com.pixelmc.pixellogic.core.runtime.RuntimeSubjectReference;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class SimulationEntity {
    private final UUID id;
    private final String entityTypeId;
    private final String displayName;
    private final Set<String> tags = new LinkedHashSet<>();

    public SimulationEntity(UUID id, String entityTypeId, String displayName, Collection<String> tags) {
        this.id = id;
        this.entityTypeId = entityTypeId == null || entityTypeId.isBlank() ? "minecraft:pig" : entityTypeId;
        this.displayName = displayName == null || displayName.isBlank() ? "模拟实体" : displayName;
        if (tags != null) {
            this.tags.addAll(tags.stream().filter(tag -> tag != null && !tag.isBlank()).toList());
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

    public RuntimeSubjectReference reference() {
        return new RuntimeSubjectReference(id.toString(), RuntimeSubjectReference.Kind.ENTITY, displayName);
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
