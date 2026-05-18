package org.hatrack.frauholle.error;

import java.time.Instant;

/**
 * v1.1: thrown by the backtester when a {@code ClosePositionAtPrice} signal
 * carries a {@code fillTime} that reaches beyond the bar after the bar at
 * which the signal was emitted (a lookahead-safety violation).
 */
public final class InvalidExplicitFillException extends BacktestException {

    private final transient Instant fillTime;
    private final transient Instant barTime;

    public InvalidExplicitFillException(Instant fillTime, Instant barTime) {
        super("ClosePositionAtPrice fillTime " + fillTime
                + " reaches beyond the next bar at " + barTime
                + " — explicit fills may not look ahead past the bar after the signal");
        this.fillTime = fillTime;
        this.barTime = barTime;
    }

    /** The offending fill time supplied by the signal. */
    public Instant fillTime() {
        return fillTime;
    }

    /** The time of the bar after the signal bar, against which the fill time was checked. */
    public Instant barTime() {
        return barTime;
    }
}
