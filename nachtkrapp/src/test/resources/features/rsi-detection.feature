Feature: RSI pattern detection
  Maps nachtkrapp/CLAUDE.md section 10 (Block 4).

  Scenario: RSIOverbought state on bars exceeding the threshold
    Given a detection spec builder
    And an OHLC series of 20 bars with close strictly increasing
    And the rule RSIThresholdRule with period 14 overbought 70 oversold 30 source CLOSE
    When I detect
    Then the result contains 6 RSIOverbought
    And every RSIOverbought match has flavor STATE

  Scenario: RSIExitedOverbought event at the exit bar
    Given a detection spec builder
    And an OHLC series rising for 18 bars then falling for 18 bars
    And the rule RSIThresholdRule with period 14 overbought 70 oversold 30 source CLOSE
    When I detect
    Then the result contains 1 RSIExitedOverbought
    And every RSIExitedOverbought match has flavor EVENT

  Scenario: RSICrossedAbove50 event at the crossing bar
    Given a detection spec builder
    And an OHLC series falling for 18 bars then rising for 18 bars
    And the rule RSILevel50CrossRule with period 14 source CLOSE
    When I detect
    Then the result contains 1 RSICrossedAbove50
    And every RSICrossedAbove50 match has flavor EVENT
