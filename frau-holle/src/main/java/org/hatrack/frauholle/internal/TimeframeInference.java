package org.hatrack.frauholle.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Infers {@code periodsPerYear} from bar spacing (frau-holle/CLAUDE.md
 * section 3.1). It uses the MOST COMMON consecutive gap (the modal Δt) so that
 * the rare larger gaps every real feed has — overnight, weekend, holiday,
 * Easter, the odd multi-day closure — are ignored. The modal gap is matched
 * against the known-timeframe table (so daily stays the 252 trading-day
 * convention); an unrecognized cadence degrades to a calendar estimate with a
 * console warning rather than failing. Only genuinely broken input — fewer than
 * two bars, an out-of-order gap (Δt &lt; 0) or a duplicate timestamp (Δt = 0) —
 * returns empty, which the builder surfaces as V5.
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
        Map<Long, Integer> counts = new HashMap<>();
        for (int i = 1; i < times.size(); i++) {
            long delta = Duration.between(times.get(i - 1), times.get(i)).getSeconds();
            if (delta <= 0) {
                // out-of-order or duplicate timestamp: genuine corruption, not a rhythm question
                return Optional.empty();
            }
            counts.merge(delta, 1, Integer::sum);
        }
        long modal = modalGap(counts);
        for (Map.Entry<Long, BigDecimal> entry : KNOWN.entrySet()) {
            if (Math.abs(modal - entry.getKey()) <= entry.getKey() * 0.01) {
                return Optional.of(entry.getValue());
            }
        }
        System.err.println("[frau-holle] unrecognized bar cadence (" + modal
                + "s); using a calendar-based periodsPerYear estimate");
        return Optional.of(SECONDS_PER_YEAR.divide(new BigDecimal(modal), MC));
    }

    /** The most-frequent gap; ties broken deterministically by the smaller gap. */
    private static long modalGap(Map<Long, Integer> counts) {
        long modal = Long.MAX_VALUE;
        int best = -1;
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            int count = entry.getValue();
            long gap = entry.getKey();
            if (count > best || (count == best && gap < modal)) {
                best = count;
                modal = gap;
            }
        }
        return modal;
    }
}
