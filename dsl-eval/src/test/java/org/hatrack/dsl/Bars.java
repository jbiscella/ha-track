package org.hatrack.dsl;

import org.hatrack.commons.OHLCBar;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Deterministic OHLC fixtures for the dsl-eval tests. */
final class Bars {

    private Bars() {
    }

    static final Instant EPOCH = Instant.parse("2024-01-01T00:00:00Z");

    /** {@code count} bars spaced by {@code step}, oscillating around 100, all invariant-valid. */
    static List<OHLCBar> oscillating(int count, Duration step) {
        List<OHLCBar> bars = new ArrayList<>(count);
        BigDecimal prevClose = bd(100);
        for (int i = 0; i < count; i++) {
            double c = 100 + 5 * Math.sin(i * 0.3);
            BigDecimal close = bd(round2(c));
            BigDecimal open = prevClose;
            BigDecimal high = open.max(close).add(bd(1));
            BigDecimal low = open.min(close).subtract(bd(1));
            OHLCBar bar = new OHLCBar(EPOCH.plus(step.multipliedBy(i)),
                    open, high, low, close, Optional.of(bd(1000 + i)));
            bar.validateInvariants();
            bars.add(bar);
            prevClose = close;
        }
        return bars;
    }

    static OHLCBar bar(Instant time, double open, double high, double low, double close) {
        return new OHLCBar(time, bd(open), bd(high), bd(low), bd(close), Optional.of(bd(1000)));
    }

    static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
