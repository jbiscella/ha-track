package org.hatrack.heerwisch.api.spec;

import org.hatrack.commons.PriceSource;

import java.math.BigDecimal;
import java.util.Objects;

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

    record RSI(int period, BigDecimal overbought, BigDecimal oversold, PriceSource priceSource)
            implements Indicator {
        public RSI {
            requirePeriod(period, "period");
            requirePositive(overbought, "overbought");
            requirePositive(oversold, "oversold");
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
