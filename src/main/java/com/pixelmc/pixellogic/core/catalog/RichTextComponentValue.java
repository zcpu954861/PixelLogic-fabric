package com.pixelmc.pixellogic.core.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RichTextComponentValue {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final int MAX_TEXT_LENGTH = 1024;
    public static final int MAX_SEGMENTS = 64;
    private static final Set<String> COLORS = Set.of(
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "dark_gray",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white"
    );
    private static final Set<String> BOOLEAN_STYLES = Set.of(
            "bold",
            "italic",
            "underlined",
            "strikethrough",
            "obfuscated"
    );
    private static final Set<String> STYLE_KEYS = Set.of(
            "color",
            "bold",
            "italic",
            "underlined",
            "strikethrough",
            "obfuscated"
    );

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
            Optional<String> segmented = plainTextFromSegments(object);
            if (segmented.isPresent()) {
                return segmented.get();
            }
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

    public static List<String> validationErrors(String raw) {
        List<String> errors = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return errors;
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(raw);
        } catch (RuntimeException ignored) {
            validateTextLength(raw, errors);
            return errors;
        }
        if (!root.isJsonObject()) {
            validateTextLength(raw, errors);
            return errors;
        }

        JsonObject object = root.getAsJsonObject();
        if (!object.has("version") || !object.has("plainText")) {
            errors.add("富文本结构缺少 version 或 plainText。");
            return errors;
        }
        if (!object.get("version").isJsonPrimitive()
                || !object.get("version").getAsJsonPrimitive().isNumber()
                || object.get("version").getAsInt() != 1) {
            errors.add("富文本版本无效。");
        }
        if (!object.get("plainText").isJsonPrimitive() || !object.get("plainText").getAsJsonPrimitive().isString()) {
            errors.add("富文本 plainText 必须是文本。");
            return errors;
        }

        String plainText = object.get("plainText").getAsString();
        validateTextLength(plainText, errors);
        if (!object.has("segments")) {
            return errors;
        }
        if (!object.get("segments").isJsonArray()) {
            errors.add("富文本 segments 必须是列表。");
            return errors;
        }

        JsonArray segments = object.getAsJsonArray("segments");
        if (segments.isEmpty()) {
            errors.add("富文本至少需要一个文本片段。");
        }
        if (segments.size() > MAX_SEGMENTS) {
            errors.add("富文本片段不能超过 " + MAX_SEGMENTS + " 个。");
        }

        StringBuilder joined = new StringBuilder();
        for (int index = 0; index < segments.size(); index++) {
            JsonElement segmentElement = segments.get(index);
            if (!segmentElement.isJsonObject()) {
                errors.add("富文本片段必须是对象：" + index);
                continue;
            }
            JsonObject segment = segmentElement.getAsJsonObject();
            if (!segment.has("text") || !segment.get("text").isJsonPrimitive() || !segment.get("text").getAsJsonPrimitive().isString()) {
                errors.add("富文本片段 text 必须是文本：" + index);
                continue;
            }
            String text = segment.get("text").getAsString();
            if (text.isEmpty()) {
                errors.add("富文本片段 text 不能为空：" + index);
            }
            joined.append(text);
            validateStyle(segment, index, errors);
        }
        if (!plainText.contentEquals(joined)) {
            errors.add("富文本 plainText 与片段内容不一致。");
        }
        validateTextLength(joined.toString(), errors);
        return errors;
    }

    private static Optional<String> plainTextFromSegments(JsonObject object) {
        if (!object.has("segments") || !object.get("segments").isJsonArray()) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder();
        for (JsonElement element : object.getAsJsonArray("segments")) {
            if (!element.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject segment = element.getAsJsonObject();
            if (!segment.has("text") || !segment.get("text").isJsonPrimitive()) {
                return Optional.empty();
            }
            text.append(segment.get("text").getAsString());
        }
        return Optional.of(text.toString());
    }

    private static void validateStyle(JsonObject segment, int segmentIndex, List<String> errors) {
        if (!segment.has("style") || segment.get("style").isJsonNull()) {
            return;
        }
        if (!segment.get("style").isJsonObject()) {
            errors.add("富文本片段 style 必须是对象：" + segmentIndex);
            return;
        }
        JsonObject style = segment.getAsJsonObject("style");
        for (Map.Entry<String, JsonElement> entry : style.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (!STYLE_KEYS.contains(key)) {
                errors.add("富文本样式字段无效：" + key);
                continue;
            }
            if ("color".equals(key)) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !COLORS.contains(value.getAsString())) {
                    errors.add("富文本颜色无效：" + segmentIndex);
                }
            } else if (BOOLEAN_STYLES.contains(key) && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())) {
                errors.add("富文本样式必须是是/否值：" + key);
            }
        }
    }

    private static void validateTextLength(String text, List<String> errors) {
        if (text.length() > MAX_TEXT_LENGTH) {
            errors.add("富文本总长度不能超过 " + MAX_TEXT_LENGTH + " 个字符。");
        }
    }
}
