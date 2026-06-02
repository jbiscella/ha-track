package org.hatrack.dsl;

import org.hatrack.commons.OHLCBar;
import org.hatrack.indicators.Indicators;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parity port of wichtelm-app's {@code AtrDynamicStopEvaluationTest} (value
 * test). {@code atr_value(n)} at a bar must equal the {@code indicators}-module
 * ATR(n) there. The freeze-at-fill behaviour the original also covered lives in
 * the wichtelm signal layer (it positions the source at the entry bar) and is
 * NOT part of the promoted, stateless evaluator — so only the per-bar value
 * equivalence is asserted here.
 */
class AtrValueParityTest {

    @Test
    void atrValueResolvesToTheIndicatorsAtrAtTheSourceBar() {
        List<OHLCBar> bars = new ArrayList<>();
        List<BigDecimal> highs = new ArrayList<>();
        List<BigDecimal> lows = new ArrayList<>();
        List<BigDecimal> closes = new ArrayList<>();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < 30; i++) {
            BigDecimal close = BigDecimal.valueOf(100 + i);
            BigDecimal high = close.add(BigDecimal.valueOf((i % 5) + 1));
            BigDecimal low = close.subtract(BigDecimal.valueOf((i % 3) + 1));
            bars.add(new OHLCBar(start.plus(Duration.ofHours(i)), close, high, low, close,
                    Optional.empty()));
            highs.add(high);
            lows.add(low);
            closes.add(close);
        }
        long lastIndex = bars.size() - 1;
        BarIndicatorSource source = new BarIndicatorSource(
                bars, "atr-eval", bars.get((int) lastIndex).time(), lastIndex);

        BigDecimal got = source.evaluate("atr_value", List.of(BigDecimal.valueOf(14)));

        BigDecimal[] atr = Indicators.atr(highs, lows, closes, 14);
        BigDecimal expected = atr[atr.length - 1];
        assertEquals(0, got.compareTo(expected),
                "atr_value(14) must equal ATR(14) at the bar; got " + got + " expected " + expected);
    }
}
