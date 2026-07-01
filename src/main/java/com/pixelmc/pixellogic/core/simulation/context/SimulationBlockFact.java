package com.pixelmc.pixellogic.core.simulation.context;

public record SimulationBlockFact(
        boolean enabled,
        String dimensionId,
        int x,
        int y,
        int z,
        String blockId
) {
    public SimulationBlockFact {
        dimensionId = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
        blockId = blockId == null || blockId.isBlank() ? "minecraft:stone" : blockId;
    }

    public static SimulationBlockFact disabled() {
        return new SimulationBlockFact(false, "minecraft:overworld", 0, 64, 0, "minecraft:stone");
    }

    public SimulationPosition position() {
        return new SimulationPosition(dimensionId, x, y, z);
    }
}
