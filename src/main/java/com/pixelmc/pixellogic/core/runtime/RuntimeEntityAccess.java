package com.pixelmc.pixellogic.core.runtime;

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

    boolean hasTag(String tag);

    boolean addTag(String tag);

    boolean removeTag(String tag);

    Set<String> tags();
}
