package org.hatrack.frauholle.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug 2: rhythm inference must use the MOST COMMON gap and tolerate the rare
 * large gaps that every real feed has (overnight / weekend / holiday / Easter).
 * It must NOT demand near-uniform spacing, and must keep the documented
 * daily = 252 convention. Unrecognized cadences degrade to a calendar estimate
 * rather than failing; only genuinely broken input (too short, out-of-order,
 * duplicate) yields empty (→ V5).
 */
class TimeframeInferenceTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z"); // a Monday

    private static List<Instant> fromHourOffsets(long... hours) {
        List<Instant> out = new ArrayList<>();
        for (long h : hours) {
            out.add(BASE.plusSeconds(h * 3600L));
        }
        return out;
    }

    private static List<Instant> fromDayOffsets(long... days) {
        List<Instant> out = new ArrayList<>();
        for (long d : days) {
            out.add(BASE.plusSeconds(d * 86400L));
        }
        return out;
    }

    private static BigDecimal infer(List<Instant> times) {
        return TimeframeInference.periodsPerYear(times)
                .orElseThrow(() -> new AssertionError("expected a value but got empty"));
    }

    // --- recognized cadences via the table ---

    @Test
    void uniformDailyIsTradingDays252NotCalendar() {
        List<Instant> t = fromDayOffsets(0, 1, 2, 3, 4, 5);
        assertEquals(0, new BigDecimal("252").compareTo(infer(t)), "daily must be 252, not 365.25");
    }

    @Test
    void uniformHourly() {
        List<Instant> t = fromHourOffsets(0, 1, 2, 3, 4, 5, 6);
        assertEquals(0, new BigDecimal("8760").compareTo(infer(t)));
    }

    @Test
    void uniformWeekly() {
        List<Instant> t = fromDayOffsets(0, 7, 14, 21);
        assertEquals(0, new BigDecimal("52").compareTo(infer(t)));
    }

    // --- the real-world cases that fail today ---

    @Test
    void realDailyWithWeekendGapsStill252() {
        // Mon..Fri, weekend, Mon..Fri  → gaps mostly 1 day, two 3-day weekends
        List<Instant> t = fromDayOffsets(0, 1, 2, 3, 4, 7, 8, 9, 10, 11);
        assertEquals(0, new BigDecimal("252").compareTo(infer(t)),
                "weekend gaps must not break daily inference");
    }

    @Test
    void marketHoursHourlyWithOvernightGapsIsHourly() {
        // 3 days x 7 hourly bars (14:00..20:00), ~18h overnight gaps between days
        List<Instant> t = fromHourOffsets(
                14, 15, 16, 17, 18, 19, 20,
                38, 39, 40, 41, 42, 43, 44,
                62, 63, 64, 65, 66, 67, 68);
        assertEquals(0, new BigDecimal("8760").compareTo(infer(t)),
                "overnight gaps must not break hourly inference");
    }

    @Test
    void dailyWithHolidayAndEasterStill252() {
        // 1-day gaps dominate; one 2-day bank holiday gap and one 5-day Easter gap
        List<Instant> t = fromDayOffsets(0, 1, 2, 3, 6, 7, 8, 9, 14, 15, 16, 17);
        assertEquals(0, new BigDecimal("252").compareTo(infer(t)));
    }

    @Test
    void oneLargeClosureIsIgnored() {
        // hourly with a single 72h closure in the middle
        List<Instant> t = fromHourOffsets(0, 1, 2, 3, 75, 76, 77, 78);
        assertEquals(0, new BigDecimal("8760").compareTo(infer(t)));
    }

    // --- unrecognized cadence degrades, does not fail ---

    @Test
    void unrecognizedCadenceFallsBackToCalendarEstimate() {
        // uniform 2-hour bars (7200s) — not in the table
        List<Instant> t = fromHourOffsets(0, 2, 4, 6, 8);
        // 31_536_000 / 7200 = 4380
        assertEquals(0, new BigDecimal("4380").compareTo(infer(t)),
                "unknown cadence must degrade to a calendar estimate, not fail");
    }

    // --- genuinely broken input → empty (V5) ---

    @Test
    void fewerThanTwoBarsIsEmpty() {
        assertTrue(TimeframeInference.periodsPerYear(fromDayOffsets(0)).isEmpty());
    }

    @Test
    void outOfOrderIsEmpty() {
        List<Instant> t = new ArrayList<>(fromDayOffsets(0, 1, 2));
        t.add(BASE.minusSeconds(86400L)); // a bar earlier than the first
        assertTrue(TimeframeInference.periodsPerYear(t).isEmpty(),
                "a descending gap is genuine corruption → empty");
    }

    @Test
    void duplicateTimestampIsEmpty() {
        List<Instant> t = new ArrayList<>(fromDayOffsets(0, 1, 1, 2)); // gap 0 between bars 1 and 2
        assertTrue(TimeframeInference.periodsPerYear(t).isEmpty(),
                "a zero gap (duplicate) at the backtest boundary → empty");
    }
}
