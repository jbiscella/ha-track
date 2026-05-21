# frau-holle-eodhd

A reference **`MarketDataSource`** for [`frau-holle`](../frau-holle) that
fetches OHLC bars from EODHD's [End-of-Day](https://eodhd.com/financial-apis/api-for-historical-data-and-volumes/) **and** [Intraday](https://eodhd.com/financial-apis/intraday-historical-data-api/) APIs.

> Human-friendly guide. Authoritative contract: [`CLAUDE.md`](CLAUDE.md).

## What it is for

A live data source for symbols you do not have on disk. Routes by timeframe:

- **Daily / weekly / monthly** (`1d`, `1w`, `1M`) → EODHD `/api/eod`
- **Intraday** (`1m`, `5m`, `1h`) → EODHD `/api/intraday`

Both endpoints return JSON arrays that the driver maps into `List<OHLCBar>`.
For an offline, no-network source see [`frau-holle-csv`](../frau-holle-csv).

## Adding the dependency

```xml
<dependency>
    <groupId>net.jacopobiscella</groupId>
    <artifactId>frau-holle-eodhd</artifactId>
    <version>0.50.0-alpha</version>
</dependency>
```

Main class: `org.hatrack.frauholle.eodhd.EodhdMarketDataSource`.

## Usage

The simple constructor uses JDK-only defaults (the built-in HTTP client and
JSON reader, a 30-second timeout, the standard base URL):

```java
import org.hatrack.frauholle.eodhd.EodhdMarketDataSource;
import org.hatrack.frauholle.port.MarketDataSource;

MarketDataSource source = new EodhdMarketDataSource("YOUR_API_TOKEN");

List<OHLCBar> bars = source.fetchHistory(
        "AAPL.US", Timeframe.fromWire("1d"),   // EODHD wants TICKER.EXCHANGE
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-12-31T00:00:00Z"));
```

The symbol is passed through verbatim — EODHD expects `TICKER.EXCHANGE`
(`AAPL.US`, `EURUSD.FOREX`, `BTCUSD.CC`); the driver does not auto-suffix it.

### Injecting your own HTTP / JSON

The driver depends on two small ports, not on a specific HTTP or JSON library:

- `HttpExecutor` — performs a GET, returns an `HttpResult` (status + body);
- `JsonReader` — parses the JSON array of bar objects.

Each has a JDK-only default. To plug in your own (a shared client, a tuned
timeout, your preferred JSON library):

```java
MarketDataSource source = new EodhdMarketDataSource(
        "YOUR_API_TOKEN",
        "https://eodhistoricaldata.com",   // base URL (override for testing)
        Duration.ofSeconds(30),            // HTTP timeout
        myHttpExecutor,                    // your HttpExecutor
        myJsonReader);                     // your JsonReader
```

## Behaviour worth knowing

- **Timeframes**: daily (`1d`, `1w`, `1M`) routes to `/api/eod`; intraday
  (`1m`, `5m`, `1h`) routes to `/api/intraday`. Any other timeframe throws
  `MarketDataSchemaException` naming the supported set.
- **Intraday `from` / `to`** are passed as UNIX epoch seconds (the EOD
  endpoint uses `YYYY-MM-DD` strings — different convention per EODHD).
- **Bar time (intraday)** is the **bar start** in UTC, read from the row's
  `timestamp` field (`Instant.ofEpochSecond(...)`).
- **Pre-market / after-hours**: EODHD includes pre-market and after-hours
  bars for US tickers in intraday responses by default. The driver passes
  them through as-is — no RTH filtering. Filter downstream if needed.
- **API call cost**: `/api/intraday` consumes 5 EODHD API credits per call
  (vs 1 for `/api/eod`). Requires the EOD+Intraday All World Extended plan
  or All-In-One.
- **Intraday history availability** varies by interval and ticker: `5m`/`1h`
  typically from October 2020 onward; `1m` varies. Requests before history
  return an empty list (no error).
- **Prices** are read as text and parsed into `BigDecimal` — never via `double`.
  The raw `close` is used; `adjusted_close` is ignored.
- **No retry, no caching** — backtests pre-load data once; wrap the driver in
  your own decorator if you want retries.
- An empty result for a valid range is an **empty list**, not an error.

## Error mapping

HTTP outcomes map to typed `MarketDataException` subtypes:

| HTTP / condition | Exception |
|---|---|
| 404 (symbol not found) | `MarketDataNotFoundException` |
| 401 / 403 (auth) | `MarketDataUnavailableException` |
| 429 (rate limit) | `MarketDataUnavailableException` (message says "rate limit") |
| 5xx, timeout, network failure | `MarketDataUnavailableException` |
| malformed JSON body | `MarketDataSchemaException` |

Construction validates eagerly: a blank API token or a negative timeout throws
`IllegalArgumentException`; a `null` HTTP client throws `NullPointerException`.

## Security note

EODHD has **no header-based authentication** — the API token travels as a query
parameter, so the request URL contains it. Never log that URL at INFO level or
above; if a URL must be logged, redact the `api_token` value first. (The driver
itself does no logging.)

## Live smoke tests

Two integration tests (`EodhdEodLiveSmokeIT`, `EodhdIntradayLiveSmokeIT`)
exercise the driver against EODHD's **public demo endpoint** — `api_token=demo`
covers `AAPL.US` for free, so no subscription or GitHub Secret is needed. They
fetch a fixed historical window (daily and 1h), then assert shape only
(non-empty, OHLC invariants, strictly ascending timestamps) — never specific
prices, which drift between runs.

They run in Maven's `integration-test` phase via the Failsafe plugin
(`*IT.java`), so `./mvnw verify` runs them and `./mvnw verify -DskipITs` skips
them. In CI:

| Trigger | Behaviour |
|---|---|
| push / pull_request | smoke runs in a dedicated **soft-fail** job (`continue-on-error`) — a red run surfaces the error log without blocking the workflow |
| release pipeline | the release build runs the same ITs with no `continue-on-error`, so a broken live path **hard-fails** a publish |

On any failure the underlying `MarketDataException` propagates so the CI log
shows the real cause (e.g. an EODHD outage, a network egress restriction, or a
demo-token coverage change). If your build environment cannot reach
`eodhd.com`, run `./mvnw verify -DskipITs` to skip the live calls.

## Out of scope

WebSocket streaming, fundamentals / news / splits / dividends endpoints, bulk
download, internal retry or caching, adjusted-close usage, RTH filtering,
async API.
