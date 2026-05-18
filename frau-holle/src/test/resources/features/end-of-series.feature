Feature: Mark-to-market at end of series
  Maps frau-holle/CLAUDE.md section 11 (Block 3).

  Scenario: Open position at the last bar is marked-to-market
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    When I run the backtest
    Then an open position remains at the end
    And the result has 0 trades
    And the last equity point equity is 10090

  Scenario: No open position at the last bar
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes the position at bar 5
    When I run the backtest
    Then no open position remains at the end
    And the last equity point equity is 10050
