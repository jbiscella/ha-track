Feature: Timeframe wire format
  Maps commons/CLAUDE.md section 5.

  Scenario Outline: Round-trip of standard timeframes
    When I parse the timeframe wire string "<wire>"
    Then re-serializing it returns "<wire>"

    Examples:
      | wire |
      | 1s   |
      | 5m   |
      | 15m  |
      | 1h   |
      | 4h   |
      | 1d   |
      | 1w   |
      | 1M   |
      | 1y   |

  Scenario: Lowercase m parses as MINUTE
    When I parse the timeframe wire string "1m"
    Then the timeframe unit is MINUTE
    And re-serializing it returns "1m"

  Scenario: Uppercase M parses as MONTH
    When I parse the timeframe wire string "1M"
    Then the timeframe unit is MONTH
    And re-serializing it returns "1M"

  Scenario Outline: Zero or negative amount is rejected
    When I parse the timeframe wire string "<wire>"
    Then an IllegalArgumentException is thrown

    Examples:
      | wire |
      | 0d   |
      | -5m  |

  Scenario: Unknown unit suffix is rejected
    When I parse the timeframe wire string "5x"
    Then an IllegalArgumentException is thrown
