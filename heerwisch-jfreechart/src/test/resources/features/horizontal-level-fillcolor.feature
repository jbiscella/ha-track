Feature: HorizontalLevel semantic line colors
  Maps heerwisch-jfreechart/CLAUDE.md section 8 (HorizontalLevel) and
  heerwisch-api Annotation.HorizontalLevel's optional FillColor. The
  renderer colors the reference line by the FillColor's semantic
  (entry neutral, stop-loss red, take-profit green, etc.). The 3-arg
  HorizontalLevel keeps the default reference color.

  Scenario: HorizontalLevel without fillColor renders with the default color
    Given a chart with an OHLC series of 60 bars
    And a HorizontalLevel at price 100 with style DASHED
    When I render the chart
    Then rendering succeeds

  Scenario: WIN take-profit level renders
    Given a chart with an OHLC series of 60 bars
    And a HorizontalLevel at price 105 with style DASHED and fillColor WIN
    When I render the chart
    Then rendering succeeds

  Scenario: LOSS stop-loss level renders
    Given a chart with an OHLC series of 60 bars
    And a HorizontalLevel at price 95 with style DASHED and fillColor LOSS
    When I render the chart
    Then rendering succeeds

  Scenario: OPEN level renders
    Given a chart with an OHLC series of 60 bars
    And a HorizontalLevel at price 100 with style SOLID and fillColor OPEN
    When I render the chart
    Then rendering succeeds

  Scenario: LONG_POSITION level renders
    Given a chart with an OHLC series of 60 bars
    And a HorizontalLevel at price 100 with style SOLID and fillColor LONG_POSITION
    When I render the chart
    Then rendering succeeds

  Scenario: SHORT_POSITION level renders
    Given a chart with an OHLC series of 60 bars
    And a HorizontalLevel at price 100 with style SOLID and fillColor SHORT_POSITION
    When I render the chart
    Then rendering succeeds

  Scenario: NEUTRAL entry level renders
    Given a chart with an OHLC series of 60 bars
    And a HorizontalLevel at price 100 with style DASHED and fillColor NEUTRAL
    When I render the chart
    Then rendering succeeds

  Scenario: CAUTION level renders
    Given a chart with an OHLC series of 60 bars
    And a HorizontalLevel at price 100 with style DOTTED and fillColor CAUTION
    When I render the chart
    Then rendering succeeds

  Scenario: Entry / stop / take reference lines coexist on one chart
    Given a chart with an OHLC series of 60 bars
    And a HorizontalLevel at price 100 with style DASHED and fillColor NEUTRAL
    And a HorizontalLevel at price 94 with style DASHED and fillColor LOSS
    And a HorizontalLevel at price 108 with style DASHED and fillColor WIN
    When I render the chart
    Then rendering succeeds
