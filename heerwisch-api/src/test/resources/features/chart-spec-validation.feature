Feature: ChartSpecBuilder eager validation
  Maps heerwisch-api/CLAUDE.md section 6 (Block 1).

  Scenario: Missing series fails build
    Given a chart spec builder
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V1"

  Scenario: Empty series fails build
    Given a chart spec builder
    And an empty OHLC series
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V2"

  Scenario: Out-of-order series fails build
    Given a chart spec builder
    And an OHLC series:
      | time                 | open | high | low | close |
      | 2024-01-02T00:00:00Z | 10   | 10   | 10  | 10    |
      | 2024-01-01T00:00:00Z | 11   | 11   | 11  | 11    |
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V3"

  Scenario: Duplicate time in series fails build
    Given a chart spec builder
    And an OHLC series:
      | time                 | open | high | low | close |
      | 2024-01-01T00:00:00Z | 10   | 10   | 10  | 10    |
      | 2024-01-01T00:00:00Z | 11   | 11   | 11  | 11    |
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V4"

  Scenario: HA priceSource on OHLC series fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And an SMA indicator with period 3 and source HA_CLOSE
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V5"

  Scenario: OHLC priceSource on HA series fails build
    Given a chart spec builder
    And an HA series of 10 bars
    And an SMA indicator with period 3 and source CLOSE
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V5"

  Scenario: Insufficient bars for indicator fails build
    Given a chart spec builder
    And an OHLC series of 5 bars
    And an SMA indicator with period 20 and source CLOSE
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V6"

  Scenario: BarHighlight at a non-existent time fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And a BarHighlight annotation at time "2030-01-01T00:00:00Z"
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V7"

  Scenario: VolumePane on a series without volume fails build
    Given a chart spec builder
    And an OHLC series of 10 bars without volume
    And a VolumePane indicator
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V9"

  Scenario: Explicit layout heights not summing to 1.0 fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And an explicit layout with mainPaneHeight 0.5 and subplot SUBPLOT_1 height 0.3
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V10"

  Scenario: Explicit layout declaring a height for an unused pane fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And an explicit layout with mainPaneHeight 0.6 and subplot SUBPLOT_3 height 0.4
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V11"

  Scenario: OHLC invariant violation in series fails build
    Given a chart spec builder
    And an OHLC series with an OHLC invariant violation
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V13"

  Scenario: Subplot heights without a main pane height is rejected as a domain error, not NPE
    Given a layout builder with a subplot height but no main pane height
    When I build the layout
    Then an InvalidChartSpecException is thrown with violatedRule "V14"
    And no NullPointerException is thrown

  Scenario: A HorizontalLevel at a non-positive price fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And a HorizontalLevel annotation at price -5
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V8"

  Scenario: A FibRetracement with a non-positive swing price fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And a FibRetracement annotation with swingHigh -10 and swingLow 5
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V8"

  Scenario: An indicator at a pane with no declared height fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And an RSI indicator with period 3 overbought 70 oversold 30 and source CLOSE
    And an explicit layout with mainPaneHeight 1.0 and no subplot heights
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V15"

  Scenario: A well-formed spec builds successfully
    Given a chart spec builder
    And an OHLC series of 30 bars
    And an SMA indicator with period 20 and source CLOSE
    When I build the chart spec
    Then the chart spec builds successfully

  Scenario: An EntryExitMarker at a non-existent bar time fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And an EntryExitMarker at a non-existent time
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V16"

  Scenario: An EntryExitMarker at a valid bar passes build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And an EntryExitMarker at bar index 5 with direction LONG_ENTRY and glyph UP_TRIANGLE
    When I build the chart spec
    Then the chart spec builds successfully

  Scenario: An EntryExitMarkerAuto at a non-existent time fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And an EntryExitMarkerAuto at a non-existent time
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V16"

  Scenario: An EntryExitMarkerAuto at a valid bar passes build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And an EntryExitMarkerAuto at bar index 5 with direction LONG_ENTRY and glyph UP_TRIANGLE
    When I build the chart spec
    Then the chart spec builds successfully

  Scenario: A TimeRangeHighlight with reversed times fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And a TimeRangeHighlight with reversed times
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V17"

  Scenario: A TimeRangeHighlight entirely outside the series fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And a TimeRangeHighlight entirely after the series
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V17"

  Scenario: A TimeRangeHighlight with opacity above 1 fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And a TimeRangeHighlight with opacity 1.5
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V18"

  Scenario: A TimeRangeHighlight with negative opacity fails build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And a TimeRangeHighlight with opacity -0.1
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V18"

  Scenario: A TimeRangeHighlight with opacity at the boundary passes build
    Given a chart spec builder
    And an OHLC series of 10 bars
    And a TimeRangeHighlight from bar 1 to bar 5 with fillColor LONG_POSITION and opacity 0.15
    When I build the chart spec
    Then the chart spec builds successfully

  Scenario: EntryExitMarker and TimeRangeHighlight coexist with other annotations
    Given a chart spec builder
    And an OHLC series of 10 bars
    And a HorizontalLevel annotation at price 100
    And an EntryExitMarker at bar index 3 with direction LONG_ENTRY and glyph UP_TRIANGLE
    And an EntryExitMarker at bar index 7 with direction LONG_EXIT and glyph DOWN_TRIANGLE
    And a TimeRangeHighlight from bar 3 to bar 7 with fillColor LONG_POSITION and opacity 0.15
    When I build the chart spec
    Then the chart spec builds successfully

  Scenario: An RSI with overbought above 100 fails build (V19)
    Given a chart spec builder
    And an OHLC series of 30 bars
    And an RSI indicator with period 14 overbought 120 oversold 30 and source CLOSE
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V19"

  Scenario: An RSI with oversold below 0 fails build (V20)
    Given a chart spec builder
    And an OHLC series of 30 bars
    And an RSI indicator with period 14 overbought 70 oversold -5 and source CLOSE
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V20"

  Scenario: An RSI with oversold equal to overbought fails build (V21)
    Given a chart spec builder
    And an OHLC series of 30 bars
    And an RSI indicator with period 14 overbought 50 oversold 50 and source CLOSE
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V21"

  Scenario: An RSI with oversold greater than overbought fails build (V21)
    Given a chart spec builder
    And an OHLC series of 30 bars
    And an RSI indicator with period 14 overbought 30 oversold 70 and source CLOSE
    When I build the chart spec
    Then an InvalidChartSpecException is thrown with violatedRule "V21"

  Scenario: An RSI at the boundary values passes build
    Given a chart spec builder
    And an OHLC series of 30 bars
    And an RSI indicator with period 14 overbought 100 oversold 0 and source CLOSE
    When I build the chart spec
    Then the chart spec builds successfully
