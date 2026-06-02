package org.hatrack.dsl;

import org.hatrack.commons.OHLCBar;
import org.hatrack.dsl.error.DslEvaluationException;
import org.hatrack.dsl.error.IndicatorWarmupException;
import org.hatrack.indicators.Indicators;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarIndicatorSourceTest {

    private final List<OHLCBar> bars = Bars.oscillating(60, Duration.ofHours(1));

    private BarIndicatorSource source() {
        return new BarIndicatorSource(bars, "strat", bars.getLast().time(), bars.size() - 1);
    }

    private static BigDecimal last(BigDecimal[] series) {
        return series[series.length - 1];
    }

    @Test
    void baseIndicatorsMatchIndicatorsModule() {
        BarIndicatorSource s = source();
        List<BigDecimal> closes = bars.stream().map(OHLCBar::close).toList();
        assertEquals(0, s.evaluate("sma", List.of(Bars.bd(10)))
                .compareTo(last(Indicators.sma(closes, 10))));
        assertEquals(0, s.evaluate("ema", List.of(Bars.bd(10)))
                .compareTo(last(Indicators.ema(closes, 10))));
        assertEquals(0, s.evaluate("rsi", List.of(Bars.bd(14)))
                .compareTo(last(Indicators.rsi(closes, 14))));
    }

    @Test
    void atrAndAtrValueAreEqual() {
        BarIndicatorSource s = source();
        BigDecimal atr = s.evaluate("atr", List.of(Bars.bd(14)));
        BigDecimal atrValue = s.evaluate("atr_value", List.of(Bars.bd(14)));
        assertEquals(0, atr.compareTo(atrValue));
    }

    @Test
    void macdComponents() {
        BarIndicatorSource s = source();
        List<BigDecimal> args = List.of(Bars.bd(12), Bars.bd(26), Bars.bd(9));
        // Just assert they resolve without error and are finite.
        s.evaluate("macd_line", args);
        s.evaluate("macd_signal", args);
        s.evaluate("macd_histogram", args);
    }

    @Test
    void macdRejectsFastNotBelowSlow() {
        BarIndicatorSource s = source();
        assertThrows(DslEvaluationException.class,
                () -> s.evaluate("macd_line", List.of(Bars.bd(26), Bars.bd(12), Bars.bd(9))));
        assertThrows(DslEvaluationException.class,
                () -> s.evaluate("macd_line", List.of(Bars.bd(12), Bars.bd(26))));
    }

    @Test
    void stddevPositive() {
        BarIndicatorSource s = source();
        assertTrue(s.evaluate("stddev", List.of(Bars.bd(20))).signum() > 0);
    }

    @Test
    void windowAggregates() {
        BarIndicatorSource s = source();
        BigDecimal hh = s.evaluate("highest_high", List.of(Bars.bd(10)));
        BigDecimal ll = s.evaluate("lowest_low", List.of(Bars.bd(10)));
        BigDecimal hc = s.evaluate("highest_close", List.of(Bars.bd(10)));
        BigDecimal lc = s.evaluate("lowest_close", List.of(Bars.bd(10)));
        assertTrue(hh.compareTo(ll) > 0);
        assertTrue(hc.compareTo(lc) >= 0);
    }

    @Test
    void avgVolume() {
        BarIndicatorSource s = source();
        assertTrue(s.evaluate("avg_volume", List.of(Bars.bd(5))).signum() > 0);
    }

    @Test
    void avgVolumeRequiresVolume() {
        OHLCBar noVol = new OHLCBar(Bars.EPOCH, Bars.bd(10), Bars.bd(11),
                Bars.bd(9), Bars.bd(10), Optional.empty());
        BarIndicatorSource s = new BarIndicatorSource(List.of(noVol), "strat",
                noVol.time(), 0);
        assertThrows(DslEvaluationException.class,
                () -> s.evaluate("avg_volume", List.of(Bars.bd(1))));
    }

    @Test
    void warmupThrownWhenHistoryTooShort() {
        List<OHLCBar> few = Bars.oscillating(5, Duration.ofHours(1));
        BarIndicatorSource s = new BarIndicatorSource(few, "strat",
                few.getLast().time(), few.size() - 1);
        assertThrows(IndicatorWarmupException.class,
                () -> s.evaluate("sma", List.of(Bars.bd(20))));
        assertThrows(IndicatorWarmupException.class,
                () -> s.evaluate("stddev", List.of(Bars.bd(20))));
        assertThrows(IndicatorWarmupException.class,
                () -> s.evaluate("highest_high", List.of(Bars.bd(20))));
    }

    @Test
    void periodValidation() {
        BarIndicatorSource s = source();
        assertThrows(DslEvaluationException.class, () -> s.evaluate("sma", List.of()));
        assertThrows(DslEvaluationException.class,
                () -> s.evaluate("sma", List.of(Bars.bd(0))));
        assertThrows(DslEvaluationException.class,
                () -> s.evaluate("sma", List.of(new BigDecimal("1.5"))));
    }

    @Test
    void unknownIndicatorThrows() {
        BarIndicatorSource s = source();
        assertThrows(DslEvaluationException.class,
                () -> s.evaluate("bogus", List.of(Bars.bd(1))));
    }

    @Test
    void tierBPrimitiveNotPreIndexedThrows() {
        BarIndicatorSource s = source();
        assertThrows(DslEvaluationException.class,
                () -> s.evaluate("ha_doji", List.of()));
    }

    @Test
    void pivotNotPreIndexedThrows() {
        BarIndicatorSource s = source();
        assertThrows(DslEvaluationException.class,
                () -> s.pivotPrimitive("price_above_pivot", "R1"));
    }

    @Test
    void tierBResolvesThroughIndex() {
        Instant t = bars.getLast().time();
        NachtkrappMatchIndex index = NachtkrappMatchIndex.buildFor(
                List.of(new ExpressionRef("ha_doji()", "1h")),
                java.util.Map.of(), bars, "1h", java.util.Map.of());
        BarIndicatorSource s = new BarIndicatorSource(bars, "strat", t, bars.size() - 1,
                index, "1h");
        BigDecimal v = s.evaluate("ha_doji", List.of());
        assertTrue(v.compareTo(BigDecimal.ZERO) == 0 || v.compareTo(BigDecimal.ONE) == 0);
    }
}
