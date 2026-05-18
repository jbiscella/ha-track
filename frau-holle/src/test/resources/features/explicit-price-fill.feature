Feature: v1.1 explicit-price (intrabar) fills
  Maps frau-holle/CLAUDE.md section 15.5 (Block 6). Generated bar i has
  open = 100 + i and time 2024-01-(01+i). A valid ClosePositionAtPrice
  fillTime is an intrabar instant strictly between the signal bar and the
  next bar; the "intrabar after bar N" step places it 12 hours past bar N.

  Scenario: ClosePositionAtPrice fills at the signal-provided price, not next bar open
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes at price 150 intrabar after bar 5
    When I run the backtest
    Then the result has 1 trade
    And trade 0 exitPrice is 150

  Scenario: ClosePositionAtPrice fills at the signal-provided fillTime, not next bar time
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes at price 150 intrabar after bar 4
    When I run the backtest
    Then the result has 1 trade
    And trade 0 exitTime is intrabar after bar 4

  Scenario: ClosePositionAtPrice on no open position is a no-op
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy closes at price 150 intrabar after bar 0
    When I run the backtest
    Then the result has 0 trades
    And diagnostics noOpClosePositionSignals is 1
    And diagnostics forcedClosesAtExplicitPrice is 0

  Scenario: ClosePositionAtPrice with fillTime equal to the signal bar time is rejected
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes at price 150 as of bar 5 at bar 5
    When I run the backtest
    Then an InvalidExplicitFillException is thrown
    And the exception fillTime is bar 5
    And the exception barTime is bar 6

  Scenario: ClosePositionAtPrice with fillTime before the signal bar time is rejected
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes at price 150 as of bar 3 at bar 5
    When I run the backtest
    Then an InvalidExplicitFillException is thrown
    And the exception fillTime is bar 3
    And the exception barTime is bar 6

  Scenario: ClosePositionAtPrice with fillTime equal to the next bar time is rejected
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes at price 150 as of bar 6 at bar 5
    When I run the backtest
    Then an InvalidExplicitFillException is thrown
    And the exception fillTime is bar 6
    And the exception barTime is bar 6

  Scenario: ClosePositionAtPrice with fillTime beyond the next bar is rejected
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes at price 150 as of bar 8 at bar 5
    When I run the backtest
    Then an InvalidExplicitFillException is thrown

  Scenario: diagnostics.forcedClosesAtExplicitPrice counts explicit-price closes
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes at price 150 intrabar after bar 5
    When I run the backtest
    Then diagnostics forcedClosesAtExplicitPrice is 1

  Scenario: Mixing ClosePositionAtPrice with regular signals in the same backtest
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes at price 150 intrabar after bar 2
    And the strategy buys 5 at bar 4
    And the strategy closes the position at bar 7
    When I run the backtest
    Then the result has 2 trades
    And diagnostics forcedClosesAtExplicitPrice is 1

  Scenario: A v1-only backtest is unaffected by the v1.1 extension
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes the position at bar 5
    When I run the backtest
    Then the result has 1 trade
    And trade 0 exitPrice is 106
    And diagnostics forcedClosesAtExplicitPrice is 0

  Scenario: ClosePositionAtPrice fills at a price outside the next bar's range
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes at price 999 intrabar after bar 5
    When I run the backtest
    Then the result has 1 trade
    And trade 0 exitPrice is 999
    And diagnostics forcedClosesAtExplicitPrice is 1
