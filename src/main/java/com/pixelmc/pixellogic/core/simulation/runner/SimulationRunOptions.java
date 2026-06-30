package com.pixelmc.pixellogic.core.simulation.runner;

public record SimulationRunOptions(boolean realTimeTimers, boolean fastForwardTimers) {
    public static SimulationRunOptions realTime() {
        return new SimulationRunOptions(true, false);
    }
}
