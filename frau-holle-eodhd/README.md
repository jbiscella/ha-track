# frau-holle-eodhd

A reference **`MarketDataSource`** for [`frau-holle`](../frau-holle) that
fetches OHLC bars from the [EODHD](https://eodhd.com/) **End-of-Day API**.

> Human-friendly guide. Authoritative contract: [`CLAUDE.md`](CLAUDE.md).

## What it is for

A live data source for symbols you do not have on disk. It hits one EODHD
endpoint — historical end-of-day bars — and maps the JSON response into
`List<OHLCBar>`. For an offline, no-network source see
[`frau-holle-csv`](../frau-holle-csv).

## Adding the dependency

```xml
<dependency>
    <groupId>net.jacopobiscella</groupId>
    <artifactId>frau-holle-eodhd</artifactId>
    <version>1.0.0-SNAPSHOT</version>
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

- **Timeframes**: only `1d`, `1w`, `1M` are supported (the EOD endpoint). Any
  other timeframe throws `MarketDataSchemaException`. Intraday is a different
  endpoint and out of scope.
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

## Out of scope

Intraday / WebSocket / fundamentals / splits endpoints, internal retry or
caching, adjusted-close usage, async API.
