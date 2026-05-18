package org.hatrack.frauholle.error;

/** A transient data-source failure: timeout, 5xx, rate limit or auth error. */
public final class MarketDataUnavailableException extends MarketDataException {

    public MarketDataUnavailableException(String symbol, String message) {
        super(symbol, message);
    }

    public MarketDataUnavailableException(String symbol, String message, Throwable cause) {
        super(symbol, message, cause);
    }
}
