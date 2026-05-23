Feature: OHLC aggregation to a coarser period
  UTC-only. DAY buckets by calendar day; WEEK by ISO week (Monday 00:00Z).
  2024-01-01 is a Monday.

  Scenario: Daily aggregation buckets intraday bars by UTC day
    Given the following OHLC bars:
      | time                 | open | high | low | close |
      | 2024-01-01T09:00:00Z | 100  | 110  | 95  | 105   |
      | 2024-01-01T15:00:00Z | 105  | 115  | 100 | 108   |
      | 2024-01-02T09:00:00Z | 108  | 120  | 107 | 118   |
    When I aggregate to period "1d"
    Then the aggregated series has 2 bars
    And aggregated bar 0 has open=100, high=115, low=95, close=108 at time "2024-01-01T00:00:00Z"
    And aggregated bar 1 has open=108, high=120, low=107, close=118 at time "2024-01-02T00:00:00Z"

  Scenario: Weekly aggregation buckets by ISO week starting Monday
    Given the following OHLC bars:
      | time                 | open | high | low | close |
      | 2024-01-01T12:00:00Z | 100  | 110  | 95  | 105   |
      | 2024-01-03T12:00:00Z | 105  | 118  | 102 | 115   |
      | 2024-01-08T12:00:00Z | 115  | 125  | 112 | 120   |
    When I aggregate to period "1w"
    Then the aggregated series has 2 bars
    And aggregated bar 0 has open=100, high=118, low=95, close=115 at time "2024-01-01T00:00:00Z"
    And aggregated bar 1 has open=115, high=125, low=112, close=120 at time "2024-01-08T00:00:00Z"

  Scenario: Empty periods between gaps are skipped (no bar emitted for the gap day)
    Given the following OHLC bars:
      | time                 | open | high | low | close |
      | 2024-01-01T12:00:00Z | 100  | 110  | 95  | 105   |
      | 2024-01-03T12:00:00Z | 105  | 118  | 102 | 115   |
    When I aggregate to period "1d"
    Then the aggregated series has 2 bars
    And aggregated bar 0 has open=100, high=110, low=95, close=105 at time "2024-01-01T00:00:00Z"
    And aggregated bar 1 has open=105, high=118, low=102, close=115 at time "2024-01-03T00:00:00Z"

  Scenario: A single bar produces a single period bar
    Given the following OHLC bars:
      | time                 | open | high | low | close |
      | 2024-01-01T09:30:00Z | 100  | 110  | 95  | 105   |
    When I aggregate to period "1d"
    Then the aggregated series has 1 bars
    And aggregated bar 0 has open=100, high=110, low=95, close=105 at time "2024-01-01T00:00:00Z"

  Scenario: A Sunday bar and the following Monday bar fall in different ISO weeks
    Given the following OHLC bars:
      | time                 | open | high | low | close |
      | 2024-01-07T12:00:00Z | 100  | 110  | 95  | 105   |
      | 2024-01-08T12:00:00Z | 105  | 118  | 102 | 115   |
    When I aggregate to period "1w"
    Then the aggregated series has 2 bars
    And aggregated bar 0 has open=100, high=110, low=95, close=105 at time "2024-01-01T00:00:00Z"
    And aggregated bar 1 has open=105, high=118, low=102, close=115 at time "2024-01-08T00:00:00Z"

  Scenario: Volume is summed when every bar in the period carries it
    Given the following OHLC bars with volume:
      | time                 | open | high | low | close | volume |
      | 2024-01-01T09:00:00Z | 100  | 110  | 95  | 105   | 10     |
      | 2024-01-01T15:00:00Z | 105  | 115  | 100 | 108   | 25     |
    When I aggregate to period "1d"
    Then the aggregated series has 1 bars
    And aggregated bar 0 has volume 35

  Scenario: Volume is absent when any bar in the period lacks it
    Given the following OHLC bars with volume:
      | time                 | open | high | low | close | volume |
      | 2024-01-01T09:00:00Z | 100  | 110  | 95  | 105   | 10     |
      | 2024-01-01T15:00:00Z | 105  | 115  | 100 | 108   |        |
    When I aggregate to period "1d"
    Then the aggregated series has 1 bars
    And aggregated bar 0 has no volume

  Scenario: Intraday aggregation period is rejected
    Given the following OHLC bars:
      | time                 | open | high | low | close |
      | 2024-01-01T09:00:00Z | 100  | 110  | 95  | 105   |
    When I aggregate to period "1h"
    Then an IllegalArgumentException is thrown

  Scenario: Multi-day aggregation period is rejected
    Given the following OHLC bars:
      | time                 | open | high | low | close |
      | 2024-01-01T09:00:00Z | 100  | 110  | 95  | 105   |
    When I aggregate to period "2d"
    Then an IllegalArgumentException is thrown
