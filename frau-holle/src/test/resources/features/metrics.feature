Feature: BacktestMetrics computation
  Maps frau-holle/CLAUDE.md section 12 (Block 4).

  Scenario: totalReturn on a flat backtest
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy holds every bar
    When I run the backtest
    Then metrics totalReturn is 0
    And metrics numTrades is 0
    And metrics winRate is 0
    And metrics maxDrawdown is 0

  Scenario: Trade-based metrics on mixed outcomes
    Given an equity curve 10000, 10000
    And trades with pnls 100, 100, 100, 100, 100, 100, -50, -50, -50, -50
    And periodsPerYear is 252
    When I compute the metrics
    Then metrics numTrades is 10
    And metrics winRate is 0.6
    And metrics profitFactor is 3
    And metrics avgWin is 100
    And metrics avgLoss is -50

  Scenario: maxDrawdown as a non-negative fraction
    Given an equity curve 10000, 12000, 9000, 11000
    And periodsPerYear is 252
    When I compute the metrics
    Then metrics maxDrawdown is 0.25

  Scenario: Sharpe ratio annualized for a daily timeframe
    Given an equity curve 1000, 1011, 1001.901
    And periodsPerYear is 252
    When I compute the metrics
    Then metrics sharpeRatio is approximately 1.587

  Scenario: Sharpe ratio is zero when the equity curve is constant
    Given an equity curve 1000, 1000, 1000
    And periodsPerYear is 252
    When I compute the metrics
    Then metrics sharpeRatio is 0

  Scenario: Sortino ratio is zero when there are no negative returns
    Given an equity curve 1000, 1010, 1020, 1030
    And periodsPerYear is 252
    When I compute the metrics
    Then metrics sortinoRatio is 0

  Scenario: Calmar ratio is zero when maxDrawdown is zero
    Given an equity curve 1000, 1010, 1020
    And periodsPerYear is 252
    When I compute the metrics
    Then metrics calmarRatio is 0
