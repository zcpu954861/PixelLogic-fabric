package com.pixelmc.pixellogic.core.catalog;

public enum BlockCapabilityLevel {
    FULLY_SIMULATABLE,
    APPROXIMATE_SIMULATION,
    REQUIRES_MINECRAFT_RUNTIME,
    UNSAFE_OR_WORLD_MUTATING,
    SERVER_ADMIN_ONLY
}
