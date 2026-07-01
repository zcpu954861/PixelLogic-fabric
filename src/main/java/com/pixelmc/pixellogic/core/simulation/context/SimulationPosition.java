package com.pixelmc.pixellogic.core.simulation.context;

public record SimulationPosition(String dimensionId, int x, int y, int z) {
    public SimulationPosition {
        dimensionId = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
    }

    public static SimulationPosition overworldSpawn() {
        return new SimulationPosition("minecraft:overworld", 0, 64, 0);
    }
}
