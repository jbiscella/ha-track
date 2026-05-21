package org.hatrack.heerwisch.api.spec;

import java.util.List;
import java.util.Objects;

/**
 * Immutable rendered chart output. The {@code bytes} array is defensively
 * copied on construction and on access. {@code legend} lists the rendered
 * series with their labels and colors (see {@link LegendEntry}); it is
 * defensively copied to an immutable list.
 */
public record ChartImage(byte[] bytes, String contentType, int widthPx, int heightPx,
                         List<LegendEntry> legend) {

    public ChartImage {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(legend, "legend");
        bytes = bytes.clone();
        legend = List.copyOf(legend);
    }

    /** Backward-compatible overload — empty legend. */
    public ChartImage(byte[] bytes, String contentType, int widthPx, int heightPx) {
        this(bytes, contentType, widthPx, heightPx, List.of());
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
