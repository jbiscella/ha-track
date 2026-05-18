Feature: File name pattern
  Maps frau-holle-csv/CLAUDE.md section 7 (Block 3).

  Scenario: Default pattern resolves files
    Given a base directory
    And a CSV file "AAPL_1d.csv" with content:
      """
      time,open,high,low,close
      2024-01-02T00:00:00Z,1,2,0.5,1.5
      """
    And a CSV data source with the default pattern
    When I fetch history for "AAPL" "1d" over the full range
    Then the result has 1 bars
    And no exception is thrown

  Scenario: Custom pattern resolves files
    Given a base directory
    And a CSV file "1d/AAPL.csv" with content:
      """
      time,open,high,low,close
      2024-01-02T00:00:00Z,1,2,0.5,1.5
      """
    And a CSV data source with pattern "{timeframe}/{symbol}.csv"
    When I fetch history for "AAPL" "1d" over the full range
    Then the result has 1 bars
    And no exception is thrown

  Scenario: Pattern without the symbol placeholder is rejected at construction
    Given a base directory
    When I construct a CSV data source with pattern "data_{timeframe}.csv"
    Then an IllegalArgumentException is thrown
