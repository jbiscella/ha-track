package org.hatrack.heerwisch.jfreechart;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.heerwisch.api.spec.AxisMode;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.ImageFormat;
import org.hatrack.heerwisch.api.spec.Indicator;
import org.hatrack.heerwisch.api.spec.LayoutSpec;
import org.hatrack.heerwisch.api.spec.Pane;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.ohlc.OHLCSeriesCollection;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ORDINAL (gap-collapsing) axis mode: bars sit at integer positions, so a
 * non-trading gap takes no horizontal space and the indicator line never draws a
 * misleading slope across it. ORDINAL is the default; TIME preserves the
 * time-proportional axis. These assertions inspect the JFreeChart structure
 * (the rendered pixels are not recoverable from the PNG).
 */
class OrdinalAxisTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void defaultAxisModeIsOrdinal() throws Exception {
        assertEquals(AxisMode.ORDINAL, LayoutSpec.defaults().axisMode());
        assertEquals(AxisMode.ORDINAL, LayoutSpec.builder().build().axisMode());
    }

    @Test
    void ordinalUsesIndexAxisAndNumericDatasets() {
        XYPlot main = mainPlot(buildSpec(AxisMode.ORDINAL));
        CombinedDomainXYPlot combined = combined(buildSpec(AxisMode.ORDINAL));

        assertInstanceOf(JFreeChartRenderer.OrdinalTimeAxis.class, combined.getDomainAxis(),
                "ORDINAL mode must use the index-based domain axis");
        assertInstanceOf(JFreeChartRenderer.OrdinalOHLCDataset.class, main.getDataset(0),
                "ORDINAL candles must use the numeric-x OHLC dataset");
        // the RSI subplot's indicator line is a plain XY (index) dataset
        XYPlot sub = (XYPlot) combined.getSubplots().get(1);
        assertInstanceOf(XYSeriesCollection.class, sub.getDataset(0),
                "ORDINAL indicator lines must be index-based XY datasets");
    }

    @Test
    void ordinalDomainRangeSpansBarIndices() {
        CombinedDomainXYPlot combined = combined(buildSpec(AxisMode.ORDINAL));
        var range = combined.getDomainAxis().getRange();
        // 40 bars -> [-0.5, 39.5]
        assertEquals(-0.5, range.getLowerBound(), 1e-9);
        assertEquals(39.5, range.getUpperBound(), 1e-9);
    }

    @Test
    void timeModeKeepsDateAxisAndTimeDatasets() {
        XYPlot main = mainPlot(buildSpec(AxisMode.TIME));
        CombinedDomainXYPlot combined = combined(buildSpec(AxisMode.TIME));

        assertInstanceOf(DateAxis.class, combined.getDomainAxis(),
                "TIME mode must keep the time-proportional DateAxis");
        assertInstanceOf(OHLCSeriesCollection.class, main.getDataset(0),
                "TIME candles must keep the time-based OHLC dataset");
    }

    @Test
    void ordinalRenderProducesABitmap() {
        // end-to-end: a gappy series still renders without error in ORDINAL mode
        byte[] png = render(buildSpec(AxisMode.ORDINAL));
        assertTrue(png.length > 0, "ordinal render must produce bytes");
    }

    // --- helpers ---

    private static ChartSpec buildSpec(AxisMode mode) {
        try {
            return ChartSpec.builder()
                    .withSeries(new OHLCSeries(gappyDaily(40)))
                    .addIndicator(new Indicator.RSI(14, BigDecimal.valueOf(70),
                            BigDecimal.valueOf(30), PriceSource.CLOSE), Pane.SUBPLOT_1)
                    .withLayout(new LayoutSpec.AutoLayoutSpec(900, 500, ImageFormat.PNG, mode))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static CombinedDomainXYPlot combined(ChartSpec spec) {
        try {
            JFreeChart chart = new JFreeChartRenderer().buildChart(spec);
            chart.createBufferedImage(900, 500);
            return (CombinedDomainXYPlot) chart.getPlot();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static XYPlot mainPlot(ChartSpec spec) {
        return (XYPlot) combined(spec).getSubplots().get(0);
    }

    private static byte[] render(ChartSpec spec) {
        try {
            return new JFreeChartRenderer().render(spec).bytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Daily bars skipping weekends (so the series has real time gaps). */
    private static List<OHLCBar> gappyDaily(int n) {
        List<OHLCBar> out = new ArrayList<>();
        double price = 100.0;
        long day = 0;
        while (out.size() < n) {
            Instant t = BASE.plusSeconds(day * 86400L);
            day++;
            java.time.DayOfWeek dow = t.atZone(java.time.ZoneOffset.UTC).getDayOfWeek();
            if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) {
                continue; // skip weekends -> real gaps between Fri and Mon
            }
            double open = price;
            double close = price + ((out.size() % 3 == 0) ? 1.1 : -0.7);
            double high = Math.max(open, close) + 0.8;
            double low = Math.min(open, close) - 0.8;
            out.add(new OHLCBar(t, bd(open), bd(high), bd(low), bd(close), Optional.empty()));
            price = close;
        }
        return out;
    }

    private static BigDecimal bd(double d) {
        return new BigDecimal(String.format(java.util.Locale.ROOT, "%.2f", d));
    }
}
