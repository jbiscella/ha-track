package org.hatrack.frauholle.error;

/**
 * Thrown by {@code MarketDataSource} implementations when a data fetch fails.
 * Non-sealed so reference data sources may define their own subclasses.
 */
public non-sealed class MarketDataException extends BacktestException {

    private final String symbol;

    public MarketDataException(String symbol, String message) {
        super(message);
        this.symbol = symbol;
    }

    public MarketDataException(String symbol, String message, Throwable cause) {
        super(message, cause);
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
