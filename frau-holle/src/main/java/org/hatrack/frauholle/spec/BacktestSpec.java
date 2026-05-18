package org.hatrack.frauholle.spec;

import org.hatrack.commons.OHLCBar;
import org.hatrack.frauholle.port.SignalGenerator;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable bundle of backtest inputs. Has no public constructor: instances
 * are built only via {@link #builder()}.
 */
public final class BacktestSpec {

    private final List<OHLCBar> series;
    private final SignalGenerator signalGenerator;
    private final BigDecimal initialCash;

    BacktestSpec(List<OHLCBar> series, SignalGenerator signalGenerator, BigDecimal initialCash) {
        this.series = List.copyOf(series);
        this.signalGenerator = signalGenerator;
        this.initialCash = initialCash;
    }

    public static BacktestSpecBuilder builder() {
        return new BacktestSpecBuilder();
    }

    public List<OHLCBar> series() {
        return series;
    }

    public SignalGenerator signalGenerator() {
        return signalGenerator;
    }

    public BigDecimal initialCash() {
        return initialCash;
    }
}
