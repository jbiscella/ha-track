package org.hatrack.heerwisch.api.spec;

import org.hatrack.commons.HABar;
import org.hatrack.commons.HASeries;
import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCInvariantViolationException;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.commons.Series;
import org.hatrack.heerwisch.api.error.InvalidChartSpecException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Fluent builder for {@link ChartSpec}. {@link #build()} validates eagerly
 * (rules V1-V11) and throws {@link InvalidChartSpecException} on the first
 * violation found.
 */
public final class ChartSpecBuilder {

    private static final BigDecimal LAYOUT_TOLERANCE = new BigDecimal("0.000001");

    private Series series;
    private LayoutSpec layout;
    private final List<IndicatorPlacement> placements = new ArrayList<>();
    private final List<Annotation> annotations = new ArrayList<>();

    public ChartSpecBuilder withSeries(Series series) {
        this.series = series;
        return this;
    }

    public ChartSpecBuilder withLayout(LayoutSpec layout) {
        this.layout = layout;
        return this;
    }

    public ChartSpecBuilder addIndicator(Indicator indicator) {
        Objects.requireNonNull(indicator, "indicator");
        placements.add(new IndicatorPlacement(indicator, defaultPaneFor(indicator)));
        return this;
    }

    public ChartSpecBuilder addIndicator(Indicator indicator, Pane pane) {
        placements.add(new IndicatorPlacement(indicator, pane));
        return this;
    }

    /** Appends an indicator at an explicit pane with a label override. */
    public ChartSpecBuilder addIndicator(Indicator indicator, Pane pane, String label) {
        Objects.requireNonNull(indicator, "indicator");
        Objects.requireNonNull(pane, "pane");
        Objects.requireNonNull(label, "label");
        placements.add(new IndicatorPlacement(indicator, pane, Optional.of(label)));
        return this;
    }

    public ChartSpecBuilder addAnnotation(Annotation annotation) {
        annotations.add(Objects.requireNonNull(annotation, "annotation"));
        return this;
    }

    /** The default pane an indicator is placed at when none is specified. */
    public static Pane defaultPaneFor(Indicator indicator) {
        return indicator.defaultPane();
    }

    public ChartSpec build() throws InvalidChartSpecException {
        if (series == null) {
            throw new InvalidChartSpecException("V1", null);
        }
        List<Instant> times = barTimes(series);
        if (times.isEmpty()) {
            throw new InvalidChartSpecException("V2", null);
        }
        for (int i = 1; i < times.size(); i++) {
            int cmp = times.get(i).compareTo(times.get(i - 1));
            if (cmp < 0) {
                throw new InvalidChartSpecException("V3", times.get(i));
            }
            if (cmp == 0) {
                throw new InvalidChartSpecException("V4", times.get(i));
            }
        }
        if (series instanceof OHLCSeries ohlcSeries) {
            List<OHLCBar> bars = ohlcSeries.bars();
            for (int i = 0; i < bars.size(); i++) {
                OHLCBar bar = bars.get(i);
                try {
                    bar.validateInvariants();
                } catch (OHLCInvariantViolationException e) {
                    throw new InvalidChartSpecException("V13",
                            "bar " + i + " at " + bar.time() + ": " + e.violatedInvariant());
                }
            }
        }
        LayoutSpec effectiveLayout = layout != null ? layout : LayoutSpec.defaults();
        boolean haSeries = series instanceof HASeries;
        for (IndicatorPlacement placement : placements) {
            Indicator indicator = placement.indicator();
            checkPriceSourceCompatibility(indicator, haSeries);
            if (times.size() < indicator.minBars()) {
                throw new InvalidChartSpecException("V6", indicator);
            }
            if (indicator instanceof Indicator.VolumePane && !hasVolumeOnEveryBar(series)) {
                throw new InvalidChartSpecException("V9", indicator);
            }
            if (indicator instanceof Indicator.RSI rsi) {
                if (rsi.overbought().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
                    throw new InvalidChartSpecException("V19", rsi.overbought());
                }
                if (rsi.oversold().signum() < 0) {
                    throw new InvalidChartSpecException("V20", rsi.oversold());
                }
                if (rsi.oversold().compareTo(rsi.overbought()) >= 0) {
                    throw new InvalidChartSpecException("V21", rsi);
                }
            }
        }
        Set<Instant> barTimes = new HashSet<>(times);
        for (Annotation annotation : annotations) {
            switch (annotation) {
                case Annotation.BarHighlight highlight -> {
                    if (!barTimes.contains(highlight.time())) {
                        throw new InvalidChartSpecException("V7", highlight.time());
                    }
                }
                case Annotation.HorizontalLevel level -> {
                    if (level.price().signum() <= 0) {
                        throw new InvalidChartSpecException("V8", level.price());
                    }
                }
                case Annotation.FibRetracement fib -> {
                    if (fib.swingHigh().signum() <= 0 || fib.swingLow().signum() <= 0) {
                        throw new InvalidChartSpecException("V8", fib);
                    }
                }
                case Annotation.PivotPointLevels ignored -> {
                    // no eager validation
                }
                case Annotation.EntryExitMarker marker -> {
                    if (!barTimes.contains(marker.time())) {
                        throw new InvalidChartSpecException("V16", marker.time());
                    }
                }
                case Annotation.EntryExitMarkerAuto marker -> {
                    if (!barTimes.contains(marker.time())) {
                        throw new InvalidChartSpecException("V16", marker.time());
                    }
                }
                case Annotation.TimeRangeHighlight range -> {
                    if (!range.startTime().isBefore(range.endTime())) {
                        throw new InvalidChartSpecException("V17", range);
                    }
                    Instant firstBar = times.get(0);
                    Instant lastBar = times.get(times.size() - 1);
                    if (range.endTime().isBefore(firstBar) || range.startTime().isAfter(lastBar)) {
                        throw new InvalidChartSpecException("V17", range);
                    }
                    BigDecimal opacity = range.opacity();
                    if (opacity.signum() < 0 || opacity.compareTo(BigDecimal.ONE) > 0) {
                        throw new InvalidChartSpecException("V18", opacity);
                    }
                }
            }
        }
        if (effectiveLayout instanceof LayoutSpec.ExplicitLayoutSpec explicit) {
            checkExplicitLayout(explicit);
        }
        return new ChartSpec(series, placements, annotations, effectiveLayout);
    }

    private static void checkPriceSourceCompatibility(Indicator indicator, boolean haSeries)
            throws InvalidChartSpecException {
        PriceSource source = switch (indicator) {
            case Indicator.SMA sma -> sma.priceSource();
            case Indicator.EMA ema -> ema.priceSource();
            case Indicator.RollingMax max -> max.priceSource();
            case Indicator.RollingMin min -> min.priceSource();
            case Indicator.BollingerBands bb -> bb.priceSource();
            case Indicator.StdDev sd -> sd.priceSource();
            case Indicator.MACD macd -> macd.priceSource();
            case Indicator.RSI rsi -> rsi.priceSource();
            case Indicator.ADX ignored -> null;
            case Indicator.Stochastic ignored -> null;
            case Indicator.ATR ignored -> null;
            case Indicator.VolumePane ignored -> null;
        };
        if (source != null && isHaSource(source) != haSeries) {
            throw new InvalidChartSpecException("V5", indicator);
        }
    }

    private void checkExplicitLayout(LayoutSpec.ExplicitLayoutSpec layout)
            throws InvalidChartSpecException {
        BigDecimal sum = layout.mainPaneHeight();
        for (BigDecimal height : layout.subplotHeights().values()) {
            sum = sum.add(height);
        }
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(LAYOUT_TOLERANCE) > 0) {
            throw new InvalidChartSpecException("V10", sum);
        }
        Set<Pane> usedPanes = new HashSet<>();
        for (IndicatorPlacement placement : placements) {
            usedPanes.add(placement.pane());
        }
        for (Pane pane : layout.subplotHeights().keySet()) {
            if (!usedPanes.contains(pane)) {
                throw new InvalidChartSpecException("V11", pane);
            }
        }
        for (IndicatorPlacement placement : placements) {
            Pane pane = placement.pane();
            if (pane != Pane.MAIN && !layout.subplotHeights().containsKey(pane)) {
                throw new InvalidChartSpecException("V15", pane);
            }
        }
    }

    private static boolean isHaSource(PriceSource source) {
        return switch (source) {
            case HA_OPEN, HA_HIGH, HA_LOW, HA_CLOSE -> true;
            case OPEN, HIGH, LOW, CLOSE -> false;
        };
    }

    private static List<Instant> barTimes(Series series) {
        return switch (series) {
            case OHLCSeries o -> o.bars().stream().map(OHLCBar::time).toList();
            case HASeries h -> h.bars().stream().map(HABar::time).toList();
        };
    }

    private static boolean hasVolumeOnEveryBar(Series series) {
        return switch (series) {
            case OHLCSeries o -> o.bars().stream().allMatch(b -> b.volume().isPresent());
            case HASeries ignored -> false;
        };
    }
}
