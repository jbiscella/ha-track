Feature: Layout spec
  Maps heerwisch-api/CLAUDE.md section 8 (Block 3).

  Scenario: Defaults provide auto layout in PNG
    When I get the default layout
    Then the default layout is an AutoLayoutSpec with width 900 and height 500
    And the default layout format is PNG

  Scenario: Builder with no format set also defaults to PNG
    When I build a layout from the builder with no format set
    Then the default layout format is PNG

  Scenario: Builder omits layout, defaults applied
    Given a chart spec builder
    And an OHLC series of 30 bars
    And an SMA indicator with period 20 and source CLOSE
    When I build the chart spec
    Then the chart spec builds successfully
    And the layout is an AutoLayoutSpec with width 900 and height 500
    And the layout format is PNG

  Scenario: Explicit layout heights exactly summing to 1.0 passes
    Given a chart spec builder
    And an OHLC series of 30 bars
    And an RSI indicator with period 14 overbought 70 oversold 30 and source CLOSE
    And an explicit layout with mainPaneHeight 0.6 and subplot SUBPLOT_1 height 0.4
    When I build the chart spec
    Then the chart spec builds successfully
