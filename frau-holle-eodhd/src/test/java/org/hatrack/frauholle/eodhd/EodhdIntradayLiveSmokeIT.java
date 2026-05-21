package org.hatrack.frauholle.eodhd;

import org.hatrack.commons.OHLCBar;
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
 * {@code -DskipITs}. Soft-failed in CI on push / PR; hard-failed on the release
 * pipeline. The call is logged to {@code System.out} (URL with token redacted,
 * HTTP duration, bar count, first/last timestamps and closes). Asserts shape
 * and invariants only — never specific prices. On failure the
 * {@link MarketDataException} propagates so the CI log shows the real cause.
 */
class EodhdIntradayLiveSmokeIT {

    private static final String SYMBOL = "AAPL.US";

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fetchesHourlyBarsFromTheLiveIntradayEndpoint() throws MarketDataException {
        // A single past trading day (Mon 2024-06-03) — enough 1h bars to
        // prove the intraday endpoint is reached and returns real data,
        // without pulling a wide range.
        Instant since = Instant.parse("2024-06-03T00:00:00Z");
        Instant until = Instant.parse("2024-06-04T00:00:00Z");

        List<OHLCBar> bars = LiveSmokeSupport.fetchAndLog(
                "EodhdIntradayLiveSmokeIT", SYMBOL, "1h", since, until);

        assertFalse(bars.isEmpty(), "expected at least one hourly bar for " + SYMBOL);
        LiveSmokeSupport.assertOhlcInvariantsAndOrdering(bars);
    }
}
