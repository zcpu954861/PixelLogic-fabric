package com.pixelmc.pixellogic.core.simulation.runner;

public record SimulationRunOptions(boolean realTimeTimers) {
    public static SimulationRunOptions realTime() {
        return new SimulationRunOptions(true);
    }
}
