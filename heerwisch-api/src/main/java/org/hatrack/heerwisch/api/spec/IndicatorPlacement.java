package org.hatrack.heerwisch.api.spec;

import java.util.Objects;

/**
 * Associates an indicator with the pane it is rendered in. Produced by
 * {@code ChartSpecBuilder} and exposed in {@code ChartSpec.indicators()}.
 */
public record IndicatorPlacement(Indicator indicator, Pane pane) {

    public IndicatorPlacement {
        Objects.requireNonNull(indicator, "indicator");
        Objects.requireNonNull(pane, "pane");
    }
}
