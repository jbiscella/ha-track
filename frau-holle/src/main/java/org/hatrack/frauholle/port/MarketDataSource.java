package org.hatrack.frauholle.port;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.Timeframe;
import org.hatrack.frauholle.error.MarketDataException;

import java.time.Instant;
import java.util.List;

/**
 * Pluggable market-data provider port. Reference implementations live in
 * sibling modules ({@code frau-holle-csv}, {@code frau-holle-eodhd}).
 */
public interface MarketDataSource {

    /**
     * Returns OHLC bars for the symbol within {@code [since, until]} (inclusive),
     * ordered ascending by time with unique times. An empty range yields an
     * empty list (not an error).
     */
    List<OHLCBar> fetchHistory(String symbol, Timeframe timeframe, Instant since, Instant until)
            throws MarketDataException;
}
