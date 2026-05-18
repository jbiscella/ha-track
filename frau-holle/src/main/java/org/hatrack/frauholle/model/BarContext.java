package org.hatrack.frauholle.model;

import org.hatrack.commons.OHLCBar;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable view passed to {@code SignalGenerator.generate()} at each step.
 * The strategy may use only data up to and including {@code currentBar()}.
 */
public record BarContext(OHLCBar currentBar, List<OHLCBar> history,
                         Optional<Position> currentPosition, BigDecimal currentCash,
                         BigDecimal currentEquity, int barIndex) {

    public BarContext {
        Objects.requireNonNull(currentBar, "currentBar");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(currentPosition, "currentPosition");
        Objects.requireNonNull(currentCash, "currentCash");
        Objects.requireNonNull(currentEquity, "currentEquity");
        history = List.copyOf(history);
    }
}
