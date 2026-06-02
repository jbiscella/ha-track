package org.hatrack.dsl;

import org.hatrack.commons.OHLCBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NachtkrappMatchIndexTest {

    // Hourly bars over several UTC days so the daily pivot rule has prior days.
    private final List<OHLCBar> bars = Bars.oscillating(240, Duration.ofHours(1));

    @Test
    void emptyWhenNoPrimitivesReferenced() {
        NachtkrappMatchIndex index = NachtkrappMatchIndex.buildFor(
                List.of(new ExpressionRef("close is above sma(50)", "1h")),
                Map.of(), bars, "1h", Map.of());
        assertFalse(index.hasKey(new NachtkrappMatchIndex.Key("ha_doji", List.of(), "1h")));
    }

    @Test
    void buildsAcrossAllPrimitiveFamilies() {
        // One expression naming a wide spread of primitives so the buildKeySpec
        // switch and every detection pass (HA + OHLC) execute.
        String expr = String.join(" and ",
                "ha_doji()", "ha_strong()", "ha_strong_bullish()", "ha_strong_bearish()",
                "ha_bullish_reversal(2)", "ha_bearish_reversal(2)",
                "rsi_overbought(70)", "rsi_oversold(30)", "rsi_crosses_50()",
                "macd_bullish_cross()", "macd_bearish_cross()",
                "macd_zero_cross_up()", "macd_zero_cross_down()",
                "price_above_sma(20)", "price_below_sma(20)",
                "price_above_ema(20)", "price_below_ema(20)",
                "price_crosses_above_sma(20)", "price_crosses_below_sma(20)",
                "price_crosses_above_ema(20)", "price_crosses_below_ema(20)",
                "sma_above_ema(10, 20)", "sma_crosses_above_ema(10, 20)",
                "sma_crosses_below_ema(10, 20)",
                "price_above_pivot(R1)", "price_below_pivot(S1)",
                "price_crosses_above_pivot(R1)", "price_crosses_below_pivot(S1)");

        NachtkrappMatchIndex index = NachtkrappMatchIndex.buildFor(
                List.of(new ExpressionRef(expr, "1h")), Map.of(), bars, "1h", Map.of());

        // Each referenced key is present (membership), regardless of whether it
        // actually matched at any bar.
        assertTrue(index.hasKey(new NachtkrappMatchIndex.Key("ha_doji", List.of(), "1h")));
        assertTrue(index.hasKey(new NachtkrappMatchIndex.Key(
                "price_above_sma", List.of(new BigDecimal("20")), "1h")));
        assertTrue(index.hasKey(
                NachtkrappMatchIndex.Key.pivot("price_above_pivot", "R1", "1h")));

        // matches() answers false for a bar time that was not in any match set.
        Instant alien = Instant.parse("1999-01-01T00:00:00Z");
        assertFalse(index.matches(
                new NachtkrappMatchIndex.Key("ha_doji", List.of(), "1h"), alien));
    }

    @Test
    void parametersResolveInArguments() {
        NachtkrappMatchIndex index = NachtkrappMatchIndex.buildFor(
                List.of(new ExpressionRef("rsi_overbought(level)", "1h")),
                Map.of("level", new BigDecimal("70")), bars, "1h", Map.of());
        assertTrue(index.hasKey(new NachtkrappMatchIndex.Key(
                "rsi_overbought", List.of(new BigDecimal("70")), "1h")));
    }

    @Test
    void nonNumericNonParameterArgumentRejected() {
        assertThrows(IllegalArgumentException.class, () -> NachtkrappMatchIndex.buildFor(
                List.of(new ExpressionRef("rsi_overbought(close)", "1h")),
                Map.of(), bars, "1h", Map.of()));
    }

    @Test
    void missingHigherTimeframeBarsThrows() {
        assertThrows(IllegalStateException.class, () -> NachtkrappMatchIndex.buildFor(
                List.of(new ExpressionRef("ha_doji()", "1d")),
                Map.of(), bars, "1h", Map.of()));
    }

    @Test
    void higherTimeframeBarsAreUsed() {
        List<OHLCBar> daily = Bars.oscillating(30, Duration.ofDays(1));
        NachtkrappMatchIndex index = NachtkrappMatchIndex.buildFor(
                List.of(new ExpressionRef("ha_doji() and rsi_overbought(70)", "1d")),
                Map.of(), bars, "1h", Map.of("1d", daily));
        assertTrue(index.hasKey(new NachtkrappMatchIndex.Key("ha_doji", List.of(), "1d")));
    }

    @Test
    void warmupExceedingSeriesDropsRuleWithoutThrowing() {
        // sma_above_ema(300, 400) needs more bars than exist → rule dropped,
        // key still registered with an empty match set.
        List<OHLCBar> few = Bars.oscillating(30, Duration.ofHours(1));
        NachtkrappMatchIndex index = NachtkrappMatchIndex.buildFor(
                List.of(new ExpressionRef("sma_above_ema(300, 400)", "1h")),
                Map.of(), few, "1h", Map.of());
        NachtkrappMatchIndex.Key key = new NachtkrappMatchIndex.Key(
                "sma_above_ema",
                List.of(new BigDecimal("300"), new BigDecimal("400")), "1h");
        assertTrue(index.hasKey(key));
        assertFalse(index.matches(key, bars.getFirst().time()));
    }

    @Test
    void highPrecisionLiteralKeyMatchesEvaluatorRounding() {
        // The prepass must round a >16-digit literal with DECIMAL64 just like
        // ExpressionEvaluator.number() does, so the runtime key (built from the
        // evaluator's rounded arg) hits the pre-indexed key instead of missing.
        String literal = "0.12345678901234567890";
        NachtkrappMatchIndex index = NachtkrappMatchIndex.buildFor(
                List.of(new ExpressionRef("ha_doji(" + literal + ")", "1h")),
                Map.of(), bars, "1h", Map.of());

        // The key the runtime evaluator would build: literal rounded to DECIMAL64.
        BigDecimal runtimeArg = new BigDecimal(literal, java.math.MathContext.DECIMAL64);
        NachtkrappMatchIndex.Key runtimeKey =
                new NachtkrappMatchIndex.Key("ha_doji", List.of(runtimeArg), "1h");
        assertTrue(index.hasKey(runtimeKey),
                "prepass key must equal the DECIMAL64-rounded runtime key");

        // And end-to-end: BarIndicatorSource.tierB resolves rather than throwing.
        BarIndicatorSource src = new BarIndicatorSource(bars, "strat",
                bars.getLast().time(), bars.size() - 1, index, "1h");
        src.evaluate("ha_doji", List.of(runtimeArg));
    }

    @Test
    void emptyFactoryHasNoKeys() {
        NachtkrappMatchIndex empty = NachtkrappMatchIndex.empty();
        assertFalse(empty.hasKey(new NachtkrappMatchIndex.Key("ha_doji", List.of(), "1h")));
        assertFalse(empty.matches(
                new NachtkrappMatchIndex.Key("ha_doji", List.of(), "1h"), Instant.EPOCH));
    }

    @Test
    void tierBNamesExposed() {
        assertTrue(NachtkrappMatchIndex.tierBNames().contains("ha_doji"));
        assertTrue(NachtkrappMatchIndex.tierBNames().contains("price_above_pivot"));
    }

    @Test
    void duplicateKeyScannedOnce() {
        // Same primitive twice across two refs → one key, no double work.
        NachtkrappMatchIndex index = NachtkrappMatchIndex.buildFor(
                List.of(new ExpressionRef("ha_doji()", "1h"),
                        new ExpressionRef("ha_doji()", "1h")),
                Map.of(), bars, "1h", Map.of());
        assertTrue(index.hasKey(new NachtkrappMatchIndex.Key("ha_doji", List.of(), "1h")));
    }
}
