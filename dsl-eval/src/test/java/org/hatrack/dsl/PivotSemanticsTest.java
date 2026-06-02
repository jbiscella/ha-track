package org.hatrack.dsl;

import org.hatrack.commons.OHLCBar;
import org.hatrack.dsl.NachtkrappMatchIndex.Key;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity port of wichtelm-app's {@code PivotEvaluationTest}. Proves the per-bar
 * SEMANTICS of the pivot primitives against a series whose STANDARD daily pivot
 * is known by construction — catching mutations a wiring-only test would miss
 * (R1/S1 confusion, a dropped level filter, above/below swap, cross-vs-state).
 *
 * <p>Day 1 aggregates to High=110, Low=90, Close=100 → P=100, R1=2P−Low=110,
 * S1=2P−High=90. Day 2's bars sit in three close bands (above R1 / between /
 * below S1) so each primitive's truth at a mid-band bar is known exactly.
 */
class PivotSemanticsTest {

    private static final Instant DAY1 = Instant.parse("2024-01-01T00:00:00Z");
    private static final String TF = "1h";

    private static List<OHLCBar> twoDaySeries() {
        List<OHLCBar> bars = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            BigDecimal close = BigDecimal.valueOf(100);
            BigDecimal high = i == 0 ? BigDecimal.valueOf(110) : BigDecimal.valueOf(101);
            BigDecimal low = i == 1 ? BigDecimal.valueOf(90) : BigDecimal.valueOf(99);
            bars.add(new OHLCBar(DAY1.plus(Duration.ofHours(i)), close, high, low, close,
                    Optional.empty()));
        }
        for (int i = 0; i < 24; i++) {
            int close = i < 8 ? 120 : i < 16 ? 100 : 80;
            BigDecimal c = BigDecimal.valueOf(close);
            bars.add(new OHLCBar(DAY1.plus(Duration.ofHours(24 + i)),
                    c, c.add(BigDecimal.ONE), c.subtract(BigDecimal.ONE), c, Optional.empty()));
        }
        return bars;
    }

    private static NachtkrappMatchIndex indexFor(String semicolonSteps, List<OHLCBar> bars) {
        List<ExpressionRef> refs = new ArrayList<>();
        for (String step : semicolonSteps.split(";")) {
            refs.add(new ExpressionRef(step.strip(), TF));
        }
        return NachtkrappMatchIndex.buildFor(refs, Map.of(), bars, TF, Map.of());
    }

    private static Key pivot(String name, String level) {
        return Key.pivot(name, level, TF);
    }

    @Test
    void aboveAndBelowAreDistinguishedAndLevelAware() {
        List<OHLCBar> bars = twoDaySeries();
        NachtkrappMatchIndex idx = indexFor(
                "price_above_pivot(R1);price_above_pivot(S1)"
                        + ";price_below_pivot(R1);price_below_pivot(S1)", bars);
        Instant aboveR1 = bars.get(28).time();      // 120-band
        Instant betweenS1R1 = bars.get(35).time();  // 100-band
        Instant belowS1 = bars.get(44).time();      // 80-band

        assertTrue(idx.matches(pivot("price_above_pivot", "R1"), aboveR1), "120 > R1(110)");
        assertTrue(idx.matches(pivot("price_above_pivot", "S1"), aboveR1), "120 > S1(90)");
        assertFalse(idx.matches(pivot("price_below_pivot", "R1"), aboveR1), "120 not < R1");

        assertTrue(idx.matches(pivot("price_above_pivot", "S1"), betweenS1R1), "100 > S1(90)");
        assertFalse(idx.matches(pivot("price_above_pivot", "R1"), betweenS1R1),
                "100 is NOT above R1(110) — catches R1/S1 confusion or a dropped level filter");
        assertTrue(idx.matches(pivot("price_below_pivot", "R1"), betweenS1R1), "100 < R1(110)");
        assertFalse(idx.matches(pivot("price_below_pivot", "S1"), betweenS1R1), "100 not < S1(90)");

        assertTrue(idx.matches(pivot("price_below_pivot", "S1"), belowS1), "80 < S1(90)");
        assertFalse(idx.matches(pivot("price_above_pivot", "S1"), belowS1), "80 not > S1(90)");
    }

    @Test
    void crossIsAnEventNotAPerBarState() {
        List<OHLCBar> bars = twoDaySeries();
        NachtkrappMatchIndex idx = indexFor(
                "price_above_pivot(R1);price_crosses_above_pivot(R1)", bars);
        int state = 0;
        int cross = 0;
        for (OHLCBar b : bars) {
            if (idx.matches(pivot("price_above_pivot", "R1"), b.time())) {
                state++;
            }
            if (idx.matches(pivot("price_crosses_above_pivot", "R1"), b.time())) {
                cross++;
            }
        }

        assertTrue(state > 1, "the above-R1 state should hold on the whole 120-band, was " + state);
        assertTrue(cross <= 1, "price crosses above R1 at most once here, was " + cross);
        assertTrue(cross < state,
                "cross is a transition: it must fire on fewer bars than the state");
    }
}
