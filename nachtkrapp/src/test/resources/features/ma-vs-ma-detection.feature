Feature: MA-vs-MA trend filter detection
  Maps nachtkrapp/CLAUDE.md section 9 (Block 3) — MA-vs-MA family.

  Scenario: MAAboveMA emitted while the fast MA sits above the slow MA
    Given a detection spec builder
    And an OHLC series with closes 1, 2, 3, 4, 5
    And the rule MAVsMARule with SMA period 2 and SMA period 4 source CLOSE
    When I detect
    Then the result contains 2 MAAboveMA
    And the result contains 0 MABelowMA
    And no match occurs before bar 4
    And every MAAboveMA match has flavor STATE

  Scenario: MABelowMA emitted while the fast MA sits below the slow MA
    Given a detection spec builder
    And an OHLC series with closes 5, 4, 3, 2, 1
    And the rule MAVsMARule with SMA period 2 and SMA period 4 source CLOSE
    When I detect
    Then the result contains 2 MABelowMA
    And the result contains 0 MAAboveMA

  Scenario: Equal MA configurations emit nothing
    Given a detection spec builder
    And an OHLC series with closes 1, 2, 3, 4, 5
    And the rule MAVsMARule with SMA period 3 and SMA period 3 source CLOSE
    When I detect
    Then the result contains 0 MAAboveMA
    And the result contains 0 MABelowMA

  Scenario: MACrossedAboveMA emitted only at the crossing bar, no re-fire
    Given a detection spec builder
    And an OHLC series with closes 5, 4, 3, 2, 3, 4, 5, 6
    And the rule MACrossMARule with SMA period 2 and SMA period 4 source CLOSE
    When I detect
    Then the result contains 1 MACrossedAboveMA
    And the result contains 0 MACrossedBelowMA
    And every MACrossedAboveMA match has flavor EVENT

  Scenario: MACrossedBelowMA emitted only at the crossing bar
    Given a detection spec builder
    And an OHLC series with closes 10, 11, 12, 13, 12, 11, 10, 9
    And the rule MACrossMARule with SMA period 2 and SMA period 4 source CLOSE
    When I detect
    Then the result contains 1 MACrossedBelowMA
    And the result contains 0 MACrossedAboveMA
