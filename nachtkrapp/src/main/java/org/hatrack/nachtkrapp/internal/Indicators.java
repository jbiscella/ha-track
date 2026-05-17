package org.hatrack.nachtkrapp.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * Indicator calculators using the canonical formulas in nachtkrapp/CLAUDE.md
 * section 13. Each method returns an array indexed by bar; entries are
 * {@code null} for bars before the indicator's warm-up window.
 */
public final class Indicators {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Indicators() {
    }

    /** Simple moving average; null before index {@code period - 1}. */
    public static BigDecimal[] sma(List<BigDecimal> src, int period) {
        int n = src.size();
        BigDecimal[] out = new BigDecimal[n];
        BigDecimal divisor = new BigDecimal(period);
        for (int i = period - 1; i < n; i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = i - period + 1; j <= i; j++) {
                sum = sum.add(src.get(j), MC);
            }
            out[i] = sum.divide(divisor, MC);
        }
        return out;
    }

    /** Exponential moving average; seed = SMA of the first {@code period} values. */
    public static BigDecimal[] ema(List<BigDecimal> src, int period) {
        int n = src.size();
        BigDecimal[] out = new BigDecimal[n];
        if (n < period) {
            return out;
        }
        BigDecimal k = new BigDecimal(2).divide(new BigDecimal(period + 1), MC);
        BigDecimal oneMinusK = BigDecimal.ONE.subtract(k, MC);
        BigDecimal sum = BigDecimal.ZERO;
        for (int j = 0; j < period; j++) {
            sum = sum.add(src.get(j), MC);
        }
        BigDecimal prev = sum.divide(new BigDecimal(period), MC);
        out[period - 1] = prev;
        for (int i = period; i < n; i++) {
            prev = src.get(i).multiply(k, MC).add(prev.multiply(oneMinusK, MC), MC);
            out[i] = prev;
        }
        return out;
    }

    /** Wilder-smoothed RSI; null before index {@code period}. */
    public static BigDecimal[] rsi(List<BigDecimal> src, int period) {
        int n = src.size();
        BigDecimal[] out = new BigDecimal[n];
        if (n <= period) {
            return out;
        }
        BigDecimal p = new BigDecimal(period);
        BigDecimal pMinusOne = p.subtract(BigDecimal.ONE);
        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            BigDecimal d = src.get(i).subtract(src.get(i - 1), MC);
            if (d.signum() > 0) {
                gainSum = gainSum.add(d, MC);
            } else if (d.signum() < 0) {
                lossSum = lossSum.add(d.negate(), MC);
            }
        }
        BigDecimal avgGain = gainSum.divide(p, MC);
        BigDecimal avgLoss = lossSum.divide(p, MC);
        out[period] = rsiValue(avgGain, avgLoss);
        for (int i = period + 1; i < n; i++) {
            BigDecimal d = src.get(i).subtract(src.get(i - 1), MC);
            BigDecimal gain = d.signum() > 0 ? d : BigDecimal.ZERO;
            BigDecimal loss = d.signum() < 0 ? d.negate() : BigDecimal.ZERO;
            avgGain = avgGain.multiply(pMinusOne, MC).add(gain, MC).divide(p, MC);
            avgLoss = avgLoss.multiply(pMinusOne, MC).add(loss, MC).divide(p, MC);
            out[i] = rsiValue(avgGain, avgLoss);
        }
        return out;
    }

    private static BigDecimal rsiValue(BigDecimal avgGain, BigDecimal avgLoss) {
        if (avgLoss.signum() == 0) {
            return HUNDRED;
        }
        BigDecimal rs = avgGain.divide(avgLoss, MC);
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs, MC), MC), MC);
    }

    public record Macd(BigDecimal[] macdLine, BigDecimal[] signalLine, BigDecimal[] histogram) {
    }

    /** MACD line, signal line and histogram per the canonical formula. */
    public static Macd macd(List<BigDecimal> src, int fast, int slow, int signal) {
        int n = src.size();
        BigDecimal[] emaFast = ema(src, fast);
        BigDecimal[] emaSlow = ema(src, slow);
        BigDecimal[] macdLine = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            if (emaFast[i] != null && emaSlow[i] != null) {
                macdLine[i] = emaFast[i].subtract(emaSlow[i], MC);
            }
        }
        BigDecimal[] signalLine = new BigDecimal[n];
        int firstMacd = -1;
        for (int i = 0; i < n; i++) {
            if (macdLine[i] != null) {
                firstMacd = i;
                break;
            }
        }
        if (firstMacd >= 0 && n - firstMacd >= signal) {
            BigDecimal k = new BigDecimal(2).divide(new BigDecimal(signal + 1), MC);
            BigDecimal oneMinusK = BigDecimal.ONE.subtract(k, MC);
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = 0; j < signal; j++) {
                sum = sum.add(macdLine[firstMacd + j], MC);
            }
            BigDecimal prev = sum.divide(new BigDecimal(signal), MC);
            signalLine[firstMacd + signal - 1] = prev;
            for (int i = firstMacd + signal; i < n; i++) {
                prev = macdLine[i].multiply(k, MC).add(prev.multiply(oneMinusK, MC), MC);
                signalLine[i] = prev;
            }
        }
        BigDecimal[] histogram = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            if (macdLine[i] != null && signalLine[i] != null) {
                histogram[i] = macdLine[i].subtract(signalLine[i], MC);
            }
        }
        return new Macd(macdLine, signalLine, histogram);
    }
}
