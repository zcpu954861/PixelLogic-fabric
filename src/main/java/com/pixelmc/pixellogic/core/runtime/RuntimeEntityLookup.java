package com.pixelmc.pixellogic.core.runtime;

public record RuntimeEntityLookup(Status status, RuntimeEntityAccess entity, String displayName) {
    public RuntimeEntityLookup {
        if (status == null) {
            throw new IllegalArgumentException("entity lookup status is required");
        }
        if ((status == Status.RESOLVED) != (entity != null)) {
            throw new IllegalArgumentException("resolved lookup must contain exactly one entity");
        }
        displayName = boundedDisplayName(displayName);
    }

    public static RuntimeEntityLookup resolved(RuntimeEntityAccess entity) {
        return new RuntimeEntityLookup(
                Status.RESOLVED,
                entity,
                entity == null || entity.reference() == null ? "" : entity.reference().displayName()
        );
    }

    public static RuntimeEntityLookup offline() {
        return offline("");
    }

    public static RuntimeEntityLookup offline(String displayName) {
        return new RuntimeEntityLookup(Status.OFFLINE, null, displayName);
    }

    public static RuntimeEntityLookup unresolvable() {
        return new RuntimeEntityLookup(Status.UNRESOLVABLE, null, "");
    }

    public static RuntimeEntityLookup providerUnavailable() {
        return new RuntimeEntityLookup(Status.PROVIDER_UNAVAILABLE, null, "");
    }

    private static String boundedDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        StringBuilder clean = new StringBuilder();
        for (int codePoint : displayName.codePoints().toArray()) {
            if (!Character.isISOControl(codePoint)
                    && clean.length() + Character.charCount(codePoint) <= 64) {
                clean.appendCodePoint(codePoint);
            }
        }
        return clean.toString();
    }

    public enum Status {
        RESOLVED,
        OFFLINE,
        UNRESOLVABLE,
        PROVIDER_UNAVAILABLE
    }
}
