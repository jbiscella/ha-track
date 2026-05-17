Feature: OHLC invariant validation
  Maps commons/CLAUDE.md section 4.

  Scenario: Valid bar passes
    Given an OHLC bar with open=10, high=12, low=9, close=11
    When I validate invariants
    Then no exception is thrown

  Scenario: Negative open price violates I1
    Given an OHLC bar with open=-1, high=12, low=9, close=11
    When I validate invariants
    Then an OHLCInvariantViolationException is thrown
    And the violated invariant is "open"

  Scenario: High lower than low violates I5
    Given an OHLC bar with open=9, high=8, low=10, close=9
    When I validate invariants
    Then an OHLCInvariantViolationException is thrown
    And the violated invariant is "high<low"

  Scenario: Construction with invalid values does not throw
    When I construct an OHLC bar with open=-5, high=1, low=100, close=2
    Then the OHLC bar is constructed without exception

  Scenario: Null field is rejected at construction
    When I construct an OHLC bar with a null close
    Then a NullPointerException is thrown

  Scenario: Volume present and negative violates I10
    Given an OHLC bar with open=10, high=12, low=9, close=11 and volume=-1
    When I validate invariants
    Then an OHLCInvariantViolationException is thrown
    And the violated invariant is "volume"

  Scenario: Volume absent is always valid
    Given an OHLC bar with open=10, high=12, low=9, close=11 and no volume
    When I validate invariants
    Then no exception is thrown
