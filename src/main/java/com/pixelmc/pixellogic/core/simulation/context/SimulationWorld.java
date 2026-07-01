package com.pixelmc.pixellogic.core.simulation.context;

import java.util.List;
import java.util.Optional;

public record SimulationWorld(
        String defaultDimensionId,
        SimulationBlockFact targetBlock,
        List<SimulationRegionFact> regions
) {
    public SimulationWorld {
        defaultDimensionId = defaultDimensionId == null || defaultDimensionId.isBlank()
                ? "minecraft:overworld"
                : defaultDimensionId;
        targetBlock = targetBlock == null ? SimulationBlockFact.disabled() : targetBlock;
        regions = regions == null ? List.of() : List.copyOf(regions);
    }

    public SimulationWorld(String dimensionId) {
        this(dimensionId, SimulationBlockFact.disabled(), List.of());
    }

    public static SimulationWorld overworld() {
        return new SimulationWorld("minecraft:overworld");
    }

    public String dimensionId() {
        return defaultDimensionId;
    }

    public Optional<SimulationRegionFact> findRegion(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String targetName = name.trim();
        return regions.stream().filter(region -> region.name().equals(targetName)).findFirst();
    }

    public boolean isPositionInsideRegion(SimulationPosition position, SimulationRegionFact region) {
        return region != null && region.contains(position);
    }
}
