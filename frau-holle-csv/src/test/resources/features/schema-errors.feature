Feature: Schema error reporting
  Maps frau-holle-csv/CLAUDE.md section 6 (Block 2).

  Scenario: Missing required column
    Given a base directory
    And a CSV file "AAPL_1d.csv" with content:
      """
      time,open,high,close
      2024-01-02T00:00:00Z,1,2,1.5
      """
    And a CSV data source with the default pattern
    When I fetch history for "AAPL" "1d" over the full range
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "low"

  Scenario: Non-ISO timestamp
    Given a base directory
    And a CSV file "AAPL_1d.csv" with content:
      """
      time,open,high,low,close
      2024-01-15 00:00:00,1,2,0.5,1.5
      """
    And a CSV data source with the default pattern
    When I fetch history for "AAPL" "1d" over the full range
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "line 2"
    And the exception message mentions "2024-01-15 00:00:00"

  Scenario: Non-numeric price
    Given a base directory
    And a CSV file "AAPL_1d.csv" with content:
      """
      time,open,high,low,close
      2024-01-02T00:00:00Z,N/A,2,0.5,1.5
      """
    And a CSV data source with the default pattern
    When I fetch history for "AAPL" "1d" over the full range
    Then a MarketDataSchemaException is thrown
    And the exception message mentions "line 2"

  Scenario: Comment lines are ignored
    Given a base directory
    And a CSV file "AAPL_1d.csv" with content:
      """
      # AAPL daily export
      time,open,high,low,close
      # a comment between header and data
      2024-01-02T00:00:00Z,1,2,0.5,1.5
      """
    And a CSV data source with the default pattern
    When I fetch history for "AAPL" "1d" over the full range
    Then the result has 1 bars
    And no exception is thrown

  Scenario: Blank lines are ignored
    Given a base directory
    And a CSV file "AAPL_1d.csv" with content:
      """
      time,open,high,low,close
      2024-01-02T00:00:00Z,1,2,0.5,1.5

      2024-01-03T00:00:00Z,1,2,0.5,1.5
      """
    And a CSV data source with the default pattern
    When I fetch history for "AAPL" "1d" over the full range
    Then the result has 2 bars
    And no exception is thrown
