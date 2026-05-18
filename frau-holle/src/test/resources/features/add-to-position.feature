Feature: v1.2 AddToPosition (pyramiding)
  Maps frau-holle/CLAUDE.md section 16.5 (Block 7). Generated bar i has
  open = 100 + i and time 2024-01-(01+i). An AddToPosition signal carries an
  explicit quantity and direction; it fills at the next bar open, like
  Buy/Sell, and re-prices the position to the quantity-weighted average
  entry price while keeping the original entry time.

  Scenario: AddToPosition on an open long position accumulates at the weighted-average entry price
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy adds 5 LONG at bar 3
    When I run the backtest
    Then an open position remains at the end
    And the open position quantity is 15
    And the open position entryPrice is 102
    And the open position entryTime is bar 1

  Scenario: AddToPosition on an open short position accumulates at the weighted-average entry price
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy sells 10 at bar 0
    And the strategy adds 5 SHORT at bar 3
    When I run the backtest
    Then an open position remains at the end
    And the open position quantity is 15
    And the open position entryPrice is 102
    And the open position entryTime is bar 1

  Scenario: AddToPosition fills at the next bar open, like Buy/Sell
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy adds 10 LONG at bar 5
    When I run the backtest
    Then an open position remains at the end
    And the open position quantity is 20
    And the open position entryPrice is 103.5

  Scenario: Position entryTime after AddToPosition remains the original entry time
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 2
    And the strategy adds 5 LONG at bar 6
    When I run the backtest
    Then an open position remains at the end
    And the open position quantity is 15
    And the open position entryTime is bar 3

  Scenario: AddToPosition with no open position is a no-op
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy adds 5 LONG at bar 2
    When I run the backtest
    Then the result has 0 trades
    And no open position remains at the end
    And diagnostics addToPositionOnNoPositionCount is 1
    And diagnostics addToPositionCount is 0

  Scenario: AddToPosition on an opposite-direction position throws InvalidAddToPositionDirectionException
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy sells 10 at bar 0
    And the strategy adds 5 LONG at bar 3
    When I run the backtest
    Then an InvalidAddToPositionDirectionException is thrown
    And the AddToPosition exception barIndex is 4
    And the AddToPosition exception barTime is bar 4
    And the AddToPosition exception openPositionDirection is SHORT
    And the AddToPosition exception signalDirection is LONG

  Scenario: diagnostics.addToPositionCount increments on a successful fill
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy adds 5 LONG at bar 3
    When I run the backtest
    Then an open position remains at the end
    And the open position quantity is 15
    And diagnostics addToPositionCount is 1

  Scenario: Mixing AddToPosition with Buy, ClosePosition and ClosePositionAtPrice in one backtest
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy adds 5 LONG at bar 2
    And the strategy closes the position at bar 4
    And the strategy buys 8 at bar 6
    And the strategy closes at price 150 intrabar after bar 8
    When I run the backtest
    Then the result has 2 trades
    And diagnostics addToPositionCount is 1
    And diagnostics forcedClosesAtExplicitPrice is 1

  Scenario: Multiple consecutive AddToPosition signals compound the weighted-average entry price
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy adds 10 LONG at bar 2
    And the strategy adds 20 LONG at bar 4
    When I run the backtest
    Then an open position remains at the end
    And the open position quantity is 40
    And the open position entryPrice is 103.5
    And diagnostics addToPositionCount is 2

  Scenario: A v1/v1.1-only backtest is unaffected by the v1.2 extension
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes the position at bar 5
    When I run the backtest
    Then the result has 1 trade
    And trade 0 exitPrice is 106
    And diagnostics addToPositionCount is 0
    And diagnostics addToPositionOnNoPositionCount is 0
