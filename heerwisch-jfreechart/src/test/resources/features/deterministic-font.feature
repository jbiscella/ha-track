Feature: Embedded font produces deterministic output
  Maps heerwisch-jfreechart/CLAUDE.md section 13 (Block 4).

  Scenario: Same input renders byte-identical twice
    Given a chart with an OHLC series of 60 bars
    And a SMA indicator placed at pane MAIN
    And a RSI indicator placed at pane SUBPLOT_1
    When I render the chart twice with the same renderer
    Then both chart images are byte-identical

  Scenario: Two driver instances render byte-identical output
    Given a chart with an OHLC series of 60 bars
    And a SMA indicator placed at pane MAIN
    When I render the chart with two separate driver instances
    Then both chart images are byte-identical

  Scenario: Font load failure is reported as a DriverInternalException
    When I construct a renderer with a missing font resource
    Then a DriverInternalException is thrown
    And its cause is an IOException
