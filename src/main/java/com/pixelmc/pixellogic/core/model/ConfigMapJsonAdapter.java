package com.pixelmc.pixellogic.core.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class ConfigMapJsonAdapter extends TypeAdapter<Map<String, String>> {
    private static final String STRING_ENCODED_TARGET = "\u0000string-encoded-entity-target:";

    @Override
    public void write(JsonWriter writer, Map<String, String> config) throws IOException {
        writer.beginObject();
        if (config != null) {
            for (Map.Entry<String, String> entry : new TreeMap<>(config).entrySet()) {
                writer.name(entry.getKey());
                EntityTargetRef target = "target".equals(entry.getKey()) ? parseTarget(entry.getValue()) : null;
                if (target == null) {
                    writer.value(entry.getValue());
                } else {
                    writeTarget(writer, target);
                }
            }
        }
        writer.endObject();
    }

    @Override
    public Map<String, String> read(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new JsonParseException("config must be an object");
        }
        Map<String, String> config = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            JsonToken token = reader.peek();
            if ("target".equals(key) && token == JsonToken.BEGIN_OBJECT) {
                JsonElement target = JsonParser.parseReader(reader);
                config.put(key, target.toString());
            } else if (token == JsonToken.STRING) {
                String value = reader.nextString();
                config.put(key, "target".equals(key) && parseTarget(value) != null
                        ? STRING_ENCODED_TARGET + value
                        : value);
            } else {
                throw new JsonParseException("config." + key + " must be a string"
                        + ("target".equals(key) ? " or entity target object" : ""));
            }
        }
        reader.endObject();
        return config;
    }

    private static EntityTargetRef parseTarget(String value) {
        try {
            return EntityTargetRef.parse(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void writeTarget(JsonWriter writer, EntityTargetRef target) throws IOException {
        writer.beginObject();
        writer.name("source").value(target.source().name());
        if (target.source() == EntityTargetSource.ONLINE_PLAYER) {
            writer.name("playerUuid").value(target.playerUuid().toString());
            if (target.playerNameHint() != null) {
                writer.name("playerNameHint").value(target.playerNameHint());
            }
        }
        writer.endObject();
    }
}
