package org.hatrack.heerwisch.api.spec;

import java.util.Objects;

/**
 * One row of a chart's legend: a rendered series, the label that names it, and
 * the color it was drawn in. Exposed via {@link ChartImage#legend()} so
 * consumers can render legend chrome in their own UI.
 *
 * <p>{@code rgb} is plain 24-bit {@code 0xRRGGBB} (no alpha) — engine-neutral,
 * so {@code heerwisch-api} carries no {@code java.awt} coupling. Convert as
 * needed (e.g. {@code new java.awt.Color(rgb)} or a CSS {@code #RRGGBB} string).
 *
 * <p>One entry per rendered series. Single-line indicators yield one entry per
 * {@link IndicatorPlacement}; dual-line indicators (MACD, Stochastic) yield two
 * entries that share the same {@code placement} but carry distinct {@code label}
 * and {@code rgb} (e.g. the MACD line and its signal line). Entries appear in
 * {@code ChartSpec.indicators()} insertion order; {@code pane} lets consumers
 * group rows into per-pane sections.
 */
public record LegendEntry(IndicatorPlacement placement, String label, int rgb, Pane pane) {

    public LegendEntry {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(pane, "pane");
    }
}
