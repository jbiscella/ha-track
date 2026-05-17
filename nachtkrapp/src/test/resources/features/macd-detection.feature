Feature: MACD pattern detection
  Maps nachtkrapp/CLAUDE.md section 11 (Block 5).

  Scenario: MACDBullishCross when the MACD line crosses above the signal line
    Given a detection spec builder
    And an OHLC series accelerating down for 40 bars then up for 40 bars
    And the rule MACDSignalCrossRule with fast 12 slow 26 signal 9 source CLOSE
    When I detect
    Then the result contains at least 1 MACDBullishCross
    And every MACDBullishCross match has flavor EVENT

  Scenario: MACDBearishCross when the MACD line crosses below the signal line
    Given a detection spec builder
    And an OHLC series accelerating up for 40 bars then down for 40 bars
    And the rule MACDSignalCrossRule with fast 12 slow 26 signal 9 source CLOSE
    When I detect
    Then the result contains at least 1 MACDBearishCross

  Scenario: MACDCrossedAboveZero when the MACD line crosses above zero
    Given a detection spec builder
    And an OHLC series falling for 30 bars then rising for 40 bars
    And the rule MACDZeroCrossRule with fast 12 slow 26 signal 9 source CLOSE
    When I detect
    Then the result contains at least 1 MACDCrossedAboveZero
    And every MACDCrossedAboveZero match has flavor EVENT
