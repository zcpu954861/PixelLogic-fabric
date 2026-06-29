package com.pixelmc.pixellogic.core.trace;

import java.time.Instant;

public record TraceStep(Instant timestamp, String nodeId, String message) {
}
