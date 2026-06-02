package org.hatrack.dsl;

import org.hatrack.commons.OHLCBar;
import org.hatrack.dsl.error.IndicatorWarmupException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity port of wichtelm-app's {@code indicator-evaluation.feature} /
 * {@code IndicatorEvaluationSteps}, re-expressed at the seam. The originals
 * routed each condition through {@code WichtelmSignalGenerator} and asserted
 * "entry signal emitted"; that signal layer is NOT promoted, so here the same
 * conditions are evaluated directly with {@link ExpressionEvaluator#condition}
 * over the same rising history — a true/false outcome (and a warm-up exception
 * for insufficient history) carries the identical indicator-resolution behaviour
 * the signal layer depended on, minus the trading wrapper.
 */
class IndicatorConditionParityTest {

    /** A rising history of {@code n} bars: close = 100 + i, ±2 wicks, no volume. */
    private static List<OHLCBar> rising(int n) {
        List<OHLCBar> bars = new ArrayList<>(n);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < n; i++) {
            BigDecimal close = BigDecimal.valueOf(100 + i);
            bars.add(new OHLCBar(start.plus(Duration.ofHours(i)),
                    close, close.add(BigDecimal.TWO), close.subtract(BigDecimal.TWO),
                    close, Optional.empty()));
        }
        return bars;
    }

    /** Evaluates a single condition at the last bar of the given history. */
    private static boolean condition(String text, List<OHLCBar> history) {
        OHLCBar bar = history.getLast();
        long index = history.size() - 1;
        BarIndicatorSource indicators =
                new BarIndicatorSource(history, "indicator-test", bar.time(), index);
        ExpressionEvaluator evaluator =
                new ExpressionEvaluator("indicator-test", bar.time(), index);
        ExpressionEvaluator.Values values = id -> switch (id) {
            case "close" -> bar.close();
            case "open" -> bar.open();
            case "high" -> bar.high();
            case "low" -> bar.low();
            default -> throw new AssertionError("unexpected identifier: " + id);
        };
        ExpressionEvaluator.Scope scope = new ExpressionEvaluator.Scope(values, indicators);
        return evaluator.condition(text, scope, null);
    }

    @Test
    void smaConditionHoldsOnARisingSeries() {
        assertTrue(condition("close is above sma(3)", rising(16)));
    }

    @Test
    void emaConditionHoldsOnARisingSeries() {
        assertTrue(condition("close is above ema(3)", rising(16)));
    }

    @Test
    void rsiReflectsAStrongUptrend() {
        assertTrue(condition("rsi(14) is above 70", rising(16)));
    }

    @Test
    void atrIsPositiveForANonFlatSeries() {
        assertTrue(condition("atr(14) exceeds 0", rising(16)));
    }

    @Test
    void stddevIsPositiveForANonFlatSeries() {
        assertTrue(condition("stddev(5) exceeds 0", rising(16)));
    }

    @Test
    void aConditionThatDoesNotHoldIsFalse() {
        assertFalse(condition("close is below sma(3)", rising(16)));
    }

    @Test
    void insufficientHistoryRaisesWarmup() {
        // The wichtelm signal layer caught this and emitted no entry; at the seam
        // the warm-up surfaces as IndicatorWarmupException for the caller to treat
        // as "condition not met".
        assertThrows(IndicatorWarmupException.class,
                () -> condition("highest_high(20) exceeds 0", rising(8)));
    }
}
