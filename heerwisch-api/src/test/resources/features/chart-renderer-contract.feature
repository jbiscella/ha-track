Feature: ChartRenderer port contract
  Maps heerwisch-api/CLAUDE.md section 9 (Block 4). The port contract is
  exercised against a conformant reference renderer (FakeChartRenderer);
  driver-specific render behavior is verified in heerwisch-jfreechart.

  Scenario: Render returns a ChartImage on a valid spec
    Given a chart spec builder
    And an OHLC series of 30 bars
    And an SMA indicator with period 20 and source CLOSE
    When I build the chart spec
    And I render the spec with the reference renderer
    Then the chart image is not null
    And the chart image bytes are not empty
    And the chart image contentType is "image/png"
    And the chart image dimensions are positive

  Scenario: Null spec is a programmer error
    When I render a null spec with the reference renderer
    Then a NullPointerException is thrown

  Scenario: Rendering the same spec twice is byte-identical
    Given a chart spec builder
    And an OHLC series of 30 bars
    And an SMA indicator with period 20 and source CLOSE
    When I build the chart spec
    And I render the spec with the reference renderer twice
    Then both chart images are byte-identical

  Scenario: UnsupportedFeatureException carries the feature and driver names
    When an UnsupportedFeatureException is created for feature "RSI on MAIN pane" and driver "heerwisch-jfreechart"
    Then its featureName is "RSI on MAIN pane"
    And its driverName is "heerwisch-jfreechart"

  Scenario: DriverInternalException wraps the original cause
    When a DriverInternalException is created wrapping an internal error
    Then its cause is that internal error
