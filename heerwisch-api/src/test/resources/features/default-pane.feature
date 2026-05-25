Feature: Default pane assignment for indicators
  Maps heerwisch-api/CLAUDE.md section 7 (Block 2).

  Scenario: Overlay indicators default to MAIN
    Given a chart spec builder
    And an OHLC series of 60 bars
    And an SMA indicator with period 20 and source CLOSE
    And an EMA indicator with period 50 and source CLOSE
    And a BollingerBands indicator with period 20 stdDev 2.0 and source CLOSE
    When I build the chart spec
    Then the chart spec has 3 indicators
    And indicator 0 is placed at pane MAIN
    And indicator 1 is placed at pane MAIN
    And indicator 2 is placed at pane MAIN

  Scenario: Subplot indicators default to SUBPLOT_1
    Given a chart spec builder
    And an OHLC series of 60 bars
    And an RSI indicator with period 14 overbought 70 oversold 30 and source CLOSE
    And a MACD indicator with fast 12 slow 26 signal 9 and source CLOSE
    And an ADX indicator with period 14
    And a Stochastic indicator with k 14 d 3 and smoothing 3
    And an ATR indicator with period 14
    And a VolumePane indicator
    When I build the chart spec
    Then the chart spec has 6 indicators
    And indicator 0 is placed at pane SUBPLOT_1
    And indicator 1 is placed at pane SUBPLOT_1
    And indicator 2 is placed at pane SUBPLOT_1
    And indicator 3 is placed at pane SUBPLOT_1
    And indicator 4 is placed at pane SUBPLOT_1
    And indicator 5 is placed at pane SUBPLOT_1

  Scenario: Explicit pane overrides the default
    Given a chart spec builder
    And an OHLC series of 60 bars
    And an RSI indicator with period 14 overbought 70 oversold 30 and source CLOSE placed at pane SUBPLOT_2
    When I build the chart spec
    Then indicator 0 is placed at pane SUBPLOT_2

  Scenario: Multiple indicators can share a pane in insertion order
    Given a chart spec builder
    And an OHLC series of 60 bars
    And an SMA indicator with period 20 and source CLOSE
    And an EMA indicator with period 50 and source CLOSE
    When I build the chart spec
    Then the chart spec has 2 indicators
    And indicator 0 is placed at pane MAIN
    And indicator 1 is placed at pane MAIN

  Scenario: Rolling-extremum overlays default to MAIN
    Given a chart spec builder
    And an OHLC series of 60 bars
    And a RollingMax indicator with period 20 and source HIGH
    And a RollingMin indicator with period 20 and source LOW
    When I build the chart spec
    Then the chart spec has 2 indicators
    And indicator 0 is placed at pane MAIN
    And indicator 1 is placed at pane MAIN

  Scenario: Standalone StdDev defaults to a sub-pane
    Given a chart spec builder
    And an OHLC series of 60 bars
    And a StdDev indicator with period 20 and source CLOSE
    When I build the chart spec
    Then the chart spec has 1 indicators
    And indicator 0 is placed at pane SUBPLOT_1
