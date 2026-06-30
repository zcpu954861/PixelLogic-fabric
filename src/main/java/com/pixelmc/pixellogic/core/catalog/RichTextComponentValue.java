package com.pixelmc.pixellogic.core.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RichTextComponentValue {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private RichTextComponentValue() {
    }

    public static String fromPlainText(String plainText) {
        String text = plainText == null ? "" : plainText;
        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("text", text);
        segment.put("style", Map.of());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("version", 1);
        value.put("plainText", text);
        value.put("segments", List.of(segment));
        return GSON.toJson(value);
    }

    public static String normalize(String raw) {
        return isStructured(raw) ? raw : fromPlainText(raw);
    }

    public static String plainText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
            if (object.has("plainText") && !object.get("plainText").isJsonNull()) {
                return object.get("plainText").getAsString();
            }
        } catch (RuntimeException ignored) {
            return raw;
        }
        return raw;
    }

    public static boolean isStructured(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
            return object.has("version") && object.has("plainText");
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
