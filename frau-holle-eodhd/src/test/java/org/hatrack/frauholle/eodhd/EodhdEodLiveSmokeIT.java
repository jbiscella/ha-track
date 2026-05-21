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
 * Live smoke test against EODHD's public demo endpoint (no subscription
 * required — {@code api_token=demo} covers AAPL.US for free). Verifies the
 * daily EOD path works end-to-end against the live API.
 *
 * <p>Runs in the {@code integration-test} phase via the Failsafe plugin;
 * skipped with {@code -DskipITs}. Soft-failed in CI on push / PR (the workflow
 * marks the smoke step {@code continue-on-error} and annotates a warning);
 * hard-failed on the release pipeline so a broken live path blocks publishing.
 *
 * <p>The call is logged to {@code System.out} (URL with token redacted, HTTP
 * duration, bar count, first/last timestamps and closes) so the CI log proves
 * a real live call happened. Asserts shape and invariants only — never specific
 * prices, which drift between runs. On failure the {@link MarketDataException}
 * propagates so the log shows the real cause.
 */
class EodhdEodLiveSmokeIT {

    private static final String SYMBOL = "AAPL.US";

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fetchesDailyBarsFromTheLiveEodEndpoint() throws MarketDataException {
        // A fixed window well inside EODHD's history for AAPL.US.
        Instant since = Instant.parse("2024-01-02T00:00:00Z");
        Instant until = Instant.parse("2024-03-01T00:00:00Z");

        List<OHLCBar> bars = LiveSmokeSupport.fetchAndLog(
                "EodhdEodLiveSmokeIT", SYMBOL, "1d", since, until);

        assertFalse(bars.isEmpty(), "expected at least one daily bar for " + SYMBOL);
        LiveSmokeSupport.assertOhlcInvariantsAndOrdering(bars);
    }
}
