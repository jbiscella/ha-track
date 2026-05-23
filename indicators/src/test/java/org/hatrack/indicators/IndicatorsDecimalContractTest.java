package org.hatrack.indicators;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DECIMAL64 discipline. The calculators divide (means, EMA multiplier, RSI/RS,
 * stddev). With {@code MathContext.DECIMAL64} those divisions are bounded to 16
 * significant digits and must never raise {@code ArithmeticException} on a
 * non-terminating quotient (e.g. 4/3, 2/3). This guards against a regression to
 * an unbounded {@code MathContext} (which would throw on real price data) and
 * against scale drift across the library boundary. Pure JUnit: numeric-contract
 * assertions, not behavioral scenarios.
 */
class IndicatorsDecimalContractTest {

    private static final int DECIMAL64_PRECISION = 16;

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static void assertDecimal64(BigDecimal[] values) {
        for (BigDecimal v : values) {
            if (v != null) {
                assertTrue(v.precision() <= DECIMAL64_PRECISION,
                        "value " + v + " exceeds DECIMAL64 precision (" + v.precision() + ")");
            }
        }
    }

    @Test
    void smaOfNonDivisibleSumDoesNotThrowAndStaysDecimal64() {
        // (1 + 1 + 2) / 3 = 1.333... — non-terminating, must round under DECIMAL64.
        BigDecimal[] out = assertDoesNotThrow(
                () -> Indicators.sma(List.of(bd("1"), bd("1"), bd("2")), 3));
        assertTrue(out[2].subtract(bd("1.3333333333")).abs().compareTo(bd("1e-9")) < 0,
                "mean must approximate 4/3, was " + out[2]);
        assertDecimal64(out);
    }

    @Test
    void emaMultiplierNonTerminatingDoesNotThrow() {
        // k = 2/(period+1) = 2/3 for period 2 — non-terminating.
        BigDecimal[] out = assertDoesNotThrow(
                () -> Indicators.ema(List.of(bd("1"), bd("2"), bd("3"), bd("5"), bd("8")), 2));
        assertDecimal64(out);
    }

    @Test
    void rsiNeverThrowsAndStaysWithinZeroToHundred() {
        List<BigDecimal> src = List.of(bd("10"), bd("11"), bd("9"), bd("12"), bd("8"),
                bd("13"), bd("7"), bd("14"));
        BigDecimal[] rsi = assertDoesNotThrow(() -> Indicators.rsi(src, 3));
        for (BigDecimal v : rsi) {
            if (v != null) {
                assertTrue(v.signum() >= 0 && v.compareTo(bd("100")) <= 0,
                        "RSI out of [0,100]: " + v);
            }
        }
        assertDecimal64(rsi);
    }

    @Test
    void bollingerStdDevNonTerminatingDoesNotThrow() {
        BigDecimal[] mid = assertDoesNotThrow(
                () -> Indicators.bollinger(List.of(bd("1"), bd("2"), bd("4"), bd("7"), bd("11")),
                        3, bd("2"))).middle();
        assertDecimal64(mid);
    }

    @Test
    void atrWilderSmoothingNonTerminatingDoesNotThrow() {
        List<BigDecimal> high = List.of(bd("10"), bd("12"), bd("11"), bd("13"), bd("12"));
        List<BigDecimal> low = List.of(bd("9"), bd("10"), bd("9"), bd("11"), bd("10"));
        List<BigDecimal> close = List.of(bd("9.5"), bd("11"), bd("10"), bd("12"), bd("11"));
        BigDecimal[] atr = assertDoesNotThrow(() -> Indicators.atr(high, low, close, 3));
        for (BigDecimal v : atr) {
            if (v != null) {
                assertTrue(v.signum() >= 0, "ATR must be non-negative, was " + v);
            }
        }
        assertDecimal64(atr);
    }
}
