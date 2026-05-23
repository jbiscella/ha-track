package org.hatrack.heerwisch.api.spec;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.PivotPointVariant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    /**
     * A horizontal reference line at {@code price}, styled by {@code style}
     * (solid / dashed / dotted). An optional {@link FillColor} selects a
     * semantic line color: consumers render the industry-convention scheme
     * (TradingView and similar) — entry neutral, stop-loss red ({@code LOSS}),
     * take-profit green ({@code WIN}) — by passing the matching enum value.
     * When {@code fillColor} is empty (the 3-arg constructor) the line uses the
     * driver's default reference color, unchanged from prior releases.
     */
    record HorizontalLevel(BigDecimal price, String label, LevelStyle style,
                           Optional<FillColor> fillColor) implements Annotation {
        public HorizontalLevel {
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(fillColor, "fillColor");
        }

        /** Backward-compatible overload — no semantic color (default reference line). */
        public HorizontalLevel(BigDecimal price, String label, LevelStyle style) {
            this(price, label, style, Optional.empty());
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
     * Auto-positioned variant of {@link EntryExitMarker}. The caller supplies
     * only the bar time, the direction, and the glyph shape; the renderer
     * computes the Y position from the bar at {@code time}:
     *
     * <ul>
     *   <li>{@link MarkerDirection#LONG_ENTRY} and {@link MarkerDirection#SHORT_EXIT}
     *       — glyph sits below the bar's low (semantic "up" arrow pointing
     *       at the bar from underneath).</li>
     *   <li>{@link MarkerDirection#LONG_EXIT} and {@link MarkerDirection#SHORT_ENTRY}
     *       — glyph sits above the bar's high (semantic "down" arrow pointing
     *       at the bar from above).</li>
     * </ul>
     *
     * Matches industry convention (TradingView and similar tools): trade
     * markers sit outside the candle so they do not occlude price action.
     * This is the recommended form for visualizing trade entries and exits.
     *
     * <p>{@link EntryExitMarker} (the explicit-price variant) remains
     * first-class and is the right choice for pinning markers to a specific
     * Y position that is not tied to a bar's high/low — target levels,
     * limit-order prices, indicator-driven alerts, etc.
     *
     * <p>Like {@link EntryExitMarker}, the marker's {@code time} must equal
     * a bar time in the series (rule V16); the renderer needs the bar's
     * high/low to position the glyph.
     */
    record EntryExitMarkerAuto(Instant time, MarkerDirection direction,
                               GlyphStyle glyphStyle)
            implements Annotation {
        public EntryExitMarkerAuto {
            Objects.requireNonNull(time, "time");
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
