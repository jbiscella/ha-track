Feature: Shared indicator calculators

  Reference-value tests for the indicator calculators. A cell of "null" in an
  expected series means the calculator has not produced a value for that bar
  (warm-up window). Numeric comparisons use BigDecimal.compareTo, so "2" and
  "2.000" are equal.

  Scenario: SMA equals the rolling arithmetic mean
    Given the price series "1, 2, 3, 4, 5"
    When I compute SMA with period 3
    Then the indicator series equals "null, null, 2, 3, 4"

  Scenario: SMA period 1 is the source series itself
    Given the price series "7, 8, 9"
    When I compute SMA with period 1
    Then the indicator series equals "7, 8, 9"

  Scenario: Rolling maximum over the trailing window
    Given the price series "3, 1, 4, 1, 5, 9, 2, 6"
    When I compute RollingMax with period 3
    Then the indicator series equals "null, null, 4, 4, 5, 9, 9, 9"

  Scenario: Rolling minimum over the trailing window
    Given the price series "3, 1, 4, 1, 5, 9, 2, 6"
    When I compute RollingMin with period 3
    Then the indicator series equals "null, null, 1, 1, 1, 1, 2, 2"

  Scenario: Rolling extrema with period 1 echo the source series
    Given the price series "7, 8, 9"
    When I compute RollingMax with period 1
    Then the indicator series equals "7, 8, 9"

  Scenario: EMA seeds on the SMA of the first period values
    Given the price series "1, 2, 3, 4, 5"
    When I compute EMA with period 3
    Then the indicator series equals "null, null, 2, 3, 4"

  Scenario: RSI is 100 for a strictly rising series
    Given the price series "1, 2, 3, 4, 5, 6"
    When I compute RSI with period 3
    Then the indicator series equals "null, null, null, 100, 100, 100"

  Scenario: RSI is 0 for a strictly falling series
    Given the price series "6, 5, 4, 3, 2, 1"
    When I compute RSI with period 3
    Then the indicator series equals "null, null, null, 0, 0, 0"

  Scenario: RSI tracks Wilder smoothing on an alternating series
    Given the price series "10, 11, 10, 11, 10, 11"
    When I compute RSI with period 2
    Then the indicator series equals "null, null, 50, 75, 37.5, 68.75"

  Scenario: MACD of a flat series is zero
    Given the price series "5, 5, 5, 5, 5, 5, 5, 5"
    When I compute MACD with fast 2 slow 4 signal 2
    Then the MACD line equals "null, null, null, 0, 0, 0, 0, 0"
    And the MACD signal line equals "null, null, null, null, 0, 0, 0, 0"
    And the MACD histogram equals "null, null, null, null, 0, 0, 0, 0"

  Scenario: MACD of a linear ramp has a constant line and zero histogram
    Given the price series "1, 2, 3, 4, 5, 6, 7, 8"
    When I compute MACD with fast 2 slow 4 signal 2
    Then the MACD line equals "null, null, null, 1, 1, 1, 1, 1"
    And the MACD signal line equals "null, null, null, null, 1, 1, 1, 1"
    And the MACD histogram equals "null, null, null, null, 0, 0, 0, 0"

  Scenario: Bollinger Bands wrap the SMA by multiplier standard deviations
    Given the price series "3, 5"
    When I compute Bollinger Bands with period 2 and multiplier 2
    Then the Bollinger upper band equals "null, 6"
    And the Bollinger middle band equals "null, 4"
    And the Bollinger lower band equals "null, 2"

  Scenario: Bollinger Bands collapse onto the mean for a flat series
    Given the price series "5, 5, 5, 5"
    When I compute Bollinger Bands with period 3 and multiplier 2
    Then the Bollinger upper band equals "null, null, 5, 5"
    And the Bollinger middle band equals "null, null, 5, 5"
    And the Bollinger lower band equals "null, null, 5, 5"

  Scenario: ATR is the Wilder-smoothed true range
    Given the OHLC bars:
      | high | low | close |
      | 10   | 8   | 9     |
      | 11   | 9   | 10    |
      | 14   | 10  | 10    |
    When I compute ATR with period 2
    Then the indicator series equals "null, 2, 3"

  Scenario: Stochastic oscillator %K and %D over the high-low range
    Given the OHLC bars:
      | high | low | close |
      | 10   | 8   | 9     |
      | 12   | 9   | 11    |
      | 14   | 11  | 13    |
    When I compute Stochastic with kPeriod 2 dPeriod 2 smoothing 1
    Then the Stochastic %K equals "null, 75, 80"
    And the Stochastic %D equals "null, null, 77.5"

  Scenario: ADX warms up after 2 * period bars
    Given the OHLC bars:
      | high | low | close |
      | 10   | 8   | 9     |
      | 11   | 9   | 10    |
      | 12   | 10  | 11    |
      | 13   | 11  | 12    |
      | 14   | 12  | 13    |
      | 15   | 13  | 14    |
      | 16   | 14  | 15    |
    When I compute ADX with period 2
    Then indicator values before index 4 are null
    And indicator values from index 4 are present

  Scenario: A period below 1 is rejected eagerly
    Given the price series "1, 2, 3"
    When I compute SMA with period 0
    Then an IllegalArgumentException is thrown
