Feature: Pivot point detection
  Maps nachtkrapp/CLAUDE.md section 9 (Block 3) — pivot family.
  Day 1 aggregates to H=120, L=90, C=105 -> STANDARD pivots
  P=105, R1=120, R2=135, R3=150, S1=90, S2=75, S3=60.

  Scenario: Price compared to every applicable STANDARD level of the prior day
    Given a detection spec builder
    And an OHLC series:
      | time                 | open | high | low | close |
      | 2024-01-01T10:00:00Z | 100  | 110  | 90  | 100   |
      | 2024-01-01T11:00:00Z | 100  | 120  | 95  | 105   |
      | 2024-01-02T10:00:00Z | 128  | 132  | 125 | 130   |
    And the rule PivotPointRule with period "1d" variant STANDARD source CLOSE
    When I detect
    Then the result contains 5 PriceAbovePivot
    And the result contains 2 PriceBelowPivot
    And no match occurs before bar 3
    And every PriceAbovePivot match has flavor STATE

  Scenario: Bars in the first period emit nothing (no prior closed period)
    Given a detection spec builder
    And an OHLC series:
      | time                 | open | high | low | close |
      | 2024-01-01T10:00:00Z | 100  | 110  | 90  | 100   |
      | 2024-01-01T11:00:00Z | 100  | 120  | 95  | 105   |
    And the rule PivotPointRule with period "1d" variant STANDARD source CLOSE
    When I detect
    Then the result contains 0 PriceAbovePivot
    And the result contains 0 PriceBelowPivot

  Scenario: PriceCrossedAbovePivot only at the crossing bar
    Given a detection spec builder
    And an OHLC series:
      | time                 | open | high | low | close |
      | 2024-01-01T10:00:00Z | 100  | 110  | 90  | 100   |
      | 2024-01-01T11:00:00Z | 100  | 120  | 95  | 105   |
      | 2024-01-02T10:00:00Z | 101  | 102  | 99  | 100   |
      | 2024-01-02T11:00:00Z | 128  | 132  | 125 | 130   |
    And the rule PivotPointRule with period "1d" variant STANDARD source CLOSE
    When I detect
    Then the result contains 2 PriceCrossedAbovePivot
    And the result contains 0 PriceCrossedBelowPivot
    And every PriceCrossedAbovePivot match has flavor EVENT

  Scenario: CAMARILLA produces eight levels, none central
    Given a detection spec builder
    And an OHLC series:
      | time                 | open | high | low | close |
      | 2024-01-01T10:00:00Z | 100  | 110  | 90  | 100   |
      | 2024-01-01T11:00:00Z | 100  | 120  | 95  | 105   |
      | 2024-01-02T10:00:00Z | 200  | 205  | 198 | 200   |
    And the rule PivotPointRule with period "1d" variant CAMARILLA source CLOSE
    When I detect
    Then the result contains 8 PriceAbovePivot
    And the result contains 0 PriceBelowPivot
