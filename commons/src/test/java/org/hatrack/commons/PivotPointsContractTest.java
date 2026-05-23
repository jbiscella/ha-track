package org.hatrack.commons;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural contracts for {@link PivotPoints} beyond the value spot-checks in
 * the Cucumber feature: null rejection, level ordering, and CAMARILLA symmetry.
 * Pure JUnit — these are algebraic relationships over the whole level set, not
 * single reference values. Bar H=120, L=90, C=105 (range 30).
 */
class PivotPointsContractTest {

    private static final Instant T = Instant.parse("2024-01-01T00:00:00Z");
    private static final OHLCBar BAR = new OHLCBar(
            T, new BigDecimal("105"), new BigDecimal("120"),
            new BigDecimal("90"), new BigDecimal("105"), Optional.empty());

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class,
                () -> PivotPoints.levels(null, PivotPointVariant.STANDARD));
        assertThrows(NullPointerException.class, () -> PivotPoints.levels(BAR, null));
    }

    @Test
    void standardLevelsAreStrictlyOrderedDescending() {
        PivotLevels l = PivotPoints.levels(BAR, PivotPointVariant.STANDARD);
        assertDescending(l.r3(), l.r2());
        assertDescending(l.r2(), l.r1());
        assertDescending(l.r1(), l.p());
        assertDescending(l.p(), l.s1());
        assertDescending(l.s1(), l.s2());
        assertDescending(l.s2(), l.s3());
    }

    @Test
    void standardHasNoR4OrS4() {
        PivotLevels l = PivotPoints.levels(BAR, PivotPointVariant.STANDARD);
        assertNull(l.r4());
        assertNull(l.s4());
        assertEquals(7, l.present().size());
    }

    @Test
    void woodieProducesFiveLevels() {
        PivotLevels l = PivotPoints.levels(BAR, PivotPointVariant.WOODIE);
        assertEquals(5, l.present().size());
        assertNull(l.r3());
        assertNull(l.r4());
        assertNull(l.s3());
        assertNull(l.s4());
    }

    @Test
    void camarillaHasEightLevelsAndNoCentralPivot() {
        PivotLevels l = PivotPoints.levels(BAR, PivotPointVariant.CAMARILLA);
        assertNull(l.p());
        assertEquals(8, l.present().size());
    }

    @Test
    void camarillaLevelsAreSymmetricAboutTheClose() {
        BigDecimal close = BAR.close();
        PivotLevels l = PivotPoints.levels(BAR, PivotPointVariant.CAMARILLA);
        assertSymmetric(l.r1(), l.s1(), close);
        assertSymmetric(l.r2(), l.s2(), close);
        assertSymmetric(l.r3(), l.s3(), close);
        assertSymmetric(l.r4(), l.s4(), close);
    }

    private static void assertDescending(BigDecimal higher, BigDecimal lower) {
        assertTrue(higher.compareTo(lower) > 0, higher + " must be > " + lower);
    }

    private static void assertSymmetric(BigDecimal resistance, BigDecimal support, BigDecimal close) {
        // R_i - C == C - S_i
        assertEquals(0, resistance.subtract(close).compareTo(close.subtract(support)),
                "R(" + resistance + ") and S(" + support + ") not symmetric about C(" + close + ")");
    }
}
