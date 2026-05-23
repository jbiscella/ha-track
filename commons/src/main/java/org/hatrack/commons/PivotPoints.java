package org.hatrack.commons;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/**
 * Pure pivot-point computation. Given a previous-period {@link OHLCBar} (high
 * {@code H}, low {@code L}, close {@code C}, range {@code H − L}) it returns the
 * canonical {@link PivotLevels} for a {@link PivotPointVariant}. No I/O, no
 * state, no clock. Arithmetic uses {@link MathContext#DECIMAL64}.
 *
 * <p>Per-variant levels:
 * <ul>
 *   <li>{@code STANDARD}: P, R1, R2, R3, S1, S2, S3 (no R4/S4)</li>
 *   <li>{@code WOODIE}: P, R1, R2, S1, S2 (no R3/R4/S3/S4)</li>
 *   <li>{@code CAMARILLA}: R1, R2, R3, R4, S1, S2, S3, S4 (no central P)</li>
 * </ul>
 */
public final class PivotPoints {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal THREE = new BigDecimal("3");
    private static final BigDecimal FOUR = new BigDecimal("4");
    private static final BigDecimal CAMARILLA_FACTOR = new BigDecimal("1.1");

    private PivotPoints() {
    }

    public static PivotLevels levels(OHLCBar previousPeriodBar, PivotPointVariant variant) {
        Objects.requireNonNull(previousPeriodBar, "previousPeriodBar");
        Objects.requireNonNull(variant, "variant");
        BigDecimal h = previousPeriodBar.high();
        BigDecimal l = previousPeriodBar.low();
        BigDecimal c = previousPeriodBar.close();
        BigDecimal range = h.subtract(l, MC);
        return switch (variant) {
            case STANDARD -> {
                BigDecimal p = h.add(l, MC).add(c, MC).divide(THREE, MC);
                BigDecimal r1 = p.multiply(TWO, MC).subtract(l, MC);
                BigDecimal s1 = p.multiply(TWO, MC).subtract(h, MC);
                BigDecimal r2 = p.add(range, MC);
                BigDecimal s2 = p.subtract(range, MC);
                BigDecimal r3 = h.add(TWO.multiply(p.subtract(l, MC), MC), MC);
                BigDecimal s3 = l.subtract(TWO.multiply(h.subtract(p, MC), MC), MC);
                yield new PivotLevels(p, r1, r2, r3, null, s1, s2, s3, null);
            }
            case WOODIE -> {
                BigDecimal p = h.add(l, MC).add(c.multiply(TWO, MC), MC).divide(FOUR, MC);
                BigDecimal r1 = p.multiply(TWO, MC).subtract(l, MC);
                BigDecimal s1 = p.multiply(TWO, MC).subtract(h, MC);
                BigDecimal r2 = p.add(range, MC);
                BigDecimal s2 = p.subtract(range, MC);
                yield new PivotLevels(p, r1, r2, null, null, s1, s2, null, null);
            }
            case CAMARILLA -> {
                BigDecimal base = range.multiply(CAMARILLA_FACTOR, MC);
                BigDecimal r1 = c.add(divide(base, 12), MC);
                BigDecimal r2 = c.add(divide(base, 6), MC);
                BigDecimal r3 = c.add(divide(base, 4), MC);
                BigDecimal r4 = c.add(divide(base, 2), MC);
                BigDecimal s1 = c.subtract(divide(base, 12), MC);
                BigDecimal s2 = c.subtract(divide(base, 6), MC);
                BigDecimal s3 = c.subtract(divide(base, 4), MC);
                BigDecimal s4 = c.subtract(divide(base, 2), MC);
                yield new PivotLevels(null, r1, r2, r3, r4, s1, s2, s3, s4);
            }
        };
    }

    private static BigDecimal divide(BigDecimal value, int divisor) {
        return value.divide(new BigDecimal(divisor), MC);
    }
}
