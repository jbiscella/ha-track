package org.hatrack.frauholle.error;

import java.time.Instant;

/**
 * v1.1: thrown by the backtester when a {@code ClosePositionAtPrice} signal
 * carries a {@code fillTime} that is not a valid intrabar instant. A valid
 * fill time satisfies {@code signalBar.time < fillTime < nextBar.time} (strict
 * on both sides); a retroactive fill ({@code fillTime <= signalBar.time}) or an
 * at/beyond-next-bar fill ({@code fillTime >= nextBar.time}) is a
 * lookahead-safety violation.
 */
public final class InvalidExplicitFillException extends BacktestException {

    private final transient Instant fillTime;
    private final transient Instant barTime;

    public InvalidExplicitFillException(Instant fillTime, Instant barTime) {
        super("ClosePositionAtPrice fillTime " + fillTime
                + " is not a valid intrabar instant — it must fall strictly after the signal bar"
                + " and strictly before the next bar at " + barTime);
        this.fillTime = fillTime;
        this.barTime = barTime;
    }

    /** The offending fill time supplied by the signal. */
    public Instant fillTime() {
        return fillTime;
    }

    /**
     * The time of the bar immediately after the signal bar — the exclusive
     * upper bound the fill time was checked against.
     */
    public Instant barTime() {
        return barTime;
    }
}
