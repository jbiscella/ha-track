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

  Scenario: A well-formed spec builds successfully
    Given a chart spec builder
    And an OHLC series of 30 bars
    And an SMA indicator with period 20 and source CLOSE
    When I build the chart spec
    Then the chart spec builds successfully
