Feature: Output format selection
  Maps heerwisch-jfreechart/CLAUDE.md section 10 (Block 1).

  Scenario: Default LayoutSpec produces PNG
    Given a chart with an OHLC series of 60 bars
    And a SMA indicator placed at pane MAIN
    When I render the chart
    Then rendering succeeds
    And the chart image contentType is "image/png"
    And the chart image starts with the PNG magic bytes

  Scenario: Explicit PNG format
    Given a chart with an OHLC series of 60 bars
    And a SMA indicator placed at pane MAIN
    And the layout is auto 800 by 600 with format PNG
    When I render the chart
    Then rendering succeeds
    And the chart image contentType is "image/png"
    And the chart image starts with the PNG magic bytes

  Scenario: Explicit JPEG format
    Given a chart with an OHLC series of 60 bars
    And a SMA indicator placed at pane MAIN
    And the layout is auto 800 by 600 with format JPEG
    When I render the chart
    Then rendering succeeds
    And the chart image contentType is "image/jpeg"
    And the chart image starts with the JPEG magic bytes

  Scenario: Dimensions match the layout spec
    Given a chart with an OHLC series of 60 bars
    And a SMA indicator placed at pane MAIN
    And the layout is auto 1200 by 800 with format PNG
    When I render the chart
    Then the chart image is 1200 by 800
