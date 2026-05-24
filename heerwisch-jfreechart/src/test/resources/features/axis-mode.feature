Feature: Domain axis mode (ordinal gap-collapsing vs time-proportional)
  Maps heerwisch-jfreechart/CLAUDE.md section 5.1.

  Scenario: Default layout renders with the ordinal axis
    Given a chart with an OHLC series of 60 bars
    And a SMA indicator placed at pane MAIN
    When I render the chart
    Then rendering succeeds

  Scenario: TIME axis mode renders successfully
    Given a chart with an OHLC series of 60 bars
    And a SMA indicator placed at pane MAIN
    And the layout is auto 800 by 600 with format PNG and axis mode TIME
    When I render the chart
    Then rendering succeeds

  Scenario: Ordinal and time axis modes produce different output
    Given a chart with an OHLC series of 60 bars
    And a SMA indicator placed at pane MAIN
    When I render the chart with axis modes ORDINAL and TIME
    Then the two chart images differ
