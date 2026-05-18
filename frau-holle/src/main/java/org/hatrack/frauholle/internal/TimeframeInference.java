package org.hatrack.frauholle.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Infers {@code periodsPerYear} from bar spacing (frau-holle/CLAUDE.md
 * section 3.1). Returns empty when the series is unordered, has duplicate
 * times or is not uniformly spaced — the V5 condition.
 */
public final class TimeframeInference {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal SECONDS_PER_YEAR = new BigDecimal("31536000");

    /** Bar-spacing (seconds) to periodsPerYear, per the conventional table. */
    private static final Map<Long, BigDecimal> KNOWN = new LinkedHashMap<>();

    static {
        KNOWN.put(60L, new BigDecimal("525600"));
        KNOWN.put(300L, new BigDecimal("105120"));
        KNOWN.put(900L, new BigDecimal("35040"));
        KNOWN.put(1800L, new BigDecimal("17520"));
        KNOWN.put(3600L, new BigDecimal("8760"));
        KNOWN.put(14400L, new BigDecimal("2190"));
        KNOWN.put(86400L, new BigDecimal("252"));
        KNOWN.put(604800L, new BigDecimal("52"));
        KNOWN.put(2592000L, new BigDecimal("12"));
        KNOWN.put(31536000L, BigDecimal.ONE);
    }

    private TimeframeInference() {
    }

    public static Optional<BigDecimal> periodsPerYear(List<Instant> times) {
        if (times.size() < 2) {
            return Optional.empty();
        }
        long[] deltas = new long[times.size() - 1];
        for (int i = 1; i < times.size(); i++) {
            long delta = Duration.between(times.get(i - 1), times.get(i)).getSeconds();
            if (delta <= 0) {
                return Optional.empty();
            }
            deltas[i - 1] = delta;
        }
        long[] sorted = deltas.clone();
        Arrays.sort(sorted);
        long median = sorted[sorted.length / 2];
        double tolerance = median * 0.01;
        for (long delta : deltas) {
            if (Math.abs(delta - median) > tolerance) {
                return Optional.empty();
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : KNOWN.entrySet()) {
            if (Math.abs(median - entry.getKey()) <= entry.getKey() * 0.01) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.of(SECONDS_PER_YEAR.divide(new BigDecimal(median), MC));
    }
}
