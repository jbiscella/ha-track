Feature: Halt/no-trade bars and duplicate timestamps
  Real EODHD feeds contain bars with null OHLC (trading halts / no-trade hours)
  and, around DST transitions, a duplicated timestamp. These must not abort the
  whole download. Maps frau-holle-eodhd/CLAUDE.md section 3/6.

  Scenario: A daily bar with null OHLC is skipped, the valid bars load
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"date":"2024-01-02","open":100,"high":101,"low":99,"close":100.5,"volume":1000},
        {"date":"2024-01-03","open":null,"high":null,"low":null,"close":null,"volume":null},
        {"date":"2024-01-04","open":101,"high":102,"low":100,"close":101.5,"volume":1200}
      ]
      """
    When I fetch history for "AAPL.US" as "1d"
    Then the result has 2 bars
    And bar 0 has time "2024-01-02T00:00:00Z"
    And bar 1 has time "2024-01-04T00:00:00Z"

  Scenario: An intraday bar with null OHLC is skipped
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"timestamp":1704067200,"open":42280.2,"high":42517.6,"low":42277.7,"close":42477.2,"volume":229871616},
        {"timestamp":1704070800,"open":null,"high":null,"low":null,"close":null,"volume":null},
        {"timestamp":1704074400,"open":42471.4,"high":42718.7,"low":42433.7,"close":42622.8,"volume":325710848}
      ]
      """
    When I fetch history for "BTC-USD.CC" as "1h"
    Then the result has 2 bars
    And bar 0 has time "2024-01-01T00:00:00Z"
    And bar 1 has time "2024-01-01T02:00:00Z"

  Scenario: A bar with valid OHLC but null volume is KEPT (volume becomes empty)
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"timestamp":1704067200,"open":42280.2,"high":42517.6,"low":42277.7,"close":42477.2,"volume":null},
        {"timestamp":1704070800,"open":42471.4,"high":42718.7,"low":42433.7,"close":42622.8,"volume":325710848}
      ]
      """
    When I fetch history for "BTC-USD.CC" as "1h"
    Then the result has 2 bars
    And bar 0 has no volume
    And bar 1 has volume 325710848

  Scenario: A whole response of null-OHLC bars yields an empty list, not an error
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"date":"2024-01-02","open":null,"high":null,"low":null,"close":null,"volume":null},
        {"date":"2024-01-03","open":null,"high":null,"low":null,"close":null,"volume":null}
      ]
      """
    When I fetch history for "AAPL.US" as "1d"
    Then the result has 0 bars
    And no exception is thrown

  Scenario: A duplicated timestamp keeps the LAST bar (DST-transition artifact)
    Given an EODHD data source
    And the endpoint returns the JSON body:
      """
      [
        {"timestamp":1710032400,"open":68515.5,"high":69337.4,"low":68498.2,"close":69079.5,"volume":2024794112},
        {"timestamp":1710032400,"open":69092.0,"high":69330.8,"low":68961.3,"close":68984.1,"volume":492974080}
      ]
      """
    When I fetch history for "BTC-USD.CC" as "1h"
    Then the result has 1 bar
    And bar 0 has time "2024-03-10T01:00:00Z"
    And bar 0 has close 68984.1
