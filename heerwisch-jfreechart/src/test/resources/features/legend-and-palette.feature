Feature: Legend introspection and per-placement overlay colors
  Maps the 0.50.0-alpha legend feature: ChartImage.legend() exposes one
  entry per rendered series (two for dual-line indicators), and multiple
  same-type overlays on one pane render in distinct palette shades.

  Scenario: Two SMA overlays on the main pane get distinct colors
    Given a chart with an OHLC series of 60 bars
    And an SMA indicator with period 20 placed at pane MAIN
    And an SMA indicator with period 30 placed at pane MAIN
    When I render the chart
    Then rendering succeeds
    And the legend has 2 entries
    And legend entry 0 has label "SMA(20)"
    And legend entry 1 has label "SMA(30)"
    And legend entries 0 and 1 have distinct colors

  Scenario: A lone SMA produces a single legend entry
    Given a chart with an OHLC series of 60 bars
    And an SMA indicator with period 20 placed at pane MAIN
    When I render the chart
    Then rendering succeeds
    And the legend has 1 entry
    And legend entry 0 has label "SMA(20)"

  Scenario: MACD emits two legend entries (line + signal)
    Given a chart with an OHLC series of 60 bars
    And a MACD indicator placed at pane SUBPLOT_1
    When I render the chart
    Then rendering succeeds
    And the legend has 2 entries
    And legend entry 0 has label "MACD(12,26,9)"
    And legend entry 1 has label "Signal"
    And legend entries 0 and 1 have distinct colors
