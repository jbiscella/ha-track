# CLAUDE.md — `dsl-eval` module

This is the nested spec for the `dsl-eval` module. The repo-wide rules
(architecture, API style, code style, dependency edges) live in the root
`CLAUDE.md`. This file specifies what is internal to `dsl-eval`: the public
seam, the supported DSL surface, and the statelessness contract.

`dsl-eval` is a **shared kernel**, in the same category as `commons` and
`indicators` — not one of the three folklore libraries. It was promoted out of
the `wichtelm-app` consumer so that more than one consumer (e.g. `wichtelm-app`
and `hütchen`) can share the *exact same* condition-evaluation code. That parity
— an identical condition evaluating identically in every consumer — is the
module's entire reason to exist.

## 0. Goal and scope

`dsl-eval` evaluates a textual condition/arithmetic DSL down to per-bar boolean
and `BigDecimal` values. It defines:

- **`ExpressionEvaluator`** — the recursive-descent evaluator for arithmetic,
  the English-prose comparison operators (`is above`, `is below`, `exceeds`,
  `crosses above`/`rises above`, `crosses below`/`drops below`), bare
  boolean-valued primitives, and pivot boolean steps. Stateless; holds only the
  per-bar diagnostic context.
- The injected-interface **seam**: `ExpressionEvaluator.Values` (bare
  identifier → `BigDecimal`), `ExpressionEvaluator.IndicatorSource`
  (function call → `BigDecimal`, plus the `pivotPrimitive` boolean path), and
  the `ExpressionEvaluator.Scope` record bundling the two for one bar. This is
  the **public API** by which each consumer plugs in its own value source. It
  must not be collapsed.
- **`BarIndicatorSource`** — a reference `IndicatorSource` that resolves
  indicator function names over a `List<OHLCBar>` using the `indicators` module,
  and resolves boolean pattern primitives via `NachtkrappMatchIndex`.
- **`NachtkrappMatchIndex`** — a prepass that scans expression texts for boolean
  primitive calls, maps each to a `nachtkrapp` `DetectionRule`, runs detection
  once against the closed series, and indexes match instants per call for O(1)
  per-bar lookup.
- **`ExpressionRef`** — the `(text, timeframeWire)` seam input to the prepass;
  the consumer assigns each expression its timeframe.
- The exceptions `DslEvaluationException` (hard evaluation error) and
  `IndicatorWarmupException` (expected warm-up state; the caller treats the
  affected condition as not met).

Out of scope (and explicitly NOT promoted): signal generation, positions,
direction, entry price, position sizing, backtest orchestration. Those are
stateful trading concerns and stay in the consumer. The evaluator receives every
value through the injected seam and carries no trading state.

## 1. Statelessness contract

- The evaluator and `BarIndicatorSource` hold only immutable inputs (an
  expression / a copied bar list) plus per-bar diagnostic context. No mutable
  static state.
- No reference to `entry_price`, `position`, or a trading `Signal` type exists in
  this module. `BarIndicatorSource.atr_value` is an alias for `atr` at the
  source's current bar; any "freeze" semantics are the caller's, achieved by
  positioning the source — not by this module holding state.
- Lookahead-safety (repo-wide invariant): `NachtkrappMatchIndex` pre-computes
  the whole match set against the closed series, which is equivalent to per-bar
  evaluation because each `nachtkrapp` match's `time()` is the bar at which the
  rule fired using only bars up to and including that time.

## 2. Supported DSL surface

The surface is exactly what was promoted — do not extend it here. New indicators
or primitives belong in `indicators` / `nachtkrapp` and are surfaced only once a
consumer needs them.

- **Numeric functions** (`BarIndicatorSource`): `sma`, `ema`, `rsi`, `atr`,
  `atr_value`, `stddev`, `macd_line`, `macd_signal`, `macd_histogram`,
  `highest_high`, `lowest_low`, `highest_close`, `lowest_close`, `avg_volume`.
- **Boolean primitives** (resolved through `NachtkrappMatchIndex`): the HA
  primitives (`ha_doji`, `ha_strong`, `ha_strong_bullish`, `ha_strong_bearish`,
  `ha_bullish_reversal`, `ha_bearish_reversal`), RSI (`rsi_overbought`,
  `rsi_oversold`, `rsi_crosses_50`), MACD (`macd_bullish_cross`,
  `macd_bearish_cross`, `macd_zero_cross_up`, `macd_zero_cross_down`), the
  price/MA trend filters and crosses (`price_above_sma`/`ema`,
  `price_below_sma`/`ema`, `price_crosses_above/below_sma`/`ema`, `sma_above_ema`,
  `sma_crosses_above/below_ema`), and the pivot primitives
  (`price_above_pivot`, `price_below_pivot`, `price_crosses_above/below_pivot`,
  whose single argument is a symbolic level token such as `R1`/`S1`).

## 3. Arithmetic

`BigDecimal` under `MathContext.DECIMAL64` everywhere, preserved byte-for-byte
from the promoted source. No `double`/`float`. Parity depends on identical
numeric behaviour, so do not change the arithmetic or rounding.

## 4. Dependencies

`commons`, `indicators`, `nachtkrapp`, and the JDK — nothing else. No wichtelm
dependency, no third-party libraries. Tests use JUnit Jupiter only.

## 5. Implementation delegation

Claude Code owns package layout (`org.hatrack.dsl`, errors under
`org.hatrack.dsl.error`), test conventions (JUnit Jupiter), and boilerplate. It
MUST NOT add DSL primitives, indicators, or trading state beyond the promoted
surface, nor depend on anything outside the four dependencies above.
