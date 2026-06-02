package org.hatrack.dsl;

import org.hatrack.commons.OHLCBar;
import org.hatrack.dsl.NachtkrappMatchIndex.Key;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity port of wichtelm-app's {@code TierBSteps} prepass-key assertions
 * ({@code prepassIndexedArg} / {@code prepassIndexedArgs}). Each primitive call
 * that appears in an expression must be registered as a lookup {@link Key} by
 * the prepass, with its numeric (or symbolic pivot) arguments — so the per-bar
 * evaluator can resolve it. The original drove the wichtelm parser + backtest
 * runner; here the calls are handed straight to the {@link ExpressionRef} seam.
 */
class PrepassKeyIndexingTest {

    private static final String TF = "1h";

    // 240 hourly bars span several UTC days, so the daily pivot rule has prior days.
    private final List<OHLCBar> bars = Bars.oscillating(240, Duration.ofHours(1));

    private NachtkrappMatchIndex indexFor(String expression) {
        return NachtkrappMatchIndex.buildFor(
                List.of(new ExpressionRef(expression, TF)), Map.of(), bars, TF, Map.of());
    }

    private static Key key(String name, long... args) {
        List<BigDecimal> a = new java.util.ArrayList<>();
        for (long v : args) {
            a.add(BigDecimal.valueOf(v));
        }
        return new Key(name, a, TF);
    }

    @Test
    void priceVsMaPrimitiveIsIndexedWithItsPeriod() {
        assertTrue(indexFor("price_above_ema(20)").hasKey(key("price_above_ema", 20)));
    }

    @Test
    void maVsMaPrimitiveIsIndexedWithBothPeriods() {
        assertTrue(indexFor("sma_above_ema(5, 20)").hasKey(key("sma_above_ema", 5, 20)));
    }

    @Test
    void haPrimitivesAreIndexed() {
        assertTrue(indexFor("ha_doji()").hasKey(key("ha_doji")));
        assertTrue(indexFor("ha_bullish_reversal(2)").hasKey(key("ha_bullish_reversal", 2)));
    }

    @Test
    void rsiAndMacdPrimitivesAreIndexed() {
        assertTrue(indexFor("rsi_overbought(70)").hasKey(key("rsi_overbought", 70)));
        assertTrue(indexFor("macd_bullish_cross()").hasKey(key("macd_bullish_cross")));
    }

    @Test
    void pivotPrimitiveIsIndexedWithItsSymbolicLevel() {
        assertTrue(indexFor("price_above_pivot(R1)")
                .hasKey(Key.pivot("price_above_pivot", "R1", TF)));
    }
}
