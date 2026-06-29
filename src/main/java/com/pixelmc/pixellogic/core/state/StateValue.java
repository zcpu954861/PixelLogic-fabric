package com.pixelmc.pixellogic.core.state;

import com.pixelmc.pixellogic.core.model.StateValueType;

public record StateValue(StateValueType type, Object value) {
    public static StateValue bool(boolean value) {
        return new StateValue(StateValueType.BOOLEAN, value);
    }

    public static StateValue integer(int value) {
        return new StateValue(StateValueType.INTEGER, value);
    }

    public static StateValue string(String value) {
        return new StateValue(StateValueType.STRING, value);
    }

    public boolean asBoolean(boolean fallback) {
        return type == StateValueType.BOOLEAN ? (Boolean) value : fallback;
    }

    public int asInteger(int fallback) {
        return type == StateValueType.INTEGER ? (Integer) value : fallback;
    }

    public String displayValue() {
        return String.valueOf(value);
    }
}
