package com.pixelmc.pixellogic.core.runtime;

public record RuntimeResult(
        boolean success,
        String traceId,
        String message,
        boolean suspended,
        EntityTargetError targetError,
        EntityActionError actionError
) {
    public RuntimeResult(boolean success, String traceId, String message) {
        this(success, traceId, message, false, null, null);
    }

    public RuntimeResult(boolean success, String traceId, String message, boolean suspended) {
        this(success, traceId, message, suspended, null, null);
    }

    public RuntimeResult(
            boolean success,
            String traceId,
            String message,
            boolean suspended,
            EntityTargetError targetError
    ) {
        this(success, traceId, message, suspended, targetError, null);
    }
}
