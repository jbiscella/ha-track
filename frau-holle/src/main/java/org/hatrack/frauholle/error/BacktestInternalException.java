package org.hatrack.frauholle.error;

/** Thrown when an internal error occurs inside the backtester loop. */
public final class BacktestInternalException extends BacktestException {

    public BacktestInternalException(Throwable cause) {
        super("internal backtester error", cause);
    }
}
