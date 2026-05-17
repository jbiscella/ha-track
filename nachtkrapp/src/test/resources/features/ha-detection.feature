Feature: Heikin Ashi pattern detection
  Maps nachtkrapp/CLAUDE.md section 8 (Block 2).

  Scenario: HABullishReversal after a bearish streak
    Given a detection spec builder
    And an HA series:
      | time                 | haOpen | haHigh | haLow | haClose |
      | 2024-01-01T00:00:00Z | 10     | 10     | 8     | 9       |
      | 2024-01-02T00:00:00Z | 9      | 9      | 7     | 8       |
      | 2024-01-03T00:00:00Z | 8      | 8      | 6     | 7       |
      | 2024-01-04T00:00:00Z | 7      | 12     | 7     | 11      |
    And the rule HAColorChangeRule with minStreakLength 3
    When I detect
    Then the result contains 1 HABullishReversal
    And the result contains 0 HABearishReversal
    And the match at index 0 has time "2024-01-04T00:00:00Z"

  Scenario: HABearishReversal after a bullish streak
    Given a detection spec builder
    And an HA series:
      | time                 | haOpen | haHigh | haLow | haClose |
      | 2024-01-01T00:00:00Z | 10     | 12     | 10    | 11      |
      | 2024-01-02T00:00:00Z | 11     | 13     | 11    | 12      |
      | 2024-01-03T00:00:00Z | 12     | 14     | 12    | 13      |
      | 2024-01-04T00:00:00Z | 13     | 13     | 9     | 10      |
    And the rule HAColorChangeRule with minStreakLength 3
    When I detect
    Then the result contains 1 HABearishReversal
    And the match at index 0 has time "2024-01-04T00:00:00Z"

  Scenario: Streak shorter than minStreakLength is not a reversal
    Given a detection spec builder
    And an HA series:
      | time                 | haOpen | haHigh | haLow | haClose |
      | 2024-01-01T00:00:00Z | 10     | 12     | 10    | 11      |
      | 2024-01-02T00:00:00Z | 11     | 11     | 9     | 10      |
      | 2024-01-03T00:00:00Z | 10     | 10     | 8     | 9       |
      | 2024-01-04T00:00:00Z | 9      | 13     | 9     | 12      |
    And the rule HAColorChangeRule with minStreakLength 3
    When I detect
    Then the result contains 0 HABullishReversal

  Scenario: HABullishStrong on a clean bullish candle
    Given a detection spec builder
    And an HA series:
      | time                 | haOpen | haHigh | haLow | haClose |
      | 2024-01-01T00:00:00Z | 10     | 20     | 10    | 20      |
    And the rule HAStrongCandleRule with wickTolerance 0.1 and minBodyRatio 0.6
    When I detect
    Then the result contains 1 HABullishStrong

  Scenario: HABearishStrong on a clean bearish candle
    Given a detection spec builder
    And an HA series:
      | time                 | haOpen | haHigh | haLow | haClose |
      | 2024-01-01T00:00:00Z | 20     | 20     | 10    | 10      |
    And the rule HAStrongCandleRule with wickTolerance 0.1 and minBodyRatio 0.6
    When I detect
    Then the result contains 1 HABearishStrong

  Scenario: HADoji on a small body
    Given a detection spec builder
    And an HA series:
      | time                 | haOpen | haHigh | haLow | haClose |
      | 2024-01-01T00:00:00Z | 10     | 11     | 9     | 10.1    |
    And the rule HADojiRule with maxBodyRatio 0.1
    When I detect
    Then the result contains 1 HADoji
