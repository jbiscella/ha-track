package org.hatrack.heerwisch.jfreechart;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.jfree.chart.JFreeChart;
import org.junit.jupiter.api.Test;

import java.awt.Window;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Block-3 parity: rendering must not instantiate any AWT/Swing window or native
 * peer — the driver runs headless (Lambda-compatible). The Cucumber suite
 * covers "render works with headless=true"; this complements it by asserting no
 * {@link java.awt.Window} is created across a render. Pure JUnit — an
 * environment assertion.
 */
class NoSwingWindowTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void renderingCreatesNoAwtWindow() throws Exception {
        int before = Window.getWindows().length;
        ChartSpec spec = ChartSpec.builder().withSeries(new OHLCSeries(bars(40))).build();

        JFreeChart chart = new JFreeChartRenderer().buildChart(spec);
        chart.createBufferedImage(900, 500); // exercise the full draw path

        assertEquals(before, Window.getWindows().length,
                "rendering must not open any AWT/Swing window");
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
