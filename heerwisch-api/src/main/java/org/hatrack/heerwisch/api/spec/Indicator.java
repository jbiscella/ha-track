package org.hatrack.heerwisch.api.spec;

import org.hatrack.commons.PriceSource;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Closed hierarchy of chart indicators. Variants are nested records. Canonical
 * constructors reject non-positive periods and ratios eagerly (heerwisch-api
 * CLAUDE.md section 1.2).
 */
public sealed interface Indicator {

    /** Minimum number of bars the series must have for this indicator. */
    int minBars();

    /** Default pane for this indicator (heerwisch-api CLAUDE.md section 1.2). */
    Pane defaultPane();

    private static int requirePeriod(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be >= 1, was " + value);
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be > 0, was " + value);
        }
        return value;
    }

    record SMA(int period, PriceSource priceSource) implements Indicator {
        public SMA {
            requirePeriod(period, "period");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return period;
        }

        @Override
        public Pane defaultPane() {
            return Pane.MAIN;
        }
    }

    record EMA(int period, PriceSource priceSource) implements Indicator {
        public EMA {
            requirePeriod(period, "period");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return period;
        }

        @Override
        public Pane defaultPane() {
            return Pane.MAIN;
        }
    }

    /**
     * Rolling maximum of {@code priceSource} over a trailing {@code period}
     * window, drawn as a single main-pane line (highest-high / highest-close
     * style channel). Pair with {@link RollingMin} to form a Donchian-style
     * channel; there is no band-with-fill variant.
     */
    record RollingMax(int period, PriceSource priceSource) implements Indicator {
        public RollingMax {
            requirePeriod(period, "period");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return period;
        }

        @Override
        public Pane defaultPane() {
            return Pane.MAIN;
        }
    }

    /**
     * Rolling minimum of {@code priceSource} over a trailing {@code period}
     * window, drawn as a single main-pane line (lowest-low / lowest-close style
     * channel). Pair with {@link RollingMax} to form a Donchian-style channel.
     */
    record RollingMin(int period, PriceSource priceSource) implements Indicator {
        public RollingMin {
            requirePeriod(period, "period");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return period;
        }

        @Override
        public Pane defaultPane() {
            return Pane.MAIN;
        }
    }

    record BollingerBands(int period, BigDecimal stdDevMultiplier, PriceSource priceSource)
            implements Indicator {
        public BollingerBands {
            requirePeriod(period, "period");
            requirePositive(stdDevMultiplier, "stdDevMultiplier");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return period;
        }

        @Override
        public Pane defaultPane() {
            return Pane.MAIN;
        }
    }

    record MACD(int fastPeriod, int slowPeriod, int signalPeriod, PriceSource priceSource)
            implements Indicator {
        public MACD {
            requirePeriod(fastPeriod, "fastPeriod");
            requirePeriod(slowPeriod, "slowPeriod");
            requirePeriod(signalPeriod, "signalPeriod");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return slowPeriod + signalPeriod;
        }

        @Override
        public Pane defaultPane() {
            return Pane.SUBPLOT_1;
        }
    }

    record RSI(int period, BigDecimal overbought, BigDecimal oversold, PriceSource priceSource,
               Optional<RsiVisualization> visualization)
            implements Indicator {
        public RSI {
            requirePeriod(period, "period");
            Objects.requireNonNull(overbought, "overbought");
            Objects.requireNonNull(oversold, "oversold");
            Objects.requireNonNull(priceSource, "priceSource");
            Objects.requireNonNull(visualization, "visualization");
            // Bounds checks (overbought in (0, 100], oversold in [0, 100),
            // oversold < overbought) are enforced as V19/V20/V21 by
            // ChartSpecBuilder.build(). The canonical constructor accepts
            // any non-null BigDecimals so the bounds-violation path always
            // produces an InvalidChartSpecException rather than a raw
            // IllegalArgumentException.
        }

        /**
         * Backward-compatible overload — defers to the canonical constructor
         * with {@code visualization = Optional.empty()}. Existing callers
         * built against the 4-argument signature continue to work unchanged.
         */
        public RSI(int period, BigDecimal overbought, BigDecimal oversold,
                   PriceSource priceSource) {
            this(period, overbought, oversold, priceSource, Optional.empty());
        }

        @Override
        public int minBars() {
            return period;
        }

        @Override
        public Pane defaultPane() {
            return Pane.SUBPLOT_1;
        }
    }

    /**
     * Optional sub-pane visualization knobs for {@link RSI}. Carries
     * driver-applied rendering decisions that have no impact on the
     * indicator's numeric values; absent (i.e. {@code Optional.empty()}) means
     * "default rendering" (threshold lines at overbought/oversold per
     * {@code heerwisch-jfreechart/CLAUDE.md} §7, no shaded danger zones).
     *
     * <p>The danger-zone toggle pattern is intended to generalize to other
     * bounded indicators in future releases (e.g. {@code Stochastic}); RSI is
     * the first instance.
     */
    record RsiVisualization(boolean dangerZones) {
        /** Default visualization: threshold lines drawn, no danger-zone shading. */
        public static final RsiVisualization DEFAULT = new RsiVisualization(false);

        /** Threshold lines drawn AND shaded danger zones above overbought / below oversold. */
        public static final RsiVisualization DANGER_ZONES_ON = new RsiVisualization(true);
    }

    record ADX(int period) implements Indicator {
        public ADX {
            requirePeriod(period, "period");
        }

        @Override
        public int minBars() {
            return period;
        }

        @Override
        public Pane defaultPane() {
            return Pane.SUBPLOT_1;
        }
    }

    record Stochastic(int kPeriod, int dPeriod, int smoothing) implements Indicator {
        public Stochastic {
            requirePeriod(kPeriod, "kPeriod");
            requirePeriod(dPeriod, "dPeriod");
            requirePeriod(smoothing, "smoothing");
        }

        @Override
        public int minBars() {
            return kPeriod + dPeriod + smoothing;
        }

        @Override
        public Pane defaultPane() {
            return Pane.SUBPLOT_1;
        }
    }

    record ATR(int period) implements Indicator {
        public ATR {
            requirePeriod(period, "period");
        }

        @Override
        public int minBars() {
            return period;
        }

        @Override
        public Pane defaultPane() {
            return Pane.SUBPLOT_1;
        }
    }

    /**
     * Rolling population standard deviation of {@code priceSource} over a
     * trailing {@code period} window, drawn as a single line in its own sub-pane
     * (σ is unbounded relative to price, like {@link ATR}). Unlike
     * {@link BollingerBands}, this surfaces the raw σ(period) series as a value
     * rather than as bands around a moving average.
     */
    record StdDev(int period, PriceSource priceSource) implements Indicator {
        public StdDev {
            requirePeriod(period, "period");
            Objects.requireNonNull(priceSource, "priceSource");
        }

        @Override
        public int minBars() {
            return period;
        }

        @Override
        public Pane defaultPane() {
            return Pane.SUBPLOT_1;
        }
    }

    record VolumePane() implements Indicator {
        @Override
        public int minBars() {
            return 1;
        }

        @Override
        public Pane defaultPane() {
            return Pane.SUBPLOT_1;
        }
    }
}
