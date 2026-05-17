Feature: Moving Average pattern detection
  Maps nachtkrapp/CLAUDE.md section 9 (Block 3).

  Scenario: PriceAboveMA emitted for every bar where price exceeds the MA
    Given a detection spec builder
    And an OHLC series with closes 1, 2, 3, 4, 5
    And the rule PriceVsMARule with SMA period 3 source CLOSE
    When I detect
    Then the result contains 3 PriceAboveMA
    And the result contains 0 PriceBelowMA
    And no match occurs before bar 3

  Scenario: PriceBelowMA emitted for every bar where price is under the MA
    Given a detection spec builder
    And an OHLC series with closes 5, 4, 3, 2, 1
    And the rule PriceVsMARule with SMA period 3 source CLOSE
    When I detect
    Then the result contains 3 PriceBelowMA
    And the result contains 0 PriceAboveMA

  Scenario: PriceCrossedAboveMA emitted only at the crossing bar
    Given a detection spec builder
    And an OHLC series with closes 5, 4, 3, 2, 3, 4, 5, 6
    And the rule PriceMACrossRule with SMA period 3 source CLOSE
    When I detect
    Then the result contains 1 PriceCrossedAboveMA

  Scenario: EMA produces different match values than SMA
    Given a detection spec builder
    And an OHLC series with closes 1, 2, 3, 4, 5, 6, 7, 8
    And the rule PriceVsMARule with SMA period 3 source CLOSE
    And the rule PriceVsMARule with EMA period 3 source CLOSE
    When I detect
    Then the PriceAboveMA matches have at least 2 distinct maValue
