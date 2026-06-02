package org.hatrack.dsl;

import org.hatrack.commons.OHLCBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parity port of wichtelm-app's {@code window-aggregates.feature} /
 * {@code WindowAggregateSteps}. Pins highest_high / lowest_low / highest_close /
 * lowest_close / avg_volume to the reference reductions over the trailing window.
 */
class WindowAggregateParityTest {

    /** 30 bars, non-monotonic so the extreme is not trivially first or last. */
    private static List<OHLCBar> series(int barCount) {
        List<OHLCBar> bars = new ArrayList<>(barCount);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < barCount; i++) {
            double base = 100 + 6 * Math.sin(i / 3.0) + i * 0.1;
            BigDecimal close = BigDecimal.valueOf(base);
            BigDecimal high = BigDecimal.valueOf(base + 2 + (i % 4));
            BigDecimal low = BigDecimal.valueOf(base - 2 - (i % 3));
            BigDecimal volume = BigDecimal.valueOf(1000 + (i % 7) * 50L);
            bars.add(new OHLCBar(start.plus(Duration.ofHours(i)),
                    close, high, low, close, Optional.of(volume)));
        }
        return bars;
    }

    private final List<OHLCBar> bars = series(30);

    private BigDecimal evaluate(String expression) {
        OHLCBar bar = bars.getLast();
        long index = bars.size() - 1;
        BarIndicatorSource indicators =
                new BarIndicatorSource(bars, "window-test", bar.time(), index);
        ExpressionEvaluator evaluator =
                new ExpressionEvaluator("window-test", bar.time(), index);
        ExpressionEvaluator.Scope scope = new ExpressionEvaluator.Scope(
                id -> {
                    throw new AssertionError("unexpected bare identifier: " + id);
                },
                indicators);
        return evaluator.arithmetic(expression, scope);
    }

    private List<OHLCBar> window(int period) {
        return bars.subList(bars.size() - period, bars.size());
    }

    @Test
    void highestHighIsTheWindowMaxHigh() {
        BigDecimal expected = window(10).stream().map(OHLCBar::high)
                .max(BigDecimal::compareTo).orElseThrow();
        assertEquals(0, evaluate("highest_high(10)").compareTo(expected));
    }

    @Test
    void lowestLowIsTheWindowMinLow() {
        BigDecimal expected = window(10).stream().map(OHLCBar::low)
                .min(BigDecimal::compareTo).orElseThrow();
        assertEquals(0, evaluate("lowest_low(10)").compareTo(expected));
    }

    @Test
    void highestCloseIsTheWindowMaxClose() {
        BigDecimal expected = window(10).stream().map(OHLCBar::close)
                .max(BigDecimal::compareTo).orElseThrow();
        assertEquals(0, evaluate("highest_close(10)").compareTo(expected));
    }

    @Test
    void lowestCloseIsTheWindowMinClose() {
        BigDecimal expected = window(10).stream().map(OHLCBar::close)
                .min(BigDecimal::compareTo).orElseThrow();
        assertEquals(0, evaluate("lowest_close(10)").compareTo(expected));
    }

    @Test
    void avgVolumeIsTheWindowMeanVolume() {
        int period = 20;
        BigDecimal sum = window(period).stream().map(b -> b.volume().orElseThrow())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MathContext.DECIMAL64));
        BigDecimal expected = sum.divide(BigDecimal.valueOf(period), MathContext.DECIMAL64);
        assertEquals(0, evaluate("avg_volume(20)").compareTo(expected));
    }
}
