package com.pixelmc.pixellogic.core.catalog;

import java.util.List;

public record BlockFormFieldDefinition(
        String key,
        String label,
        String control,
        List<FieldOption> options,
        boolean required,
        boolean full,
        String suffix
) {
    public BlockFormFieldDefinition {
        options = options == null ? List.of() : List.copyOf(options);
        suffix = suffix == null ? "" : suffix;
    }

    public record FieldOption(String value, String label) {
    }
}
