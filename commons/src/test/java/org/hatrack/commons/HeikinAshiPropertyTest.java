package org.hatrack.commons;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Property-based invariant for {@link HeikinAshiCalculator}: on every computed
 * bar, {@code haHigh} dominates the body and {@code haLow} is dominated by it,
 * for any chain of valid OHLC bars (jqwik).
 */
class HeikinAshiPropertyTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    @Provide
    Arbitrary<List<OHLCBar>> validSeries() {
        Arbitrary<Double> low = Arbitraries.doubles().between(1.0, 1000.0);
        Arbitrary<Double> range = Arbitraries.doubles().between(0.0, 200.0);
        Arbitrary<Double> openFrac = Arbitraries.doubles().between(0.0, 1.0);
        Arbitrary<Double> closeFrac = Arbitraries.doubles().between(0.0, 1.0);
        Arbitrary<OHLCBar> bar = Combinators.combine(low, range, openFrac, closeFrac)
                .as((lo, r, of, cf) -> new OHLCBar(BASE,
                        bd(lo + of * r), bd(lo + r), bd(lo), bd(lo + cf * r), Optional.empty()));
        return bar.list().ofMinSize(1).ofMaxSize(50);
    }

    @Property
    void haHighAndHaLowBoundTheBodyOnEveryBar(@ForAll("validSeries") List<OHLCBar> series) {
        for (HABar h : HeikinAshiCalculator.computeChain(Optional.empty(), series)) {
            BigDecimal top = h.haOpen().max(h.haClose());
            BigDecimal bottom = h.haOpen().min(h.haClose());
            check(h.haHigh().compareTo(top) >= 0, "haHigh < max(haOpen, haClose)");
            check(h.haLow().compareTo(bottom) <= 0, "haLow > min(haOpen, haClose)");
            check(h.haHigh().compareTo(h.haLow()) >= 0, "haHigh < haLow");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static BigDecimal bd(double d) {
        return BigDecimal.valueOf(d);
    }
}
