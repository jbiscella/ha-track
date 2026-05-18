Feature: Headless rendering
  Maps heerwisch-jfreechart/CLAUDE.md section 12 (Block 3).

  Scenario: Render works with java.awt.headless set to true
    Given java.awt.headless is set to true
    And a chart with an OHLC series of 60 bars
    And a SMA indicator placed at pane MAIN
    And a RSI indicator placed at pane SUBPLOT_1
    When I render the chart
    Then rendering succeeds
    And no HeadlessException is thrown
