package org.hatrack.frauholle.eodhd;

import org.hatrack.commons.OHLCBar;
import org.hatrack.commons.Timeframe;
import org.hatrack.frauholle.eodhd.internal.DefaultJsonReader;
import org.hatrack.frauholle.eodhd.internal.JdkHttpExecutor;
import org.hatrack.frauholle.error.MarketDataException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared helpers for the EODHD live smoke tests. Wraps the real
 * {@link JdkHttpExecutor} so the test can log the exact URL the driver called
 * (token redacted), the round-trip duration, and a sample of the data — making
 * it visible in the CI failsafe log that a real live call happened and returned
 * plausible values.
 */
final class LiveSmokeSupport {

    static final String DEMO_TOKEN = "demo";
    static final String BASE_URL = "https://eodhd.com";
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    private LiveSmokeSupport() {
    }

    /** Wraps a delegate executor, recording the most recent URL it was given. */
    static final class CapturingExecutor implements HttpExecutor {
        private final HttpExecutor delegate;
        private volatile String lastUrl;

        CapturingExecutor(HttpExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpResult get(String url, Map<String, String> headers, Duration timeout)
                throws IOException {
            this.lastUrl = url;
            return delegate.get(url, headers, timeout);
        }

        String redactedUrl() {
            return lastUrl == null
                    ? "(no request sent)"
                    : lastUrl.replaceAll("api_token=[^&]*", "api_token=***");
        }
    }

    /**
     * Fetches via the real JDK HTTP executor and logs the call to
     * {@code System.out} (so it lands in the surefire/failsafe CI log, not via
     * SLF4J which may be filtered). On {@link MarketDataException} the URL,
     * timing, and exception are logged and the exception is rethrown so the
     * test fails loudly with a diagnosable log.
     */
    static List<OHLCBar> fetchAndLog(String testName, String symbol, String wire,
                                     Instant since, Instant until) throws MarketDataException {
        CapturingExecutor capturing = new CapturingExecutor(new JdkHttpExecutor(TIMEOUT));
        EodhdMarketDataSource source = new EodhdMarketDataSource(
                DEMO_TOKEN, BASE_URL, TIMEOUT, capturing, new DefaultJsonReader());
        long startNanos = System.nanoTime();
        try {
            List<OHLCBar> bars = source.fetchHistory(symbol, Timeframe.fromWire(wire), since, until);
            long ms = (System.nanoTime() - startNanos) / 1_000_000L;
            System.out.println("=== " + testName + " ===");
            System.out.println("URL: " + capturing.redactedUrl());
            System.out.println("HTTP duration: " + ms + " ms");
            System.out.println("Bars received: " + bars.size());
            if (!bars.isEmpty()) {
                OHLCBar first = bars.get(0);
                OHLCBar last = bars.get(bars.size() - 1);
                System.out.println("First bar: " + first.time() + " close=" + first.close());
                System.out.println("Last bar:  " + last.time() + " close=" + last.close());
            }
            System.out.println("===");
            return bars;
        } catch (MarketDataException e) {
            long ms = (System.nanoTime() - startNanos) / 1_000_000L;
            System.out.println("=== " + testName + " (FAILED) ===");
            System.out.println("URL: " + capturing.redactedUrl());
            System.out.println("HTTP duration: " + ms + " ms");
            System.out.println("Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("===");
            throw e;
        }
    }

    /** Asserts OHLC invariants and strictly-ascending timestamps across all bars. */
    static void assertOhlcInvariantsAndOrdering(List<OHLCBar> bars) {
        Instant previous = null;
        for (int i = 0; i < bars.size(); i++) {
            OHLCBar bar = bars.get(i);
            assertTrue(bar.high().compareTo(bar.low()) >= 0, "bar " + i + ": high < low");
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
