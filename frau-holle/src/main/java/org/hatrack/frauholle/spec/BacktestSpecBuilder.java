package org.hatrack.frauholle.spec;

import org.hatrack.commons.OHLCBar;
import org.hatrack.frauholle.error.InvalidBacktestSpecException;
import org.hatrack.frauholle.internal.TimeframeInference;
import org.hatrack.frauholle.port.SignalGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link BacktestSpec}. {@link #build()} validates eagerly
 * (rules V1-V6) and throws {@link InvalidBacktestSpecException} on the first
 * violation found.
 */
public final class BacktestSpecBuilder {

    private List<OHLCBar> series;
    private SignalGenerator signalGenerator;
    private BigDecimal initialCash;

    public BacktestSpecBuilder withSeries(List<OHLCBar> series) {
        this.series = series;
        return this;
    }

    public BacktestSpecBuilder withSignalGenerator(SignalGenerator signalGenerator) {
        this.signalGenerator = signalGenerator;
        return this;
    }

    public BacktestSpecBuilder withInitialCash(BigDecimal initialCash) {
        this.initialCash = initialCash;
        return this;
    }

    public BacktestSpec build() throws InvalidBacktestSpecException {
        if (series == null) {
            throw new InvalidBacktestSpecException("V1", null);
        }
        if (series.isEmpty()) {
            throw new InvalidBacktestSpecException("V2", null);
        }
        if (signalGenerator == null) {
            throw new InvalidBacktestSpecException("V3", null);
        }
        if (initialCash == null || initialCash.signum() <= 0) {
            throw new InvalidBacktestSpecException("V4", initialCash);
        }
        if (series.size() < 2) {
            throw new InvalidBacktestSpecException("V6", series.size());
        }
        List<Instant> times = new ArrayList<>(series.size());
        for (OHLCBar bar : series) {
            times.add(bar.time());
        }
        if (TimeframeInference.periodsPerYear(times).isEmpty()) {
            throw new InvalidBacktestSpecException("V5", null);
        }
        return new BacktestSpec(series, signalGenerator, initialCash);
    }
}
