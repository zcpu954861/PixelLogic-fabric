package com.pixelmc.pixellogic.core.catalog;

import java.util.List;

public record BlockFormFieldDefinition(
        String key,
        String type,
        String label,
        String description,
        boolean required,
        String defaultValue,
        String placeholder,
        List<FieldOption> options,
        String min,
        String max,
        String step,
        String ui,
        String suffix
) {
    public BlockFormFieldDefinition {
        options = options == null ? List.of() : List.copyOf(options);
        description = description == null ? "" : description;
        defaultValue = defaultValue == null ? "" : defaultValue;
        placeholder = placeholder == null ? "" : placeholder;
        min = min == null ? "" : min;
        max = max == null ? "" : max;
        step = step == null ? "" : step;
        ui = ui == null ? "" : ui;
        suffix = suffix == null ? "" : suffix;
    }

    public record FieldOption(String value, String label) {
    }
}
