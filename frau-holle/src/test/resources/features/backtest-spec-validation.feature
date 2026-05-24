Feature: BacktestSpecBuilder eager validation
  Maps frau-holle/CLAUDE.md section 9 (Block 1).

  Scenario: Missing series fails build
    Given a backtest builder
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V1"

  Scenario: Empty series fails build
    Given a backtest builder
    And an empty OHLC series
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V2"

  Scenario: Missing signalGenerator fails build
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And initial cash 10000
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V3"

  Scenario: Non-positive initialCash fails build
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And the strategy holds every bar
    And initial cash 0
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V4"

  Scenario: Irregularly-spaced but monotonic bars build (rhythm from the most-common gap)
    Given a backtest builder
    And an OHLC series with irregular spacing
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then the backtest spec builds successfully

  Scenario: Single-bar series fails build
    Given a backtest builder
    And an OHLC series of 1 daily bars
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V6"

  Scenario: OHLC invariant violation in the series is rejected
    Given a backtest builder
    And an OHLC series with an OHLC invariant violation
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V7"

  Scenario: Bars out of chronological order fail build
    Given a backtest builder
    And an OHLC series with bars out of chronological order
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V5"

  Scenario: Duplicate bar timestamps fail build
    Given a backtest builder
    And an OHLC series with a duplicate timestamp
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V5"

  Scenario: A bar whose open exceeds its high is rejected
    Given a backtest builder
    And an OHLC series with a bar whose open exceeds its high
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V7"

  Scenario: A bar whose close is below its low is rejected
    Given a backtest builder
    And an OHLC series with a bar whose close is below its low
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V7"

  Scenario: A bar with negative volume is rejected
    Given a backtest builder
    And an OHLC series with a bar whose volume is negative
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V7"

  Scenario: A well-formed spec builds successfully
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then the backtest spec builds successfully
