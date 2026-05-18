Feature: Configuration validation at construction
  Maps frau-holle-eodhd/CLAUDE.md section 10 (Block 4).

  Scenario: Empty API token is rejected
    When I construct a data source with an empty API token
    Then an IllegalArgumentException is thrown

  Scenario: Null API token is rejected
    When I construct a data source with a null API token
    Then an IllegalArgumentException is thrown

  Scenario: Negative HTTP timeout is rejected
    When I construct a data source with a negative HTTP timeout
    Then an IllegalArgumentException is thrown

  Scenario: Null HTTP executor is rejected
    When I construct a data source with a null HTTP executor
    Then a NullPointerException is thrown

  Scenario: Default User-Agent header names the module
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      []
      """
    When I fetch history for "AAPL.US" as "1d"
    Then the User-Agent header starts with "frau-holle-eodhd/"
