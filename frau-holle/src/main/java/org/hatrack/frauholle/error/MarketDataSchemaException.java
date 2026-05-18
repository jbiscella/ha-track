package org.hatrack.frauholle.error;

/** The data source returned data that could not be parsed. */
public final class MarketDataSchemaException extends MarketDataException {

    public MarketDataSchemaException(String symbol, String message) {
        super(symbol, message);
    }

    public MarketDataSchemaException(String symbol, String message, Throwable cause) {
        super(symbol, message, cause);
    }
}
