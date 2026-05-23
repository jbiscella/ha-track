package org.hatrack.commons;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Property-based invariants for {@link PivotPoints} over arbitrary valid prior
 * bars (jqwik). These algebraic relationships must hold for any bar, not just
 * the enumerated Cucumber/JUnit examples.
 */
class PivotPointsPropertyTest {

    private static final Instant T = Instant.parse("2024-01-01T00:00:00Z");

    @Provide
    Arbitrary<OHLCBar> validBar() {
        Arbitrary<Double> low = Arbitraries.doubles().between(1.0, 1000.0);
        Arbitrary<Double> range = Arbitraries.doubles().between(0.0, 300.0);
        Arbitrary<Double> openFrac = Arbitraries.doubles().between(0.0, 1.0);
        Arbitrary<Double> closeFrac = Arbitraries.doubles().between(0.0, 1.0);
        return Combinators.combine(low, range, openFrac, closeFrac).as((lo, r, of, cf) -> {
            double high = lo + r;
            double open = lo + of * r;
            double close = lo + cf * r;
            return new OHLCBar(T, bd(open), bd(high), bd(lo), bd(close), Optional.empty());
        });
    }

    @Property
    void standardLevelsAreOrderedDescending(@ForAll("validBar") OHLCBar bar) {
        PivotLevels l = PivotPoints.levels(bar, PivotPointVariant.STANDARD);
        // non-strict: equal only for a zero-range (flat) bar
        ge(l.r3(), l.r2());
        ge(l.r2(), l.r1());
        ge(l.r1(), l.p());
        ge(l.p(), l.s1());
        ge(l.s1(), l.s2());
        ge(l.s2(), l.s3());
    }

    @Property
    void camarillaLevelsAreSymmetricAboutTheClose(@ForAll("validBar") OHLCBar bar) {
        BigDecimal c = bar.close();
        PivotLevels l = PivotPoints.levels(bar, PivotPointVariant.CAMARILLA);
        symmetric(l.r1(), l.s1(), c);
        symmetric(l.r2(), l.s2(), c);
        symmetric(l.r3(), l.s3(), c);
        symmetric(l.r4(), l.s4(), c);
    }

    @Property
    void presentSizesAreFixedPerVariant(@ForAll("validBar") OHLCBar bar) {
        check(PivotPoints.levels(bar, PivotPointVariant.STANDARD).present().size() == 7, "STANDARD");
        check(PivotPoints.levels(bar, PivotPointVariant.WOODIE).present().size() == 5, "WOODIE");
        check(PivotPoints.levels(bar, PivotPointVariant.CAMARILLA).present().size() == 8, "CAMARILLA");
    }

    private static void ge(BigDecimal higher, BigDecimal lower) {
        check(higher.compareTo(lower) >= 0, higher + " must be >= " + lower);
    }

    private static final BigDecimal EPS = new BigDecimal("0.0000001");

    private static void symmetric(BigDecimal r, BigDecimal s, BigDecimal close) {
        // R_i = C + offset, S_i = C - offset; symmetric up to DECIMAL64 rounding
        BigDecimal diff = r.subtract(close).subtract(close.subtract(s)).abs();
        check(diff.compareTo(EPS) < 0,
                "R(" + r + ") / S(" + s + ") not symmetric about C(" + close + "), diff=" + diff);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static BigDecimal bd(double d) {
        return BigDecimal.valueOf(d);
    }
}
