package org.hatrack.heerwisch.jfreechart;

import org.hatrack.commons.HABar;
import org.hatrack.commons.HASeries;
import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PivotPoints;
import org.hatrack.commons.PriceSource;
import org.hatrack.commons.Series;
import org.hatrack.heerwisch.api.error.ChartRenderException;
import org.hatrack.heerwisch.api.error.DriverInternalException;
import org.hatrack.heerwisch.api.error.UnsupportedFeatureException;
import org.hatrack.heerwisch.api.port.ChartRenderer;
import org.hatrack.heerwisch.api.spec.Annotation;
import org.hatrack.heerwisch.api.spec.AxisMode;
import org.hatrack.heerwisch.api.spec.ChartImage;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.FillColor;
import org.hatrack.heerwisch.api.spec.GlyphStyle;
import org.hatrack.heerwisch.api.spec.ImageFormat;
import org.hatrack.heerwisch.api.spec.Indicator;
import org.hatrack.heerwisch.api.spec.IndicatorPlacement;
import org.hatrack.heerwisch.api.spec.LegendEntry;
import org.hatrack.heerwisch.api.spec.LayoutSpec;
import org.hatrack.heerwisch.api.spec.LevelStyle;
import org.hatrack.heerwisch.api.spec.MarkerDirection;
import org.hatrack.heerwisch.api.spec.Pane;
import org.hatrack.heerwisch.jfreechart.theme.ThemeConstants;
import org.hatrack.indicators.BollingerBands;
import org.hatrack.indicators.Indicators;
import org.hatrack.indicators.MacdResult;
import org.hatrack.indicators.StochasticResult;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYShapeAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.AxisState;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTick;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.AbstractXYDataset;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.time.ohlc.OHLCSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The default {@link ChartRenderer} driver, backed by JFreeChart 1.5.x.
 * Headless, deterministic (embedded DejaVu Sans font), single-threaded.
 */
public final class JFreeChartRenderer implements ChartRenderer {

    private static final String DEFAULT_FONT_RESOURCE = "/heerwisch-fonts/DejaVuSans.ttf";
    private static final String DRIVER_NAME = "heerwisch-jfreechart";

    private static volatile Font cachedFont;

    private final Font baseFont;

    /** Creates a renderer using the bundled DejaVu Sans font (cached for the JVM). */
    public JFreeChartRenderer() throws DriverInternalException {
        this.baseFont = defaultFont();
    }

    /** Test seam: load the font from an explicit classpath resource (not cached). */
    JFreeChartRenderer(String fontResource) throws DriverInternalException {
        this.baseFont = loadFont(fontResource);
    }

    @Override
    public ChartImage render(ChartSpec spec) throws ChartRenderException {
        Objects.requireNonNull(spec, "spec");
        enforceIndicatorPaneCompatibility(spec);
        try {
            LayoutSpec layout = spec.layout();
            int width = layout.widthPx();
            int height = layout.heightPx();
            JFreeChart chart = buildChart(spec);
            BufferedImage image = chart.createBufferedImage(width, height,
                    BufferedImage.TYPE_INT_RGB, null);
            byte[] bytes = encode(image, layout.format());
            return new ChartImage(bytes, contentType(layout.format()), width, height,
                    buildLegend(spec));
        } catch (RuntimeException e) {
            throw new DriverInternalException(e);
        }
    }

    // --- V12 strict enforcement ---

    private static void enforceIndicatorPaneCompatibility(ChartSpec spec)
            throws UnsupportedFeatureException {
        for (IndicatorPlacement placement : spec.indicators()) {
            if (placement.pane() == Pane.MAIN && !isOverlayCompatible(placement.indicator())) {
                String feature = placement.indicator().getClass().getSimpleName() + " on MAIN pane";
                throw new UnsupportedFeatureException(feature, DRIVER_NAME);
            }
        }
    }

    private static boolean isOverlayCompatible(Indicator indicator) {
        return indicator instanceof Indicator.SMA
                || indicator instanceof Indicator.EMA
                || indicator instanceof Indicator.BollingerBands;
    }

    // --- chart construction ---

    // Package-private for tests: lets a test inspect the JFreeChart (e.g. the
    // main pane's range axis) without going through PNG encoding.
    JFreeChart buildChart(ChartSpec spec) {
        boolean ordinal = spec.layout().axisMode() == AxisMode.ORDINAL;
        List<Instant> times = times(spec.series());
        ValueAxis domainAxis = ordinal ? new OrdinalTimeAxis(times) : new DateAxis();
        styleAxis(domainAxis);
        CombinedDomainXYPlot combined = new CombinedDomainXYPlot(domainAxis);
        combined.setGap(8.0);
        combined.setBackgroundPaint(ThemeConstants.BACKGROUND);

        combined.add(buildMainPlot(spec, ordinal, times), 60);

        List<Pane> subplotPanes = referencedSubplotPanes(spec);
        if (!subplotPanes.isEmpty()) {
            int weight = Math.max(1, 40 / subplotPanes.size());
            for (Pane pane : subplotPanes) {
                combined.add(buildSubplot(spec, pane, ordinal, times), weight);
            }
        }

        JFreeChart chart = new JFreeChart(null, baseFont.deriveFont(Font.BOLD, 14f),
                combined, false);
        chart.setBackgroundPaint(ThemeConstants.BACKGROUND);
        return chart;
    }

    private XYPlot buildMainPlot(ChartSpec spec, boolean ordinal, List<Instant> times) {
        CandlestickRenderer candleRenderer = new CandlestickRenderer();
        candleRenderer.setUpPaint(ThemeConstants.BULLISH_CANDLE);
        candleRenderer.setDownPaint(ThemeConstants.BEARISH_CANDLE);
        candleRenderer.setDrawVolume(false);
        candleRenderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);

        NumberAxis rangeAxis = new NumberAxis("Price");
        styleAxis(rangeAxis);
        rangeAxis.setAutoRangeIncludesZero(false);

        XYPlot plot = new XYPlot(buildPriceDataset(spec.series(), ordinal), null, rangeAxis, candleRenderer);
        stylePlot(plot);

        int datasetIndex = 1;
        List<IndicatorPlacement> placements = spec.indicators();
        for (int i = 0; i < placements.size(); i++) {
            IndicatorPlacement placement = placements.get(i);
            if (placement.pane() == Pane.MAIN) {
                datasetIndex = addIndicator(plot, datasetIndex, spec.series(),
                        placement.indicator(), paletteColorAt(spec, i), ordinal, times);
            }
        }
        addAnnotations(plot, spec, ordinal, times);
        includeAnnotationLevelsInRange(plot, spec, datasetIndex, ordinal, times);
        return plot;
    }

    /**
     * Extends the main pane's auto-range to include every {@code HorizontalLevel}
     * value and every {@code PivotPointLevels} level, so a reference line outside
     * the price window (e.g. a stop, target, or pivot beyond the visible
     * high/low) is still on-chart — matching TradingView/MetaTrader. Implemented
     * by adding a non-drawing dataset (no lines, no shapes) carrying the level
     * values: JFreeChart's range auto-calc includes it (with the axis's normal
     * margins) and the axis only widens when a level actually exceeds the price
     * bounds. No effect when there are no such annotations, and it never touches a
     * manually-bounded axis (the main axis is always auto-ranged).
     */
    private static void includeAnnotationLevelsInRange(XYPlot plot, ChartSpec spec, int index,
                                                       boolean ordinal, List<Instant> times) {
        if (times.isEmpty()) {
            return;
        }
        double x = ordinal ? 0.0 : times.get(0).toEpochMilli();
        XYSeries levels = new XYSeries("levels", false, true);
        boolean any = false;
        for (Annotation annotation : spec.annotations()) {
            if (annotation instanceof Annotation.HorizontalLevel level) {
                levels.add(x, level.price().doubleValue());
                any = true;
            } else if (annotation instanceof Annotation.PivotPointLevels pivots) {
                for (BigDecimal level : pivotLevels(pivots)) {
                    levels.add(x, level.doubleValue());
                    any = true;
                }
            }
        }
        if (!any) {
            return;
        }
        XYLineAndShapeRenderer invisible = new XYLineAndShapeRenderer(false, false);
        invisible.setDataBoundsIncludesVisibleSeriesOnly(false);
        plot.setDataset(index, new XYSeriesCollection(levels));
        plot.setRenderer(index, invisible);
        plot.mapDatasetToRangeAxis(index, 0);
    }

    private XYPlot buildSubplot(ChartSpec spec, Pane pane, boolean ordinal, List<Instant> times) {
        NumberAxis rangeAxis = new NumberAxis(subplotLabel(spec, pane));
        styleAxis(rangeAxis);
        rangeAxis.setAutoRangeIncludesZero(false);

        XYPlot plot = new XYPlot();
        plot.setRangeAxis(rangeAxis);
        stylePlot(plot);

        // RSI is mathematically bounded to [0, 100]. When a pane contains only
        // RSI indicators, bind the range axis explicitly so the line and the
        // overbought / oversold reference levels render against the canonical
        // 0–100 scale rather than JFreeChart's auto-range. Mixed panes (RSI
        // combined with an unbounded indicator) keep auto-range to avoid
        // clipping the unbounded sibling.
        if (paneContainsOnlyRsi(spec, pane)) {
            rangeAxis.setRange(0.0, 100.0);
            rangeAxis.setAutoRange(false);
        }

        int datasetIndex = 0;
        List<IndicatorPlacement> placements = spec.indicators();
        for (int i = 0; i < placements.size(); i++) {
            IndicatorPlacement placement = placements.get(i);
            if (placement.pane() == pane) {
                datasetIndex = addIndicator(plot, datasetIndex, spec.series(),
                        placement.indicator(), paletteColorAt(spec, i), ordinal, times);
            }
        }
        return plot;
    }

    /**
     * Y-axis label for a sub-pane: indicator-meaningful (e.g. {@code "RSI(14)"},
     * {@code "MACD(12,26,9)"}), joined with {@code " / "} when a pane holds more
     * than one indicator. Falls back to the generic {@code Pane} name only when
     * the pane has no indicators (should not happen for a referenced subplot).
     */
    static String subplotLabel(ChartSpec spec, Pane pane) {
        StringBuilder label = new StringBuilder();
        for (IndicatorPlacement placement : spec.indicators()) {
            if (placement.pane() != pane) {
                continue;
            }
            if (label.length() > 0) {
                label.append(" / ");
            }
            label.append(placement.label().orElseGet(() -> indicatorLabel(placement.indicator())));
        }
        return label.length() > 0 ? label.toString() : pane.name();
    }

    // --- per-placement color + legend ---

    private static List<Color> paletteFor(Indicator indicator) {
        if (indicator instanceof Indicator.SMA) {
            return ThemeConstants.SMA_PALETTE;
        }
        if (indicator instanceof Indicator.EMA) {
            return ThemeConstants.EMA_PALETTE;
        }
        if (indicator instanceof Indicator.BollingerBands) {
            return ThemeConstants.BB_PALETTE;
        }
        return null;
    }

    /**
     * Palette color for the placement at {@code position}, by the count of
     * earlier same-type placements on the same pane (occurrence index). Returns
     * {@code null} for indicator types without a per-placement palette.
     */
    private static Color paletteColorAt(ChartSpec spec, int position) {
        List<IndicatorPlacement> placements = spec.indicators();
        IndicatorPlacement placement = placements.get(position);
        List<Color> palette = paletteFor(placement.indicator());
        if (palette == null) {
            return null;
        }
        int occurrence = 0;
        for (int j = 0; j < position; j++) {
            IndicatorPlacement earlier = placements.get(j);
            if (earlier.pane() == placement.pane()
                    && earlier.indicator().getClass() == placement.indicator().getClass()) {
                occurrence++;
            }
        }
        return palette.get(occurrence % palette.size());
    }

    private static int rgb(Color color) {
        return color.getRGB() & 0xFFFFFF;
    }

    /**
     * Legend rows in spec insertion order, one entry per rendered line. A
     * single-line indicator emits one entry labeled by its base (the
     * placement's override or the auto-derived indicator label, e.g.
     * {@code "SMA(20)"}). Multi-line indicators use the {@code "<base>: <role>"}
     * colon convention: MACD → {@code "<base>: MACD"} + {@code "<base>: Signal"};
     * Stochastic → {@code "<base>: %K"} + {@code "<base>: %D"}; BollingerBands →
     * {@code "<base>: Upper"} + {@code ": Basis"} + {@code ": Lower"} (all three
     * sharing the placement's color). The full base keeps every parameter
     * visible so two placements of the same type with different parameters do
     * not collapse to identical labels.
     */
    private static List<LegendEntry> buildLegend(ChartSpec spec) {
        List<IndicatorPlacement> placements = spec.indicators();
        List<LegendEntry> entries = new ArrayList<>();
        for (int i = 0; i < placements.size(); i++) {
            IndicatorPlacement placement = placements.get(i);
            Indicator indicator = placement.indicator();
            String label = placement.label().orElseGet(() -> indicatorLabel(indicator));
            Pane pane = placement.pane();
            switch (indicator) {
                case Indicator.MACD ignored -> {
                    entries.add(new LegendEntry(placement, label + ": MACD", rgb(ThemeConstants.MACD_LINE), pane));
                    entries.add(new LegendEntry(placement, label + ": Signal", rgb(ThemeConstants.MACD_SIGNAL), pane));
                }
                case Indicator.Stochastic ignored -> {
                    entries.add(new LegendEntry(placement, label + ": %K", rgb(ThemeConstants.STOCHASTIC_K), pane));
                    entries.add(new LegendEntry(placement, label + ": %D", rgb(ThemeConstants.STOCHASTIC_D), pane));
                }
                case Indicator.BollingerBands ignored -> {
                    // Three lines (upper/basis/lower) share the placement's
                    // palette color — emit one entry per rendered line, with
                    // the "<base>: <role>" colon convention.
                    int c = rgb(legendPrimaryColor(spec, i));
                    entries.add(new LegendEntry(placement, label + ": Upper", c, pane));
                    entries.add(new LegendEntry(placement, label + ": Basis", c, pane));
                    entries.add(new LegendEntry(placement, label + ": Lower", c, pane));
                }
                default ->
                        entries.add(new LegendEntry(placement, label,
                                rgb(legendPrimaryColor(spec, i)), pane));
            }
        }
        return entries;
    }

    private static Color legendPrimaryColor(ChartSpec spec, int position) {
        Color palette = paletteColorAt(spec, position);
        if (palette != null) {
            return palette;
        }
        return switch (spec.indicators().get(position).indicator()) {
            case Indicator.SMA ignored -> ThemeConstants.SMA_LINE;
            case Indicator.EMA ignored -> ThemeConstants.EMA_LINE;
            case Indicator.BollingerBands ignored -> ThemeConstants.BB_BAND;
            case Indicator.MACD ignored -> ThemeConstants.MACD_LINE;
            case Indicator.RSI ignored -> ThemeConstants.RSI_LINE;
            case Indicator.ADX ignored -> ThemeConstants.ADX_LINE;
            case Indicator.Stochastic ignored -> ThemeConstants.STOCHASTIC_K;
            case Indicator.ATR ignored -> ThemeConstants.ATR_LINE;
            case Indicator.VolumePane ignored -> ThemeConstants.VOLUME_BAR_UP;
        };
    }

    static String indicatorLabel(Indicator indicator) {
        return switch (indicator) {
            case Indicator.SMA sma -> "SMA(" + sma.period() + ")";
            case Indicator.EMA ema -> "EMA(" + ema.period() + ")";
            case Indicator.BollingerBands bb -> "BB(" + bb.period() + "," + bb.stdDevMultiplier() + ")";
            case Indicator.MACD macd -> "MACD(" + macd.fastPeriod() + ","
                    + macd.slowPeriod() + "," + macd.signalPeriod() + ")";
            case Indicator.RSI rsi -> "RSI(" + rsi.period() + ")";
            case Indicator.ADX adx -> "ADX(" + adx.period() + ")";
            case Indicator.Stochastic stoch -> "Stoch(" + stoch.kPeriod() + ","
                    + stoch.dPeriod() + "," + stoch.smoothing() + ")";
            case Indicator.ATR atr -> "ATR(" + atr.period() + ")";
            case Indicator.VolumePane ignored -> "Volume";
        };
    }

    private static boolean paneContainsOnlyRsi(ChartSpec spec, Pane pane) {
        boolean sawRsi = false;
        for (IndicatorPlacement placement : spec.indicators()) {
            if (placement.pane() != pane) continue;
            if (!(placement.indicator() instanceof Indicator.RSI)) {
                return false;
            }
            sawRsi = true;
        }
        return sawRsi;
    }

    private static List<Pane> referencedSubplotPanes(ChartSpec spec) {
        TreeSet<Pane> panes = new TreeSet<>();
        for (IndicatorPlacement placement : spec.indicators()) {
            if (placement.pane() != Pane.MAIN) {
                panes.add(placement.pane());
            }
        }
        return new ArrayList<>(panes);
    }

    // --- datasets ---

    private static OHLCDataset buildPriceDataset(Series series, boolean ordinal) {
        if (ordinal) {
            return new OrdinalOHLCDataset(series);
        }
        org.jfree.data.time.ohlc.OHLCSeries jfSeries = new org.jfree.data.time.ohlc.OHLCSeries("price");
        switch (series) {
            case OHLCSeries ohlc -> {
                for (OHLCBar bar : ohlc.bars()) {
                    jfSeries.add(new FixedMillisecond(bar.time().toEpochMilli()),
                            bar.open().doubleValue(), bar.high().doubleValue(),
                            bar.low().doubleValue(), bar.close().doubleValue());
                }
            }
            case HASeries ha -> {
                for (HABar bar : ha.bars()) {
                    jfSeries.add(new FixedMillisecond(bar.time().toEpochMilli()),
                            bar.haOpen().doubleValue(), bar.haHigh().doubleValue(),
                            bar.haLow().doubleValue(), bar.haClose().doubleValue());
                }
            }
        }
        OHLCSeriesCollection collection = new OHLCSeriesCollection();
        collection.addSeries(jfSeries);
        return collection;
    }

    private int addIndicator(XYPlot plot, int index, Series series, Indicator indicator,
                             Color overrideColor, boolean ordinal, List<Instant> times) {
        switch (indicator) {
            case Indicator.SMA sma -> {
                return addLine(plot, index, "SMA", times,
                        Indicators.sma(prices(series, sma.priceSource()), sma.period()),
                        overrideColor != null ? overrideColor : ThemeConstants.SMA_LINE, ordinal);
            }
            case Indicator.EMA ema -> {
                return addLine(plot, index, "EMA", times,
                        Indicators.ema(prices(series, ema.priceSource()), ema.period()),
                        overrideColor != null ? overrideColor : ThemeConstants.EMA_LINE, ordinal);
            }
            case Indicator.BollingerBands bb -> {
                Color band = overrideColor != null ? overrideColor : ThemeConstants.BB_BAND;
                BollingerBands bands = Indicators.bollinger(
                        prices(series, bb.priceSource()), bb.period(), bb.stdDevMultiplier());
                int next = addLine(plot, index, "BB upper", times, bands.upper(), band, ordinal);
                next = addLine(plot, next, "BB middle", times, bands.middle(), band, ordinal);
                return addLine(plot, next, "BB lower", times, bands.lower(), band, ordinal);
            }
            case Indicator.MACD macd -> {
                MacdResult lines = Indicators.macd(prices(series, macd.priceSource()),
                        macd.fastPeriod(), macd.slowPeriod(), macd.signalPeriod());
                int next = addLine(plot, index, "MACD", times, lines.macdLine(), ThemeConstants.MACD_LINE, ordinal);
                return addLine(plot, next, "Signal", times, lines.signalLine(), ThemeConstants.MACD_SIGNAL, ordinal);
            }
            case Indicator.RSI rsi -> {
                // Optional shaded danger zones (drawn first so they sit
                // behind the threshold lines and the RSI line itself).
                if (rsi.visualization().map(Indicator.RsiVisualization::dangerZones).orElse(false)) {
                    double obValue = rsi.overbought().doubleValue();
                    double osValue = rsi.oversold().doubleValue();
                    IntervalMarker overboughtZone = new IntervalMarker(obValue, 100.0,
                            ThemeConstants.RSI_OVERBOUGHT_ZONE);
                    overboughtZone.setOutlinePaint(null);
                    plot.addRangeMarker(overboughtZone, Layer.BACKGROUND);
                    IntervalMarker oversoldZone = new IntervalMarker(0.0, osValue,
                            ThemeConstants.RSI_OVERSOLD_ZONE);
                    oversoldZone.setOutlinePaint(null);
                    plot.addRangeMarker(oversoldZone, Layer.BACKGROUND);
                }
                // Horizontal reference levels at the configured overbought /
                // oversold thresholds, per heerwisch-jfreechart/CLAUDE.md §7.
                plot.addRangeMarker(marker(rsi.overbought().doubleValue(),
                        ThemeConstants.RSI_OVERBOUGHT_LEVEL));
                plot.addRangeMarker(marker(rsi.oversold().doubleValue(),
                        ThemeConstants.RSI_OVERSOLD_LEVEL));
                return addLine(plot, index, "RSI", times,
                        Indicators.rsi(prices(series, rsi.priceSource()), rsi.period()),
                        ThemeConstants.RSI_LINE, ordinal);
            }
            case Indicator.ADX adx -> {
                return addLine(plot, index, "ADX", times,
                        Indicators.adx(highs(series), lows(series), closes(series), adx.period()),
                        ThemeConstants.ADX_LINE, ordinal);
            }
            case Indicator.Stochastic stoch -> {
                StochasticResult lines = Indicators.stochastic(highs(series), lows(series),
                        closes(series), stoch.kPeriod(), stoch.dPeriod(), stoch.smoothing());
                int next = addLine(plot, index, "%K", times, lines.percentK(), ThemeConstants.STOCHASTIC_K, ordinal);
                return addLine(plot, next, "%D", times, lines.percentD(), ThemeConstants.STOCHASTIC_D, ordinal);
            }
            case Indicator.ATR atr -> {
                return addLine(plot, index, "ATR", times,
                        Indicators.atr(highs(series), lows(series), closes(series), atr.period()),
                        ThemeConstants.ATR_LINE, ordinal);
            }
            case Indicator.VolumePane ignored -> {
                return addVolumeBars(plot, index, series, ordinal);
            }
        }
    }

    private static int addLine(XYPlot plot, int index, String name, List<Instant> times,
                               BigDecimal[] values, Color color, boolean ordinal) {
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, color);
        renderer.setSeriesStroke(0, ThemeConstants.STROKE_INDICATOR);
        if (ordinal) {
            XYSeries series = new XYSeries(name, false, false);
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null) {
                    series.add((double) i, values[i].doubleValue());
                }
            }
            plot.setDataset(index, new XYSeriesCollection(series));
        } else {
            TimeSeries series = new TimeSeries(name);
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null) {
                    series.add(new FixedMillisecond(times.get(i).toEpochMilli()), values[i].doubleValue());
                }
            }
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            plot.setDataset(index, dataset);
        }
        plot.setRenderer(index, renderer);
        return index + 1;
    }

    private static int addVolumeBars(XYPlot plot, int index, Series series, boolean ordinal) {
        XYBarRenderer renderer = new XYBarRenderer();
        renderer.setShadowVisible(false);
        renderer.setSeriesPaint(0, ThemeConstants.VOLUME_BAR_UP);
        if (ordinal) {
            XYSeries volume = new XYSeries("Volume", false, false);
            if (series instanceof OHLCSeries ohlc) {
                List<OHLCBar> bars = ohlc.bars();
                for (int i = 0; i < bars.size(); i++) {
                    volume.add((double) i, bars.get(i).volume().map(BigDecimal::doubleValue).orElse(0.0));
                }
            }
            plot.setDataset(index, new XYSeriesCollection(volume));
        } else {
            TimeSeries volume = new TimeSeries("Volume");
            if (series instanceof OHLCSeries ohlc) {
                for (OHLCBar bar : ohlc.bars()) {
                    volume.add(new FixedMillisecond(bar.time().toEpochMilli()),
                            bar.volume().map(BigDecimal::doubleValue).orElse(0.0));
                }
            }
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(volume);
            plot.setDataset(index, dataset);
        }
        plot.setRenderer(index, renderer);
        return index + 1;
    }

    // --- annotations (MAIN pane only) ---

    private void addAnnotations(XYPlot plot, ChartSpec spec, boolean ordinal, List<Instant> times) {
        GlyphExtents glyphExtents = computeGlyphExtents(spec, ordinal);
        for (Annotation annotation : spec.annotations()) {
            switch (annotation) {
                case Annotation.BarHighlight highlight -> {
                    XYTextAnnotation text = new XYTextAnnotation(highlight.label(),
                            domainX(highlight.time(), ordinal, times), highlight.price().doubleValue());
                    text.setFont(baseFont.deriveFont(10f));
                    text.setPaint(ThemeConstants.ANNOTATION_NEUTRAL);
                    plot.addAnnotation(text);
                }
                case Annotation.HorizontalLevel level ->
                        plot.addRangeMarker(levelMarker(level));
                case Annotation.FibRetracement fib -> {
                    BigDecimal span = fib.swingHigh().subtract(fib.swingLow());
                    for (BigDecimal fraction : fib.levels()) {
                        double price = fib.swingLow().add(fraction.multiply(span)).doubleValue();
                        plot.addRangeMarker(marker(price, ThemeConstants.FIB_LEVEL));
                    }
                }
                case Annotation.PivotPointLevels pivots -> {
                    for (BigDecimal level : pivotLevels(pivots)) {
                        plot.addRangeMarker(marker(level.doubleValue(), ThemeConstants.PIVOT_LEVEL));
                    }
                }
                case Annotation.EntryExitMarker entryExit -> {
                    Color color = entryExitColor(entryExit.direction());
                    Shape glyph = glyphShape(entryExit.glyphStyle(),
                            domainX(entryExit.time(), ordinal, times),
                            entryExit.price().doubleValue(),
                            glyphExtents.dx(), glyphExtents.dy());
                    XYShapeAnnotation shape = new XYShapeAnnotation(glyph,
                            ThemeConstants.STROKE_DEFAULT, color, color);
                    plot.addAnnotation(shape);
                }
                case Annotation.EntryExitMarkerAuto entryExit -> {
                    Color color = entryExitColor(entryExit.direction());
                    double[] hl = barHighLow(spec.series(), entryExit.time());
                    double padding = glyphExtents.dy() * ThemeConstants.GLYPH_OFFSET_FACTOR_BAR;
                    double yPosition = switch (entryExit.direction()) {
                        case LONG_ENTRY, SHORT_EXIT -> hl[1] - padding; // below bar.low
                        case LONG_EXIT, SHORT_ENTRY -> hl[0] + padding; // above bar.high
                    };
                    Shape glyph = glyphShape(entryExit.glyphStyle(),
                            domainX(entryExit.time(), ordinal, times),
                            yPosition,
                            glyphExtents.dx(), glyphExtents.dy());
                    XYShapeAnnotation shape = new XYShapeAnnotation(glyph,
                            ThemeConstants.STROKE_DEFAULT, color, color);
                    plot.addAnnotation(shape);
                }
                case Annotation.TimeRangeHighlight range -> {
                    Color base = timeRangeColor(range.fillColor());
                    int alpha = range.opacity().multiply(new BigDecimal("255")).intValue();
                    Color fill = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
                    IntervalMarker band = new IntervalMarker(
                            domainX(range.startTime(), ordinal, times),
                            domainX(range.endTime(), ordinal, times),
                            fill);
                    band.setOutlinePaint(null);
                    plot.addDomainMarker(band, Layer.BACKGROUND);
                }
            }
        }
    }

    private static Color entryExitColor(MarkerDirection direction) {
        return switch (direction) {
            case LONG_ENTRY, SHORT_EXIT -> ThemeConstants.ANNOTATION_BULLISH;
            case SHORT_ENTRY, LONG_EXIT -> ThemeConstants.ANNOTATION_BEARISH;
        };
    }

    private static Color timeRangeColor(FillColor fillColor) {
        return switch (fillColor) {
            case LONG_POSITION -> ThemeConstants.TIME_RANGE_LONG;
            case SHORT_POSITION -> ThemeConstants.TIME_RANGE_SHORT;
            case NEUTRAL -> ThemeConstants.TIME_RANGE_NEUTRAL;
            case CAUTION -> ThemeConstants.TIME_RANGE_CAUTION;
            case WIN -> ThemeConstants.TIME_RANGE_WIN;
            case LOSS -> ThemeConstants.TIME_RANGE_LOSS;
            case OPEN -> ThemeConstants.TIME_RANGE_OPEN;
        };
    }

    // Glyph extents in data space. Computed once per render from the series'
    // bar period (so the glyph width tracks candle width regardless of zoom
    // or device) and the series' price/time span scaled by the chart's pixel
    // aspect (so the glyph reads as roughly square in pixel space — no more
    // horizontal slivers on zoomed-in or narrow charts).
    private record GlyphExtents(double dx, double dy) {}

    private static GlyphExtents computeGlyphExtents(ChartSpec spec, boolean ordinal) {
        Series series = spec.series();
        long firstT, lastT;
        double priceMin = Double.POSITIVE_INFINITY;
        double priceMax = Double.NEGATIVE_INFINITY;
        long minIntervalMillis = Long.MAX_VALUE;
        int barCount;
        if (series instanceof OHLCSeries ohlc) {
            var bars = ohlc.bars();
            barCount = bars.size();
            firstT = bars.get(0).time().toEpochMilli();
            lastT = bars.get(barCount - 1).time().toEpochMilli();
            long prevT = firstT;
            for (int i = 0; i < barCount; i++) {
                OHLCBar bar = bars.get(i);
                priceMin = Math.min(priceMin, bar.low().doubleValue());
                priceMax = Math.max(priceMax, bar.high().doubleValue());
                if (i > 0) {
                    long t = bar.time().toEpochMilli();
                    minIntervalMillis = Math.min(minIntervalMillis, t - prevT);
                    prevT = t;
                }
            }
        } else {
            HASeries ha = (HASeries) series;
            var bars = ha.bars();
            barCount = bars.size();
            firstT = bars.get(0).time().toEpochMilli();
            lastT = bars.get(barCount - 1).time().toEpochMilli();
            long prevT = firstT;
            for (int i = 0; i < barCount; i++) {
                HABar bar = bars.get(i);
                priceMin = Math.min(priceMin, bar.haLow().doubleValue());
                priceMax = Math.max(priceMax, bar.haHigh().doubleValue());
                if (i > 0) {
                    long t = bar.time().toEpochMilli();
                    minIntervalMillis = Math.min(minIntervalMillis, t - prevT);
                    prevT = t;
                }
            }
        }

        double priceSpan = Math.max(priceMax - priceMin, 1e-9);
        int widthPx = spec.layout().widthPx();
        int heightPx = spec.layout().heightPx();

        // Single-bar series (legal under V2) provides no period or aspect
        // information. Fall back to fixed extents — there is nothing
        // meaningful to scale to. (In ordinal space the x fallback is one
        // bar-width fraction; in time space it is a fixed 12h.)
        if (barCount < 2) {
            double fallbackDx = ordinal ? 0.4 : 12.0 * 3_600_000;
            double fallbackDy = Math.max(priceMin * 0.005, 1e-3);
            return new GlyphExtents(fallbackDx, fallbackDy);
        }

        // dy chosen so the glyph appears roughly square in pixel space:
        //   glyph_width_px  = (2·dx / domainSpan) · widthPx
        //   glyph_height_px = (2·dy / priceSpan)  · heightPx
        // setting them equal: dy = dx · (priceSpan / domainSpan) · (widthPx / heightPx).
        if (ordinal) {
            // Bars are equally spaced one index-unit apart, so the smallest
            // interval is 1.0 and the domain span is (barCount - 1).
            double dx = 0.4;
            double dy = dx * (priceSpan / (double) (barCount - 1))
                    * ((double) widthPx / (double) heightPx);
            return new GlyphExtents(dx, dy);
        }

        // Time axis: dx = 40% of the *smallest* bar interval (matches
        // CandlestickRenderer.WIDTHMETHOD_SMALLEST so the glyph tracks candle
        // width on irregular timelines).
        long timeSpan = lastT - firstT;
        double dx = 0.4 * minIntervalMillis;
        double dy = dx * (priceSpan / (double) timeSpan)
                * ((double) widthPx / (double) heightPx);
        return new GlyphExtents(dx, dy);
    }

    private static Shape glyphShape(GlyphStyle style, double x, double y, double dx, double dy) {
        // XYShapeAnnotation interprets the Shape's coordinates in the plot's
        // domain (time-millis) and range (price), so coordinates must be
        // doubles — Path2D.Double, not the int-based Polygon.
        //
        // ARROW_* glyphs: a thin shaft topped (or bottomed) with a wider
        // chevron. Geometry chosen for clear arrow-like silhouette while
        // preserving equal filled area with the matching triangle:
        //   chevron area = 0.5 · 2·chevronHalf · dy = chevronHalf · dy
        //   shaft   area = 2·shaft · dy
        //   total        = (chevronHalf + 2·shaft) · dy = 2·dx·dy
        // with chevronHalf = 1.5·dx and shaft = dx/4, total = 2·dx·dy,
        // exactly the triangle's filled area (0.5 · 2dx · 2dy).
        double chevronHalf = 1.5 * dx;
        double shaft = dx / 4.0;
        Path2D.Double p = new Path2D.Double();
        switch (style) {
            case UP_TRIANGLE -> {
                p.moveTo(x,      y + dy);
                p.lineTo(x - dx, y - dy);
                p.lineTo(x + dx, y - dy);
            }
            case DOWN_TRIANGLE -> {
                p.moveTo(x,      y - dy);
                p.lineTo(x - dx, y + dy);
                p.lineTo(x + dx, y + dy);
            }
            case ARROW_UP -> {
                p.moveTo(x - shaft,       y - dy);
                p.lineTo(x + shaft,       y - dy);
                p.lineTo(x + shaft,       y);
                p.lineTo(x + chevronHalf, y);
                p.lineTo(x,               y + dy);
                p.lineTo(x - chevronHalf, y);
                p.lineTo(x - shaft,       y);
            }
            case ARROW_DOWN -> {
                p.moveTo(x - shaft,       y + dy);
                p.lineTo(x + shaft,       y + dy);
                p.lineTo(x + shaft,       y);
                p.lineTo(x + chevronHalf, y);
                p.lineTo(x,               y - dy);
                p.lineTo(x - chevronHalf, y);
                p.lineTo(x - shaft,       y);
            }
        }
        p.closePath();
        return p;
    }

    private static ValueMarker levelMarker(Annotation.HorizontalLevel level) {
        Color color = level.fillColor()
                .map(JFreeChartRenderer::horizontalLevelColor)
                .orElse(ThemeConstants.HORIZONTAL_LEVEL);
        ValueMarker marker = marker(level.price().doubleValue(), color);
        marker.setStroke(switch (level.style()) {
            case SOLID -> ThemeConstants.STROKE_DEFAULT;
            case DASHED -> ThemeConstants.STROKE_HORIZONTAL_LEVEL_DASHED;
            case DOTTED -> ThemeConstants.STROKE_HORIZONTAL_LEVEL_DOTTED;
        });
        return marker;
    }

    static Color horizontalLevelColor(FillColor fillColor) {
        return switch (fillColor) {
            case WIN -> ThemeConstants.HORIZONTAL_LEVEL_WIN;
            case LOSS -> ThemeConstants.HORIZONTAL_LEVEL_LOSS;
            case OPEN -> ThemeConstants.HORIZONTAL_LEVEL_OPEN;
            case LONG_POSITION -> ThemeConstants.HORIZONTAL_LEVEL_LONG_POSITION;
            case SHORT_POSITION -> ThemeConstants.HORIZONTAL_LEVEL_SHORT_POSITION;
            case NEUTRAL -> ThemeConstants.HORIZONTAL_LEVEL_NEUTRAL;
            case CAUTION -> ThemeConstants.HORIZONTAL_LEVEL_CAUTION;
        };
    }

    private static ValueMarker marker(double value, Color color) {
        ValueMarker marker = new ValueMarker(value);
        marker.setPaint(color);
        marker.setStroke(ThemeConstants.STROKE_DEFAULT);
        return marker;
    }

    private static List<BigDecimal> pivotLevels(Annotation.PivotPointLevels pivots) {
        return new ArrayList<>(
                PivotPoints.levels(pivots.previousPeriodBar(), pivots.variant()).present().values());
    }

    // --- price extraction ---

    private static List<Instant> times(Series series) {
        return switch (series) {
            case OHLCSeries o -> o.bars().stream().map(OHLCBar::time).toList();
            case HASeries h -> h.bars().stream().map(HABar::time).toList();
        };
    }

    /** Domain x-coordinate for an instant: bar index in ORDINAL mode, epoch-millis in TIME mode. */
    private static double domainX(Instant t, boolean ordinal, List<Instant> times) {
        return ordinal ? ordinalX(times, t) : (double) t.toEpochMilli();
    }

    /**
     * Maps an instant to a (possibly fractional) bar index. A bar's own time maps
     * to its exact integer index; an instant strictly between two bars (e.g. a
     * mid-bar trade exit) interpolates linearly by time; instants at or beyond the
     * ends clamp to the first / last index.
     */
    private static double ordinalX(List<Instant> times, Instant t) {
        int n = times.size();
        if (n == 0 || t.compareTo(times.get(0)) <= 0) {
            return 0.0;
        }
        if (t.compareTo(times.get(n - 1)) >= 0) {
            return n - 1;
        }
        int lo = 0;
        int hi = n - 1;
        while (lo + 1 < hi) {
            int mid = (lo + hi) >>> 1;
            if (times.get(mid).compareTo(t) <= 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        Instant a = times.get(lo);
        if (t.equals(a)) {
            return lo;
        }
        Instant b = times.get(lo + 1);
        double frac = (double) (t.toEpochMilli() - a.toEpochMilli())
                / (double) (b.toEpochMilli() - a.toEpochMilli());
        return lo + frac;
    }

    /**
     * Returns {@code {high, low}} for the bar at {@code time} in {@code series}.
     * V16 guarantees the bar exists by the time the renderer runs; this helper
     * still throws {@link IllegalStateException} if invoked outside that guard
     * (defensive, should never fire in practice).
     */
    private static double[] barHighLow(Series series, Instant time) {
        switch (series) {
            case OHLCSeries o -> {
                for (OHLCBar bar : o.bars()) {
                    if (bar.time().equals(time)) {
                        return new double[] {
                                bar.high().doubleValue(),
                                bar.low().doubleValue()
                        };
                    }
                }
            }
            case HASeries h -> {
                for (HABar bar : h.bars()) {
                    if (bar.time().equals(time)) {
                        return new double[] {
                                bar.haHigh().doubleValue(),
                                bar.haLow().doubleValue()
                        };
                    }
                }
            }
        }
        throw new IllegalStateException("bar not found at " + time
                + " (V16 should have rejected this earlier)");
    }

    private static List<BigDecimal> prices(Series series, PriceSource source) {
        return switch (series) {
            case OHLCSeries o -> o.bars().stream().map(b -> ohlcPrice(b, source)).toList();
            case HASeries h -> h.bars().stream().map(b -> haPrice(b, source)).toList();
        };
    }

    private static List<BigDecimal> highs(Series series) {
        return switch (series) {
            case OHLCSeries o -> o.bars().stream().map(OHLCBar::high).toList();
            case HASeries h -> h.bars().stream().map(HABar::haHigh).toList();
        };
    }

    private static List<BigDecimal> lows(Series series) {
        return switch (series) {
            case OHLCSeries o -> o.bars().stream().map(OHLCBar::low).toList();
            case HASeries h -> h.bars().stream().map(HABar::haLow).toList();
        };
    }

    private static List<BigDecimal> closes(Series series) {
        return switch (series) {
            case OHLCSeries o -> o.bars().stream().map(OHLCBar::close).toList();
            case HASeries h -> h.bars().stream().map(HABar::haClose).toList();
        };
    }

    private static BigDecimal ohlcPrice(OHLCBar bar, PriceSource source) {
        return switch (source) {
            case OPEN -> bar.open();
            case HIGH -> bar.high();
            case LOW -> bar.low();
            case CLOSE -> bar.close();
            case HA_OPEN, HA_HIGH, HA_LOW, HA_CLOSE -> bar.close();
        };
    }

    private static BigDecimal haPrice(HABar bar, PriceSource source) {
        return switch (source) {
            case HA_OPEN -> bar.haOpen();
            case HA_HIGH -> bar.haHigh();
            case HA_LOW -> bar.haLow();
            case HA_CLOSE -> bar.haClose();
            case OPEN, HIGH, LOW, CLOSE -> bar.haClose();
        };
    }

    // --- styling ---

    private void styleAxis(ValueAxis axis) {
        axis.setLabelFont(baseFont.deriveFont(12f));
        axis.setTickLabelFont(baseFont.deriveFont(10f));
        axis.setLabelPaint(ThemeConstants.TEXT);
        axis.setTickLabelPaint(ThemeConstants.TEXT);
        axis.setAxisLinePaint(ThemeConstants.AXIS);
        axis.setTickMarkPaint(ThemeConstants.AXIS);
    }

    private static void stylePlot(XYPlot plot) {
        plot.setBackgroundPaint(ThemeConstants.BACKGROUND);
        plot.setDomainGridlinePaint(ThemeConstants.GRID);
        plot.setRangeGridlinePaint(ThemeConstants.GRID);
        plot.setOutlinePaint(ThemeConstants.AXIS);
    }

    // --- encoding ---

    private static String contentType(ImageFormat format) {
        return format == ImageFormat.JPEG ? "image/jpeg" : "image/png";
    }

    private static byte[] encode(BufferedImage image, ImageFormat format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (format == ImageFormat.JPEG) {
                ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.9f);
                try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
                    writer.setOutput(stream);
                    writer.write(null, new IIOImage(image, null, null), param);
                } finally {
                    writer.dispose();
                }
            } else {
                ImageIO.write(image, "png", out);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    // --- font loading ---

    private static Font defaultFont() throws DriverInternalException {
        Font font = cachedFont;
        if (font == null) {
            synchronized (JFreeChartRenderer.class) {
                font = cachedFont;
                if (font == null) {
                    font = loadFont(DEFAULT_FONT_RESOURCE);
                    cachedFont = font;
                }
            }
        }
        return font;
    }

    private static Font loadFont(String resource) throws DriverInternalException {
        try (InputStream stream = JFreeChartRenderer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("bundled font resource not found: " + resource);
            }
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | FontFormatException e) {
            throw new DriverInternalException(e);
        }
    }

    // --- ORDINAL (gap-collapsing) axis support ---

    /**
     * Domain axis for ORDINAL mode. Bars sit at integer positions {@code 0..N-1},
     * so non-trading gaps take no horizontal space. Ticks are placed at UTC
     * calendar-day boundaries and labelled with the date; the domain gridlines
     * that fall on those ticks double as faint day / session separators.
     */
    static final class OrdinalTimeAxis extends NumberAxis {

        private static final int MAX_TICKS = 12;

        private final List<NumberTick> dayTicks;

        OrdinalTimeAxis(List<Instant> times) {
            super(null);
            int n = times.size();
            setAutoRange(false);
            setRange(-0.5, Math.max(0.5, n - 0.5));
            setLowerMargin(0.0);
            setUpperMargin(0.0);
            this.dayTicks = buildDayTicks(times);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public List refreshTicks(java.awt.Graphics2D g2, AxisState state,
                                 java.awt.geom.Rectangle2D dataArea, RectangleEdge edge) {
            return dayTicks;
        }

        private static List<NumberTick> buildDayTicks(List<Instant> times) {
            // Locale.US is pinned explicitly (and the formatter is built per call,
            // not captured at class-load) so the month abbreviation — and thus the
            // rendered tick text and image bytes — is independent of the JVM default
            // locale, per the byte-identical-rendering contract.
            DateTimeFormatter dayLabel = DateTimeFormatter.ofPattern("d-MMM", java.util.Locale.US)
                    .withZone(ZoneOffset.UTC);
            List<Integer> boundaries = new ArrayList<>();
            LocalDate prev = null;
            for (int i = 0; i < times.size(); i++) {
                LocalDate day = times.get(i).atZone(ZoneOffset.UTC).toLocalDate();
                if (!day.equals(prev)) {
                    boundaries.add(i);
                    prev = day;
                }
            }
            // Thin to at most MAX_TICKS labels so a long daily series does not crowd.
            int step = Math.max(1, (boundaries.size() + MAX_TICKS - 1) / MAX_TICKS);
            List<NumberTick> ticks = new ArrayList<>();
            for (int k = 0; k < boundaries.size(); k += step) {
                int idx = boundaries.get(k);
                ticks.add(new NumberTick(Double.valueOf(idx), dayLabel.format(times.get(idx)),
                        TextAnchor.TOP_CENTER, TextAnchor.CENTER, 0.0));
            }
            return ticks;
        }
    }

    /**
     * Numeric-x OHLC dataset for ORDINAL mode: each bar's x-value is its integer
     * index, so {@link CandlestickRenderer} draws equally-spaced candles with no
     * regard to wall-clock gaps. Range bounds derive from high/low as usual.
     */
    static final class OrdinalOHLCDataset extends AbstractXYDataset implements OHLCDataset {

        private final int n;
        private final double[] open;
        private final double[] high;
        private final double[] low;
        private final double[] close;

        OrdinalOHLCDataset(Series series) {
            int count = (series instanceof OHLCSeries o) ? o.bars().size()
                    : ((HASeries) series).bars().size();
            this.n = count;
            this.open = new double[count];
            this.high = new double[count];
            this.low = new double[count];
            this.close = new double[count];
            if (series instanceof OHLCSeries o) {
                List<OHLCBar> bars = o.bars();
                for (int i = 0; i < count; i++) {
                    OHLCBar b = bars.get(i);
                    open[i] = b.open().doubleValue();
                    high[i] = b.high().doubleValue();
                    low[i] = b.low().doubleValue();
                    close[i] = b.close().doubleValue();
                }
            } else {
                List<HABar> bars = ((HASeries) series).bars();
                for (int i = 0; i < count; i++) {
                    HABar b = bars.get(i);
                    open[i] = b.haOpen().doubleValue();
                    high[i] = b.haHigh().doubleValue();
                    low[i] = b.haLow().doubleValue();
                    close[i] = b.haClose().doubleValue();
                }
            }
        }

        @Override
        public int getSeriesCount() {
            return 1;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Comparable getSeriesKey(int series) {
            return "price";
        }

        @Override
        public int getItemCount(int series) {
            return n;
        }

        @Override
        public Number getX(int series, int item) {
            return Double.valueOf(item);
        }

        @Override
        public double getXValue(int series, int item) {
            return item;
        }

        @Override
        public Number getY(int series, int item) {
            return Double.valueOf(close[item]);
        }

        @Override
        public double getYValue(int series, int item) {
            return close[item];
        }

        @Override
        public Number getOpen(int series, int item) {
            return Double.valueOf(open[item]);
        }

        @Override
        public double getOpenValue(int series, int item) {
            return open[item];
        }

        @Override
        public Number getHigh(int series, int item) {
            return Double.valueOf(high[item]);
        }

        @Override
        public double getHighValue(int series, int item) {
            return high[item];
        }

        @Override
        public Number getLow(int series, int item) {
            return Double.valueOf(low[item]);
        }

        @Override
        public double getLowValue(int series, int item) {
            return low[item];
        }

        @Override
        public Number getClose(int series, int item) {
            return Double.valueOf(close[item]);
        }

        @Override
        public double getCloseValue(int series, int item) {
            return close[item];
        }

        @Override
        public Number getVolume(int series, int item) {
            return null;
        }

        @Override
        public double getVolumeValue(int series, int item) {
            return Double.NaN;
        }
    }
}
