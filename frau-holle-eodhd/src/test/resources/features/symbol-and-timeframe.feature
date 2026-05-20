Feature: Symbol and timeframe handling
  Maps frau-holle-eodhd/CLAUDE.md section 8 (Block 2).

  Scenario: Symbol is passed through verbatim
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      []
      """
    When I fetch history for "MSFT.US" as "1d"
    Then the request URL contains "/api/eod/MSFT.US"

  Scenario: Daily timeframe maps to period d
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      []
      """
    When I fetch history for "AAPL.US" as "1d"
    Then the request URL contains "&period=d"

  Scenario: Weekly timeframe maps to period w
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      []
      """
    When I fetch history for "AAPL.US" as "1w"
    Then the request URL contains "&period=w"

  Scenario: Monthly timeframe maps to period m
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      []
      """
    When I fetch history for "AAPL.US" as "1M"
    Then the request URL contains "&period=m"

  Scenario: Hourly timeframe routes to the intraday endpoint with interval=1h
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      []
      """
    When I fetch history for "AAPL.US" as "1h"
    Then the request URL contains "/api/intraday/AAPL.US"
    And the request URL contains "&interval=1h"

  Scenario: 5-minute timeframe routes to the intraday endpoint with interval=5m
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      []
      """
    When I fetch history for "AAPL.US" as "5m"
    Then the request URL contains "/api/intraday/AAPL.US"
    And the request URL contains "&interval=5m"

  Scenario: 1-minute timeframe routes to the intraday endpoint with interval=1m
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      []
      """
    When I fetch history for "AAPL.US" as "1m"
    Then the request URL contains "/api/intraday/AAPL.US"
    And the request URL contains "&interval=1m"

  Scenario: Intraday from/to are passed as UNIX epoch seconds, not YYYY-MM-DD
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      []
      """
    When I fetch history for "AAPL.US" as "1h"
    Then the request URL contains "&from=1704067200"
    And the request URL contains "&to=1735603200"

  Scenario: Unsupported intraday interval is rejected
    Given an EODHD data source
    When I fetch history for "AAPL.US" as "15m"
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "15m"
    And the exception message mentions "1m, 5m, 1h"

  Scenario: Unsupported daily timeframe is rejected
    Given an EODHD data source
    When I fetch history for "AAPL.US" as "3d"
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "3d"
    And the exception message mentions "1d, 1w, 1M"
