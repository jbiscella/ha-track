package org.hatrack.frauholle.eodhd;

import org.hatrack.commons.Timeframe;
import org.hatrack.frauholle.contract.MarketDataSourceContract;
import org.hatrack.frauholle.eodhd.internal.DefaultJsonReader;
import org.hatrack.frauholle.port.MarketDataSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Runs the shared {@link MarketDataSourceContract} against the EODHD driver,
 * backed by a canned {@link HttpExecutor} — no real network, no real API token.
 *
 * <p>The JSON payload below is entirely synthetic: a fabricated symbol and
 * round-number OHLC values, not a captured EODHD response. It only mirrors the
 * documented EODHD EOD shape ({@code date/open/high/low/close/volume}).
 */
class EodhdMarketDataSourceContractTest extends MarketDataSourceContract {

    private static final String SYMBOL = "TEST.EXAMPLE";
    private static final Timeframe TIMEFRAME = Timeframe.fromWire("1d");

    // Synthetic, ascending, well-formed bars. Not real market data.
    private static final String POPULATED_BODY = """
            [{"date":"2024-01-02","open":100,"high":101,"low":99,"close":100.5,"volume":1000},
             {"date":"2024-01-03","open":100.5,"high":102,"low":100,"close":101.5,"volume":1100},
             {"date":"2024-01-04","open":101.5,"high":103,"low":101,"close":102.5,"volume":1200}]""";

    /** Returns the synthetic bars for the populated range, an empty array otherwise. */
    private static final class CannedHttpExecutor implements HttpExecutor {
        @Override
        public HttpResult get(String url, Map<String, String> headers, Duration timeout) {
            String body = url.contains("from=2024") ? POPULATED_BODY : "[]";
            return new HttpResult(200, body);
        }
    }

    @Override
    protected MarketDataSource source() {
        return new EodhdMarketDataSource("demo", "https://eodhd.example.invalid",
                Duration.ofSeconds(5), new CannedHttpExecutor(), new DefaultJsonReader());
    }

    @Override
    protected Query populatedQuery() {
        return new Query(SYMBOL, TIMEFRAME,
                Instant.parse("2024-01-02T00:00:00Z"), Instant.parse("2024-01-04T00:00:00Z"));
    }

    @Override
    protected Query emptyQuery() {
        return new Query(SYMBOL, TIMEFRAME,
                Instant.parse("2030-01-01T00:00:00Z"), Instant.parse("2030-12-31T00:00:00Z"));
    }
}
