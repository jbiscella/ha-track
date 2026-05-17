package org.hatrack.nachtkrapp.spec;

import org.hatrack.commons.Series;
import org.hatrack.commons.Timeframe;
import org.hatrack.nachtkrapp.rule.DetectionRule;

import java.util.List;
import java.util.Optional;

/**
 * Immutable bundle of a series and the rules to apply. Has no public
 * constructor: instances are built only via {@link #builder()}.
 */
public final class DetectionSpec {

    private final Series series;
    private final List<DetectionRule> rules;
    private final Optional<Timeframe> timeframe;

    DetectionSpec(Series series, List<DetectionRule> rules, Optional<Timeframe> timeframe) {
        this.series = series;
        this.rules = List.copyOf(rules);
        this.timeframe = timeframe;
    }

    public static DetectionSpecBuilder builder() {
        return new DetectionSpecBuilder();
    }

    public Series series() {
        return series;
    }

    public List<DetectionRule> rules() {
        return rules;
    }

    public Optional<Timeframe> timeframe() {
        return timeframe;
    }
}
