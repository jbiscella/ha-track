package org.hatrack.commons;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The computed pivot levels for one period. A component is {@code null} when
 * the {@link PivotPointVariant} does not define that level (e.g. {@code CAMARILLA}
 * has no central {@code P}; {@code WOODIE} has no R3/R4/S3/S4). Use
 * {@link #present()} to iterate only the levels a given variant actually
 * produced, in canonical order.
 */
public record PivotLevels(BigDecimal p, BigDecimal r1, BigDecimal r2, BigDecimal r3,
                          BigDecimal r4, BigDecimal s1, BigDecimal s2, BigDecimal s3,
                          BigDecimal s4) {

    /** Returns the value for {@code level}, or {@code null} if not defined. */
    public BigDecimal value(PivotLevel level) {
        return switch (level) {
            case P -> p;
            case R1 -> r1;
            case R2 -> r2;
            case R3 -> r3;
            case R4 -> r4;
            case S1 -> s1;
            case S2 -> s2;
            case S3 -> s3;
            case S4 -> s4;
        };
    }

    /** The non-null levels, keyed by {@link PivotLevel} in canonical order. */
    public Map<PivotLevel, BigDecimal> present() {
        Map<PivotLevel, BigDecimal> out = new LinkedHashMap<>();
        for (PivotLevel level : PivotLevel.values()) {
            BigDecimal v = value(level);
            if (v != null) {
                out.put(level, v);
            }
        }
        return out;
    }
}
