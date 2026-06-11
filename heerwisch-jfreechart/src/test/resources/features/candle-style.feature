Feature: Candle display style (CandleStyle)
  Maps heerwisch-jfreechart/CLAUDE.md section 6.5. HEIKIN_ASHI draws Heikin-Ashi
  candles derived from a supplied OHLCSeries for rendering only; overlay
  indicators keep computing on the real price source of truth.

  Scenario: Heikin-Ashi candles render with real-price CLOSE overlays
    Given a chart with an OHLC series of 60 bars
    And the candle style is HEIKIN_ASHI
    And an SMA indicator with period 10 placed at pane MAIN
    And an RSI indicator with danger zones placed at pane SUBPLOT_1
    When I render the chart
    Then rendering succeeds

  Scenario: Heikin-Ashi changes the candles but not the indicator legend
    Given a chart with an OHLC series of 60 bars
    And an SMA indicator with period 10 placed at pane MAIN
    When I render the chart as OHLC and as HEIKIN_ASHI
    Then the two chart images differ
    And both chart images have the same legend

  Scenario: An Auto entry marker renders under Heikin-Ashi
    Given a chart with an OHLC series of 60 bars
    And the candle style is HEIKIN_ASHI
    And an EntryExitMarkerAuto at bar 30 with direction LONG_ENTRY and glyph UP_TRIANGLE
    When I render the chart
    Then rendering succeeds
