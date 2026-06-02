package org.hatrack.dsl;

import java.util.Objects;

/**
 * One expression text the {@link NachtkrappMatchIndex} prepass should scan,
 * paired with the timeframe wire string ({@code "1h"}, {@code "1d"}, …) on
 * which any boolean primitives inside it must be detected.
 *
 * <p>This is the seam by which a consumer hands its compiled/parsed conditions
 * to the shared prepass without the prepass knowing anything about the
 * consumer's strategy AST. The consumer is responsible for deciding, per
 * expression, which timeframe applies (e.g. a primitive declared "on 1d"
 * carries {@code "1d"}; one evaluated against the primary series carries the
 * primary timeframe wire).
 */
public record ExpressionRef(String text, String timeframeWire) {
    public ExpressionRef {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(timeframeWire, "timeframeWire");
    }
}
