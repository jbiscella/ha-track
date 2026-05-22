package org.hatrack.heerwisch.jfreechart;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.heerwisch.api.spec.Annotation;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.ChartSpecBuilder;
import org.hatrack.heerwisch.api.spec.LevelStyle;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G1: the main pane's auto-range must include every {@link Annotation.HorizontalLevel}
 * value, so a level outside the price window is still on-chart. The rendered
 * Y-range is not recoverable from the PNG, so these tests build the JFreeChart
 * directly, force layout (which triggers auto-range), and read the main pane's
 * range axis.
 */
class HorizontalLevelRangeTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");
    // 60 bars whose low/high stay within [~99, ~121] (price walks 100 -> ~120).
    private static final List<OHLCBar> BARS = bars(60);

    private static Range mainRange(ChartSpec spec) {
        try {
            JFreeChart chart = new JFreeChartRenderer().buildChart(spec);
            chart.createBufferedImage(900, 500); // force auto-range computation
            CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
            XYPlot main = (XYPlot) combined.getSubplots().get(0);
            return main.getRangeAxis().getRange();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ChartSpecBuilder base() {
        return ChartSpec.builder().withSeries(new OHLCSeries(BARS));
    }

    private static double priceMin() {
        return BARS.stream().mapToDouble(b -> b.low().doubleValue()).min().orElseThrow();
    }

    private static double priceMax() {
        return BARS.stream().mapToDouble(b -> b.high().doubleValue()).max().orElseThrow();
    }

    private static ChartSpec build(ChartSpecBuilder b) {
        try {
            return b.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void levelInsideRangeDoesNotExpand() {
        double mid = (priceMin() + priceMax()) / 2.0;
        Range withLevel = mainRange(build(base().addAnnotation(
                new Annotation.HorizontalLevel(BigDecimal.valueOf(mid), "mid", LevelStyle.SOLID))));
        Range noLevel = mainRange(build(base()));
        // a level inside the price window must not move the bounds
        assertEquals(noLevel.getLowerBound(), withLevel.getLowerBound(), 1e-6);
        assertEquals(noLevel.getUpperBound(), withLevel.getUpperBound(), 1e-6);
    }

    @Test
    void levelAboveRangeExtendsUpperBound() {
        double above = priceMax() + 50.0;
        Range r = mainRange(build(base().addAnnotation(
                new Annotation.HorizontalLevel(BigDecimal.valueOf(above), "tp", LevelStyle.DASHED))));
        assertTrue(r.getUpperBound() >= above,
                "upper bound " + r.getUpperBound() + " must include level " + above);
    }

    @Test
    void levelBelowRangeExtendsLowerBound() {
        double below = priceMin() - 50.0;
        Range r = mainRange(build(base().addAnnotation(
                new Annotation.HorizontalLevel(BigDecimal.valueOf(below), "sl", LevelStyle.DASHED))));
        assertTrue(r.getLowerBound() <= below,
                "lower bound " + r.getLowerBound() + " must include level " + below);
    }

    @Test
    void multipleStraddlingLevelsExtendBothBounds() {
        double above = priceMax() + 40.0;
        double below = priceMin() - 30.0;
        Range r = mainRange(build(base()
                .addAnnotation(new Annotation.HorizontalLevel(BigDecimal.valueOf(above), "tp",
                        LevelStyle.DASHED, Optional.empty()))
                .addAnnotation(new Annotation.HorizontalLevel(BigDecimal.valueOf(below), "sl",
                        LevelStyle.DASHED, Optional.empty()))));
        assertTrue(r.getUpperBound() >= above && r.getLowerBound() <= below,
                "range [" + r.getLowerBound() + ", " + r.getUpperBound()
                        + "] must include both " + below + " and " + above);
    }

    @Test
    void noLevelsLeavesRangeUnchanged() {
        Range noLevel = mainRange(build(base()));
        // baseline: bounds bracket the price series (with the axis's auto margin)
        assertTrue(noLevel.getLowerBound() <= priceMin() && noLevel.getUpperBound() >= priceMax(),
                "price-only range must bracket the series");
        // and a far-away band annotation (not a HorizontalLevel) must NOT expand it
        // — only HorizontalLevel participates in G1.
        assertTrue(noLevel.getUpperBound() < priceMax() + 50.0,
                "no horizontal level => no upward expansion toward a hypothetical far level");
    }

    private static List<OHLCBar> bars(int n) {
        List<OHLCBar> out = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price + (i % 3 == 0 ? 0.8 : -0.4) + (i % 7) * 0.1;
            double high = Math.max(open, close) + 1.0;
            double low = Math.min(open, close) - 1.0;
            out.add(new OHLCBar(BASE.plusSeconds(i * 86400L),
                    bd(open), bd(high), bd(low), bd(close), Optional.empty()));
            price = close;
        }
        return out;
    }

    private static BigDecimal bd(double d) {
        return new BigDecimal(String.format(java.util.Locale.ROOT, "%.2f", d));
    }
}
