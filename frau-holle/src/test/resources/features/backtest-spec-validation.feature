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

  Scenario: Irregular bar spacing fails build
    Given a backtest builder
    And an OHLC series with irregular spacing
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then an InvalidBacktestSpecException is thrown with violatedRule "V5"

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

  Scenario: A well-formed spec builds successfully
    Given a backtest builder
    And an OHLC series of 10 daily bars
    And the strategy holds every bar
    And initial cash 10000
    When I build the backtest spec
    Then the backtest spec builds successfully
