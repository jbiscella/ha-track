Feature: Heikin Ashi calculation
  Maps commons/CLAUDE.md section 3.3 (Block 1).

  Scenario: Seed bar from a single OHLC with no previous HA
    Given an OHLC bar with open=10, high=12, low=9, close=11 at time "2024-01-01T00:00:00Z"
    And no previous HA bar
    When I compute the HA bar
    Then haClose is 10.5
    And haOpen is 10.5
    And haHigh is 12
    And haLow is 9
    And the HA bar time is "2024-01-01T00:00:00Z"

  Scenario: Running bar with previous HA available
    Given a previous HA bar with haOpen=10.5, haHigh=12, haLow=9, haClose=10.5
    And an OHLC bar with open=11, high=13, low=10.5, close=12.5 at time "2024-01-02T00:00:00Z"
    When I compute the HA bar
    Then haClose is 11.75
    And haOpen is 10.5
    And haHigh is 13
    And haLow is 10.5

  Scenario: Time of the HA bar equals the time of the source OHLC
    Given an OHLC bar with open=10, high=12, low=9, close=11 at time "2024-06-15T13:30:00Z"
    And no previous HA bar
    When I compute the HA bar
    Then the HA bar time is "2024-06-15T13:30:00Z"

  Scenario: Chain seeded from empty previous over multiple bars
    Given no previous HA bar
    And the following OHLC bars:
      | time                 | open | high | low | close |
      | 2024-01-01T00:00:00Z | 10   | 12   | 9   | 11    |
      | 2024-01-02T00:00:00Z | 11   | 13   | 10  | 12    |
      | 2024-01-03T00:00:00Z | 12   | 14   | 11  | 13    |
    When I compute the HA chain
    Then the HA chain has 3 bars
    And the HA chain bars are:
      | haOpen | haHigh | haLow | haClose |
      | 10.5   | 12     | 9     | 10.5    |
      | 10.5   | 13     | 10    | 11.5    |
      | 11.0   | 14     | 11    | 12.5    |

  Scenario: Chain seeded from an explicit previous HA bar
    Given a previous HA bar with haOpen=10.5, haHigh=12, haLow=9, haClose=10.5
    And the following OHLC bars:
      | time                 | open | high | low | close |
      | 2024-01-02T00:00:00Z | 11   | 13   | 10  | 12    |
      | 2024-01-03T00:00:00Z | 12   | 14   | 11  | 13    |
    When I compute the HA chain
    Then the HA chain has 2 bars
    And the HA chain bars are:
      | haOpen | haHigh | haLow | haClose |
      | 10.5   | 13     | 10    | 11.5    |
      | 11.0   | 14     | 11    | 12.5    |

  Scenario: Empty input chain returns an empty list
    Given no previous HA bar
    And the following OHLC bars:
      | time | open | high | low | close |
    When I compute the HA chain
    Then the HA chain has 0 bars
    And no exception is thrown
