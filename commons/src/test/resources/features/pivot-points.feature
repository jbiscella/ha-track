Feature: Pivot point computation
  Maps the canonical pivot formulas. Uses prior-period H=120, L=90, C=108
  (range 30) so STANDARD and WOODIE central pivots differ (106 vs 106.5).

  Scenario: STANDARD produces P, R1-R3, S1-S3 (no R4/S4)
    Given a previous-period OHLC bar with high=120, low=90, close=108
    When I compute STANDARD pivots
    Then pivot P is 106
    And pivot R1 is 122
    And pivot R2 is 136
    And pivot R3 is 152
    And pivot S1 is 92
    And pivot S2 is 76
    And pivot S3 is 62
    And pivot R4 is absent
    And pivot S4 is absent

  Scenario: WOODIE produces P, R1-R2, S1-S2 (no R3/R4/S3/S4)
    Given a previous-period OHLC bar with high=120, low=90, close=108
    When I compute WOODIE pivots
    Then pivot P is 106.5
    And pivot R1 is 123
    And pivot R2 is 136.5
    And pivot S1 is 93
    And pivot S2 is 76.5
    And pivot R3 is absent
    And pivot S3 is absent
    And pivot R4 is absent
    And pivot S4 is absent

  Scenario: A zero-range (flat) prior bar collapses every STANDARD level to the close
    Given a previous-period OHLC bar with high=100, low=100, close=100
    When I compute STANDARD pivots
    Then pivot P is 100
    And pivot R1 is 100
    And pivot R2 is 100
    And pivot R3 is 100
    And pivot S1 is 100
    And pivot S2 is 100
    And pivot S3 is 100

  Scenario: CAMARILLA produces R1-R4, S1-S4 and no central P
    Given a previous-period OHLC bar with high=120, low=90, close=108
    When I compute CAMARILLA pivots
    Then pivot P is absent
    And pivot R1 is 110.75
    And pivot R2 is 113.5
    And pivot R3 is 116.25
    And pivot R4 is 124.5
    And pivot S1 is 105.25
    And pivot S2 is 102.5
    And pivot S3 is 99.75
    And pivot S4 is 91.5
