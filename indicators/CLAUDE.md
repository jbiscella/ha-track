# CLAUDE.md — `indicators` module

This is the nested spec for the `indicators` module. The repo-wide rules (architecture, code style, dependency edges) live in the root `CLAUDE.md`. This file specifies only what is internal to `indicators`: the public calculator API, the result types, the canonical formulas, and the behavior under edge cases.

## 0. Goal and scope

`indicators` is the shared technical-indicator calculator kernel. It contains the indicator calculators that were formerly duplicated in the `internal` packages of `nachtkrapp` and `heerwisch-jfreechart`. The v1.1 extraction is triggered by a third upcoming consumer (`wichtelm-app`, a separate repository) needing the same calculators; root `CLAUDE.md` §6 reserved the `indicators` name for exactly this.

It contains:

- A single public calculator class `Indicators` with one static method per indicator (`sma`, `ema`, `rsi`, `macd`, `bollinger`, `atr`, `stochastic`, `adx`, `rollingMax`, `rollingMin`).
- Result records for the multi-output indicators (`MacdResult`, `BollingerBands`, `StochasticResult`).

The arithmetic is **unchanged** from the pre-extraction calculators: this module is the destination of a move, not a rewrite. `SMA(period)` and every other calculator produce values bit-identical to the formulas previously embedded in the two consumer modules (which were verified arithmetically identical before extraction).

Out of scope: I/O, persistence, clocks, framework annotations, anything that requires an external dependency. JDK-only is a hard constraint enforced at the module's `pom.xml` level (no `<dependency>` block beyond the JUnit-platform test stack — JUnit, Cucumber, and jqwik for property-based tests — all `test`-scoped). The module does not even depend on `commons`: calculators take `List<BigDecimal>` channels, not `commons` bar types — channel extraction stays consumer-side.

## 1. Runtime profile

| Aspect | Behavior |
|---|---|
| State | None. `Indicators` is a `final` class with a private constructor and only `static` methods. No instance, no static mutable state. |
| Purity | Every calculator is a pure function: no I/O, no clock, no randomness. |
| Thread-safety | Unconditional. Stateless static methods; safe to call concurrently from any number of threads. |
| Arithmetic | `BigDecimal` with `MathContext.DECIMAL64` throughout. No `double`, no `float`. |
| Evaluation model | Batch. Each call recomputes the whole series from the input list. There is no incremental / streaming update API in v1. |
| Lookahead-safety | Each output at bar `t` depends only on input bars `≤ t` (repo-wide invariant). |

## 2. Public types

### 2.1 `Indicators`

`final` class, private constructor, static methods only. Each calculator returns an array indexed by bar; entries are `null` for bars before the indicator's warm-up window, or when the input is too short to produce any value.

| Method | Signature | Returns |
|---|---|---|
| `sma` | `sma(List<BigDecimal> src, int period)` | `BigDecimal[]` — null before index `period - 1` |
| `ema` | `ema(List<BigDecimal> src, int period)` | `BigDecimal[]` — null before index `period - 1`; seed = SMA of first `period` values |
| `rsi` | `rsi(List<BigDecimal> src, int period)` | `BigDecimal[]` — null before index `period` |
| `macd` | `macd(List<BigDecimal> src, int fast, int slow, int signal)` | `MacdResult` |
| `bollinger` | `bollinger(List<BigDecimal> src, int period, BigDecimal multiplier)` | `BollingerBands` |
| `atr` | `atr(List<BigDecimal> high, List<BigDecimal> low, List<BigDecimal> close, int period)` | `BigDecimal[]` — null before index `period - 1` |
| `stochastic` | `stochastic(List<BigDecimal> high, List<BigDecimal> low, List<BigDecimal> close, int kPeriod, int dPeriod, int smoothing)` | `StochasticResult` |
| `adx` | `adx(List<BigDecimal> high, List<BigDecimal> low, List<BigDecimal> close, int period)` | `BigDecimal[]` — needs at least `2 * period + 1` bars |
| `rollingMax` | `rollingMax(List<BigDecimal> src, int period)` | `BigDecimal[]` — null before index `period - 1` |
| `rollingMin` | `rollingMin(List<BigDecimal> src, int period)` | `BigDecimal[]` — null before index `period - 1` |

Eager validation: every source list is `Objects.requireNonNull`-checked, `multiplier` is null-checked, and every period argument must be `>= 1`. A period `< 1` throws `IllegalArgumentException` before any computation; a null list throws `NullPointerException`. This is the repo-wide eager-validation convention applied to a static-function surface.

### 2.2 Result records

| Record | Components |
|---|---|
| `MacdResult` | `BigDecimal[] macdLine`, `BigDecimal[] signalLine`, `BigDecimal[] histogram` |
| `BollingerBands` | `BigDecimal[] upper`, `BigDecimal[] middle`, `BigDecimal[] lower` |
| `StochasticResult` | `BigDecimal[] percentK`, `BigDecimal[] percentD` |

Single-output indicators (`sma`, `ema`, `rsi`, `atr`, `adx`, `rollingMax`, `rollingMin`) return a bare `BigDecimal[]` — wrapping them in a one-component record would add no value.

### 2.3 Why no sealed `Indicator` spec hierarchy

The module exposes calculators, not indicator *specs*. The two consuming libraries each keep their own spec type — `nachtkrapp` has `DetectionRule`, `heerwisch-api` has `Indicator` — and those drive their own builders and validation. Introducing a third, parallel sealed `Indicator` hierarchy here would be net-new API with no consumer, contradicting the "moved, not changed" nature of this extraction. If a future consumer needs an inspectable indicator-spec type it can be added then; v1.1 keeps the surface minimal. The "one record per indicator kind" shape is satisfied by the three result records above.

## 3. Canonical formulas

The implementation MUST use these formulas. They are the same formulas previously documented in `nachtkrapp/CLAUDE.md` §13 (SMA, EMA, RSI, MACD) and `heerwisch-jfreechart/CLAUDE.md` §7 (the same four plus Bollinger, ATR, Stochastic, ADX); this module is now their single authoritative home.

| Indicator | Formula |
|---|---|
| `sma(period)` at bar `t` | arithmetic mean of `src` over the window `[t - period + 1, t]` |
| `ema(period)` at bar `t` | recursive smoothing with multiplier `k = 2 / (period + 1)`; seed = SMA of the first `period` values, placed at index `period - 1` |
| `rsi(period)` at bar `t` | Wilder's smoothing: seed `avgGain` / `avgLoss` = simple average of the first `period` gains / losses; thereafter `avg = (prevAvg * (period - 1) + current) / period`; `RSI = 100 - 100 / (1 + avgGain / avgLoss)`; when `avgLoss = 0`, `RSI = 100` |
| `macd(fast, slow, signal)` | `macdLine = ema(fast) - ema(slow)`; `signalLine = ema(signal)` of the `macdLine` (seeded from the first non-null MACD value); `histogram = macdLine - signalLine` |
| `bollinger(period, multiplier)` | `middle = sma(period)`; `stdDev` = population standard deviation over the same window (sum of squared deviations divided by `period`, then square-rooted); `upper = middle + multiplier * stdDev`; `lower = middle - multiplier * stdDev` |
| `atr(period)` | true range `TR[t] = max(high - low, |high - close[t-1]|, |low - close[t-1]|)` with `TR[0] = high[0] - low[0]`; ATR is Wilder-smoothed TR: seed = simple average of the first `period` TRs, then `ATR = (prevATR * (period - 1) + TR) / period` |
| `stochastic(kPeriod, dPeriod, smoothing)` | raw `%K = (close - lowestLow) / (highestHigh - lowestLow) * 100` over `kPeriod` bars (50 when the range is zero); `percentK` = SMA of raw %K over `smoothing`; `percentD` = SMA of `percentK` over `dPeriod` |
| `adx(period)` | directional movement `+DM` / `-DM`, Wilder-smoothed alongside TR into `+DI` / `-DI`; `DX = 100 * |+DI - -DI| / (+DI + -DI)`; ADX is Wilder-smoothed DX seeded at index `2 * period` |
| `rollingMax(period)` at bar `t` | the greatest value of `src` over the window `[t - period + 1, t]`; null before index `period - 1`. Pure comparison — no arithmetic, no rounding |
| `rollingMin(period)` at bar `t` | the smallest value of `src` over the window `[t - period + 1, t]`; null before index `period - 1`. Pure comparison — no arithmetic, no rounding |

## 4. Block — Indicator reference values

Reference-value scenarios. A cell of `null` means the calculator produced no value for that bar (warm-up window). Numeric comparison tolerates last-digit `DECIMAL64` rounding noise (absolute tolerance `1e-7`), well below any genuine formula error.

```gherkin
Feature: Shared indicator calculators

  Scenario: SMA equals the rolling arithmetic mean
    Given the price series "1, 2, 3, 4, 5"
    When I compute SMA with period 3
    Then the indicator series equals "null, null, 2, 3, 4"

  Scenario: SMA period 1 is the source series itself
    Given the price series "7, 8, 9"
    When I compute SMA with period 1
    Then the indicator series equals "7, 8, 9"

  Scenario: Rolling maximum over the trailing window
    Given the price series "3, 1, 4, 1, 5, 9, 2, 6"
    When I compute RollingMax with period 3
    Then the indicator series equals "null, null, 4, 4, 5, 9, 9, 9"

  Scenario: Rolling minimum over the trailing window
    Given the price series "3, 1, 4, 1, 5, 9, 2, 6"
    When I compute RollingMin with period 3
    Then the indicator series equals "null, null, 1, 1, 1, 1, 2, 2"

  Scenario: EMA seeds on the SMA of the first period values
    Given the price series "1, 2, 3, 4, 5"
    When I compute EMA with period 3
    Then the indicator series equals "null, null, 2, 3, 4"

  Scenario: RSI is 100 for a strictly rising series
    Given the price series "1, 2, 3, 4, 5, 6"
    When I compute RSI with period 3
    Then the indicator series equals "null, null, null, 100, 100, 100"

  Scenario: RSI is 0 for a strictly falling series
    Given the price series "6, 5, 4, 3, 2, 1"
    When I compute RSI with period 3
    Then the indicator series equals "null, null, null, 0, 0, 0"

  Scenario: RSI tracks Wilder smoothing on an alternating series
    Given the price series "10, 11, 10, 11, 10, 11"
    When I compute RSI with period 2
    Then the indicator series equals "null, null, 50, 75, 37.5, 68.75"

  Scenario: MACD of a flat series is zero
    Given the price series "5, 5, 5, 5, 5, 5, 5, 5"
    When I compute MACD with fast 2 slow 4 signal 2
    Then the MACD line equals "null, null, null, 0, 0, 0, 0, 0"
    And the MACD signal line equals "null, null, null, null, 0, 0, 0, 0"
    And the MACD histogram equals "null, null, null, null, 0, 0, 0, 0"

  Scenario: MACD of a linear ramp has a constant line and zero histogram
    Given the price series "1, 2, 3, 4, 5, 6, 7, 8"
    When I compute MACD with fast 2 slow 4 signal 2
    Then the MACD line equals "null, null, null, 1, 1, 1, 1, 1"
    And the MACD signal line equals "null, null, null, null, 1, 1, 1, 1"
    And the MACD histogram equals "null, null, null, null, 0, 0, 0, 0"

  Scenario: Bollinger Bands wrap the SMA by multiplier standard deviations
    Given the price series "3, 5"
    When I compute Bollinger Bands with period 2 and multiplier 2
    Then the Bollinger upper band equals "null, 6"
    And the Bollinger middle band equals "null, 4"
    And the Bollinger lower band equals "null, 2"

  Scenario: Bollinger Bands collapse onto the mean for a flat series
    Given the price series "5, 5, 5, 5"
    When I compute Bollinger Bands with period 3 and multiplier 2
    Then the Bollinger upper band equals "null, null, 5, 5"
    And the Bollinger middle band equals "null, null, 5, 5"
    And the Bollinger lower band equals "null, null, 5, 5"

  Scenario: ATR is the Wilder-smoothed true range
    Given the OHLC bars (high, low, close)
    When I compute ATR with period 2
    Then the indicator series equals "null, 2, 3"

  Scenario: Stochastic oscillator %K and %D over the high-low range
    Given the OHLC bars (high, low, close)
    When I compute Stochastic with kPeriod 2 dPeriod 2 smoothing 1
    Then the Stochastic %K equals "null, 75, 80"
    And the Stochastic %D equals "null, null, 77.5"

  Scenario: ADX warms up after 2 * period bars
    Given a 7-bar OHLC series
    When I compute ADX with period 2
    Then indicator values before index 4 are null
    And indicator values from index 4 are present

  Scenario: A period below 1 is rejected eagerly
    Given the price series "1, 2, 3"
    When I compute SMA with period 0
    Then an IllegalArgumentException is thrown
```

The runnable feature file (`src/test/resources/features/indicators.feature`) carries the exact OHLC data tables for the ATR / Stochastic / ADX scenarios.

## 5. Out of scope for `indicators`

These belong elsewhere; do NOT add them to `indicators`:

- Any I/O, clock, or external dependency (JDK-only is a hard constraint).
- Incremental / streaming update API — v1 is batch; every call recomputes the full series.
- A pluggable / driver abstraction — alternative calculator implementations are not anticipated; the formulas are canonical.
- Indicator *spec* types, builders, or a sealed `Indicator` hierarchy — see §2.3.
- Channel extraction from `commons` bar types (`OHLCBar`, `HABar`) — consumers pass plain `List<BigDecimal>` channels.
- Pivot detection, candlestick or chart patterns — out of repo v1/v2 scope per the root spec.

## 6. Implementation delegation to Claude Code

Claude Code is responsible for:

- Package layout (single package `org.hatrack.indicators`; no `internal` subpackage — every type is public API).
- Implementing the `BigDecimal` arithmetic with the prescribed `MathContext.DECIMAL64`.
- Writing the eager argument validation.
- Test infrastructure for the Gherkin scenarios (Cucumber for Java via JUnit Platform; feature files under `src/test/resources/features/`, step definitions under `src/test/java/`).

What Claude Code MUST NOT do unilaterally:

- Add dependencies beyond JDK + the test stack.
- Change any indicator's arithmetic — the extraction must be value-preserving.
- Use `double` or `float` in any calculation.
- Add an incremental-update API or a driver abstraction.
