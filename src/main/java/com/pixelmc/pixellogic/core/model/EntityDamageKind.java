package com.pixelmc.pixellogic.core.model;

public enum EntityDamageKind {
    GENERIC("普通"),
    MAGIC("魔法"),
    FIRE("火焰"),
    FALL("摔落"),
    VOID("虚空");

    private final String displayName;

    EntityDamageKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
