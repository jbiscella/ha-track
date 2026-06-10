package org.hatrack.heerwisch.jfreechart;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.heerwisch.api.spec.CandleStyle;
import org.hatrack.heerwisch.api.spec.ChartImage;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.Indicator;
import org.hatrack.heerwisch.api.spec.LegendEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link CandleStyle#HEIKIN_ASHI} draws Heikin-Ashi candles from a supplied
 * {@code OHLCSeries} for rendering only. Overlay indicators keep their real
 * {@code PriceSource} (the source of truth), so a real-price {@code CLOSE} RSI/SMA
 * on an HA chart is valid — it must NOT trip the price-source compatibility check
 * (rule V5), which only guards against mixing HA-source indicators with OHLC
 * candles and vice-versa.
 */
class HeikinAshiCandleStyleTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");
    private static final List<OHLCBar> BARS = bars(40);

    private static ChartImage render(ChartSpec spec) {
        try {
            return new JFreeChartRenderer().render(spec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void defaultCandleStyleIsOhlc() {
        ChartSpec spec = assertDoesNotThrow(() -> ChartSpec.builder()
                .withSeries(new OHLCSeries(BARS))
                .build());
        assertEquals(CandleStyle.OHLC, spec.candleStyle());
    }

    @Test
    void heikinAshiStyleWithRealPriceIndicatorsBuildsAndRenders() {
        // The exact combination that broke wichtelm's report path: HA candles
        // plus CLOSE-sourced overlays. Must build (no V5) and render cleanly.
        ChartSpec spec = assertDoesNotThrow(() -> ChartSpec.builder()
                .withSeries(new OHLCSeries(BARS))
                .withCandleStyle(CandleStyle.HEIKIN_ASHI)
                .addIndicator(new Indicator.SMA(10, PriceSource.CLOSE))
                .addIndicator(new Indicator.RSI(14, bd(70), bd(30), PriceSource.CLOSE))
                .build());
        assertEquals(CandleStyle.HEIKIN_ASHI, spec.candleStyle());
        assertDoesNotThrow(() -> render(spec));
    }

    @Test
    void heikinAshiChangesCandlesButNotIndicators() {
        ChartImage ohlc = render(buildOf(BARS, CandleStyle.OHLC));
        ChartImage ha = render(buildOf(BARS, CandleStyle.HEIKIN_ASHI));

        // Candle bodies differ → the rendered pixels differ.
        assertFalse(Arrays.equals(ohlc.bytes(), ha.bytes()),
                "HA candles should render differently from raw OHLC candles");
        // Indicators are computed from the real series either way → identical legend.
        List<LegendEntry> lo = ohlc.legend();
        List<LegendEntry> lh = ha.legend();
        assertEquals(lo.size(), lh.size());
        for (int i = 0; i < lo.size(); i++) {
            assertEquals(lo.get(i).label(), lh.get(i).label());
            assertEquals(lo.get(i).rgb(), lh.get(i).rgb());
        }
    }

    private static ChartSpec buildOf(List<OHLCBar> bars, CandleStyle style) {
        try {
            return ChartSpec.builder()
                    .withSeries(new OHLCSeries(bars))
                    .withCandleStyle(style)
                    .addIndicator(new Indicator.SMA(10, PriceSource.CLOSE))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<OHLCBar> bars(int n) {
        List<OHLCBar> out = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < n; i++) {
            double open = price;
            double close = price + (i % 3 == 0 ? 1.6 : -0.7);
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
