Feature: ThemeConstants read-only exposure
  Maps heerwisch-jfreechart/CLAUDE.md section 14 (Block 5).

  Scenario Outline: Constants match the documented hex values
    Then ThemeConstants color <name> equals hex "<hex>"

    Examples:
      | name            | hex    |
      | BACKGROUND      | FFFFFF |
      | BULLISH_CANDLE  | 26A69A |
      | BEARISH_CANDLE  | EF5350 |
      | SMA_LINE        | 1976D2 |
      | EMA_LINE        | F57C00 |
      | RSI_LINE        | 7B1FA2 |

  Scenario: Every ThemeConstants color field is public, static and final
    Then every ThemeConstants color field is public static and final
