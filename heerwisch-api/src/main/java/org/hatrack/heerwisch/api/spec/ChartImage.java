package org.hatrack.heerwisch.api.spec;

import java.util.Objects;

/**
 * Immutable rendered chart output. The {@code bytes} array is defensively
 * copied on construction and on access.
 */
public record ChartImage(byte[] bytes, String contentType, int widthPx, int heightPx) {

    public ChartImage {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(contentType, "contentType");
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
