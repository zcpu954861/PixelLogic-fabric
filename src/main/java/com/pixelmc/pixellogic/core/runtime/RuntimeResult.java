package com.pixelmc.pixellogic.core.runtime;

public record RuntimeResult(boolean success, String traceId, String message, boolean suspended) {
    public RuntimeResult(boolean success, String traceId, String message) {
        this(success, traceId, message, false);
    }
}
