package com.pixelmc.pixellogic.core.runtime;

public record RuntimeActionOutcome(
        Status status,
        String kind,
        String code,
        String message,
        RuntimeSubjectReference target,
        boolean changed,
        int affectedCount
) {
    public RuntimeActionOutcome {
        kind = kind == null ? "" : kind;
        code = code == null ? "" : code;
        message = message == null ? "" : message;
        if (status == null || affectedCount < 0) {
            throw new IllegalArgumentException("action outcome is invalid");
        }
    }

    public static RuntimeActionOutcome success(String message, RuntimeSubjectReference target, boolean changed) {
        return success("entity_tag", message, target, changed);
    }

    public static RuntimeActionOutcome success(
            String kind,
            String message,
            RuntimeSubjectReference target,
            boolean changed
    ) {
        return new RuntimeActionOutcome(Status.SUCCESS, kind, "", message, target, changed, 1);
    }

    public static RuntimeActionOutcome failure(EntityTargetError error) {
        return failure("entity_tag", error);
    }

    public static RuntimeActionOutcome failure(String kind, EntityTargetError error) {
        return new RuntimeActionOutcome(Status.FAILURE, kind, error.code().id(), error.message(), null, false, 0);
    }

    public static RuntimeActionOutcome failure(String kind, EntityActionError error) {
        return new RuntimeActionOutcome(
                Status.FAILURE,
                kind,
                error.code().id(),
                error.message(),
                error.target(),
                false,
                0
        );
    }

    public enum Status {
        SUCCESS,
        FAILURE
    }
}
