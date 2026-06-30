package com.pixelmc.pixellogic.core.simulation.context;

public record SimulationWorld(String dimensionId) {
    public SimulationWorld {
        dimensionId = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
    }

    public static SimulationWorld overworld() {
        return new SimulationWorld("minecraft:overworld");
    }
}
