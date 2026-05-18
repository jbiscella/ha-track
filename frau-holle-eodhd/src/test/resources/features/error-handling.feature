Feature: Error mapping
  Maps frau-holle-eodhd/CLAUDE.md section 9 (Block 3).

  Scenario: HTTP 404 maps to MarketDataNotFoundException
    Given an EODHD data source
    And the endpoint responds with HTTP status 404
    When I fetch history for "AAPL.US" as "1d"
    Then a MarketDataNotFoundException is thrown
    And the exception message mentions "AAPL.US"

  Scenario Outline: HTTP 401 and 403 map to MarketDataUnavailableException
    Given an EODHD data source
    And the endpoint responds with HTTP status <status>
    When I fetch history for "AAPL.US" as "1d"
    Then a MarketDataUnavailableException is thrown
    And the exception message mentions "authentication"

    Examples:
      | status |
      | 401    |
      | 403    |

  Scenario: HTTP 429 maps to MarketDataUnavailableException with a rate-limit hint
    Given an EODHD data source
    And the endpoint responds with HTTP status 429
    When I fetch history for "AAPL.US" as "1d"
    Then a MarketDataUnavailableException is thrown
    And the exception message mentions "rate limit"

  Scenario: HTTP 503 maps to MarketDataUnavailableException
    Given an EODHD data source
    And the endpoint responds with HTTP status 503
    When I fetch history for "AAPL.US" as "1d"
    Then a MarketDataUnavailableException is thrown

  Scenario: Malformed JSON maps to MarketDataSchemaException
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      not valid json at all
      """
    When I fetch history for "AAPL.US" as "1d"
    Then a MarketDataSchemaException is thrown
    And the exception cause is a JsonParseException

  Scenario: Network timeout maps to MarketDataUnavailableException
    Given an EODHD data source
    And the endpoint times out
    When I fetch history for "AAPL.US" as "1d"
    Then a MarketDataUnavailableException is thrown
    And the exception cause is the timeout exception

  Scenario: No automatic retry on a 503
    Given an EODHD data source
    And the endpoint responds with HTTP status 503
    When I fetch history for "AAPL.US" as "1d"
    Then a MarketDataUnavailableException is thrown
    And exactly 1 HTTP request was made

  Scenario: Bars out of ascending date order map to MarketDataSchemaException
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"date":"2024-01-04","open":1,"high":2,"low":0.5,"close":1.5,"volume":100},
        {"date":"2024-01-02","open":1,"high":2,"low":0.5,"close":1.5,"volume":100}
      ]
      """
    When I fetch history for "AAPL.US" as "1d"
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "ascending date order"

  Scenario: Duplicate consecutive dates map to MarketDataSchemaException
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"date":"2024-01-02","open":1,"high":2,"low":0.5,"close":1.5,"volume":100},
        {"date":"2024-01-02","open":1,"high":2,"low":0.5,"close":1.5,"volume":100}
      ]
      """
    When I fetch history for "AAPL.US" as "1d"
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "ascending date order"

  Scenario: A record missing the date field maps to MarketDataSchemaException
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [{"open":1,"high":2,"low":0.5,"close":1.5,"volume":100}]
      """
    When I fetch history for "AAPL.US" as "1d"
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "date"

  Scenario: A top-level JSON object instead of an array maps to MarketDataSchemaException
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      {"date":"2024-01-02","open":1,"high":2,"low":0.5,"close":1.5}
      """
    When I fetch history for "AAPL.US" as "1d"
    Then a MarketDataSchemaException is thrown
    And the exception cause is a JsonParseException
