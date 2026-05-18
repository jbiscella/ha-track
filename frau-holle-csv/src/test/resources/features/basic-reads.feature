Feature: CsvMarketDataSource basic behavior
  Maps frau-holle-csv/CLAUDE.md section 5 (Block 1).

  Scenario: Read a well-formed CSV file
    Given a base directory
    And a CSV file "AAPL_1d.csv" with content:
      """
      time,open,high,low,close,volume
      2024-01-02T00:00:00Z,187.15,188.44,183.89,185.64,82488682
      2024-01-03T00:00:00Z,184.22,185.88,183.43,184.25,58414460
      2024-01-04T00:00:00Z,182.15,183.09,180.88,181.91,71983566
      """
    And a CSV data source with the default pattern
    When I fetch history for "AAPL" "1d" over the full range
    Then the result has 3 bars
    And bar 0 has time "2024-01-02T00:00:00Z"
    And bar 0 has close 185.64
    And bar 0 has volume 82488682
    And bar 2 has time "2024-01-04T00:00:00Z"

  Scenario: Filtering by since and until is inclusive
    Given a base directory
    And a CSV file "AAPL_1d.csv" with content:
      """
      time,open,high,low,close
      2024-01-01T00:00:00Z,1,2,0.5,1.5
      2024-01-02T00:00:00Z,1,2,0.5,1.5
      2024-01-03T00:00:00Z,1,2,0.5,1.5
      2024-01-04T00:00:00Z,1,2,0.5,1.5
      2024-01-05T00:00:00Z,1,2,0.5,1.5
      """
    And a CSV data source with the default pattern
    When I fetch history for "AAPL" "1d" from "2024-01-02T00:00:00Z" to "2024-01-04T00:00:00Z"
    Then the result has 3 bars
    And bar 0 has time "2024-01-02T00:00:00Z"
    And bar 2 has time "2024-01-04T00:00:00Z"

  Scenario: Range outside the file returns empty
    Given a base directory
    And a CSV file "AAPL_1d.csv" with content:
      """
      time,open,high,low,close
      2024-01-01T00:00:00Z,1,2,0.5,1.5
      2024-12-31T00:00:00Z,1,2,0.5,1.5
      """
    And a CSV data source with the default pattern
    When I fetch history for "AAPL" "1d" from "2025-06-01T00:00:00Z" to "2025-12-31T00:00:00Z"
    Then the result has 0 bars
    And no exception is thrown

  Scenario: File not found raises MarketDataNotFoundException
    Given a base directory
    And a CSV data source with the default pattern
    When I fetch history for "UNKNOWN" "1d" over the full range
    Then a MarketDataNotFoundException is thrown
    And the exception message mentions "UNKNOWN"
    And the exception message mentions "UNKNOWN_1d.csv"

  Scenario: Volume column absent produces empty volumes
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
    And every bar has no volume

  Scenario: Empty volume cell produces an empty volume for that bar
    Given a base directory
    And a CSV file "AAPL_1d.csv" with content:
      """
      time,open,high,low,close,volume
      2024-01-02T00:00:00Z,1,2,0.5,1.5,100
      2024-01-03T00:00:00Z,1,2,0.5,1.5,
      """
    And a CSV data source with the default pattern
    When I fetch history for "AAPL" "1d" over the full range
    Then the result has 2 bars
    And bar 0 has volume 100
    And bar 1 has no volume
