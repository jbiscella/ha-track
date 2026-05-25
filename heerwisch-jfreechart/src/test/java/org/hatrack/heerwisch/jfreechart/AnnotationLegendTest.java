package org.hatrack.heerwisch.jfreechart;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PivotPointVariant;
import org.hatrack.heerwisch.api.spec.Annotation;
import org.hatrack.heerwisch.api.spec.AnnotationLegendEntry;
import org.hatrack.heerwisch.api.spec.ChartImage;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.ChartSpecBuilder;
import org.hatrack.heerwisch.api.spec.FillColor;
import org.hatrack.heerwisch.api.spec.LevelStyle;
import org.hatrack.heerwisch.jfreechart.theme.ThemeConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.55.0-alpha: the driver surfaces annotation-overlay legend rows via
 * {@link ChartImage#annotationLegend()}, parallel to the indicator
 * {@code legend()}. Horizontal-line overlays (PivotPointLevels, HorizontalLevel,
 * FibRetracement) each emit one {@link AnnotationLegendEntry}; glyph / text / band
 * annotations emit none.
 */
class AnnotationLegendTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");
    private static final List<OHLCBar> BARS = bars(40);

    private static ChartImage render(ChartSpecBuilder b) {
        try {
            return new JFreeChartRenderer().render(b.build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ChartSpecBuilder base() {
        return ChartSpec.builder().withSeries(new OHLCSeries(BARS));
    }

    @Test
    void pivotPointLevelsProduceAnnotationLegendEntry() {
        OHLCBar prev = new OHLCBar(BASE, bd(110), bd(112), bd(108), bd(110), Optional.empty());
        ChartImage img = render(base().addAnnotation(
                new Annotation.PivotPointLevels(PivotPointVariant.STANDARD, prev)));
        assertEquals(1, img.annotationLegend().size());
        AnnotationLegendEntry e = img.annotationLegend().get(0);
        assertEquals("Pivot Points (STANDARD)", e.label());
        assertEquals(ThemeConstants.PIVOT_LEVEL.getRGB() & 0xFFFFFF, e.rgb());
        assertTrue(img.legend().isEmpty(), "no indicators => empty indicator legend");
    }

    @Test
    void horizontalLevelAndFibProduceAnnotationLegendEntries() {
        ChartImage img = render(base()
                .addAnnotation(new Annotation.HorizontalLevel(
                        bd(105), "Stop", LevelStyle.DASHED, Optional.of(FillColor.LOSS)))
                .addAnnotation(new Annotation.FibRetracement(
                        bd(120), bd(100), Annotation.FibRetracement.STANDARD_LEVELS)));
        List<AnnotationLegendEntry> legend = img.annotationLegend();
        assertEquals(2, legend.size());
        assertEquals("Stop", legend.get(0).label());
        assertEquals("Fib Retracement", legend.get(1).label());
        assertEquals(ThemeConstants.FIB_LEVEL.getRGB() & 0xFFFFFF, legend.get(1).rgb());
    }

    @Test
    void noAnnotationsYieldEmptyAnnotationLegend() {
        assertTrue(render(base()).annotationLegend().isEmpty());
    }

    private static List<OHLCBar> bars(int n) {
        List<OHLCBar> out = new ArrayList<>();
        double price = 110.0;
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price + (i % 3 == 0 ? 0.6 : -0.3);
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
