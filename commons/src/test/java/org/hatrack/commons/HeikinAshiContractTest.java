package org.hatrack.commons;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural contracts for {@link HeikinAshiCalculator} complementing the
 * reference-value Cucumber scenarios: the haHigh/haLow bound invariant holds on
 * every bar, and {@code computeChain} equals a manual bar-by-bar fold of
 * {@code compute}. Pure JUnit — invariants over a generated series, not single
 * reference values.
 */
class HeikinAshiContractTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");
    private static final int DECIMAL64_PRECISION = 16;

    private static List<OHLCBar> series() {
        List<OHLCBar> out = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < 30; i++) {
            double open = price;
            double close = price + (i % 3 == 0 ? 1.7 : -0.9) + (i % 5) * 0.3;
            double high = Math.max(open, close) + 1.1;
            double low = Math.min(open, close) - 1.3;
            out.add(new OHLCBar(BASE.plusSeconds(i * 86400L),
                    bd(open), bd(high), bd(low), bd(close), Optional.empty()));
            price = close;
        }
        return out;
    }

    @Test
    void haHighAndHaLowBoundEveryBar() {
        List<HABar> chain = HeikinAshiCalculator.computeChain(Optional.empty(), series());
        for (HABar h : chain) {
            BigDecimal bodyTop = h.haOpen().max(h.haClose());
            BigDecimal bodyBottom = h.haOpen().min(h.haClose());
            assertTrue(h.haHigh().compareTo(bodyTop) >= 0, "haHigh must be >= max(haOpen, haClose)");
            assertTrue(h.haLow().compareTo(bodyBottom) <= 0, "haLow must be <= min(haOpen, haClose)");
            assertTrue(h.haHigh().compareTo(h.haLow()) >= 0, "haHigh must be >= haLow");
        }
    }

    @Test
    void computeChainEqualsManualFold() {
        List<OHLCBar> bars = series();
        List<HABar> chain = HeikinAshiCalculator.computeChain(Optional.empty(), bars);
        List<HABar> manual = new ArrayList<>();
        Optional<HABar> prev = Optional.empty();
        for (OHLCBar bar : bars) {
            HABar h = HeikinAshiCalculator.compute(prev, bar);
            manual.add(h);
            prev = Optional.of(h);
        }
        assertEquals(manual.size(), chain.size());
        for (int i = 0; i < manual.size(); i++) {
            HABar a = manual.get(i);
            HABar b = chain.get(i);
            assertEquals(a.time(), b.time(), "time[" + i + "]");
            assertEquals(0, a.haOpen().compareTo(b.haOpen()), "haOpen[" + i + "]");
            assertEquals(0, a.haHigh().compareTo(b.haHigh()), "haHigh[" + i + "]");
            assertEquals(0, a.haLow().compareTo(b.haLow()), "haLow[" + i + "]");
            assertEquals(0, a.haClose().compareTo(b.haClose()), "haClose[" + i + "]");
        }
    }

    @Test
    void haOutputsStayWithinDecimal64Precision() {
        for (HABar h : HeikinAshiCalculator.computeChain(Optional.empty(), series())) {
            assertPrecision(h.haOpen());
            assertPrecision(h.haHigh());
            assertPrecision(h.haLow());
            assertPrecision(h.haClose());
        }
    }

    private static void assertPrecision(BigDecimal v) {
        assertTrue(v.precision() <= DECIMAL64_PRECISION,
                "value " + v + " exceeds DECIMAL64 precision (" + v.precision() + ")");
    }

    private static BigDecimal bd(double d) {
        return new BigDecimal(String.format(java.util.Locale.ROOT, "%.2f", d));
    }
}
