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

  Scenario: Intraday timeframe is rejected
    Given an EODHD data source
    When I fetch history for "AAPL.US" as "1h"
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "1h"
    And the exception message mentions "intraday"
