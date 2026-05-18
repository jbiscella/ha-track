Feature: Fill at next bar open
  Maps frau-holle/CLAUDE.md section 10 (Block 2). Generated bar i has
  open = 100 + i, so a fill at bar i happens at price 100 + i.

  Scenario: Buy signal at bar t fills at the open of bar t+1
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    When I run the backtest
    Then an open position remains at the end
    And the open position quantity is 10
    And the open position entryPrice is 101
    And the open position entryTime is bar 1

  Scenario: ClosePosition signal at bar t closes at the open of bar t+1
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes the position at bar 5
    When I run the backtest
    Then the result has 1 trade
    And trade 0 exitPrice is 106
    And trade 0 exitTime is bar 6

  Scenario: Buy at the last bar is unfilled
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 9
    When I run the backtest
    Then no open position remains at the end
    And diagnostics unfilledSignalsAtEndOfSeries is 1

  Scenario: Buy when a position is already open is ignored
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy buys 5 at bar 2
    When I run the backtest
    Then diagnostics ignoredBuySignals is 1
    And the open position quantity is 10

  Scenario: Sell when a position is already open is ignored
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy sells 5 at bar 2
    When I run the backtest
    Then diagnostics ignoredSellSignals is 1
    And the open position quantity is 10

  Scenario: ClosePosition when no position is open is a no-op
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy closes the position at bar 0
    When I run the backtest
    Then diagnostics noOpClosePositionSignals is 1
