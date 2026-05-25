package org.hatrack.heerwisch.api.spec;

import java.util.Objects;

/**
 * One row of a chart's annotation-overlay legend: the label naming an annotation
 * overlay and the color it was drawn in. Exposed via
 * {@link ChartImage#annotationLegend()}, parallel to {@link LegendEntry} (which
 * names indicator series). Annotation overlays — e.g. a pivot-point set — are not
 * indicator placements, so they carry no {@link IndicatorPlacement} and no
 * {@link Pane}; this slimmer {@code (label, rgb)} shape names them for a legend
 * strip without bending the indicator-centric {@code LegendEntry} contract.
 *
 * <p>{@code rgb} is plain 24-bit {@code 0xRRGGBB} (no alpha) — engine-neutral, so
 * {@code heerwisch-api} carries no {@code java.awt} coupling. Convert as needed
 * (e.g. {@code new java.awt.Color(rgb)} or a CSS {@code #RRGGBB} string).
 *
 * <p>Invariant enforced at construction: {@code rgb} is in {@code [0, 0xFFFFFF]}
 * (24-bit, no alpha/sign bits).
 */
public record AnnotationLegendEntry(String label, int rgb) {

    public AnnotationLegendEntry {
        Objects.requireNonNull(label, "label");
        if (rgb < 0 || rgb > 0xFFFFFF) {
            throw new IllegalArgumentException(
                    "rgb must be a 24-bit value in [0, 0xFFFFFF], was 0x" + Integer.toHexString(rgb));
        }
    }
}
