package com.pixelmc.pixellogic.core.runtime;

public record RuntimeLimits(int maxStepsPerExecution, int maxContinuationDepth) {
    public static RuntimeLimits spikeDefaults() {
        return new RuntimeLimits(64, 8);
    }
}
