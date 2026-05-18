package org.hatrack.nachtkrapp.spec;

import org.hatrack.commons.HABar;
import org.hatrack.commons.HASeries;
import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.OHLCInvariantViolationException;
import org.hatrack.commons.OHLCSeries;
import org.hatrack.commons.PriceSource;
import org.hatrack.commons.Series;
import org.hatrack.commons.Timeframe;
import org.hatrack.nachtkrapp.error.InvalidDetectionSpecException;
import org.hatrack.nachtkrapp.rule.DetectionRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.HAColorChangeRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.HADojiRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.HAStrongCandleRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.MACDSignalCrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.MACDZeroCrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.PriceMACrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.PriceVsMARule;
import org.hatrack.nachtkrapp.rule.DetectionRule.RSILevel50CrossRule;
import org.hatrack.nachtkrapp.rule.DetectionRule.RSIThresholdRule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fluent builder for {@link DetectionSpec}. {@link #build()} validates eagerly
 * (rules V1-V10) and throws {@link InvalidDetectionSpecException} on the first
 * violation found.
 */
public final class DetectionSpecBuilder {

    private static final BigDecimal FIFTY = new BigDecimal("50");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Series series;
    private final List<DetectionRule> rules = new ArrayList<>();
    private Optional<Timeframe> timeframe = Optional.empty();

    public DetectionSpecBuilder withSeries(Series series) {
        this.series = series;
        return this;
    }

    public DetectionSpecBuilder withTimeframe(Timeframe timeframe) {
        this.timeframe = Optional.of(Objects.requireNonNull(timeframe, "timeframe"));
        return this;
    }

    public DetectionSpecBuilder addRule(DetectionRule rule) {
        rules.add(Objects.requireNonNull(rule, "rule"));
        return this;
    }

    public DetectionSpecBuilder addAllRules(Collection<DetectionRule> rules) {
        Objects.requireNonNull(rules, "rules");
        rules.forEach(this::addRule);
        return this;
    }

    public DetectionSpec build() throws InvalidDetectionSpecException {
        if (series == null) {
            throw new InvalidDetectionSpecException("V1", null);
        }
        List<Instant> times = barTimes(series);
        if (times.isEmpty()) {
            throw new InvalidDetectionSpecException("V2", null);
        }
        for (int i = 1; i < times.size(); i++) {
            int cmp = times.get(i).compareTo(times.get(i - 1));
            if (cmp < 0) {
                throw new InvalidDetectionSpecException("V3", times.get(i));
            }
            if (cmp == 0) {
                throw new InvalidDetectionSpecException("V4", times.get(i));
            }
        }
        if (series instanceof OHLCSeries ohlcSeries) {
            List<OHLCBar> bars = ohlcSeries.bars();
            for (int i = 0; i < bars.size(); i++) {
                OHLCBar bar = bars.get(i);
                try {
                    bar.validateInvariants();
                } catch (OHLCInvariantViolationException e) {
                    throw new InvalidDetectionSpecException("V10",
                            "bar " + i + " at " + bar.time() + ": " + e.violatedInvariant());
                }
            }
        }
        if (rules.isEmpty()) {
            throw new InvalidDetectionSpecException("V8", null);
        }
        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                if (rules.get(i).equals(rules.get(j))) {
                    throw new InvalidDetectionSpecException("V9", rules.get(j));
                }
            }
        }
        boolean haSeries = series instanceof HASeries;
        for (DetectionRule rule : rules) {
            checkParameters(rule);
            checkSeriesCompatibility(rule, haSeries);
            if (times.size() < rule.minBars()) {
                throw new InvalidDetectionSpecException("V6", rule);
            }
        }
        return new DetectionSpec(series, rules, timeframe);
    }

    private static void checkParameters(DetectionRule rule) throws InvalidDetectionSpecException {
        boolean ok = switch (rule) {
            case HAColorChangeRule r -> r.minStreakLength() >= 1;
            case HAStrongCandleRule r -> r.wickTolerance().signum() >= 0 && inOpenUnitInterval(r.minBodyRatio());
            case HADojiRule r -> inOpenUnitInterval(r.maxBodyRatio());
            case PriceVsMARule r -> r.period() >= 1;
            case PriceMACrossRule r -> r.period() >= 1;
            case RSIThresholdRule r -> r.period() >= 1
                    && r.overbought().compareTo(FIFTY) > 0 && r.overbought().compareTo(HUNDRED) < 0
                    && r.oversold().signum() > 0 && r.oversold().compareTo(FIFTY) < 0;
            case RSILevel50CrossRule r -> r.period() >= 1;
            case MACDSignalCrossRule r -> r.fastPeriod() >= 1 && r.slowPeriod() >= 1
                    && r.signalPeriod() >= 1 && r.slowPeriod() > r.fastPeriod();
            case MACDZeroCrossRule r -> r.fastPeriod() >= 1 && r.slowPeriod() >= 1
                    && r.signalPeriod() >= 1 && r.slowPeriod() > r.fastPeriod();
        };
        if (!ok) {
            throw new InvalidDetectionSpecException("V7", rule);
        }
    }

    private static void checkSeriesCompatibility(DetectionRule rule, boolean haSeries)
            throws InvalidDetectionSpecException {
        boolean ok = switch (rule) {
            case HAColorChangeRule ignored -> haSeries;
            case HAStrongCandleRule ignored -> haSeries;
            case HADojiRule ignored -> haSeries;
            case PriceVsMARule r -> priceSourceMatches(r.priceSource(), haSeries);
            case PriceMACrossRule r -> priceSourceMatches(r.priceSource(), haSeries);
            case RSIThresholdRule r -> priceSourceMatches(r.priceSource(), haSeries);
            case RSILevel50CrossRule r -> priceSourceMatches(r.priceSource(), haSeries);
            case MACDSignalCrossRule r -> priceSourceMatches(r.priceSource(), haSeries);
            case MACDZeroCrossRule r -> priceSourceMatches(r.priceSource(), haSeries);
        };
        if (!ok) {
            throw new InvalidDetectionSpecException("V5", rule);
        }
    }

    private static boolean priceSourceMatches(PriceSource source, boolean haSeries) {
        return isHaSource(source) == haSeries;
    }

    private static boolean isHaSource(PriceSource source) {
        return switch (source) {
            case HA_OPEN, HA_HIGH, HA_LOW, HA_CLOSE -> true;
            case OPEN, HIGH, LOW, CLOSE -> false;
        };
    }

    private static boolean inOpenUnitInterval(BigDecimal value) {
        return value.signum() > 0 && value.compareTo(BigDecimal.ONE) <= 0;
    }

    private static List<Instant> barTimes(Series series) {
        return switch (series) {
            case OHLCSeries o -> o.bars().stream().map(OHLCBar::time).toList();
            case HASeries h -> h.bars().stream().map(HABar::time).toList();
        };
    }
}
