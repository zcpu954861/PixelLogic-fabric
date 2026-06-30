package com.pixelmc.pixellogic.core.catalog;

public enum BlockSafetyFlag {
    READ_ONLY,
    STATE_MUTATING,
    PLAYER_MUTATING,
    WORLD_MUTATING,
    COMMAND_LIKE,
    REQUIRES_PLAYER,
    REQUIRES_WORLD,
    REQUIRES_LOADED_CHUNK,
    SERVER_ADMIN_ONLY,
    NOT_SIMULATABLE
}
