package org.hatrack.heerwisch.api.spec;

import org.hatrack.commons.OHLCBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Closed hierarchy of chart annotations. Variants are nested records.
 */
public sealed interface Annotation {

    record BarHighlight(Instant time, BigDecimal price, String label) implements Annotation {
        public BarHighlight {
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(label, "label");
        }
    }

    record HorizontalLevel(BigDecimal price, String label, LevelStyle style) implements Annotation {
        public HorizontalLevel {
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(style, "style");
        }
    }

    record FibRetracement(BigDecimal swingHigh, BigDecimal swingLow, List<BigDecimal> levels)
            implements Annotation {

        /** The standard Fibonacci retracement fractions. */
        public static final List<BigDecimal> STANDARD_LEVELS = List.of(
                new BigDecimal("0.236"),
                new BigDecimal("0.382"),
                new BigDecimal("0.5"),
                new BigDecimal("0.618"),
                new BigDecimal("0.786"));

        public FibRetracement {
            Objects.requireNonNull(swingHigh, "swingHigh");
            Objects.requireNonNull(swingLow, "swingLow");
            Objects.requireNonNull(levels, "levels");
            levels = List.copyOf(levels);
            for (BigDecimal level : levels) {
                if (level.signum() < 0 || level.compareTo(BigDecimal.ONE) > 0) {
                    throw new IllegalArgumentException("fib level must be in [0, 1], was " + level);
                }
            }
        }
    }

    record PivotPointLevels(PivotPointVariant variant, OHLCBar previousPeriodBar)
            implements Annotation {
        public PivotPointLevels {
            Objects.requireNonNull(variant, "variant");
            Objects.requireNonNull(previousPeriodBar, "previousPeriodBar");
        }
    }

    /**
     * A semantic glyph (triangle or arrow) at a specific bar, colored by the
     * conventional entry/exit role. The shape is chosen via {@code glyphStyle};
     * the color is chosen by the renderer from {@code direction} (long-entry
     * and short-exit render in the bullish theme color, short-entry and
     * long-exit in the bearish theme color). Use this for trade markers on a
     * backtest chart.
     */
    record EntryExitMarker(Instant time, BigDecimal price,
                           MarkerDirection direction, GlyphStyle glyphStyle)
            implements Annotation {
        public EntryExitMarker {
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(glyphStyle, "glyphStyle");
        }
    }

    /**
     * A semi-transparent shaded band over a closed time interval, drawn behind
     * the chart's series and indicators. Typical use: shade the "in-position"
     * period of a backtest trade. The color is chosen by the renderer from
     * {@code fillColor}; {@code opacity} is in {@code [0, 1]} inclusive.
     */
    record TimeRangeHighlight(Instant startTime, Instant endTime,
                              FillColor fillColor, BigDecimal opacity)
            implements Annotation {
        public TimeRangeHighlight {
            Objects.requireNonNull(startTime, "startTime");
            Objects.requireNonNull(endTime, "endTime");
            Objects.requireNonNull(fillColor, "fillColor");
            Objects.requireNonNull(opacity, "opacity");
        }
    }
}
