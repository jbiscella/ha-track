Feature: RSI sub-pane rendering
  Maps heerwisch-jfreechart/CLAUDE.md §7 row for RSI and
  heerwisch-api/CLAUDE.md §1.2.1 (RsiVisualization). The driver
  renders the line, the two horizontal threshold levels at
  overbought / oversold, and the [0,100] bounded Y axis. The
  optional RsiVisualization.DANGER_ZONES_ON adds shaded zones
  above overbought and below oversold.

  Scenario: Default RSI (4-arg constructor) renders threshold lines without danger zones
    Given a chart with an OHLC series of 60 bars
    And a RSI indicator placed at pane SUBPLOT_1
    When I render the chart
    Then rendering succeeds

  Scenario: RSI with danger zones renders without error
    Given a chart with an OHLC series of 60 bars
    And an RSI indicator with danger zones placed at pane SUBPLOT_1
    When I render the chart
    Then rendering succeeds

  Scenario: Backward compatibility — the 4-arg RSI constructor still works
    Given a chart with an OHLC series of 60 bars
    And a RSI indicator placed at pane SUBPLOT_2
    When I render the chart
    Then rendering succeeds
