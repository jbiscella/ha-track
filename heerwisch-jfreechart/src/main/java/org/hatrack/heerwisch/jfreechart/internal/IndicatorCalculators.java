package org.hatrack.heerwisch.jfreechart.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * Indicator calculators for the JFreeChart driver, using the canonical
 * formulas (heerwisch-jfreechart/CLAUDE.md section 7; duplicated per the
 * repo-wide v1 acknowledgement of indicator duplication). All arithmetic is
 * {@code BigDecimal} with {@code MathContext.DECIMAL64}; results are converted
 * to {@code double} only at the JFreeChart dataset boundary.
 *
 * <p>Each method returns an array indexed by bar; entries are {@code null}
 * before the indicator's warm-up window or when data is insufficient.
 */
public final class IndicatorCalculators {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private IndicatorCalculators() {
    }

    public static BigDecimal[] sma(List<BigDecimal> src, int period) {
        int n = src.size();
        BigDecimal[] out = new BigDecimal[n];
        BigDecimal divisor = new BigDecimal(period);
        for (int i = period - 1; i >= 0 && i < n; i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = i - period + 1; j <= i; j++) {
                sum = sum.add(src.get(j), MC);
            }
            out[i] = sum.divide(divisor, MC);
        }
        return out;
    }

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

    /** Returns {macdLine, signalLine}. */
    public static BigDecimal[][] macd(List<BigDecimal> src, int fast, int slow, int signal) {
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
        int firstMacd = firstNonNull(macdLine);
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
        return new BigDecimal[][] {macdLine, signalLine};
    }

    /** Returns {upperBand, middleBand, lowerBand}. */
    public static BigDecimal[][] bollinger(List<BigDecimal> src, int period, BigDecimal multiplier) {
        int n = src.size();
        BigDecimal[] middle = sma(src, period);
        BigDecimal[] upper = new BigDecimal[n];
        BigDecimal[] lower = new BigDecimal[n];
        BigDecimal divisor = new BigDecimal(period);
        for (int i = period - 1; i >= 0 && i < n; i++) {
            BigDecimal mean = middle[i];
            BigDecimal sumSq = BigDecimal.ZERO;
            for (int j = i - period + 1; j <= i; j++) {
                BigDecimal dev = src.get(j).subtract(mean, MC);
                sumSq = sumSq.add(dev.multiply(dev, MC), MC);
            }
            BigDecimal stdDev = sumSq.divide(divisor, MC).sqrt(MC);
            BigDecimal offset = stdDev.multiply(multiplier, MC);
            upper[i] = mean.add(offset, MC);
            lower[i] = mean.subtract(offset, MC);
        }
        return new BigDecimal[][] {upper, middle, lower};
    }

    public static BigDecimal[] atr(List<BigDecimal> high, List<BigDecimal> low,
                                   List<BigDecimal> close, int period) {
        int n = high.size();
        BigDecimal[] out = new BigDecimal[n];
        if (n < period) {
            return out;
        }
        BigDecimal[] tr = trueRange(high, low, close);
        BigDecimal p = new BigDecimal(period);
        BigDecimal pMinusOne = p.subtract(BigDecimal.ONE);
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(tr[i], MC);
        }
        BigDecimal prev = sum.divide(p, MC);
        out[period - 1] = prev;
        for (int i = period; i < n; i++) {
            prev = prev.multiply(pMinusOne, MC).add(tr[i], MC).divide(p, MC);
            out[i] = prev;
        }
        return out;
    }

    /** Returns {percentK, percentD}. */
    public static BigDecimal[][] stochastic(List<BigDecimal> high, List<BigDecimal> low,
                                            List<BigDecimal> close, int kPeriod, int dPeriod,
                                            int smoothing) {
        int n = high.size();
        BigDecimal[] rawK = new BigDecimal[n];
        for (int i = kPeriod - 1; i >= 0 && i < n; i++) {
            BigDecimal hi = high.get(i);
            BigDecimal lo = low.get(i);
            for (int j = i - kPeriod + 1; j <= i; j++) {
                hi = hi.max(high.get(j));
                lo = lo.min(low.get(j));
            }
            BigDecimal range = hi.subtract(lo, MC);
            rawK[i] = range.signum() == 0
                    ? new BigDecimal("50")
                    : close.get(i).subtract(lo, MC).multiply(HUNDRED, MC).divide(range, MC);
        }
        BigDecimal[] percentK = smaOfArray(rawK, smoothing);
        BigDecimal[] percentD = smaOfArray(percentK, dPeriod);
        return new BigDecimal[][] {percentK, percentD};
    }

    public static BigDecimal[] adx(List<BigDecimal> high, List<BigDecimal> low,
                                   List<BigDecimal> close, int period) {
        int n = high.size();
        BigDecimal[] out = new BigDecimal[n];
        if (n < 2 * period + 1) {
            return out;
        }
        BigDecimal[] tr = trueRange(high, low, close);
        BigDecimal[] plusDm = new BigDecimal[n];
        BigDecimal[] minusDm = new BigDecimal[n];
        plusDm[0] = BigDecimal.ZERO;
        minusDm[0] = BigDecimal.ZERO;
        for (int i = 1; i < n; i++) {
            BigDecimal up = high.get(i).subtract(high.get(i - 1), MC);
            BigDecimal down = low.get(i - 1).subtract(low.get(i), MC);
            plusDm[i] = (up.signum() > 0 && up.compareTo(down) > 0) ? up : BigDecimal.ZERO;
            minusDm[i] = (down.signum() > 0 && down.compareTo(up) > 0) ? down : BigDecimal.ZERO;
        }
        BigDecimal p = new BigDecimal(period);
        BigDecimal pMinusOne = p.subtract(BigDecimal.ONE);
        BigDecimal smTr = sumRange(tr, 1, period);
        BigDecimal smPlus = sumRange(plusDm, 1, period);
        BigDecimal smMinus = sumRange(minusDm, 1, period);
        BigDecimal[] dx = new BigDecimal[n];
        for (int i = period; i < n; i++) {
            if (i > period) {
                smTr = smTr.subtract(smTr.divide(p, MC), MC).add(tr[i], MC);
                smPlus = smPlus.subtract(smPlus.divide(p, MC), MC).add(plusDm[i], MC);
                smMinus = smMinus.subtract(smMinus.divide(p, MC), MC).add(minusDm[i], MC);
            }
            BigDecimal plusDi = smTr.signum() == 0 ? BigDecimal.ZERO
                    : smPlus.multiply(HUNDRED, MC).divide(smTr, MC);
            BigDecimal minusDi = smTr.signum() == 0 ? BigDecimal.ZERO
                    : smMinus.multiply(HUNDRED, MC).divide(smTr, MC);
            BigDecimal diSum = plusDi.add(minusDi, MC);
            dx[i] = diSum.signum() == 0 ? BigDecimal.ZERO
                    : plusDi.subtract(minusDi, MC).abs().multiply(HUNDRED, MC).divide(diSum, MC);
        }
        BigDecimal adxSeed = BigDecimal.ZERO;
        for (int i = period + 1; i <= 2 * period; i++) {
            adxSeed = adxSeed.add(dx[i], MC);
        }
        BigDecimal prev = adxSeed.divide(p, MC);
        out[2 * period] = prev;
        for (int i = 2 * period + 1; i < n; i++) {
            prev = prev.multiply(pMinusOne, MC).add(dx[i], MC).divide(p, MC);
            out[i] = prev;
        }
        return out;
    }

    private static BigDecimal[] trueRange(List<BigDecimal> high, List<BigDecimal> low,
                                          List<BigDecimal> close) {
        int n = high.size();
        BigDecimal[] tr = new BigDecimal[n];
        tr[0] = high.get(0).subtract(low.get(0), MC);
        for (int i = 1; i < n; i++) {
            BigDecimal hl = high.get(i).subtract(low.get(i), MC);
            BigDecimal hc = high.get(i).subtract(close.get(i - 1), MC).abs();
            BigDecimal lc = low.get(i).subtract(close.get(i - 1), MC).abs();
            tr[i] = hl.max(hc).max(lc);
        }
        return tr;
    }

    private static BigDecimal[] smaOfArray(BigDecimal[] src, int period) {
        int n = src.length;
        BigDecimal[] out = new BigDecimal[n];
        BigDecimal divisor = new BigDecimal(period);
        for (int i = 0; i < n; i++) {
            if (i < period - 1 || src[i] == null) {
                continue;
            }
            BigDecimal sum = BigDecimal.ZERO;
            boolean complete = true;
            for (int j = i - period + 1; j <= i; j++) {
                if (src[j] == null) {
                    complete = false;
                    break;
                }
                sum = sum.add(src[j], MC);
            }
            if (complete) {
                out[i] = sum.divide(divisor, MC);
            }
        }
        return out;
    }

    private static BigDecimal sumRange(BigDecimal[] values, int from, int toInclusive) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = from; i <= toInclusive; i++) {
            sum = sum.add(values[i], MC);
        }
        return sum;
    }

    private static int firstNonNull(BigDecimal[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                return i;
            }
        }
        return -1;
    }
}
