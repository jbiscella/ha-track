package org.hatrack.frauholle.eodhd.internal;

import org.hatrack.frauholle.eodhd.JsonParseException;
import org.hatrack.frauholle.eodhd.JsonReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDK-only default {@link JsonReader}, backed by {@link JsonParser}. */
public final class DefaultJsonReader implements JsonReader {

    @Override
    public List<Map<String, String>> readArrayOfObjects(String json) {
        Object root = JsonParser.parse(json);
        if (!(root instanceof List<?> elements)) {
            throw new JsonParseException("expected a top-level JSON array");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (Object element : elements) {
            if (!(element instanceof Map<?, ?> object)) {
                throw new JsonParseException("expected a JSON object inside the array");
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                Object value = entry.getValue();
                row.put(String.valueOf(entry.getKey()), value == null ? null : String.valueOf(value));
            }
            rows.add(row);
        }
        return rows;
    }
}
