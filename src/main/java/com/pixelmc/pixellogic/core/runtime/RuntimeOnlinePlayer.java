package com.pixelmc.pixellogic.core.runtime;

import java.util.UUID;

public record RuntimeOnlinePlayer(UUID uuid, String name) {
    public RuntimeOnlinePlayer {
        if (uuid == null) {
            throw new IllegalArgumentException("online player uuid is required");
        }
        name = name == null ? "" : name;
        if (name.length() > 64 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("online player name is invalid");
        }
    }
}
