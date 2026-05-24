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

  Scenario: Bars out of order are re-sequenced into ascending order (not rejected)
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"date":"2024-01-04","open":1,"high":2,"low":0.5,"close":1.5,"volume":100},
        {"date":"2024-01-02","open":1.6,"high":2.6,"low":1.1,"close":1.9,"volume":200}
      ]
      """
    When I fetch history for "AAPL.US" as "1d"
    Then the result has 2 bars
    And bar 0 has time "2024-01-02T00:00:00Z"
    And bar 1 has time "2024-01-04T00:00:00Z"

  Scenario: Duplicate consecutive dates keep the last bar (no error)
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"date":"2024-01-02","open":1,"high":2,"low":0.5,"close":1.5,"volume":100},
        {"date":"2024-01-02","open":1.6,"high":2.6,"low":1.1,"close":1.9,"volume":200}
      ]
      """
    When I fetch history for "AAPL.US" as "1d"
    Then the result has 1 bar
    And bar 0 has close 1.9

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

  # The request URL carries ?api_token=<token> (EODHD has no header auth), so an
  # exception message that embedded the URL would leak the secret into logs /
  # stack traces. CLAUDE.md section 12 forbids this. Guards every error path.
  Scenario Outline: HTTP error exceptions never reveal the API token
    Given an EODHD data source
    And the endpoint responds with HTTP status <status>
    When I fetch history for "AAPL.US" as "1d"
    Then no exception in the chain reveals the API token

    Examples:
      | status |
      | 404    |
      | 401    |
      | 403    |
      | 429    |
      | 503    |

  Scenario: A malformed-JSON exception never reveals the API token
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      not valid json at all
      """
    When I fetch history for "AAPL.US" as "1d"
    Then no exception in the chain reveals the API token

  Scenario: A timeout exception never reveals the API token
    Given an EODHD data source
    And the endpoint times out
    When I fetch history for "AAPL.US" as "1d"
    Then no exception in the chain reveals the API token
