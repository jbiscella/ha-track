package org.hatrack.frauholle.eodhd;

import java.util.Objects;

/** The outcome of an HTTP GET: the status code and the response body. */
public record HttpResult(int statusCode, String body) {

    public HttpResult {
        Objects.requireNonNull(body, "body");
    }
}
