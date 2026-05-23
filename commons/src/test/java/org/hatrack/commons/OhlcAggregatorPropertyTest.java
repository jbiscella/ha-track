package org.hatrack.commons;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Property-based invariants for {@link OHLCAggregator} over arbitrary
 * ascending intraday series (jqwik). Aggregation must preserve OHLC validity,
 * emit strictly-ascending period starts, and never produce more bars than it
 * consumed.
 */
class OhlcAggregatorPropertyTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    @Provide
    Arbitrary<List<BigDecimal>> closes() {
        return Arbitraries.doubles().between(2.0, 5000.0).map(BigDecimal::valueOf)
                .list().ofMinSize(1).ofMaxSize(72);
    }

    private static List<OHLCBar> hourlyBars(List<BigDecimal> closes) {
        List<OHLCBar> bars = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            BigDecimal c = closes.get(i);
            bars.add(new OHLCBar(BASE.plusSeconds(i * 3600L),
                    c, c.add(BigDecimal.ONE), c.subtract(BigDecimal.ONE), c, Optional.empty()));
        }
        return bars;
    }

    @Property
    void aggregationPreservesValidityAndOrdering(@ForAll("closes") List<BigDecimal> closes) {
        List<OHLCBar> intraday = hourlyBars(closes);
        List<OHLCBar> out = OHLCAggregator.toPeriod(new OHLCSeries(intraday), Timeframe.fromWire("1d")).bars();

        check(!out.isEmpty(), "non-empty input must yield at least one period bar");
        check(out.size() <= intraday.size(), "aggregation cannot create bars");

        for (int i = 0; i < out.size(); i++) {
            OHLCBar b = out.get(i);
            // OHLC validity is preserved by aggregation (max high, min low, first open, last close)
            check(b.high().compareTo(b.low()) >= 0, "high < low");
            check(b.high().compareTo(b.open()) >= 0, "high < open");
            check(b.high().compareTo(b.close()) >= 0, "high < close");
            check(b.low().compareTo(b.open()) <= 0, "low > open");
            check(b.low().compareTo(b.close()) <= 0, "low > close");
            if (i > 0) {
                check(b.time().isAfter(out.get(i - 1).time()), "period starts not strictly ascending");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
