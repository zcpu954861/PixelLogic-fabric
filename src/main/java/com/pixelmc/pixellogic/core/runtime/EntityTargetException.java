package com.pixelmc.pixellogic.core.runtime;

public final class EntityTargetException extends RuntimeException {
    private final EntityTargetError error;

    public EntityTargetException(EntityTargetError error) {
        super(error.message());
        this.error = error;
    }

    public EntityTargetError error() {
        return error;
    }
}
