Feature: DetectionSpecBuilder eager validation
  Maps nachtkrapp/CLAUDE.md section 7 (Block 1).

  Scenario: Missing series fails build
    Given a detection spec builder
    And the rule HADojiRule with maxBodyRatio 0.1
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V1"

  Scenario: Empty series fails build
    Given a detection spec builder
    And an empty OHLC series
    And the rule PriceVsMARule with SMA period 3 source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V2"

  Scenario: Out-of-order series fails build
    Given a detection spec builder
    And an OHLC series:
      | time                 | open | high | low | close |
      | 2024-01-02T00:00:00Z | 10   | 10   | 10  | 10    |
      | 2024-01-01T00:00:00Z | 11   | 11   | 11  | 11    |
    And the rule PriceVsMARule with SMA period 1 source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V3"

  Scenario: Duplicate time in series fails build
    Given a detection spec builder
    And an OHLC series:
      | time                 | open | high | low | close |
      | 2024-01-01T00:00:00Z | 10   | 10   | 10  | 10    |
      | 2024-01-01T00:00:00Z | 11   | 11   | 11  | 11    |
    And the rule PriceVsMARule with SMA period 1 source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V4"

  Scenario: HA rule on OHLC series fails build
    Given a detection spec builder
    And an OHLC series of 10 bars with close strictly increasing
    And the rule HAColorChangeRule with minStreakLength 3
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V5"

  Scenario: HA priceSource on OHLC series fails build
    Given a detection spec builder
    And an OHLC series of 10 bars with close strictly increasing
    And the rule PriceVsMARule with SMA period 3 source HA_CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V5"

  Scenario: OHLC priceSource on HA series fails build
    Given a detection spec builder
    And an HA series of 10 bars with haClose strictly increasing
    And the rule PriceVsMARule with SMA period 3 source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V5"

  Scenario: Insufficient bars fails build
    Given a detection spec builder
    And an OHLC series of 5 bars with close strictly increasing
    And the rule PriceVsMARule with SMA period 20 source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V6"

  Scenario: Out-of-range RSI threshold fails build
    Given a detection spec builder
    And an OHLC series of 30 bars with close strictly increasing
    And the rule RSIThresholdRule with period 14 overbought 120 oversold 30 source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V7"

  Scenario: MACD with slowPeriod not greater than fastPeriod fails build
    Given a detection spec builder
    And an OHLC series of 60 bars with close strictly increasing
    And the rule MACDSignalCrossRule with fast 26 slow 12 signal 9 source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V7"

  Scenario: Empty rules fails build
    Given a detection spec builder
    And an OHLC series of 10 bars with close strictly increasing
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V8"

  Scenario: Duplicate rule fails build
    Given a detection spec builder
    And an OHLC series of 10 bars with close strictly increasing
    And the rule PriceVsMARule with SMA period 3 source CLOSE
    And the rule PriceVsMARule with SMA period 3 source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V9"

  Scenario: OHLC invariant violation in series is rejected by builder
    Given a detection spec builder
    And an OHLC series with an OHLC invariant violation
    And the rule PriceVsMARule with SMA period 3 source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V10"

  Scenario: HA priceSource on OHLC series fails build for MAVsMARule
    Given a detection spec builder
    And an OHLC series of 10 bars with close strictly increasing
    And the rule MAVsMARule with SMA period 2 and SMA period 4 source HA_CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V5"

  Scenario: Insufficient bars fails build for MAVsMARule
    Given a detection spec builder
    And an OHLC series with closes 1, 2, 3
    And the rule MAVsMARule with SMA period 2 and SMA period 4 source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V6"

  Scenario: Zero period fails build for MAVsMARule
    Given a detection spec builder
    And an OHLC series of 10 bars with close strictly increasing
    And the rule MAVsMARule with SMA period 0 and SMA period 4 source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V7"

  Scenario: PivotPointRule on an HA series fails build (pivots are OHLC-only)
    Given a detection spec builder
    And an HA series of 10 bars with haClose strictly increasing
    And the rule PivotPointRule with period "1d" variant STANDARD source HA_CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V5"

  Scenario: PivotPointRule with an intraday period fails build
    Given a detection spec builder
    And an OHLC series of 10 bars with close strictly increasing
    And the rule PivotPointRule with period "1h" variant STANDARD source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V7"

  Scenario: PivotPointRule with a multi-day period fails build
    Given a detection spec builder
    And an OHLC series of 10 bars with close strictly increasing
    And the rule PivotPointRule with period "2d" variant STANDARD source CLOSE
    When I build the detection spec
    Then an InvalidDetectionSpecException is thrown with violatedRule "V7"

  Scenario: Valid spec builds successfully
    Given a detection spec builder
    And an OHLC series of 10 bars with close strictly increasing
    And the rule PriceVsMARule with SMA period 3 source CLOSE
    When I build the detection spec
    Then the spec builds successfully

  Scenario: Timeframe tag is optional
    Given a detection spec builder
    And an OHLC series of 10 bars with close strictly increasing
    And the rule PriceVsMARule with SMA period 3 source CLOSE
    When I build the detection spec
    Then the spec builds successfully
    And the spec timeframe is empty
