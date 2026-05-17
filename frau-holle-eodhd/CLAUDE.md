# CLAUDE.md — `frau-holle-eodhd` module

This is the nested spec for the `frau-holle-eodhd` module. It is a reference implementation of the `MarketDataSource` port declared in `frau-holle/CLAUDE.md` §2.1, hitting the **EODHD End-of-Day API**. The repo-wide rules live in the root `CLAUDE.md`.

## 0. Goal and scope

`frau-holle-eodhd` fetches historical OHLC bars from EODHD's End-of-Day API. It is ported from the existing EODHD adapter in the H-tchen-Mail consumer project, generalized to fit the `MarketDataSource` port.

Out of scope: intraday EODHD endpoints (different URL, different schema, different rate limits), real-time WebSocket streaming, fundamentals queries (news, earnings, financials — those stay in the consumer's domain layer), other EODHD products (forex tick data, splits/dividends API).

Dependencies: `frau-holle`, `commons`, an HTTP client and a JSON parser of the implementor's choice (the spec does not pin them).

## 1. EODHD API surface used

| Aspect | Choice |
|---|---|
| Endpoint | `https://eodhistoricaldata.com/api/eod/<symbol>?api_token=...&fmt=json&from=...&to=...&period=...` |
| Authentication | API token as query parameter |
| Response format | JSON array of bar objects |
| Bar object shape | `{ "date": "YYYY-MM-DD", "open": number, "high": number, "low": number, "close": number, "adjusted_close": number, "volume": integer }` |
| Period parameter mapping | `Timeframe("1d") → "d"`, `Timeframe("1w") → "w"`, `Timeframe("1M") → "m"`. Other timeframes are NOT supported by the EODHD EOD endpoint (intraday is a different endpoint, out of scope) |
| Date format in request | `YYYY-MM-DD` (no time component); EODHD interprets as UTC |
| Adjusted vs raw close | This driver uses **`close`** (raw, unadjusted). `adjusted_close` is ignored. Rationale: backtests with corporate-action adjustments require care; v1 keeps it simple and consumer-explicit |

## 2. Driver constructor and configuration

The driver is a class `EodhdMarketDataSource` implementing `MarketDataSource`. Configuration at construction:

| Field | Type | Meaning |
|---|---|---|
| `apiToken` | `String` | EODHD API token; non-blank |
| `baseUrl` | `String` | default `https://eodhistoricaldata.com`. Overridable for testing |
| `httpTimeout` | `Duration` | default 30 seconds. Applied to both connect and read |
| `httpClient` | (impl-specific) | the consumer's HTTP client. The driver does NOT construct its own; it accepts one for lifecycle control. JDK's `java.net.http.HttpClient` is a natural fit but not mandated |
| `jsonMapper` | (impl-specific) | the consumer's JSON parser. The driver accepts an injected mapper to avoid pinning Jackson, Gson, or another library |

The HTTP client and JSON mapper abstractions are exposed as small interfaces in the driver module (`HttpExecutor`, `JsonReader`), with simple JDK-only default implementations also provided.

## 3. `fetchHistory` behavior

| Aspect | Behavior |
|---|---|
| Symbol format | EODHD requires `TICKER.EXCHANGE` (e.g. `AAPL.US`, `EURUSD.FOREX`, `BTCUSD.CC`). The driver does NOT auto-suffix — the consumer passes the EODHD-formatted symbol |
| Timeframe mapping | `1d → d`, `1w → w`, `1M → m`. Other timeframes throw `MarketDataSchemaException` with explanation |
| Date filtering | `since` and `until` are converted to `YYYY-MM-DD` strings (UTC date) and passed as `from` and `to` query parameters |
| Bar `time` reconstruction | Each response row has a `date` field; the driver maps to `Instant.parse("YYYY-MM-DDT00:00:00Z")` (midnight UTC) |
| Ordering | EODHD returns bars in ascending date order; the driver does NOT re-sort but verifies and throws `MarketDataSchemaException` if out of order (defensive) |
| `volume` field | mapped to `Optional<BigDecimal>`; if the field is absent or null in the response, becomes `Optional.empty()` |
| Failed HTTP request | `MarketDataUnavailableException` (transient) |
| Auth failure (401, 403) | `MarketDataUnavailableException` — treated as transient since token may be regenerable |
| Symbol not found (404, or empty result with explanatory body) | `MarketDataNotFoundException` |
| Malformed JSON response | `MarketDataSchemaException` |
| 5xx server error | `MarketDataUnavailableException` |
| Rate limit (429) | `MarketDataUnavailableException` with message indicating rate limit |
| Empty result for valid range | empty list, NOT an error |

## 4. Retry policy

The driver does NOT retry internally. Retries are the consumer's concern (or a higher-level decorator). Rationale: backtests are not real-time critical; the consumer pre-loads data once. Adding retry logic at the driver level would conflict with consumer-side caching and retry strategies.

If the consumer wants retries, they wrap the driver in their own decorator. v1.1+ may add an opt-in `RetryingMarketDataSource` decorator in `frau-holle`, separate from this driver.

## 5. URL and request shape

For `fetchHistory("AAPL.US", Timeframe.of("1d"), 2024-01-01T00:00:00Z, 2024-12-31T23:59:59Z)`:

```
GET https://eodhistoricaldata.com/api/eod/AAPL.US?api_token=<token>&fmt=json&from=2024-01-01&to=2024-12-31&period=d
```

Headers:

| Header | Value |
|---|---|
| `Accept` | `application/json` |
| `User-Agent` | `frau-holle-eodhd/<version>` (where `<version>` is the artifact version at build time, OR a hardcoded default if version is unknown) |

No request body (GET).

## 6. Response parsing

The JSON response is an array of bar objects. The driver iterates and maps each:

| JSON field | Mapped to | Notes |
|---|---|---|
| `date` | `time` (via `LocalDate.parse(...).atStartOfDay(ZoneOffset.UTC).toInstant()`) | ISO local date |
| `open` | `open` | numeric → `BigDecimal` via `new BigDecimal(jsonText)` |
| `high` | `high` | same |
| `low` | `low` | same |
| `close` | `close` | same |
| `volume` | `volume` | numeric → `Optional.of(new BigDecimal(jsonText))`; null/missing → `Optional.empty()` |
| `adjusted_close` | (ignored) | — |

Numeric values are read as **text** from the JSON and parsed into `BigDecimal`. Direct `double` deserialization is forbidden (precision loss).

## 7. Block 1 — Successful fetch

```gherkin
Feature: EodhdMarketDataSource basic behavior

  Scenario: Fetch a small range
    Given an EODHD endpoint returning 3 bars for AAPL.US over 3 days
    When I fetchHistory("AAPL.US", "1d", day1, day3)
    Then the result is a List<OHLCBar> of 3 entries
    And each bar's time = LocalDate.parse(date).atStartOfDay(UTC).toInstant()
    And each bar's prices match the JSON values as BigDecimal

  Scenario: Volume present
    Given a JSON row with "volume": 100000
    When I parse
    Then bar.volume = Optional.of(new BigDecimal("100000"))

  Scenario: Volume null or missing
    Given a JSON row with "volume": null OR missing the field
    When I parse
    Then bar.volume = Optional.empty()

  Scenario: Empty range
    Given EODHD returns an empty array
    When I fetch
    Then result is emptyList
    And no exception is thrown

  Scenario: Adjusted close is ignored
    Given a JSON row with close = 100.0 and adjusted_close = 95.0
    When I parse
    Then bar.close = 100.0 (raw close, not adjusted)
```

## 8. Block 2 — Symbol and timeframe mapping

```gherkin
Feature: Symbol and timeframe handling

  Scenario: Symbol is passed through verbatim
    Given a fetchHistory call with symbol "MSFT.US"
    When the driver constructs the URL
    Then the URL contains "/api/eod/MSFT.US"

  Scenario: Daily timeframe maps to period "d"
    Given fetchHistory with timeframe "1d"
    Then the URL contains "&period=d"

  Scenario: Weekly timeframe maps to period "w"
    Given fetchHistory with timeframe "1w"
    Then the URL contains "&period=w"

  Scenario: Monthly timeframe maps to period "m"
    Given fetchHistory with timeframe "1M"
    Then the URL contains "&period=m"

  Scenario: Intraday timeframes are rejected
    Given fetchHistory with timeframe "1h"
    When the driver processes the call
    Then MarketDataSchemaException is thrown
    And the message names the unsupported timeframe and points to the intraday endpoint
```

## 9. Block 3 — Error handling

```gherkin
Feature: Error mapping

  Scenario: 404 maps to MarketDataNotFoundException
    Given the EODHD endpoint returns HTTP 404
    When I fetch
    Then MarketDataNotFoundException is thrown
    And the exception names the symbol

  Scenario: 401 / 403 maps to MarketDataUnavailableException
    Given the EODHD endpoint returns 401 or 403
    When I fetch
    Then MarketDataUnavailableException is thrown
    And the message hints at authentication issue

  Scenario: 429 maps to MarketDataUnavailableException with rate-limit hint
    Given the EODHD endpoint returns 429
    When I fetch
    Then MarketDataUnavailableException is thrown
    And the message includes "rate limit"

  Scenario: 5xx maps to MarketDataUnavailableException
    Given the EODHD endpoint returns 503
    When I fetch
    Then MarketDataUnavailableException is thrown

  Scenario: Malformed JSON maps to MarketDataSchemaException
    Given the response body is not valid JSON
    When I fetch
    Then MarketDataSchemaException is thrown
    And the cause is the JSON parse exception

  Scenario: Network timeout
    Given the HTTP request times out
    When I fetch
    Then MarketDataUnavailableException is thrown
    And the cause is the timeout exception

  Scenario: No automatic retry
    Given the first HTTP attempt fails with a 503
    When I fetch
    Then exactly one HTTP request is made
    And MarketDataUnavailableException is thrown immediately
```

## 10. Block 4 — Configuration validation

```gherkin
Feature: Configuration validation at construction

  Scenario: Missing API token
    When I construct EodhdMarketDataSource with apiToken = "" or null
    Then IllegalArgumentException is thrown

  Scenario: Negative HTTP timeout
    When I construct with httpTimeout = Duration.ofSeconds(-1)
    Then IllegalArgumentException is thrown

  Scenario: Null HTTP client
    When I construct with httpClient = null
    Then NullPointerException is thrown

  Scenario: Default User-Agent includes module name
    Given the driver is constructed
    When it sends a request
    Then the User-Agent header starts with "frau-holle-eodhd/"
```

## 11. Out of scope for `frau-holle-eodhd`

- Intraday endpoints (`/api/intraday/...`) — different schema, different rate limits
- WebSocket streaming
- Fundamentals queries (`/api/fundamentals/...`)
- News (`/api/news/...`)
- Splits and dividends (`/api/splits/...`)
- Forex tick data
- Bulk download endpoints
- Internal retry / circuit-breaker (consumer concern)
- Internal caching (consumer concern)
- Adjusted close usage (v1 uses raw close)
- Multi-symbol batched fetches (one symbol per call)
- Async / reactive API (the port is synchronous)
- Token rotation / refresh (consumer concern)

## 12. Implementation delegation to Claude Code

Claude Code is responsible for:

- Package layout (suggested: `<group>.frauholle.eodhd` with a single public class `EodhdMarketDataSource`, plus small interfaces `HttpExecutor` and `JsonReader` exposed for consumer-injection, plus default JDK-only implementations)
- URL construction with proper query parameter encoding
- HTTP request execution using the injected `HttpExecutor`
- JSON parsing using the injected `JsonReader`
- Numeric parsing to `BigDecimal` from JSON text (not float)
- Status-code-to-exception mapping per §3
- Test infrastructure using a mock `HttpExecutor` (no real network in tests)

What Claude Code MUST NOT do unilaterally:

- Add other EODHD endpoints (intraday, fundamentals, news, splits)
- Add internal retry / circuit-breaker
- Add internal caching
- Use `adjusted_close` instead of `close`
- Use `double` for prices (must be `BigDecimal` parsed from JSON text)
- Bring in a specific HTTP client or JSON library (depends only on the injected interfaces; JDK-only default impl)
- Add reflective bean wiring or DI annotations
- Hardcode the API token (must be configured)
- Log the API token at any level (security concern)
- Add intraday timeframe support without scope change
