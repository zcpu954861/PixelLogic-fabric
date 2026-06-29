package com.pixelmc.pixellogic.core.runtime;

public record RuntimeResult(boolean success, String traceId, String message) {
}
