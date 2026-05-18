Feature: Backtester contract
  Maps frau-holle/CLAUDE.md section 13 (Block 5).

  Scenario: Run returns a result on a valid spec
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy holds every bar
    When I run the backtest
    Then the backtest result is not null
    And the equity curve has 10 points
    And the first equity point equity is 10000

  Scenario: Null spec is a programmer error
    When I run the backtest with a null spec
    Then a NullPointerException is thrown

  Scenario: SignalGenerator exception is wrapped
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy throws at bar 5
    When I run the backtest
    Then a SignalGenerationException is thrown
    And the exception barIndex is 5

  Scenario: A null Signal from the strategy is reported as a generation failure
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy returns a null signal at bar 5
    When I run the backtest
    Then a SignalGenerationException is thrown
    And the exception barIndex is 5

  Scenario: Determinism with a stateless SignalGenerator
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    And the strategy buys 10 at bar 0
    And the strategy closes the position at bar 4
    When I run the backtest twice
    Then both backtest results are equal
