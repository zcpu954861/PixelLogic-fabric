package com.pixelmc.pixellogic.core.runtime;

public record RuntimeActionOutcome(
        Status status,
        String code,
        String message,
        RuntimeSubjectReference target,
        boolean changed,
        int affectedCount
) {
    public RuntimeActionOutcome {
        code = code == null ? "" : code;
        message = message == null ? "" : message;
        if (status == null || affectedCount < 0) {
            throw new IllegalArgumentException("action outcome is invalid");
        }
    }

    public static RuntimeActionOutcome success(String message, RuntimeSubjectReference target, boolean changed) {
        return new RuntimeActionOutcome(Status.SUCCESS, "", message, target, changed, 1);
    }

    public static RuntimeActionOutcome failure(EntityTargetError error) {
        return new RuntimeActionOutcome(Status.FAILURE, error.code().id(), error.message(), null, false, 0);
    }

    public enum Status {
        SUCCESS,
        FAILURE
    }
}
