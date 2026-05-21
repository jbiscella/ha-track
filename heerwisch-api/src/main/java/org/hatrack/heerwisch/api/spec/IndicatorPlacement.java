package org.hatrack.heerwisch.api.spec;

import java.util.Objects;
import java.util.Optional;

/**
 * Associates an indicator with the pane it is rendered in, plus an optional
 * label override. Produced by {@code ChartSpecBuilder} and exposed in
 * {@code ChartSpec.indicators()}.
 *
 * <p>When {@code label} is present it overrides the driver's auto-derived
 * label (e.g. {@code "SMA(20)"}) wherever the placement is named — sub-pane
 * axis labels and legend entries. When empty (the 2-arg constructor) the
 * auto-derived label is used.
 */
public record IndicatorPlacement(Indicator indicator, Pane pane, Optional<String> label) {

    public IndicatorPlacement {
        Objects.requireNonNull(indicator, "indicator");
        Objects.requireNonNull(pane, "pane");
        Objects.requireNonNull(label, "label");
    }

    /** Backward-compatible overload — no label override. */
    public IndicatorPlacement(Indicator indicator, Pane pane) {
        this(indicator, pane, Optional.empty());
    }
}
