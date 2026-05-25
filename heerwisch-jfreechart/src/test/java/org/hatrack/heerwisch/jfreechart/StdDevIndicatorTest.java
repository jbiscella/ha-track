package org.hatrack.heerwisch.jfreechart;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.heerwisch.api.error.UnsupportedFeatureException;
import org.hatrack.heerwisch.api.spec.ChartImage;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.ChartSpecBuilder;
import org.hatrack.heerwisch.api.spec.Indicator;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 0.55.0-alpha: the standalone {@code StdDev} indicator renders as a single line
 * in its own sub-pane (σ is unbounded relative to price, like ATR), with a legend
 * entry labeled {@code σ(period)} in the {@code STDDEV_LINE} color. Placing it on
 * the MAIN pane is rejected by the driver's strict V12 check.
 */
class StdDevIndicatorTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");
    private static final List<OHLCBar> BARS = bars(40);

    private static ChartSpec build(ChartSpecBuilder b) {
        try {
            return b.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ChartImage render(ChartSpec spec) {
        try {
            return new JFreeChartRenderer().render(spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void stdDevDefaultsToSubplotAndEmitsLegendEntry() {
        ChartSpec spec = build(ChartSpec.builder()
                .withSeries(new OHLCSeries(BARS))
                .addIndicator(new Indicator.StdDev(20, PriceSource.CLOSE)));
        assertEquals(Pane.SUBPLOT_1, spec.indicators().get(0).pane());

        ChartImage img = render(spec);
        List<LegendEntry> legend = img.legend();
        assertEquals(1, legend.size());
        LegendEntry e = legend.get(0);
        assertEquals("σ(20)", e.label());
        assertEquals(ThemeConstants.STDDEV_LINE.getRGB() & 0xFFFFFF, e.rgb());
        assertEquals(Pane.SUBPLOT_1, e.pane());
    }

    @Test
    void stdDevOnMainPaneIsRejected() {
        ChartSpec spec = build(ChartSpec.builder()
                .withSeries(new OHLCSeries(BARS))
                .addIndicator(new Indicator.StdDev(20, PriceSource.CLOSE), Pane.MAIN));
        UnsupportedFeatureException ex = assertThrows(UnsupportedFeatureException.class,
                () -> new JFreeChartRenderer().render(spec));
        assertEquals("StdDev on MAIN pane", ex.featureName());
    }

    private static List<OHLCBar> bars(int n) {
        List<OHLCBar> out = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price + (i % 3 == 0 ? 0.8 : -0.4);
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
