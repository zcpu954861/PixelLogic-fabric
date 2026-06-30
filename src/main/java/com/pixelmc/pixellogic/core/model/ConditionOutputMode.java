package com.pixelmc.pixellogic.core.model;

import java.util.Map;

public enum ConditionOutputMode {
    PASS_ONLY,
    FAIL_ONLY,
    BRANCH;

    public static final String CONFIG_KEY = "outputMode";

    public static ConditionOutputMode fromConfig(Map<String, String> config) {
        String value = config == null ? "" : config.getOrDefault(CONFIG_KEY, "");
        if (value == null || value.isBlank()) {
            return BRANCH;
        }
        return valueOf(value);
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            valueOf(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String outputSlot(boolean passed) {
        return switch (this) {
            case PASS_ONLY -> passed ? "pass" : null;
            case FAIL_ONLY -> passed ? null : "fail";
            case BRANCH -> passed ? "pass" : "fail";
        };
    }

    public String traceMessage(boolean passed) {
        return switch (this) {
            case PASS_ONLY -> passed ? "条件满足，继续执行。" : "条件不满足，流程在此结束。";
            case FAIL_ONLY -> passed ? "条件满足，流程在此结束。" : "条件不满足，继续执行。";
            case BRANCH -> passed ? "条件满足，走满足分支。" : "条件不满足，走不满足分支。";
        };
    }
}
