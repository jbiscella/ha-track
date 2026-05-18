Feature: Driver V12 strict enforcement
  Maps heerwisch-jfreechart/CLAUDE.md section 11 (Block 2).

  Scenario: RSI on MAIN pane is rejected
    Given a chart with an OHLC series of 60 bars
    And a RSI indicator placed at pane MAIN
    When I render the chart
    Then an UnsupportedFeatureException is thrown
    And the exception featureName is "RSI on MAIN pane"
    And the exception driverName is "heerwisch-jfreechart"

  Scenario: MACD on MAIN pane is rejected
    Given a chart with an OHLC series of 60 bars
    And a MACD indicator placed at pane MAIN
    When I render the chart
    Then an UnsupportedFeatureException is thrown
    And the exception featureName is "MACD on MAIN pane"

  Scenario Outline: Subplot-only indicators on MAIN are rejected
    Given a chart with an OHLC series of 60 bars
    And a <indicator> indicator placed at pane MAIN
    When I render the chart
    Then an UnsupportedFeatureException is thrown

    Examples:
      | indicator  |
      | ADX        |
      | Stochastic |
      | ATR        |
      | VolumePane |

  Scenario: SMA on MAIN pane is accepted
    Given a chart with an OHLC series of 60 bars
    And a SMA indicator placed at pane MAIN
    When I render the chart
    Then rendering succeeds

  Scenario: RSI on a subplot is accepted
    Given a chart with an OHLC series of 60 bars
    And a RSI indicator placed at pane SUBPLOT_1
    When I render the chart
    Then rendering succeeds
