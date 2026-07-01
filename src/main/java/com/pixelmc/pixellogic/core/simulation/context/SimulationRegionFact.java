package com.pixelmc.pixellogic.core.simulation.context;

public record SimulationRegionFact(
        String name,
        String dimensionId,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {
    public SimulationRegionFact {
        name = name == null || name.isBlank() ? "测试区域" : name.trim();
        dimensionId = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
        int lowX = Math.min(minX, maxX);
        int highX = Math.max(minX, maxX);
        int lowY = Math.min(minY, maxY);
        int highY = Math.max(minY, maxY);
        int lowZ = Math.min(minZ, maxZ);
        int highZ = Math.max(minZ, maxZ);
        minX = lowX;
        maxX = highX;
        minY = lowY;
        maxY = highY;
        minZ = lowZ;
        maxZ = highZ;
    }

    public boolean contains(SimulationPosition position) {
        return position != null
                && dimensionId.equals(position.dimensionId())
                && position.x() >= minX
                && position.x() <= maxX
                && position.y() >= minY
                && position.y() <= maxY
                && position.z() >= minZ
                && position.z() <= maxZ;
    }
}
