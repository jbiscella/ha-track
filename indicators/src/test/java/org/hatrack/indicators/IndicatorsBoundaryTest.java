package org.hatrack.indicators;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Warm-up / boundary matrix for {@link Indicators}. The Cucumber feature covers
 * reference values on comfortably-sized series; this locks the null-prefix
 * indices and the too-short-input behavior (the off-by-one class of bug that
 * would shift or NPE every downstream consumer). Pure JUnit: these are index
 * arithmetic assertions, not behavioral scenarios.
 */
class IndicatorsBoundaryTest {

    private static List<BigDecimal> series(int n) {
        List<BigDecimal> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new BigDecimal(100 + i));
        }
        return out;
    }

    private static int firstNonNull(BigDecimal[] a) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != null) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void smaPeriodOneEqualsSource() {
        List<BigDecimal> src = series(4);
        BigDecimal[] out = Indicators.sma(src, 1);
        for (int i = 0; i < src.size(); i++) {
            assertEquals(0, src.get(i).compareTo(out[i]), "sma[" + i + "]");
        }
    }

    @Test
    void smaNullBeforePeriodMinusOne() {
        BigDecimal[] out = Indicators.sma(series(5), 3);
        assertNull(out[0]);
        assertNull(out[1]);
        assertNotNull(out[2]);
        assertEquals(2, firstNonNull(out));
    }

    @Test
    void smaPeriodEqualsLengthHasOnlyLastIndex() {
        BigDecimal[] out = Indicators.sma(series(3), 3);
        assertNull(out[0]);
        assertNull(out[1]);
        assertNotNull(out[2]);
    }

    @Test
    void smaPeriodGreaterThanLengthIsAllNull() {
        BigDecimal[] out = Indicators.sma(series(3), 5);
        assertEquals(-1, firstNonNull(out), "every entry must be null when period > length");
    }

    @Test
    void emaFirstNonNullAtPeriodMinusOne() {
        BigDecimal[] out = Indicators.ema(series(6), 3);
        assertEquals(2, firstNonNull(out));
    }

    @Test
    void emaShorterThanPeriodIsAllNull() {
        assertEquals(-1, firstNonNull(Indicators.ema(series(2), 3)));
    }

    @Test
    void rsiFirstNonNullAtPeriod() {
        BigDecimal[] out = Indicators.rsi(series(8), 3);
        assertEquals(3, firstNonNull(out));
    }

    @Test
    void rsiLengthEqualToPeriodIsAllNull() {
        // n <= period yields no value (needs period + 1 bars for the first diff window)
        assertEquals(-1, firstNonNull(Indicators.rsi(series(3), 3)));
    }

    @Test
    void macdLineStartsAtSlowMinusOneAndSignalLater() {
        int fast = 2, slow = 4, signal = 2;
        MacdResult macd = Indicators.macd(series(10), fast, slow, signal);
        assertEquals(slow - 1, firstNonNull(macd.macdLine()), "macd line first index");
        // signal seeds at firstMacd + signal - 1
        assertEquals((slow - 1) + signal - 1, firstNonNull(macd.signalLine()), "signal line first index");
        // histogram present only where both are present
        assertEquals(firstNonNull(macd.signalLine()), firstNonNull(macd.histogram()));
    }

    @Test
    void atrFirstNonNullAtPeriodMinusOne() {
        List<BigDecimal> high = series(5);
        List<BigDecimal> low = series(5);
        List<BigDecimal> close = series(5);
        assertEquals(1, firstNonNull(Indicators.atr(high, low, close, 2)));
    }

    @Test
    void atrShorterThanPeriodIsAllNull() {
        assertEquals(-1, firstNonNull(Indicators.atr(series(1), series(1), series(1), 2)));
    }

    @Test
    void adxNeedsTwicePeriodPlusOneBars() {
        int period = 2;
        assertEquals(-1, firstNonNull(Indicators.adx(series(4), series(4), series(4), period)),
                "fewer than 2*period+1 bars must yield all null");
        BigDecimal[] enough = Indicators.adx(series(7), series(7), series(7), period);
        assertEquals(2 * period, firstNonNull(enough), "ADX seeds at index 2*period");
    }

    @Test
    void periodBelowOneIsRejectedAcrossCalculators() {
        List<BigDecimal> s = series(5);
        assertThrows(() -> Indicators.sma(s, 0));
        assertThrows(() -> Indicators.ema(s, 0));
        assertThrows(() -> Indicators.rsi(s, 0));
        assertThrows(() -> Indicators.atr(s, s, s, 0));
        assertThrows(() -> Indicators.adx(s, s, s, 0));
    }

    private static void assertThrows(Runnable r) {
        try {
            r.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(true);
        }
    }
}
