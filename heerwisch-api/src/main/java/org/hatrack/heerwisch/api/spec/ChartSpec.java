package org.hatrack.heerwisch.api.spec;

import org.hatrack.commons.Series;

import java.util.List;

/**
 * Immutable description of a chart. Has no public constructor: instances are
 * built only via {@link #builder()}.
 */
public final class ChartSpec {

    private final Series series;
    private final List<IndicatorPlacement> indicators;
    private final List<Annotation> annotations;
    private final LayoutSpec layout;
    private final CandleStyle candleStyle;

    ChartSpec(Series series, List<IndicatorPlacement> indicators,
              List<Annotation> annotations, LayoutSpec layout, CandleStyle candleStyle) {
        this.series = series;
        this.indicators = List.copyOf(indicators);
        this.annotations = List.copyOf(annotations);
        this.layout = layout;
        this.candleStyle = candleStyle;
    }

    public static ChartSpecBuilder builder() {
        return new ChartSpecBuilder();
    }

    public Series series() {
        return series;
    }

    public List<IndicatorPlacement> indicators() {
        return indicators;
    }

    public List<Annotation> annotations() {
        return annotations;
    }

    public LayoutSpec layout() {
        return layout;
    }

    public CandleStyle candleStyle() {
        return candleStyle;
    }
}
