package org.hatrack.indicators;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.List;

/**
 * Property-based invariants for the pure calculators (jqwik). The Cucumber
 * suite pins exact reference values on hand-picked series; these assert the
 * mathematical invariants that must hold for <em>any</em> realistic positive
 * price series — catching edge cases no enumerated example would. A small
 * absolute tolerance absorbs last-digit DECIMAL64 rounding.
 */
class IndicatorsPropertyTest {

    private static final BigDecimal EPS = new BigDecimal("0.0001");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Provide
    Arbitrary<List<BigDecimal>> priceSeries() {
        Arbitrary<BigDecimal> price = Arbitraries.doubles().between(1.0, 10000.0)
                .map(BigDecimal::valueOf);
        return price.list().ofMinSize(5).ofMaxSize(80);
    }

    @Property
    void smaStaysWithinItsWindowBounds(@ForAll("priceSeries") List<BigDecimal> src) {
        int period = Math.min(5, src.size());
        BigDecimal[] sma = Indicators.sma(src, period);
        for (int t = period - 1; t < src.size(); t++) {
            BigDecimal min = src.get(t);
            BigDecimal max = src.get(t);
            for (int j = t - period + 1; j <= t; j++) {
                min = min.min(src.get(j));
                max = max.max(src.get(j));
            }
            check(sma[t].compareTo(min.subtract(EPS)) >= 0
                            && sma[t].compareTo(max.add(EPS)) <= 0,
                    "SMA[" + t + "]=" + sma[t] + " escaped window [" + min + ", " + max + "]");
        }
    }

    @Property
    void emaStaysWithinTheSeriesBounds(@ForAll("priceSeries") List<BigDecimal> src) {
        BigDecimal min = src.get(0);
        BigDecimal max = src.get(0);
        for (BigDecimal v : src) {
            min = min.min(v);
            max = max.max(v);
        }
        BigDecimal[] ema = Indicators.ema(src, 3);
        for (BigDecimal v : ema) {
            if (v != null) {
                check(v.compareTo(min.subtract(EPS)) >= 0 && v.compareTo(max.add(EPS)) <= 0,
                        "EMA value " + v + " escaped series bounds [" + min + ", " + max + "]");
            }
        }
    }

    @Property
    void rsiAlwaysWithinZeroToHundred(@ForAll("priceSeries") List<BigDecimal> src) {
        for (BigDecimal v : Indicators.rsi(src, 3)) {
            if (v != null) {
                check(v.compareTo(EPS.negate()) >= 0 && v.compareTo(HUNDRED.add(EPS)) <= 0,
                        "RSI out of [0,100]: " + v);
            }
        }
    }

    @Property
    void bollingerUpperNeverBelowLower(@ForAll("priceSeries") List<BigDecimal> src) {
        BollingerBands bb = Indicators.bollinger(src, 4, new BigDecimal("2"));
        for (int i = 0; i < src.size(); i++) {
            if (bb.upper()[i] != null) {
                check(bb.upper()[i].compareTo(bb.middle()[i]) >= 0, "upper < middle at " + i);
                check(bb.middle()[i].compareTo(bb.lower()[i]) >= 0, "middle < lower at " + i);
            }
        }
    }

    @Property
    void atrIsNeverNegative(@ForAll("priceSeries") List<BigDecimal> closes) {
        // build a valid high/low/close triple from the generated closes
        java.util.List<BigDecimal> high = new java.util.ArrayList<>();
        java.util.List<BigDecimal> low = new java.util.ArrayList<>();
        for (BigDecimal c : closes) {
            high.add(c.add(BigDecimal.ONE));
            low.add(c.subtract(c.min(BigDecimal.ONE).min(new BigDecimal("0.5"))));
        }
        for (BigDecimal v : Indicators.atr(high, low, closes, 3)) {
            if (v != null) {
                check(v.signum() >= 0, "ATR negative: " + v);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
