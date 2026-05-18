package org.hatrack.frauholle.eodhd;

/** Thrown by a {@link JsonReader} when the response body is not valid JSON. */
public final class JsonParseException extends RuntimeException {

    public JsonParseException(String message) {
        super(message);
    }
}
