package com.pixelmc.pixellogic.core.runtime;

public record RuntimeSubjectReference(String id, Kind kind, String displayName) {
    public RuntimeSubjectReference {
        id = id == null ? "" : id;
        displayName = displayName == null ? "" : displayName;
    }

    public boolean isEntity() {
        return kind == Kind.PLAYER || kind == Kind.ENTITY;
    }

    public enum Kind {
        PLAYER,
        ENTITY,
        BLOCK,
        OTHER
    }
}
