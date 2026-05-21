Feature: Intraday endpoint successful fetch and response parsing
  Maps frau-holle-eodhd/CLAUDE.md §3.1 (intraday).
  The /api/intraday response shape differs from /api/eod: rows carry
  `timestamp` (unix seconds, bar start UTC), `gmtoffset`, `datetime`,
  and OHLCV. The driver reads `timestamp` and maps it directly to
  `Instant.ofEpochSecond(...)` for the bar's time.

  Scenario: Intraday 1h response is parsed correctly
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"timestamp": 1704067200, "gmtoffset": 0, "datetime": "2024-01-01 00:00:00",
         "open": 100.10, "high": 101.20, "low": 99.50, "close": 100.80, "volume": 12345},
        {"timestamp": 1704070800, "gmtoffset": 0, "datetime": "2024-01-01 01:00:00",
         "open": 100.80, "high": 102.00, "low": 100.50, "close": 101.50, "volume": 23456},
        {"timestamp": 1704074400, "gmtoffset": 0, "datetime": "2024-01-01 02:00:00",
         "open": 101.50, "high": 101.90, "low": 100.80, "close": 101.10, "volume": 18900}
      ]
      """
    When I fetch history for "AAPL.US" as "1h"
    Then the result has 3 bars
    And bar 0 has time "2024-01-01T00:00:00Z"
    And bar 0 has close 100.80
    And bar 0 has volume 12345
    And bar 1 has time "2024-01-01T01:00:00Z"
    And bar 2 has time "2024-01-01T02:00:00Z"

  Scenario: Intraday 5m response is parsed correctly
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"timestamp": 1704067200, "open": 100.10, "high": 100.30, "low": 100.00, "close": 100.20, "volume": 500},
        {"timestamp": 1704067500, "open": 100.20, "high": 100.40, "low": 100.10, "close": 100.30, "volume": 600}
      ]
      """
    When I fetch history for "AAPL.US" as "5m"
    Then the result has 2 bars
    And bar 0 has time "2024-01-01T00:00:00Z"
    And bar 1 has time "2024-01-01T00:05:00Z"

  Scenario: Intraday volume null produces Optional.empty
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"timestamp": 1704067200, "open": 100.10, "high": 100.30, "low": 100.00, "close": 100.20, "volume": null}
      ]
      """
    When I fetch history for "AAPL.US" as "1h"
    Then the result has 1 bar
    And bar 0 has no volume

  Scenario: Intraday empty response is not an error
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      []
      """
    When I fetch history for "AAPL.US" as "1h"
    Then the result has 0 bars
    And no exception is thrown

  Scenario: Intraday bars out of ascending order is rejected
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"timestamp": 1704070800, "open": 100, "high": 101, "low": 99, "close": 100, "volume": 100},
        {"timestamp": 1704067200, "open": 100, "high": 101, "low": 99, "close": 100, "volume": 100}
      ]
      """
    When I fetch history for "AAPL.US" as "1h"
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "ascending"

  Scenario: Intraday row missing timestamp is rejected
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"open": 100.10, "high": 100.30, "low": 100.00, "close": 100.20, "volume": 500}
      ]
      """
    When I fetch history for "AAPL.US" as "1h"
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "timestamp"

  Scenario: Intraday row with non-numeric timestamp is rejected
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"timestamp": "not-a-number", "open": 100, "high": 101, "low": 99, "close": 100, "volume": 100}
      ]
      """
    When I fetch history for "AAPL.US" as "1h"
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "timestamp"

  Scenario: Intraday row with an out-of-range timestamp is rejected as a schema error
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"timestamp": 99999999999999999, "open": 100, "high": 101, "low": 99, "close": 100, "volume": 100}
      ]
      """
    When I fetch history for "AAPL.US" as "1h"
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "timestamp"
