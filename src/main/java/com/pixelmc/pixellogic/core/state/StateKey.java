package com.pixelmc.pixellogic.core.state;

import com.pixelmc.pixellogic.core.model.StateScope;

public record StateKey(StateScope scope, String ownerId, String key) {
    private static final String GLOBAL_OWNER = "_global";

    public static StateKey global(String key) {
        return new StateKey(StateScope.GLOBAL, GLOBAL_OWNER, key);
    }

    public static StateKey of(StateScope scope, String ownerId, String key) {
        if (scope == StateScope.GLOBAL) {
            return global(key);
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException(scope + " state requires owner id");
        }
        return new StateKey(scope, ownerId, key);
    }
}
