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
 * <p>One entry per rendered line. Single-line indicators yield one entry per
 * {@link IndicatorPlacement}; dual-line indicators (MACD, Stochastic) yield two
 * entries (line + signal / {@code %K} + {@code %D}); BollingerBands yields three
 * (upper / basis / lower). All entries of a multi-line indicator share the same
 * {@code placement}; MACD/Stochastic entries differ in color, BollingerBands'
 * three entries share one color (they are a visually grouped band). Entries
 * appear in {@code ChartSpec.indicators()} insertion order; {@code pane} (which
 * always equals {@code placement.pane()}) lets consumers group rows into
 * per-pane sections.
 *
 * <p>Invariants enforced at construction: {@code rgb} is in {@code [0, 0xFFFFFF]}
 * (24-bit, no alpha/sign bits) and {@code pane} equals {@code placement.pane()}.
 */
public record LegendEntry(IndicatorPlacement placement, String label, int rgb, Pane pane) {

    public LegendEntry {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(pane, "pane");
        if (rgb < 0 || rgb > 0xFFFFFF) {
            throw new IllegalArgumentException(
                    "rgb must be a 24-bit value in [0, 0xFFFFFF], was 0x" + Integer.toHexString(rgb));
        }
        if (!pane.equals(placement.pane())) {
            throw new IllegalArgumentException("pane " + pane
                    + " must equal placement.pane() " + placement.pane());
        }
    }
}
