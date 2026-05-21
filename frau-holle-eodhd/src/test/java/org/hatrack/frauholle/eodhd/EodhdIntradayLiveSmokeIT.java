package org.hatrack.frauholle.eodhd;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.Timeframe;
import org.hatrack.frauholle.error.MarketDataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Live smoke test for the intraday path against EODHD's public demo endpoint
 * ({@code api_token=demo}, AAPL.US). Fetches 1h bars over a fixed historical
 * window (intraday 1h history begins October 2020) and verifies the response
 * is non-empty, OHLC-valid, and strictly time-ordered.
 *
 * <p>Runs in the {@code integration-test} phase via Failsafe; skipped with
 * {@code -DskipITs}. Soft-failed in CI on push / PR / schedule; hard-failed
 * on the release pipeline. Asserts shape and invariants only — never specific
 * prices. On failure the {@link MarketDataException} propagates so the CI log
 * shows the real cause.
 */
class EodhdIntradayLiveSmokeIT {

    private static final String DEMO_TOKEN = "demo";
    private static final String SYMBOL = "AAPL.US";

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fetchesHourlyBarsFromTheLiveIntradayEndpoint() throws MarketDataException {
        EodhdMarketDataSource source = new EodhdMarketDataSource(DEMO_TOKEN);

        // A fixed past week well inside intraday history coverage.
        Instant since = Instant.parse("2024-06-03T00:00:00Z");
        Instant until = Instant.parse("2024-06-08T00:00:00Z");

        List<OHLCBar> bars = source.fetchHistory(SYMBOL, Timeframe.fromWire("1h"), since, until);

        assertFalse(bars.isEmpty(), "expected at least one hourly bar for " + SYMBOL);
        EodhdEodLiveSmokeIT.assertOhlcInvariantsAndOrdering(bars);
    }
}
