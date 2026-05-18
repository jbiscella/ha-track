package org.hatrack.frauholle.eodhd;

import java.util.List;
import java.util.Map;

/**
 * Small JSON-parser abstraction injected into {@link EodhdMarketDataSource}, so
 * the driver does not pin a specific JSON library. A JDK-only default
 * implementation is provided.
 */
public interface JsonReader {

    /**
     * Parses a JSON array of flat objects. Each field value is returned as its
     * raw text; a JSON {@code null} maps to a {@code null} map value. Malformed
     * JSON MUST be signalled by throwing {@link JsonParseException}.
     */
    List<Map<String, String>> readArrayOfObjects(String json);
}
