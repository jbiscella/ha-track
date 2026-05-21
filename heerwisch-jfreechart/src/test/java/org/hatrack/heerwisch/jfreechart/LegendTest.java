package org.hatrack.heerwisch.jfreechart;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.heerwisch.api.spec.ChartImage;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.ImageFormat;
import org.hatrack.heerwisch.api.spec.Indicator;
import org.hatrack.heerwisch.api.spec.LayoutSpec;
import org.hatrack.heerwisch.api.spec.LegendEntry;
import org.hatrack.heerwisch.api.spec.Pane;
import org.hatrack.heerwisch.jfreechart.theme.ThemeConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-placement colors + legend introspection via {@link ChartImage#legend()}. */
class LegendTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");
    private static final BigDecimal OB = new BigDecimal("70");
    private static final BigDecimal OS = new BigDecimal("30");

    private static int rgb(java.awt.Color c) {
        return c.getRGB() & 0xFFFFFF;
    }

    private static ChartImage render(ChartSpec spec) throws Exception {
        return new JFreeChartRenderer().render(spec);
    }

    @Test
    void twoSmaOnOnePaneGetDistinctPaletteColors() throws Exception {
        ChartSpec spec = ChartSpec.builder()
                .withSeries(new OHLCSeries(bars(140)))
                .addIndicator(new Indicator.SMA(20, PriceSource.CLOSE), Pane.MAIN)
                .addIndicator(new Indicator.SMA(100, PriceSource.CLOSE), Pane.MAIN)
                .build();
        List<LegendEntry> legend = render(spec).legend();

        assertEquals(2, legend.size());
        assertEquals("SMA(20)", legend.get(0).label());
        assertEquals("SMA(100)", legend.get(1).label());
        assertEquals(rgb(ThemeConstants.SMA_PALETTE[0]), legend.get(0).rgb());
        assertEquals(rgb(ThemeConstants.SMA_PALETTE[1]), legend.get(1).rgb());
        assertNotEquals(legend.get(0).rgb(), legend.get(1).rgb(),
                "two SMA overlays must be distinct colors");
    }

    @Test
    void loneSmaUsesPaletteZeroForBackwardCompat() throws Exception {
        ChartSpec spec = ChartSpec.builder()
                .withSeries(new OHLCSeries(bars(60)))
                .addIndicator(new Indicator.SMA(20, PriceSource.CLOSE), Pane.MAIN)
                .build();
        List<LegendEntry> legend = render(spec).legend();

        assertEquals(1, legend.size());
        assertEquals(rgb(ThemeConstants.SMA_LINE), legend.get(0).rgb());
        assertEquals(rgb(ThemeConstants.SMA_PALETTE[0]), legend.get(0).rgb());
    }

    @Test
    void labelOverridePropagatesToLegendAndSubplotLabel() throws Exception {
        ChartSpec spec = ChartSpec.builder()
                .withSeries(new OHLCSeries(bars(60)))
                .addIndicator(new Indicator.RSI(14, OB, OS, PriceSource.CLOSE), Pane.SUBPLOT_1, "Momentum")
                .build();
        List<LegendEntry> legend = render(spec).legend();

        assertEquals(1, legend.size());
        assertEquals("Momentum", legend.get(0).label());
        assertEquals("Momentum", JFreeChartRenderer.subplotLabel(spec, Pane.SUBPLOT_1));
    }

    @Test
    void macdEmitsTwoLegendEntries() throws Exception {
        ChartSpec spec = ChartSpec.builder()
                .withSeries(new OHLCSeries(bars(80)))
                .addIndicator(new Indicator.MACD(12, 26, 9, PriceSource.CLOSE), Pane.SUBPLOT_1)
                .build();
        List<LegendEntry> legend = render(spec).legend();

        assertEquals(2, legend.size());
        assertEquals("MACD(12,26,9)", legend.get(0).label());
        assertEquals("Signal", legend.get(1).label());
        assertEquals(rgb(ThemeConstants.MACD_LINE), legend.get(0).rgb());
        assertEquals(rgb(ThemeConstants.MACD_SIGNAL), legend.get(1).rgb());
        assertEquals(Pane.SUBPLOT_1, legend.get(0).pane());
        assertEquals(Pane.SUBPLOT_1, legend.get(1).pane());
    }

    @Test
    void stochasticEmitsTwoLegendEntries() throws Exception {
        ChartSpec spec = ChartSpec.builder()
                .withSeries(new OHLCSeries(bars(60)))
                .addIndicator(new Indicator.Stochastic(14, 3, 3), Pane.SUBPLOT_1)
                .build();
        List<LegendEntry> legend = render(spec).legend();

        assertEquals(2, legend.size());
        assertEquals("Stoch(14,3,3)", legend.get(0).label());
        assertEquals("%D", legend.get(1).label());
        assertNotEquals(legend.get(0).rgb(), legend.get(1).rgb());
    }

    @Test
    void legendHasOneEntryPerSingleLinePlacementAcrossPanes() throws Exception {
        ChartSpec spec = ChartSpec.builder()
                .withSeries(new OHLCSeries(bars(60)))
                .addIndicator(new Indicator.SMA(20, PriceSource.CLOSE), Pane.MAIN)
                .addIndicator(new Indicator.EMA(50, PriceSource.CLOSE), Pane.MAIN)
                .addIndicator(new Indicator.RSI(14, OB, OS, PriceSource.CLOSE), Pane.SUBPLOT_1)
                .build();
        List<LegendEntry> legend = render(spec).legend();

        assertEquals(3, legend.size());
        assertEquals(Pane.MAIN, legend.get(0).pane());
        assertEquals(Pane.MAIN, legend.get(1).pane());
        assertEquals(Pane.SUBPLOT_1, legend.get(2).pane());
        // distinct colors across the three series
        assertTrue(legend.stream().map(LegendEntry::rgb).distinct().count() == 3);
    }

    private static List<OHLCBar> bars(int n) {
        List<OHLCBar> bars = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < n; i++) {
            BigDecimal o = BigDecimal.valueOf(price);
            BigDecimal c = BigDecimal.valueOf(price + 1);
            BigDecimal h = BigDecimal.valueOf(price + 2);
            BigDecimal l = BigDecimal.valueOf(price - 1);
            bars.add(new OHLCBar(BASE.plusSeconds(i * 86400L), o, h, l, c, Optional.empty()));
            price += 1;
        }
        return bars;
    }
}
