Feature: EodhdMarketDataSource basic behavior
  Maps frau-holle-eodhd/CLAUDE.md section 7 (Block 1).

  Scenario: Fetch a small range
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"date":"2024-01-02","open":187.15,"high":188.44,"low":183.89,"close":185.64,"adjusted_close":185.0,"volume":82488682},
        {"date":"2024-01-03","open":184.22,"high":185.88,"low":183.43,"close":184.25,"adjusted_close":184.0,"volume":58414460},
        {"date":"2024-01-04","open":182.15,"high":183.09,"low":180.88,"close":181.91,"adjusted_close":181.0,"volume":71983566}
      ]
      """
    When I fetch history for "AAPL.US" as "1d"
    Then the result has 3 bars
    And bar 0 has time "2024-01-02T00:00:00Z"
    And bar 0 has close 185.64
    And bar 0 has volume 82488682
    And bar 2 has time "2024-01-04T00:00:00Z"

  Scenario: Volume present
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [{"date":"2024-01-02","open":1,"high":2,"low":0.5,"close":1.5,"volume":100000}]
      """
    When I fetch history for "AAPL.US" as "1d"
    Then the result has 1 bars
    And bar 0 has volume 100000

  Scenario: Volume null or missing produces an empty volume
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"date":"2024-01-02","open":1,"high":2,"low":0.5,"close":1.5,"volume":null},
        {"date":"2024-01-03","open":1,"high":2,"low":0.5,"close":1.5}
      ]
      """
    When I fetch history for "AAPL.US" as "1d"
    Then the result has 2 bars
    And bar 0 has no volume
    And bar 1 has no volume

  Scenario: Empty range returns an empty list
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      []
      """
    When I fetch history for "AAPL.US" as "1d"
    Then the result has 0 bars
    And no exception is thrown

  Scenario: Adjusted close is ignored, raw close is used
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [{"date":"2024-01-02","open":1,"high":2,"low":0.5,"close":100.0,"adjusted_close":95.0,"volume":1}]
      """
    When I fetch history for "AAPL.US" as "1d"
    Then the result has 1 bars
    And bar 0 has close 100.0
