package org.hatrack.heerwisch.jfreechart;

import org.hatrack.commons.HABar;
import org.hatrack.commons.HASeries;
import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.commons.Series;
import org.hatrack.heerwisch.api.error.ChartRenderException;
import org.hatrack.heerwisch.api.error.DriverInternalException;
import org.hatrack.heerwisch.api.error.UnsupportedFeatureException;
import org.hatrack.heerwisch.api.port.ChartRenderer;
import org.hatrack.heerwisch.api.spec.Annotation;
import org.hatrack.heerwisch.api.spec.ChartImage;
import org.hatrack.heerwisch.api.spec.ChartSpec;
import org.hatrack.heerwisch.api.spec.FillColor;
import org.hatrack.heerwisch.api.spec.GlyphStyle;
import org.hatrack.heerwisch.api.spec.ImageFormat;
import org.hatrack.heerwisch.api.spec.Indicator;
import org.hatrack.heerwisch.api.spec.IndicatorPlacement;
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
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.ohlc.OHLCSeriesCollection;

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
            return new ChartImage(bytes, contentType(layout.format()), width, height);
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

    private JFreeChart buildChart(ChartSpec spec) {
        DateAxis domainAxis = new DateAxis();
        styleAxis(domainAxis);
        CombinedDomainXYPlot combined = new CombinedDomainXYPlot(domainAxis);
        combined.setGap(8.0);
        combined.setBackgroundPaint(ThemeConstants.BACKGROUND);

        combined.add(buildMainPlot(spec), 60);

        List<Pane> subplotPanes = referencedSubplotPanes(spec);
        if (!subplotPanes.isEmpty()) {
            int weight = Math.max(1, 40 / subplotPanes.size());
            for (Pane pane : subplotPanes) {
                combined.add(buildSubplot(spec, pane), weight);
            }
        }

        JFreeChart chart = new JFreeChart(null, baseFont.deriveFont(Font.BOLD, 14f),
                combined, false);
        chart.setBackgroundPaint(ThemeConstants.BACKGROUND);
        return chart;
    }

    private XYPlot buildMainPlot(ChartSpec spec) {
        CandlestickRenderer candleRenderer = new CandlestickRenderer();
        candleRenderer.setUpPaint(ThemeConstants.BULLISH_CANDLE);
        candleRenderer.setDownPaint(ThemeConstants.BEARISH_CANDLE);
        candleRenderer.setDrawVolume(false);
        candleRenderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);

        NumberAxis rangeAxis = new NumberAxis("Price");
        styleAxis(rangeAxis);
        rangeAxis.setAutoRangeIncludesZero(false);

        XYPlot plot = new XYPlot(buildPriceDataset(spec.series()), null, rangeAxis, candleRenderer);
        stylePlot(plot);

        int datasetIndex = 1;
        for (IndicatorPlacement placement : spec.indicators()) {
            if (placement.pane() == Pane.MAIN) {
                datasetIndex = addIndicator(plot, datasetIndex, spec.series(), placement.indicator());
            }
        }
        addAnnotations(plot, spec);
        return plot;
    }

    private XYPlot buildSubplot(ChartSpec spec, Pane pane) {
        NumberAxis rangeAxis = new NumberAxis(pane.name());
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
        for (IndicatorPlacement placement : spec.indicators()) {
            if (placement.pane() == pane) {
                datasetIndex = addIndicator(plot, datasetIndex, spec.series(), placement.indicator());
            }
        }
        return plot;
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

    private static OHLCSeriesCollection buildPriceDataset(Series series) {
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

    private int addIndicator(XYPlot plot, int index, Series series, Indicator indicator) {
        List<Instant> times = times(series);
        switch (indicator) {
            case Indicator.SMA sma -> {
                return addLine(plot, index, "SMA", times,
                        Indicators.sma(prices(series, sma.priceSource()), sma.period()),
                        ThemeConstants.SMA_LINE);
            }
            case Indicator.EMA ema -> {
                return addLine(plot, index, "EMA", times,
                        Indicators.ema(prices(series, ema.priceSource()), ema.period()),
                        ThemeConstants.EMA_LINE);
            }
            case Indicator.BollingerBands bb -> {
                BollingerBands bands = Indicators.bollinger(
                        prices(series, bb.priceSource()), bb.period(), bb.stdDevMultiplier());
                int next = addLine(plot, index, "BB upper", times, bands.upper(), ThemeConstants.BB_BAND);
                next = addLine(plot, next, "BB middle", times, bands.middle(), ThemeConstants.BB_BAND);
                return addLine(plot, next, "BB lower", times, bands.lower(), ThemeConstants.BB_BAND);
            }
            case Indicator.MACD macd -> {
                MacdResult lines = Indicators.macd(prices(series, macd.priceSource()),
                        macd.fastPeriod(), macd.slowPeriod(), macd.signalPeriod());
                int next = addLine(plot, index, "MACD", times, lines.macdLine(), ThemeConstants.MACD_LINE);
                return addLine(plot, next, "Signal", times, lines.signalLine(), ThemeConstants.MACD_SIGNAL);
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
                        ThemeConstants.RSI_LINE);
            }
            case Indicator.ADX adx -> {
                return addLine(plot, index, "ADX", times,
                        Indicators.adx(highs(series), lows(series), closes(series), adx.period()),
                        ThemeConstants.ADX_LINE);
            }
            case Indicator.Stochastic stoch -> {
                StochasticResult lines = Indicators.stochastic(highs(series), lows(series),
                        closes(series), stoch.kPeriod(), stoch.dPeriod(), stoch.smoothing());
                int next = addLine(plot, index, "%K", times, lines.percentK(), ThemeConstants.STOCHASTIC_K);
                return addLine(plot, next, "%D", times, lines.percentD(), ThemeConstants.STOCHASTIC_D);
            }
            case Indicator.ATR atr -> {
                return addLine(plot, index, "ATR", times,
                        Indicators.atr(highs(series), lows(series), closes(series), atr.period()),
                        ThemeConstants.ATR_LINE);
            }
            case Indicator.VolumePane ignored -> {
                return addVolumeBars(plot, index, series);
            }
        }
    }

    private static int addLine(XYPlot plot, int index, String name, List<Instant> times,
                               BigDecimal[] values, Color color) {
        TimeSeries series = new TimeSeries(name);
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                series.add(new FixedMillisecond(times.get(i).toEpochMilli()), values[i].doubleValue());
            }
        }
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(series);
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, color);
        renderer.setSeriesStroke(0, ThemeConstants.STROKE_INDICATOR);
        plot.setDataset(index, dataset);
        plot.setRenderer(index, renderer);
        return index + 1;
    }

    private static int addVolumeBars(XYPlot plot, int index, Series series) {
        TimeSeries volume = new TimeSeries("Volume");
        if (series instanceof OHLCSeries ohlc) {
            for (OHLCBar bar : ohlc.bars()) {
                volume.add(new FixedMillisecond(bar.time().toEpochMilli()),
                        bar.volume().map(BigDecimal::doubleValue).orElse(0.0));
            }
        }
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(volume);
        XYBarRenderer renderer = new XYBarRenderer();
        renderer.setShadowVisible(false);
        renderer.setSeriesPaint(0, ThemeConstants.VOLUME_BAR_UP);
        plot.setDataset(index, dataset);
        plot.setRenderer(index, renderer);
        return index + 1;
    }

    // --- annotations (MAIN pane only) ---

    private void addAnnotations(XYPlot plot, ChartSpec spec) {
        GlyphExtents glyphExtents = computeGlyphExtents(spec);
        for (Annotation annotation : spec.annotations()) {
            switch (annotation) {
                case Annotation.BarHighlight highlight -> {
                    XYTextAnnotation text = new XYTextAnnotation(highlight.label(),
                            highlight.time().toEpochMilli(), highlight.price().doubleValue());
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
                            entryExit.time().toEpochMilli(),
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
                            entryExit.time().toEpochMilli(),
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
                            range.startTime().toEpochMilli(),
                            range.endTime().toEpochMilli(),
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

    private static GlyphExtents computeGlyphExtents(ChartSpec spec) {
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

        // Single-bar series (legal under V2) provides no period or aspect
        // information. Fall back to the pre-fix fixed extents — there is
        // nothing meaningful to scale to.
        if (barCount < 2) {
            double fallbackDx = 12.0 * 3_600_000;
            double fallbackDy = Math.max(priceMin * 0.005, 1e-3);
            return new GlyphExtents(fallbackDx, fallbackDy);
        }

        long timeSpan = lastT - firstT;
        double priceSpan = Math.max(priceMax - priceMin, 1e-9);

        LayoutSpec layout = spec.layout();
        int widthPx = (layout instanceof LayoutSpec.AutoLayoutSpec auto) ? auto.widthPx()
                : ((LayoutSpec.ExplicitLayoutSpec) layout).widthPx();
        int heightPx = (layout instanceof LayoutSpec.AutoLayoutSpec auto) ? auto.heightPx()
                : ((LayoutSpec.ExplicitLayoutSpec) layout).heightPx();

        // dx = 40% of the *smallest* bar interval. JFreeChart's
        // CandlestickRenderer uses WIDTHMETHOD_SMALLEST, so candle width
        // tracks the minimum interval — we match it. On irregular timelines
        // (e.g. daily data with weekend gaps) the average interval would
        // overstate the candle width and the glyph would extend past
        // adjacent candles.
        double dx = 0.4 * minIntervalMillis;

        // dy chosen so glyph appears roughly square in pixel space:
        //   glyph_width_px  = (2·dx / timeSpan) · widthPx
        //   glyph_height_px = (2·dy / priceSpan) · heightPx
        // Setting them equal and solving for dy:
        //   dy = dx · (priceSpan / timeSpan) · (widthPx / heightPx)
        // This adapts to any chart aspect, any zoom, any device.
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
        ValueMarker marker = marker(level.price().doubleValue(), ThemeConstants.HORIZONTAL_LEVEL);
        marker.setStroke(switch (level.style()) {
            case SOLID -> ThemeConstants.STROKE_DEFAULT;
            case DASHED -> ThemeConstants.STROKE_HORIZONTAL_LEVEL_DASHED;
            case DOTTED -> ThemeConstants.STROKE_HORIZONTAL_LEVEL_DOTTED;
        });
        return marker;
    }

    private static ValueMarker marker(double value, Color color) {
        ValueMarker marker = new ValueMarker(value);
        marker.setPaint(color);
        marker.setStroke(ThemeConstants.STROKE_DEFAULT);
        return marker;
    }

    private static List<BigDecimal> pivotLevels(Annotation.PivotPointLevels pivots) {
        OHLCBar bar = pivots.previousPeriodBar();
        BigDecimal high = bar.high();
        BigDecimal low = bar.low();
        BigDecimal close = bar.close();
        BigDecimal range = high.subtract(low);
        List<BigDecimal> levels = new ArrayList<>();
        switch (pivots.variant()) {
            case STANDARD -> {
                BigDecimal p = high.add(low).add(close).divide(new BigDecimal(3),
                        java.math.MathContext.DECIMAL64);
                levels.add(p);
                levels.add(p.multiply(new BigDecimal(2)).subtract(low));
                levels.add(p.multiply(new BigDecimal(2)).subtract(high));
                levels.add(p.add(range));
                levels.add(p.subtract(range));
            }
            case WOODIE -> {
                BigDecimal p = high.add(low).add(close).add(close).divide(new BigDecimal(4),
                        java.math.MathContext.DECIMAL64);
                levels.add(p);
                levels.add(p.multiply(new BigDecimal(2)).subtract(low));
                levels.add(p.multiply(new BigDecimal(2)).subtract(high));
            }
            case CAMARILLA -> {
                BigDecimal factor = new BigDecimal("1.1");
                for (int divisor : new int[] {12, 6, 4, 2}) {
                    BigDecimal offset = range.multiply(factor)
                            .divide(new BigDecimal(divisor), java.math.MathContext.DECIMAL64);
                    levels.add(close.add(offset));
                    levels.add(close.subtract(offset));
                }
            }
        }
        return levels;
    }

    // --- price extraction ---

    private static List<Instant> times(Series series) {
        return switch (series) {
            case OHLCSeries o -> o.bars().stream().map(OHLCBar::time).toList();
            case HASeries h -> h.bars().stream().map(HABar::time).toList();
        };
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
}
