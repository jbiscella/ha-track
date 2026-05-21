package org.hatrack.heerwisch.jfreechart;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.Indicator;
import org.hatrack.heerwisch.api.spec.Pane;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit coverage for {@link JFreeChartRenderer#subplotLabel} and
 * {@link JFreeChartRenderer#indicatorLabel}. The rendered axis label is not
 * recoverable from the encoded PNG, so the pure label helpers are tested
 * directly.
 */
class SubplotLabelTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void rsiSubpaneLabelReflectsThePeriod() throws Exception {
        ChartSpec spec = ChartSpec.builder()
                .withSeries(new OHLCSeries(bars(60)))
                .addIndicator(new Indicator.RSI(14, new BigDecimal("70"),
                        new BigDecimal("30"), PriceSource.CLOSE), Pane.SUBPLOT_1)
                .build();
        assertEquals("RSI(14)", JFreeChartRenderer.subplotLabel(spec, Pane.SUBPLOT_1));
    }

    @Test
    void rsiSubpaneLabelUsesTheConfiguredPeriod() throws Exception {
        ChartSpec spec = ChartSpec.builder()
                .withSeries(new OHLCSeries(bars(60)))
                .addIndicator(new Indicator.RSI(21, new BigDecimal("70"),
                        new BigDecimal("30"), PriceSource.CLOSE), Pane.SUBPLOT_1)
                .build();
        assertEquals("RSI(21)", JFreeChartRenderer.subplotLabel(spec, Pane.SUBPLOT_1));
    }

    @Test
    void macdAndStochasticLabelsCarryAllPeriods() {
        assertEquals("MACD(12,26,9)",
                JFreeChartRenderer.indicatorLabel(new Indicator.MACD(12, 26, 9, PriceSource.CLOSE)));
        assertEquals("Stoch(14,3,3)",
                JFreeChartRenderer.indicatorLabel(new Indicator.Stochastic(14, 3, 3)));
        assertEquals("ADX(14)", JFreeChartRenderer.indicatorLabel(new Indicator.ADX(14)));
        assertEquals("ATR(14)", JFreeChartRenderer.indicatorLabel(new Indicator.ATR(14)));
        assertEquals("Volume", JFreeChartRenderer.indicatorLabel(new Indicator.VolumePane()));
    }

    @Test
    void multipleIndicatorsOnOnePaneAreJoined() throws Exception {
        ChartSpec spec = ChartSpec.builder()
                .withSeries(new OHLCSeries(bars(60)))
                .addIndicator(new Indicator.RSI(14, new BigDecimal("70"),
                        new BigDecimal("30"), PriceSource.CLOSE), Pane.SUBPLOT_1)
                .addIndicator(new Indicator.RSI(21, new BigDecimal("70"),
                        new BigDecimal("30"), PriceSource.CLOSE), Pane.SUBPLOT_1)
                .build();
        assertEquals("RSI(14) / RSI(21)", JFreeChartRenderer.subplotLabel(spec, Pane.SUBPLOT_1));
    }

    @Test
    void paneWithNoIndicatorFallsBackToPaneName() throws Exception {
        ChartSpec spec = ChartSpec.builder()
                .withSeries(new OHLCSeries(bars(60)))
                .addIndicator(new Indicator.RSI(14, new BigDecimal("70"),
                        new BigDecimal("30"), PriceSource.CLOSE), Pane.SUBPLOT_1)
                .build();
        assertEquals("SUBPLOT_2", JFreeChartRenderer.subplotLabel(spec, Pane.SUBPLOT_2));
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
