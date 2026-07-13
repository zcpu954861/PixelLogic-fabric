package com.pixelmc.pixellogic.core.runtime;

public record RuntimeResult(
        boolean success,
        String traceId,
        String message,
        boolean suspended,
        EntityTargetError targetError
) {
    public RuntimeResult(boolean success, String traceId, String message) {
        this(success, traceId, message, false, null);
    }

    public RuntimeResult(boolean success, String traceId, String message, boolean suspended) {
        this(success, traceId, message, suspended, null);
    }
}
