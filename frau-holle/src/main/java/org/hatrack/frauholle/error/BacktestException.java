package org.hatrack.frauholle.error;

/**
 * Root of the checked exception hierarchy for {@code frau-holle}. Abstract;
 * never thrown directly.
 */
public abstract sealed class BacktestException extends Exception
        permits InvalidBacktestSpecException, MarketDataException,
        SignalGenerationException, BacktestInternalException, InvalidExplicitFillException,
        InvalidAddToPositionDirectionException {

    protected BacktestException(String message) {
        super(message);
    }

    protected BacktestException(String message, Throwable cause) {
        super(message, cause);
    }
}
