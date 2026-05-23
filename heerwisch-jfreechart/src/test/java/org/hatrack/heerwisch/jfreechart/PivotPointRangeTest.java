package org.hatrack.heerwisch.jfreechart;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PivotPointVariant;
import org.hatrack.heerwisch.api.spec.Annotation;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.ChartSpecBuilder;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.52.0-alpha: the main pane's auto-range must include every
 * {@link Annotation.PivotPointLevels} level (same mechanism as
 * {@code HorizontalLevel}), so a pivot outside the price window stays on-chart.
 * The price series walks within roughly [99, 121]; the pivot bars below put all
 * computed levels well outside that window.
 */
class PivotPointRangeTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");
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

    private static ChartSpec build(ChartSpecBuilder b) {
        try {
            return b.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ChartSpecBuilder base() {
        return ChartSpec.builder().withSeries(new OHLCSeries(BARS));
    }

    @Test
    void pivotLevelsAbovePriceWindowExtendUpperBound() {
        // STANDARD pivots of H=300,L=290,C=295 span [280, 310], all above the price window.
        OHLCBar prev = new OHLCBar(BASE, bd(295), bd(300), bd(290), bd(295), Optional.empty());
        Range r = mainRange(build(base().addAnnotation(
                new Annotation.PivotPointLevels(PivotPointVariant.STANDARD, prev))));
        assertTrue(r.getUpperBound() >= 310.0,
                "upper bound " + r.getUpperBound() + " must include the highest pivot level (310)");
    }

    @Test
    void pivotLevelsBelowPriceWindowExtendLowerBound() {
        // STANDARD pivots of H=60,L=40,C=50 span [20, 80], all below the price window.
        OHLCBar prev = new OHLCBar(BASE, bd(50), bd(60), bd(40), bd(50), Optional.empty());
        Range r = mainRange(build(base().addAnnotation(
                new Annotation.PivotPointLevels(PivotPointVariant.STANDARD, prev))));
        assertTrue(r.getLowerBound() <= 20.0,
                "lower bound " + r.getLowerBound() + " must include the lowest pivot level (20)");
    }

    @Test
    void noPivotAnnotationLeavesPriceRange() {
        Range r = mainRange(build(base()));
        assertTrue(r.getUpperBound() < 200.0 && r.getLowerBound() > 0.0,
                "price-only range must stay near the series, not expand toward absent pivots");
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
