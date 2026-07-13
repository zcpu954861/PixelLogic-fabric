package com.pixelmc.pixellogic.core.simulation.result;

import com.pixelmc.pixellogic.core.runtime.RuntimeActionOutcome;

public record SimulationActionResult(
        String nodeId,
        String kind,
        String message,
        RuntimeActionOutcome outcome
) {
    public SimulationActionResult(String nodeId, String kind, String message) {
        this(nodeId, kind, message, null);
    }
}
