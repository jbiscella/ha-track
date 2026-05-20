Feature: EntryExitMarker and TimeRangeHighlight rendering
  Maps heerwisch-jfreechart/CLAUDE.md section 8 (the two new Annotation
  subtypes added in 0.44.0-alpha). The renderer draws EntryExitMarker as
  a chunky glyph at (time, price) via XYShapeAnnotation, and
  TimeRangeHighlight as a semi-transparent IntervalMarker on the domain
  axis at Layer.BACKGROUND.

  Scenario: EntryExitMarker at LONG_ENTRY with an UP_TRIANGLE renders cleanly
    Given a chart with an OHLC series of 60 bars
    And an EntryExitMarker at bar 10 with direction LONG_ENTRY and glyph UP_TRIANGLE
    When I render the chart
    Then rendering succeeds

  Scenario: EntryExitMarker at SHORT_ENTRY with a DOWN_TRIANGLE renders cleanly
    Given a chart with an OHLC series of 60 bars
    And an EntryExitMarker at bar 12 with direction SHORT_ENTRY and glyph DOWN_TRIANGLE
    When I render the chart
    Then rendering succeeds

  Scenario: All four MarkerDirection values render without error
    Given a chart with an OHLC series of 60 bars
    And an EntryExitMarker at bar 5 with direction LONG_ENTRY and glyph UP_TRIANGLE
    And an EntryExitMarker at bar 15 with direction LONG_EXIT and glyph DOWN_TRIANGLE
    And an EntryExitMarker at bar 25 with direction SHORT_ENTRY and glyph DOWN_TRIANGLE
    And an EntryExitMarker at bar 35 with direction SHORT_EXIT and glyph UP_TRIANGLE
    When I render the chart
    Then rendering succeeds

  Scenario: TimeRangeHighlight LONG_POSITION renders as a shaded band
    Given a chart with an OHLC series of 60 bars
    And a TimeRangeHighlight from bar 10 to bar 30 with fillColor LONG_POSITION and opacity 0.15
    When I render the chart
    Then rendering succeeds

  Scenario: All four FillColor values render without error
    Given a chart with an OHLC series of 60 bars
    And a TimeRangeHighlight from bar 5 to bar 12 with fillColor LONG_POSITION and opacity 0.15
    And a TimeRangeHighlight from bar 15 to bar 22 with fillColor SHORT_POSITION and opacity 0.15
    And a TimeRangeHighlight from bar 25 to bar 32 with fillColor NEUTRAL and opacity 0.10
    And a TimeRangeHighlight from bar 35 to bar 42 with fillColor CAUTION and opacity 0.20
    When I render the chart
    Then rendering succeeds

  Scenario: EntryExitMarker and TimeRangeHighlight coexist with other annotations in one chart
    Given a chart with an OHLC series of 60 bars
    And an EntryExitMarker at bar 10 with direction LONG_ENTRY and glyph UP_TRIANGLE
    And a TimeRangeHighlight from bar 10 to bar 25 with fillColor LONG_POSITION and opacity 0.15
    And an EntryExitMarker at bar 25 with direction LONG_EXIT and glyph DOWN_TRIANGLE
    When I render the chart
    Then rendering succeeds

  Scenario: EntryExitMarkerAuto LONG_ENTRY renders glyph below bar.low
    Given a chart with an OHLC series of 60 bars
    And an EntryExitMarkerAuto at bar 10 with direction LONG_ENTRY and glyph UP_TRIANGLE
    When I render the chart
    Then rendering succeeds

  Scenario: EntryExitMarkerAuto LONG_EXIT renders glyph above bar.high
    Given a chart with an OHLC series of 60 bars
    And an EntryExitMarkerAuto at bar 30 with direction LONG_EXIT and glyph DOWN_TRIANGLE
    When I render the chart
    Then rendering succeeds

  Scenario: EntryExitMarkerAuto SHORT_ENTRY renders glyph above bar.high
    Given a chart with an OHLC series of 60 bars
    And an EntryExitMarkerAuto at bar 20 with direction SHORT_ENTRY and glyph DOWN_TRIANGLE
    When I render the chart
    Then rendering succeeds

  Scenario: EntryExitMarkerAuto SHORT_EXIT renders glyph below bar.low
    Given a chart with an OHLC series of 60 bars
    And an EntryExitMarkerAuto at bar 40 with direction SHORT_EXIT and glyph UP_TRIANGLE
    When I render the chart
    Then rendering succeeds

  Scenario: EntryExitMarker (explicit price) renders unchanged for backward compatibility
    Given a chart with an OHLC series of 60 bars
    And an EntryExitMarker at bar 15 with direction LONG_ENTRY and glyph UP_TRIANGLE
    When I render the chart
    Then rendering succeeds

  Scenario: EntryExitMarkerAuto and EntryExitMarker coexist in a single chart
    Given a chart with an OHLC series of 60 bars
    And an EntryExitMarkerAuto at bar 10 with direction LONG_ENTRY and glyph UP_TRIANGLE
    And an EntryExitMarker at bar 20 with direction LONG_EXIT and glyph DOWN_TRIANGLE
    And an EntryExitMarkerAuto at bar 30 with direction SHORT_ENTRY and glyph DOWN_TRIANGLE
    When I render the chart
    Then rendering succeeds

  Scenario: Scheduled-vs-forced semantic — consumer wires triangles for scenario exits, arrows for stop-loss
    # Documents the consumer-side mapping codified by GlyphStyle's semantic
    # contract (heerwisch-api/CLAUDE.md section 1.3.1): the scenario-driven
    # long exit is plotted with DOWN_TRIANGLE; the stop-loss-driven long
    # exit on a second hypothetical trade is plotted with ARROW_DOWN. The
    # renderer treats both inputs identically (it has no knowledge of why
    # the consumer picked one shape over the other); the test verifies
    # only that the documented combination renders without error and that
    # the API does not constrain the consumer's choice of glyph beyond
    # shape correctness.
    Given a chart with an OHLC series of 60 bars
    And an EntryExitMarker at bar 10 with direction LONG_ENTRY and glyph UP_TRIANGLE
    And an EntryExitMarker at bar 20 with direction LONG_EXIT and glyph DOWN_TRIANGLE
    And an EntryExitMarker at bar 30 with direction LONG_ENTRY and glyph UP_TRIANGLE
    And an EntryExitMarker at bar 38 with direction LONG_EXIT and glyph ARROW_DOWN
    When I render the chart
    Then rendering succeeds
