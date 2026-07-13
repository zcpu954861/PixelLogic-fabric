package com.pixelmc.pixellogic.core.runtime;

public final class EntityActionException extends RuntimeException {
    private final EntityActionError error;

    public EntityActionException(EntityActionError error) {
        super(error.message());
        this.error = error;
    }

    public EntityActionError error() {
        return error;
    }
}
