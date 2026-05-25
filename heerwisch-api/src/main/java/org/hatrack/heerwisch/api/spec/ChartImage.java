package org.hatrack.heerwisch.api.spec;

import java.util.List;
import java.util.Objects;

/**
 * Immutable rendered chart output. The {@code bytes} array is defensively
 * copied on construction and on access. {@code legend} lists the rendered
 * indicator series with their labels and colors (see {@link LegendEntry});
 * {@code annotationLegend} lists annotation overlays — e.g. pivot-point levels —
 * with their labels and colors (see {@link AnnotationLegendEntry}). Both lists are
 * defensively copied to immutable lists.
 */
public record ChartImage(byte[] bytes, String contentType, int widthPx, int heightPx,
                         List<LegendEntry> legend, List<AnnotationLegendEntry> annotationLegend) {

    public ChartImage {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(legend, "legend");
        Objects.requireNonNull(annotationLegend, "annotationLegend");
        bytes = bytes.clone();
        legend = List.copyOf(legend);
        annotationLegend = List.copyOf(annotationLegend);
    }

    /** Backward-compatible overload — empty annotation legend. */
    public ChartImage(byte[] bytes, String contentType, int widthPx, int heightPx,
                      List<LegendEntry> legend) {
        this(bytes, contentType, widthPx, heightPx, legend, List.of());
    }

    /** Backward-compatible overload — empty legend and annotation legend. */
    public ChartImage(byte[] bytes, String contentType, int widthPx, int heightPx) {
        this(bytes, contentType, widthPx, heightPx, List.of(), List.of());
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
