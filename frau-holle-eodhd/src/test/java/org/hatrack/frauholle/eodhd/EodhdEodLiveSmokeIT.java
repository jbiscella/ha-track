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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live smoke test against EODHD's public demo endpoint (no subscription
 * required — {@code api_token=demo} covers AAPL.US for free). Verifies the
 * daily EOD path actually works end-to-end against the live API.
 *
 * <p>Runs in the {@code integration-test} phase via the Failsafe plugin;
 * skipped with {@code -DskipITs}. Soft-failed in CI on push / PR / schedule
 * (the workflow marks the smoke step {@code continue-on-error}); hard-failed
 * on the release pipeline so a broken live path blocks publishing.
 *
 * <p>Asserts shape and invariants only — never specific prices, which drift
 * between runs. On any failure the underlying {@link MarketDataException} is
 * allowed to propagate so the CI log shows the real cause.
 */
class EodhdEodLiveSmokeIT {

    private static final String DEMO_TOKEN = "demo";
    private static final String SYMBOL = "AAPL.US";

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fetchesDailyBarsFromTheLiveEodEndpoint() throws MarketDataException {
        EodhdMarketDataSource source = new EodhdMarketDataSource(DEMO_TOKEN);

        // A fixed window well inside EODHD's history for AAPL.US.
        Instant since = Instant.parse("2024-01-02T00:00:00Z");
        Instant until = Instant.parse("2024-03-01T00:00:00Z");

        List<OHLCBar> bars = source.fetchHistory(SYMBOL, Timeframe.fromWire("1d"), since, until);

        assertFalse(bars.isEmpty(), "expected at least one daily bar for " + SYMBOL);
        assertOhlcInvariantsAndOrdering(bars);
    }

    static void assertOhlcInvariantsAndOrdering(List<OHLCBar> bars) {
        Instant previous = null;
        for (int i = 0; i < bars.size(); i++) {
            OHLCBar bar = bars.get(i);
            assertTrue(bar.high().compareTo(bar.low()) >= 0,
                    "bar " + i + ": high < low");
            assertTrue(bar.high().compareTo(bar.open()) >= 0
                            && bar.high().compareTo(bar.close()) >= 0,
                    "bar " + i + ": high is not the max");
            assertTrue(bar.low().compareTo(bar.open()) <= 0
                            && bar.low().compareTo(bar.close()) <= 0,
                    "bar " + i + ": low is not the min");
            assertTrue(bar.open().signum() > 0 && bar.close().signum() > 0,
                    "bar " + i + ": non-positive price");
            if (previous != null) {
                assertTrue(bar.time().isAfter(previous),
                        "bar " + i + ": timestamps not strictly ascending");
            }
            previous = bar.time();
        }
    }
}
