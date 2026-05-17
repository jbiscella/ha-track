Feature: PatternDetector contract
  Maps nachtkrapp/CLAUDE.md section 12 (Block 6).

  Scenario: Detect returns matches ordered ascending by time
    Given a detection spec builder
    And an OHLC series rising for 20 bars then falling for 20 bars
    And the rule PriceVsMARule with SMA period 5 source CLOSE
    When I detect
    Then the matches are ordered ascending by time

  Scenario: Null spec is a programmer error
    When I detect with a null spec
    Then a NullPointerException is thrown

  Scenario: Detection is idempotent
    Given a detection spec builder
    And an OHLC series rising for 20 bars then falling for 20 bars
    And the rule RSIThresholdRule with period 14 overbought 70 oversold 30 source CLOSE
    When I detect twice
    Then both detection results are equal

  Scenario: Determinism across detector instances
    Given a detection spec builder
    And an OHLC series rising for 20 bars then falling for 20 bars
    And the rule MACDSignalCrossRule with fast 12 slow 26 signal 9 source CLOSE
    When I detect with two separate detector instances
    Then both detection results are equal

  Scenario: Concurrent calls on the same detector instance are safe
    Given a detection spec builder
    And an OHLC series rising for 20 bars then falling for 20 bars
    And the rule PriceVsMARule with SMA period 5 source CLOSE
    When I detect concurrently from 8 threads
    Then all 8 detection results are equal

  Scenario: Lookahead-safety - truncated matches agree with the full series
    Given a detection spec builder
    And an OHLC series rising for 25 bars then falling for 25 bars
    And the rule RSIThresholdRule with period 14 overbought 70 oversold 30 source CLOSE
    When I detect on the full series and on the series truncated to 30 bars
    Then every truncated match also appears in the full result

  Scenario: Timeframe tag is propagated to every match
    Given a detection spec builder
    And an OHLC series of 20 bars with close strictly increasing
    And the timeframe tag "1d"
    And the rule PriceVsMARule with SMA period 5 source CLOSE
    When I detect
    Then every match has timeframe "1d"
