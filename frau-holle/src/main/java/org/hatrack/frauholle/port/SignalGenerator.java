package org.hatrack.frauholle.port;

import org.hatrack.frauholle.error.SignalGenerationException;
import org.hatrack.frauholle.model.BarContext;
import org.hatrack.frauholle.model.Signal;

/**
 * Opaque consumer strategy port. The backtester calls {@code generate}
 * sequentially on a single thread; implementations may hold internal state.
 * Implementations MUST NOT consult bars after {@code context.currentBar()}.
 */
public interface SignalGenerator {

    Signal generate(BarContext context) throws SignalGenerationException;
}
