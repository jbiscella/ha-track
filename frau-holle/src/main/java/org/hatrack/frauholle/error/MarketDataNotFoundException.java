package org.hatrack.frauholle.error;

/** The requested symbol is unknown to the data source. */
public final class MarketDataNotFoundException extends MarketDataException {

    public MarketDataNotFoundException(String symbol, String message) {
        super(symbol, message);
    }
}
