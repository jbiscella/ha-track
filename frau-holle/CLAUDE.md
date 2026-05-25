# CLAUDE.md — `frau-holle` module

This is the nested spec for the `frau-holle` module. The repo-wide rules live in the root `CLAUDE.md`. This file specifies what is internal to `frau-holle`: the public types, the strategy port, the market-data port, the backtester engine, the result types, the validation rules, and the exception hierarchy.

`frau-holle` is a **single module**. There is no separate `-api` artifact and no "engine driver" abstraction. Pluggability lives in two ports exposed by the module: `MarketDataSource` (for data providers) and `SignalGenerator` (for strategies). Implementations of `MarketDataSource` live in sibling modules (`frau-holle-csv`, `frau-holle-eodhd`, …); implementations of `SignalGenerator` are written by the consumer.

## 0. Goal and scope

`frau-holle` is the backtester library. It defines:

- The **`MarketDataSource`** port — pluggable data provider abstraction.
- The **`SignalGenerator`** port — opaque consumer strategy abstraction.
- The **`BarContext`** record passed to the strategy at each step.
- The **`Signal`** sealed hierarchy returned by the strategy.
- The **`BacktestSpec`** that bundles the inputs of a backtest run.
- The **`BacktestResult`** with metrics, trade list, equity curve, and optional open position.
- The **`Backtester`** entry point that executes the simulation.
- The checked exception hierarchy rooted at `BacktestException`.

Out of scope (v1): slippage models, commission models, multi-instrument portfolios, optimization / parameter sweeps, pivot detection (out of repo v1), multi-timeframe orchestration (consumer-side per repo principle), live trading.

Dependencies: only `commons` and JDK.

## 1. Runtime profile

`frau-holle` is a **local backtester** intended to run on a developer laptop. CPU cost is not a v1 constraint. The engine is event-driven (bar-by-bar), not vectorized. Lambda-compatibility is NOT a requirement for this module.

| Aspect | v1 choice |
|---|---|
| Execution model | Event-driven, sequential per bar |
| Parallelism | None at the bar loop (single-threaded simulation) |
| Multi-instrument | Single instrument per backtest run |
| Memory budget | Holds the full series in memory; suitable for daily/weekly bars over decades, or intraday over months |

## 2. Public types

### 2.1 `MarketDataSource` port

```
List<OHLCBar> fetchHistory(String symbol, Timeframe timeframe, Instant since, Instant until)
    throws MarketDataException
```

Contract:

| Aspect | Required behavior |
|---|---|
| Input | `symbol` non-null non-blank; `timeframe` non-null; `since` ≤ `until`; both non-null |
| Output | `List<OHLCBar>` ordered ascending by `time()`, with unique `time()` values, all within `[since, until]` (inclusive endpoints) |
| Empty result | Returns empty list if no bars are available in the range; this is NOT an error |
| Lookahead-safety | Implementations MUST NOT return bars whose `time` is in the future relative to the wall clock at call time (but `until` may be in the future, in which case the implementation truncates at the last available bar) |
| Thread-safety | NOT required at the port level. Implementations MAY declare themselves thread-safe |
| Idempotency | Calls with the same arguments SHOULD return the same data, modulo upstream provider updates and corrections. Consumer code MUST NOT rely on bit-identical responses across calls |
| Exceptions | `MarketDataException` (or subclasses); other `RuntimeException`s indicate programmer error |

`MarketDataSource` is an interface in `frau-holle`. The reference implementations (`frau-holle-csv`, `frau-holle-eodhd`) live in sibling modules.

#### 2.1.1 Shared conformance suite

`frau-holle` publishes a `test-jar` containing an abstract JUnit class `org.hatrack.frauholle.contract.MarketDataSourceContract`. It codifies the **output** invariants of this port on well-formed input: the result is a non-null list of non-null bars, ordered by strictly-ascending (hence unique) `time`, every bar within the requested `[since, until]`, and an empty range returns an empty list rather than throwing.

Every `MarketDataSource` implementation MUST run this suite: a driver module depends on the test-jar (`<type>test-jar</type>`, `test` scope) and adds a concrete subclass that wires its source to well-formed data plus one populated and one guaranteed-empty query. `frau-holle-csv` and `frau-holle-eodhd` both do this today; any future `frau-holle-<source>` MUST too.

The suite covers only the **port-contract** invariants, which are shared. A driver's **normalization policy** — how it reaches those invariants from a messy feed (sorting, de-duplication, skipping malformed rows, schema rejection) — is driver-specific and is exercised by that driver's own tests, not the shared contract.

### 2.2 `SignalGenerator` port

```
Signal generate(BarContext context) throws SignalGenerationException
```

Contract:

| Aspect | Required behavior |
|---|---|
| Input | `BarContext` non-null. Null input throws `NullPointerException` |
| Output | `Signal` non-null. If an implementation returns `null`, the backtester treats it as a strategy contract breach and throws `SignalGenerationException` carrying the offending `barIndex` (the cause is a `NullPointerException`) |
| Statelessness | NOT required. Implementations MAY hold internal state across calls (e.g. memory of recent bars, internal indicators, references to additional series the consumer has loaded out-of-band) |
| Thread-safety | NOT required. The backtester calls `generate` sequentially on a single thread |
| Lookahead-safety | Implementations MUST NOT consult bars at times `> context.currentBar().time()`. This is a contractual promise of the strategy; the backtester does not police it. Violations corrupt the backtest |

`SignalGenerator` is opaque: `frau-holle` knows nothing about how the strategy decides. The strategy can be rule-based, ML-based, hand-coded if-else, or compiled from a future YAML DSL — all consumer concerns.

### 2.3 `BarContext`

Immutable record passed to `SignalGenerator.generate()`:

| Accessor | Type | Meaning |
|---|---|---|
| `currentBar()` | `OHLCBar` | The bar at the current step. The strategy may use only data up to and including this bar |
| `history()` | `List<OHLCBar>` | All prior bars in the backtest range, ordered ascending by `time()`. Does NOT include `currentBar()`. Defensively shared (immutable List view) |
| `currentPosition()` | `Optional<Position>` | The open position at the start of the current bar, or empty if none |
| `currentCash()` | `BigDecimal` | Available cash at the start of the current bar |
| `currentEquity()` | `BigDecimal` | Total equity (cash + value of open position marked-to-market at previous bar's close) at the start of the current bar |
| `barIndex()` | `int` | Zero-based index of `currentBar` in the series |

### 2.4 `Signal` (sealed)

```
sealed interface Signal permits Hold, Buy, Sell, ClosePosition
```

| Variant | Fields | Meaning |
|---|---|---|
| `Hold` | (no fields) | No action this bar |
| `Buy` | `BigDecimal quantity` (> 0) | Open a long position with the given quantity. If a position is already open, signal is ignored (logged via diagnostics) |
| `Sell` | `BigDecimal quantity` (> 0) | Open a short position. Symmetric to `Buy` |
| `ClosePosition` | (no fields) | Close any currently open position. If no position is open, no-op |

v1 supports long-only and short-only positions implicitly via `Buy` and `Sell`. Multi-leg, pyramiding, partial close — out of scope v1. A `Buy` or `Sell` signal while a position is already open is **ignored** (the existing position is not modified); this is logged in the backtest diagnostics.

### 2.5 `Position`

Immutable record:

| Field | Type | Meaning |
|---|---|---|
| `direction` | `Direction` enum: `LONG`, `SHORT` | direction of the position |
| `quantity` | `BigDecimal` | absolute quantity |
| `entryTime` | `Instant` | bar time at which entry fill occurred |
| `entryPrice` | `BigDecimal` | actual fill price (the `open` of the bar after the signal) |

### 2.6 `Trade`

Immutable record representing a completed (entered and closed) position:

| Field | Type | Meaning |
|---|---|---|
| `direction` | `Direction` | direction of the trade |
| `quantity` | `BigDecimal` | quantity traded |
| `entryTime` | `Instant` | entry fill time |
| `entryPrice` | `BigDecimal` | entry fill price |
| `exitTime` | `Instant` | exit fill time |
| `exitPrice` | `BigDecimal` | exit fill price |
| `pnl` | `BigDecimal` | realized profit/loss |
| `pnlPercent` | `BigDecimal` | realized P&L as fraction of entry capital (signed) |

### 2.7 `EquityPoint`

Immutable record:

| Field | Type |
|---|---|
| `time` | `Instant` |
| `equity` | `BigDecimal` |
| `cash` | `BigDecimal` |
| `positionValue` | `BigDecimal` (zero if no open position) |

### 2.8 `BacktestSpec`

Immutable record:

| Accessor | Type |
|---|---|
| `series()` | `List<OHLCBar>` (non-empty, ordered, unique times) |
| `signalGenerator()` | `SignalGenerator` (non-null) |
| `initialCash()` | `BigDecimal` (> 0) |

No public constructor. Built only via `BacktestSpec.builder()`.

### 2.9 `BacktestResult`

Immutable record:

| Accessor | Type | Meaning |
|---|---|---|
| `metrics()` | `BacktestMetrics` | the 10 core metrics; see §3 |
| `trades()` | `List<Trade>` | all completed trades, ordered by `exitTime` |
| `equityCurve()` | `List<EquityPoint>` | one point per bar in the series, ordered by `time` |
| `openPositionAtEnd()` | `Optional<Position>` | a position still open at the last bar, if any. Its value is included in equity (mark-to-market) but it is NOT in `trades()` |
| `diagnostics()` | `BacktestDiagnostics` | informational counters (ignored signals, unfilled signals at end of series, etc.) |

### 2.10 `BacktestDiagnostics`

Immutable record:

| Accessor | Type | Meaning |
|---|---|---|
| `ignoredBuySignals()` | `int` | count of `Buy` signals issued while a position was already open |
| `ignoredSellSignals()` | `int` | count of `Sell` signals issued while a position was already open |
| `noOpClosePositionSignals()` | `int` | count of `ClosePosition` signals issued while no position was open |
| `unfilledSignalsAtEndOfSeries()` | `int` | count of `Buy`/`Sell` signals issued at the last bar (no next bar to fill at) |

## 3. Backtest metrics

`BacktestMetrics` is an immutable record exposing the 10 core metrics below, plus the exact `winningTrades` / `losingTrades` counts added in 0.55.0-alpha (§18):

| Metric | Type | Definition |
|---|---|---|
| `totalReturn` | `BigDecimal` | `(finalEquity - initialCash) / initialCash`, as a fraction (e.g. 0.25 = +25%) |
| `winRate` | `BigDecimal` | number of trades with `pnl > 0` divided by total trades. `BigDecimal.ZERO` if no trades |
| `numTrades` | `int` | size of `trades()` |
| `winningTrades` | `int` | number of trades with `pnl > 0` — identical to the `winRate` numerator. Added 0.55.0-alpha (§18) |
| `losingTrades` | `int` | `numTrades − winningTrades`; break-even (`pnl == 0`) trades count here, so `winningTrades + losingTrades == numTrades` exactly. Added 0.55.0-alpha (§18) |
| `maxDrawdown` | `BigDecimal` | maximum peak-to-trough decline of the equity curve, as a fraction of the peak. Always ≤ 0, expressed as a non-negative value (so 0.30 = -30% drawdown) |
| `sharpeRatio` | `BigDecimal` | `mean(returns) / stddev(returns) × sqrt(periodsPerYear)`. `returns` are bar-to-bar percentage changes of equity. `periodsPerYear` is inferred from the series timeframe (see §3.1). Risk-free rate = 0. `BigDecimal.ZERO` if stddev = 0 or < 2 bars |
| `sortinoRatio` | `BigDecimal` | same as Sharpe but with downside-only stddev (only negative `returns` contribute to the denominator). `BigDecimal.ZERO` if no negative returns or < 2 bars |
| `profitFactor` | `BigDecimal` | sum of all positive trade PnLs divided by absolute sum of all negative trade PnLs. `BigDecimal.ZERO` if no losing trades (consumer interprets). Also `BigDecimal.ZERO` when there are no winning trades — the positive numerator is zero, so the quotient is zero |
| `avgWin` | `BigDecimal` | mean `pnl` across winning trades. `BigDecimal.ZERO` if no winning trades |
| `avgLoss` | `BigDecimal` | mean `pnl` across losing trades (a negative number). `BigDecimal.ZERO` if no losing trades |
| `calmarRatio` | `BigDecimal` | `annualizedReturn / maxDrawdown`. `annualizedReturn = totalReturn / yearsCovered`. `BigDecimal.ZERO` if `maxDrawdown = 0` |

All `BigDecimal` arithmetic uses `MathContext.DECIMAL64`.

### 3.1 `periodsPerYear` inference

| Timeframe wire | periodsPerYear |
|---|---|
| `1m` | 525600 |
| `5m` | 105120 |
| `15m` | 35040 |
| `30m` | 17520 |
| `1h` | 8760 |
| `4h` | 2190 |
| `1d` | 252 (trading days convention) |
| `1w` | 52 |
| `1M` | 12 |
| `1y` | 1 |
| (other) | derived from the duration of the timeframe in seconds: `31536000 / timeframeSeconds`, with daily as the exception above |

The backtester infers `periodsPerYear` from the timeframe of the series. Since the series in `BacktestSpec` is just `List<OHLCBar>` without an attached timeframe, the timeframe is derived by `Backtester` from the spacing between bars: it takes the **most-common (modal)** consecutive `time()` delta (ties broken by the smaller gap) and matches it against the known timeframes within a 1% tolerance. Using the modal gap means the rare larger gaps every real feed has — overnight, weekend, holiday, Easter, multi-day closures — are ignored, so market-hours-only intraday and weekend-gapped daily series infer correctly. The modal match preserves the table values (so daily stays the 252 trading-day convention). If the modal gap matches **no** known timeframe, the rhythm is **not** rejected: `periodsPerYear` degrades to the calendar estimate `31536000 / modalGapSeconds` with a one-line console warning (better an approximate annualization than a backtest that cannot run). `periodsPerYear` is empty — surfacing as `V5` — only for genuinely broken input: fewer than two bars, an out-of-order gap (`Δt < 0`), or a duplicate timestamp (`Δt = 0`).

## 4. Fill timing

When `SignalGenerator.generate(context)` at bar `t` returns `Buy`, `Sell`, or `ClosePosition`:

| Aspect | Behavior |
|---|---|
| Fill bar | bar `t+1` (the next bar after the signal) |
| Fill price | `open` of bar `t+1` |
| If `t` is the last bar of the series | The signal is discarded (counted in `diagnostics.unfilledSignalsAtEndOfSeries`) |
| If a position is open at the last bar | Marked-to-market at the `close` of the last bar in the equity curve. NOT closed automatically. Reported in `openPositionAtEnd` |

Rationale: nobody trades at the close that has already passed. The realistic model executes at the next open. Mark-to-market at end-of-series preserves equity-curve accuracy without inventing an unrealistic forced close.

## 5. Frictionless v1

v1 backtest is **frictionless**: zero commissions, zero slippage, infinite liquidity at the fill price. This is documented prominently in the `BacktestResult` as a non-binding hint (no field, just convention): consumers reading the result should know the metrics overstate live performance.

Slippage and commission models are reserved for v1.1+ as additive opt-in ports (`SlippageModel`, `CommissionModel`).

## 6. `Backtester` entry point

```
BacktestResult run(BacktestSpec spec) throws BacktestException
```

Contract:

| Aspect | Required behavior |
|---|---|
| Input | `BacktestSpec` non-null. Null throws `NullPointerException` |
| Output | `BacktestResult` non-null |
| Thread-safety | NOT required. Multiple backtests on the same `Backtester` instance from different threads MUST be externally serialized. (Justification: `SignalGenerator` is allowed to be stateful) |
| Determinism | For the same spec and the same `SignalGenerator` instance with the same internal state, output is fully deterministic |
| Side effects | None at the `Backtester` level. The `SignalGenerator` may have side effects (consumer concern); the `MarketDataSource` may do I/O (it's called BEFORE the backtest, when the consumer pre-loads the series) |

The series in `BacktestSpec` is already loaded. `Backtester` does NOT call `MarketDataSource` itself; the consumer pre-loads via the data source and passes the resulting list. This separation lets the consumer cache data, transform it, or load multiple series for multi-TF strategies (consumer-side composition).

## 7. Validation rules — `InvalidBacktestSpecException`

| # | Rule | Violation example |
|---|---|---|
| V1 | `series` MUST be set | builder.build() with no series |
| V2 | `series` MUST be non-empty | empty list |
| V3 | `signalGenerator` MUST be set | builder.build() with no signal generator |
| V4 | `initialCash` MUST be > 0 | initialCash ≤ 0 |
| V5 | series bars MUST be ordered ascending by `time` with unique times, and a rhythm MUST be inferable from the most-common gap (per §3.1). Non-uniform / gapped spacing is **allowed** (the modal gap drives inference; unknown cadences degrade to a calendar estimate). V5 fires only when the rhythm cannot be inferred — an out-of-order gap or a duplicate timestamp | unordered or duplicate-timestamp bars (gapped-but-monotonic series are accepted) |
| V6 | series MUST have ≥ 2 bars | series of 1 bar (cannot compute returns) |
| V7 | every `OHLCBar` in the series MUST satisfy its OHLC invariants — positive prices, `high ≥ low`, `high ≥ open/close`, `low ≤ open/close`, `volume ≥ 0` when present (the `commons` invariant set, §2 of `commons/CLAUDE.md`) | a bar with `high < low` |

V7 enforces, at the spec boundary, the `OHLCBar.validateInvariants()` contract that `commons` leaves opt-in. The builder calls `validateInvariants()` for each bar; on `OHLCInvariantViolationException` it throws `InvalidBacktestSpecException` with `violatedRule = "V7"` and the offending bar's index and time in `offendingValue`.

## 8. Exception hierarchy

| Exception | Cause | Carrier fields |
|---|---|---|
| `BacktestException` (root) | abstract — never thrown directly | `String message`, `Throwable cause` |
| `InvalidBacktestSpecException` | Spec malformed | `String violatedRule`, `Object offendingValue` |
| `MarketDataException` | Data fetch failed (thrown by `MarketDataSource` implementations) | `String symbol`, `Throwable cause` |
| `MarketDataException` subclasses | `MarketDataNotFoundException` (symbol unknown), `MarketDataUnavailableException` (transient: timeout, 5xx, auth), `MarketDataSchemaException` (parse failure) | typed per subclass |
| `SignalGenerationException` | The `SignalGenerator` threw, or returned a `null` Signal (consumer bug) | `int barIndex`, `Throwable cause` |
| `BacktestInternalException` | Internal error inside the backtester loop | `Throwable cause` is mandatory |
| `InvalidExplicitFillException` | A v1.1 `ClosePositionAtPrice` carries a `fillTime` outside the valid intrabar window (see §15.1) | `Instant fillTime`, `Instant barTime` |

`InvalidBacktestSpecException` thrown from `build()`. `MarketDataException` thrown from `MarketDataSource` implementations. `SignalGenerationException`, `BacktestInternalException` and `InvalidExplicitFillException` thrown from `Backtester.run()`.

## 9. Block 1 — Builder validation

```gherkin
Feature: BacktestSpecBuilder eager validation

  Scenario: Missing series fails build
    Given a builder with no series set
    When I call build()
    Then InvalidBacktestSpecException is thrown with violatedRule = "V1"

  Scenario: Empty series fails build
    Given a builder with series = emptyList()
    When I call build()
    Then InvalidBacktestSpecException is thrown with violatedRule = "V2"

  Scenario: Missing signalGenerator fails build
    Given a builder with series set but no signalGenerator
    When I call build()
    Then InvalidBacktestSpecException is thrown with violatedRule = "V3"

  Scenario: Non-positive initialCash fails build
    Given a builder with initialCash = 0
    When I call build()
    Then InvalidBacktestSpecException is thrown with violatedRule = "V4"

  Scenario: Irregular bar spacing fails build
    Given a series with non-uniform time spacing not matching any Timeframe
    When I call build()
    Then InvalidBacktestSpecException is thrown with violatedRule = "V5"

  Scenario: Single-bar series fails build
    Given a series with exactly 1 bar
    When I call build()
    Then InvalidBacktestSpecException is thrown with violatedRule = "V6"

  Scenario: Unordered bars are rejected by the builder
    Given a series whose bars are not ascending by time
    When I call build()
    Then InvalidBacktestSpecException is thrown with violatedRule = "V5"

  Scenario: Duplicate bar timestamps are rejected by the builder
    Given a series with two bars sharing the same time
    When I call build()
    Then InvalidBacktestSpecException is thrown with violatedRule = "V5"

  Scenario: OHLC invariant violation in the series is rejected by the builder
    Given a series whose bars are ordered and uniformly spaced
    And one of those bars violates an OHLC invariant (high < low, open > high, close < low, or volume < 0)
    When I call build()
    Then InvalidBacktestSpecException is thrown with violatedRule = "V7"
```

## 10. Block 2 — Fill timing

```gherkin
Feature: Fill at next bar open

  Scenario: Buy signal at bar t fills at open of bar t+1
    Given a series with bars B0, B1, B2, ...
    And a SignalGenerator that returns Buy(quantity=10) at B0 and Hold thereafter
    When I run the backtest
    Then a position is opened at time B1.time with entryPrice = B1.open
    And the position quantity is 10

  Scenario: ClosePosition signal at bar t closes at open of bar t+1
    Given a position is open at bar B5
    And a SignalGenerator that returns ClosePosition at B5
    When I run the backtest
    Then the position is closed at time B6.time with exitPrice = B6.open
    And a Trade record is appended to results

  Scenario: Buy at last bar is unfilled
    Given a series of N bars
    And a SignalGenerator that returns Buy at the last bar
    When I run the backtest
    Then no position is opened
    And diagnostics.unfilledSignalsAtEndOfSeries = 1

  Scenario: Buy when a position is already open is ignored
    Given a position is open
    And the SignalGenerator returns Buy
    When the backtester processes the bar
    Then the existing position is unchanged
    And diagnostics.ignoredBuySignals is incremented

  Scenario: ClosePosition when no position is open is no-op
    Given no position is open
    And the SignalGenerator returns ClosePosition
    When the backtester processes the bar
    Then no position state change
    And diagnostics.noOpClosePositionSignals is incremented

  Scenario: Sell when a position is already open is ignored
    Given a position is open
    And the SignalGenerator returns Sell
    When the backtester processes the bar
    Then the existing position is unchanged
    And diagnostics.ignoredSellSignals is incremented
```

## 11. Block 3 — End-of-series handling

```gherkin
Feature: Mark-to-market at end of series

  Scenario: Open position at last bar is marked-to-market
    Given a position is open at the last bar Bn
    When the backtest completes
    Then BacktestResult.openPositionAtEnd is present
    And the open position is NOT in BacktestResult.trades
    And the last EquityPoint.equity reflects: cash + (quantity × Bn.close - quantity × entryPrice) for LONG (sign-adjusted for SHORT)

  Scenario: No open position at last bar
    Given all positions have been closed before the last bar
    When the backtest completes
    Then BacktestResult.openPositionAtEnd = Optional.empty
    And the last EquityPoint.equity equals cash
```

## 12. Block 4 — Metrics

```gherkin
Feature: BacktestMetrics computation

  Scenario: totalReturn on a flat backtest
    Given a backtest where no trades are taken (all signals are Hold)
    Then metrics.totalReturn = 0
    And metrics.numTrades = 0
    And metrics.winRate = 0
    And metrics.maxDrawdown = 0

  Scenario: winRate on a mixed-outcome backtest
    Given 10 trades: 6 winning, 4 losing
    Then metrics.winRate = 0.6

  Scenario: maxDrawdown as non-negative fraction
    Given an equity curve that peaks at 12000 and troughs at 9000 before recovering
    Then metrics.maxDrawdown = (12000 - 9000) / 12000 = 0.25

  Scenario: maxDrawdown is zero for a monotonically rising equity curve
    Given an equity curve that never declines
    Then metrics.maxDrawdown = 0

  Scenario: maxDrawdown for a monotonically falling equity curve
    Given an equity curve that declines from its first point to its last
    Then metrics.maxDrawdown = (firstEquity - lastEquity) / firstEquity

  Scenario: profitFactor is zero when there are no losing trades
    Given a trade list whose every trade has pnl > 0
    Then metrics.profitFactor = 0

  Scenario: profitFactor is zero when there are no winning trades
    Given a trade list whose every trade has pnl < 0
    Then metrics.profitFactor = 0

  Scenario: totalReturn reflects an open position marked-to-market
    Given a backtest that opens a position and never closes it
    Then metrics.totalReturn = (finalEquity - initialCash) / initialCash
    And finalEquity includes the open position marked at the last bar close

  Scenario: Sharpe ratio annualized for daily timeframe
    Given a series with timeframe "1d"
    And bar-to-bar equity returns with mean = 0.001 and stddev = 0.01
    Then metrics.sharpeRatio = (0.001 / 0.01) × sqrt(252) ≈ 1.587

  Scenario: Sharpe ratio is zero when stddev is zero
    Given an equity curve that is constant (no variation)
    Then metrics.sharpeRatio = 0

  Scenario: Calmar ratio is zero when maxDrawdown is zero
    Given a backtest with maxDrawdown = 0 (no drawdown ever)
    Then metrics.calmarRatio = 0

  Scenario: Sortino uses only downside stddev
    Given returns with both positive and negative values
    Then metrics.sortinoRatio numerator is mean of all returns
    And the denominator is the stddev of only the negative returns
```

## 13. Block 5 — Backtester contract

```gherkin
Feature: Backtester contract

  Scenario: Run returns a result on a valid spec
    Given a valid BacktestSpec
    When I call backtester.run(spec)
    Then the result is non-null
    And result.metrics is non-null
    And result.equityCurve.size() = series.size()
    And the first EquityPoint.equity = spec.initialCash

  Scenario: Null spec is a programmer error
    When I call backtester.run(null)
    Then NullPointerException is thrown

  Scenario: A null Signal from the strategy is a generation failure
    Given a SignalGenerator that returns null at bar B5
    When I call backtester.run(spec)
    Then SignalGenerationException is thrown
    And exception.barIndex = 5

  Scenario: SignalGenerator exception is wrapped
    Given a SignalGenerator that throws RuntimeException at bar B5
    When I call backtester.run(spec)
    Then SignalGenerationException is thrown
    And exception.barIndex = 5
    And exception.getCause() = the original RuntimeException

  Scenario: Determinism with stateless SignalGenerator
    Given a stateless SignalGenerator and a valid spec
    When I call backtester.run(spec) twice on a fresh backtester instance
    Then both BacktestResults are equal by value

  Scenario: Lookahead-safety contract
    Given a SignalGenerator implementation
    Then at any call generate(context), the implementation may only access context.history (bars strictly before currentBar) and context.currentBar
    And the backtester does NOT police this — it is a strategy responsibility
    And violation leads to invalid backtest results
```

## 14. Out of scope for `frau-holle`

- Slippage models — reserved for v1.1+
- Commission models — reserved for v1.1+
- Multi-instrument portfolios — reserved for v2
- Optimization / parameter sweeps — reserved for v2
- Pivot detection — out of repo v1
- Multi-timeframe orchestration — consumer-side per repo principle; strategy may hold extra series internally
- Live trading — out of scope entirely; this is a backtester
- Persistence of results — consumer concern
- Plotting of equity curve / trades — consumer composes with `heerwisch` if desired
- Risk management primitives (stop-loss orders, take-profit orders, position sizing helpers) — consumer concern; strategy is opaque
- Margin / leverage simulation — reserved for v2
- Tax modeling — reserved indefinitely

## 15. v1.1 additive extensions

> **Status: IMPLEMENTED in frau-holle 1.1.0.** This section was originally written as forward-looking design documentation while v1.1 was only planned; the `ClosePositionAtPrice` signal variant, the `forcedClosesAtExplicitPrice` diagnostics counter and the `InvalidExplicitFillException` are now part of the shipped implementation. The content below is retained as the authoritative design record; §15.5 adds the behavioral scenarios (Block 6).

The following extensions were PLANNED for frau-holle v1.1 to support the Wichtelm-app consumer (an end-user backtesting application that interprets a Gherkin-alike DSL). They are documented here so the spec records the agreed direction. None of them was part of v1.

All v1.1 extensions are strictly **additive** and **non-breaking**: consumers using only v1 Signal variants continue to work unchanged.

### 15.1 New Signal variant: ClosePositionAtPrice

| Aspect | v1.1 specification |
|---|---|
| New variant | `ClosePositionAtPrice(BigDecimal price, Instant fillTime)` added to the sealed `Signal` hierarchy in §2.4 |
| Purpose | enables consumers (Wichtelm-app primarily) to express intrabar fills — where a stop-loss or take-profit is hit between two bar closes and the fill price equals the stop level, not the next bar open |
| Fill price | the `price` parameter of the signal (NOT the next bar open). The backtester does NOT range-check `price` against the next bar's high/low: a stop-loss or take-profit level may legitimately sit outside the bar that follows the signal, so any `price > 0` is accepted |
| Fill time | the `fillTime` parameter of the signal — an intrabar instant strictly inside the gap between the signal bar and the bar immediately after it; NOT a bar boundary |
| Validation | `price` MUST be > 0; `fillTime` MUST satisfy `signalBar.time < fillTime < nextBar.time` — strictly after the bar at which the signal was emitted and strictly before the bar immediately following it (`signalBar` and `nextBar` respectively). Both retroactive fills (`fillTime ≤ signalBar.time`) and at-or-beyond-next-bar fills (`fillTime ≥ nextBar.time`) are lookahead-safety violations. `fillTime = nextBar.time` is rejected specifically because it would collapse into the v1 fill-at-next-bar behavior that `ClosePositionAtPrice` exists to differentiate from. Violations throw `InvalidExplicitFillException`. |
| Behavior for other variants | unchanged. `Buy`, `Sell`, `ClosePosition` continue to fill at next bar open as specified in §4 |
| BacktestResult diagnostics | a new counter `forcedClosesAtExplicitPrice` is added to BacktestDiagnostics |

### 15.2 Why v1.1 and not v1

The v1 specification of frau-holle was finalized before the Wichtelm-app DSL design surfaced the need for intrabar fill prices. Rather than retrofit v1, the extension is captured as v1.1 to keep v1 stable, focused, and shippable. Wichtelm-app is built against v1.1.

### 15.3 What this means for Claude Code

| Phase | Action |
|---|---|
| v1 implementation (current) | implement frau-holle v1 exactly as specified in §1-§14. Do NOT add ClosePositionAtPrice. Do NOT add forcedClosesAtExplicitPrice. Do NOT modify the Signal sealed hierarchy beyond v1. Treat this §15 as forward-looking documentation only |
| v1.1 implementation (future) | when this is opened, the spec change above is the authoritative source. Implementation will be a separate session with explicit v1.1 scope |

### 15.4 Consumer-side workaround availability

A consumer that needs intrabar fill behavior BEFORE v1.1 is available can run two parallel "books of truth" (one from frau-holle BacktestResult, one consumer-internal), but this is anti-architectural and is explicitly NOT the chosen path. The chosen path is v1.1 extension when the consumer arrives.

### 15.5 Block 6 — explicit-price fill behavior

A valid `fillTime` is an intrabar instant: `signalBar.time < fillTime < nextBar.time` (strict on both sides). The success scenarios below use such an instant; the four rejection scenarios cover the lower bound, the upper bound and both out-of-range cases.

```gherkin
Feature: v1.1 explicit-price (intrabar) fills

  Scenario: ClosePositionAtPrice fills at the signal-provided price, not next bar open
    Given a backtest with a long position open
    And the SignalGenerator returns ClosePositionAtPrice(price=150) at bar B5
      with an intrabar fillTime strictly between B5.time and B6.time
    When I run the backtest
    Then the position is closed at exitPrice = 150 (the signal price, not B6.open)
    And a Trade record is appended to results

  Scenario: ClosePositionAtPrice fills at the signal-provided fillTime, not next bar time
    Given a backtest with a long position open
    And the SignalGenerator returns ClosePositionAtPrice at bar B4
      with an intrabar fillTime strictly between B4.time and B5.time
    When I run the backtest
    Then the closing Trade has exitTime equal to that intrabar instant
      (neither B4.time nor B5.time)

  Scenario: ClosePositionAtPrice on no open position is a no-op
    Given no position is open
    And the SignalGenerator returns ClosePositionAtPrice with a valid intrabar fillTime
    When the backtester processes the bar
    Then no position state change occurs
    And no Trade is appended
    And diagnostics.noOpClosePositionSignals is incremented
    And diagnostics.forcedClosesAtExplicitPrice is NOT incremented

  Scenario: ClosePositionAtPrice with fillTime equal to the signal bar time is rejected
    Given a ClosePositionAtPrice signal emitted at bar Bt
    And its fillTime equals Bt.time (a retroactive fill — no look-ahead margin)
    When the backtester processes the fill
    Then InvalidExplicitFillException is thrown
    And the exception carries the offending fillTime and the next-bar time (barTime)

  Scenario: ClosePositionAtPrice with fillTime before the signal bar time is rejected
    Given a ClosePositionAtPrice signal emitted at bar Bt
    And its fillTime is earlier than Bt.time
    When the backtester processes the fill
    Then InvalidExplicitFillException is thrown
    And the exception carries the offending fillTime and the next-bar time (barTime)

  Scenario: ClosePositionAtPrice with fillTime equal to the next bar time is rejected
    Given a ClosePositionAtPrice signal emitted at bar Bt
    And its fillTime equals the time of bar Bt+1
    When the backtester processes the fill
    Then InvalidExplicitFillException is thrown
      (fillTime = nextBar.time would collapse into v1 fill-at-next-bar behavior)

  Scenario: ClosePositionAtPrice with fillTime beyond the next bar is rejected
    Given a ClosePositionAtPrice signal emitted at bar Bt
    And its fillTime is later than the time of bar Bt+1
    When the backtester processes the fill
    Then InvalidExplicitFillException is thrown
    And the exception carries the offending fillTime and the next-bar time (barTime)

  Scenario: diagnostics.forcedClosesAtExplicitPrice counts explicit-price closes
    Given a backtest where one ClosePositionAtPrice signal successfully closes a position
    When the backtest completes
    Then diagnostics.forcedClosesAtExplicitPrice = 1

  Scenario: Mixing ClosePositionAtPrice with regular signals in the same backtest
    Given a backtest that issues Buy, then ClosePositionAtPrice, then Buy, then ClosePosition
    When I run the backtest
    Then both round trips produce Trade records
    And diagnostics.forcedClosesAtExplicitPrice = 1

  Scenario: A v1-only backtest is unaffected by the v1.1 extension
    Given a backtest using only v1 Signal variants (Hold/Buy/Sell/ClosePosition)
    When I run the backtest
    Then the results are identical to v1 behavior
    And diagnostics.forcedClosesAtExplicitPrice = 0

  Scenario: ClosePositionAtPrice fills at a price outside the next bar's range
    Given a backtest with a long position open
    And the SignalGenerator returns ClosePositionAtPrice with a price above the
      next bar's high and a valid intrabar fillTime
    When I run the backtest
    Then the position is closed at exitPrice = the signal price
    And no range check is applied (a stop level may sit outside the bar)
```

## 16. v1.2 additive extensions

> **Status: IMPLEMENTED in frau-holle 1.2.0.** This section documents the v1.2 extension: the `AddToPosition` signal variant (position accumulation / pyramiding), its two diagnostics counters and the `InvalidAddToPositionDirectionException`. It was added for the Wichtelm-app consumer, which needs to model strategies that scale into a winning position.

All v1.2 extensions are strictly **additive** and **non-breaking**: consumers using only v1 and v1.1 `Signal` variants (`Hold`/`Buy`/`Sell`/`ClosePosition`/`ClosePositionAtPrice`) continue to work bit-identically. The bar-loop order, fill timing of existing variants, metrics and `BacktestResult` shape are unchanged apart from the two additive diagnostics counters.

### 16.1 New Signal variant: AddToPosition

| Aspect | v1.2 specification |
|---|---|
| New variant | `AddToPosition(BigDecimal quantity, Direction direction)` added to the sealed `Signal` hierarchy |
| Purpose | accumulate quantity into an already-open position (pyramiding) — express strategies that scale into a position rather than opening it in one shot |
| Fields | `quantity` MUST be non-null and `> 0`; `direction` MUST be non-null. Both validated in the canonical constructor |
| Direction | `AddToPosition` carries an explicit `Direction` (the `org.hatrack.frauholle.model.Direction` enum, `LONG`/`SHORT`, the same enum carried by `Position` and `Trade`). This makes the signal self-documenting and the opposite-direction error case reachable, mirroring the way `Buy`/`Sell` implicitly encode direction |
| Fill timing | the next bar open, exactly as `Buy`/`Sell` (§4). A signal at bar `t` fills at the open of bar `t+1` |
| Behavior — matching direction | when a position is open whose direction equals the signal's `direction`: the `quantity` is added to the position; the position's `entryPrice` becomes the **quantity-weighted average** of the prior position and the add — `(oldQuantity × oldEntryPrice + addQuantity × fillPrice) / (oldQuantity + addQuantity)`; the position's `entryTime` stays the **original** first-open time (the weighted-average applies to price only, not time); the direction is unchanged |
| Behavior — no open position | no-op; the `addToPositionOnNoPositionCount` diagnostics counter is incremented |
| Behavior — opposite direction | when a position is open whose direction is the **opposite** of the signal's `direction`, the backtester throws `InvalidAddToPositionDirectionException`. This is intentionally strict in v1.2 — reversing a position via `AddToPosition` has ambiguous semantics; v1.3 may relax it if a use case emerges |
| Behavior for other variants | unchanged. `Hold`, `Buy`, `Sell`, `ClosePosition`, `ClosePositionAtPrice` behave exactly as in v1/v1.1 |

### 16.2 New diagnostics counters

Two counters are added to `BacktestDiagnostics` (§2.10):

| Accessor | Type | Meaning |
|---|---|---|
| `addToPositionCount()` | `int` | count of `AddToPosition` signals that were successfully filled (an open position grew) |
| `addToPositionOnNoPositionCount()` | `int` | count of `AddToPosition` signals emitted while no position was open (no-op case) |

Both default to 0. A backtest using only v1/v1.1 signal variants leaves both at 0, so a pre-v1.2 result is reproduced unchanged.

### 16.3 New exception: InvalidAddToPositionDirectionException

`InvalidAddToPositionDirectionException` is added to the sealed `BacktestException` hierarchy (§8). It is thrown from `Backtester.run()` when an `AddToPosition` signal is filled against an open position of the opposite direction.

| Carrier field | Type | Meaning |
|---|---|---|
| `barIndex()` | `int` | index of the fill bar at which the mismatch was detected |
| `barTime()` | `Instant` | time of the fill bar at which the mismatch was detected |
| `openPositionDirection()` | `Direction` | direction of the position that is currently open |
| `signalDirection()` | `Direction` | direction carried by the offending `AddToPosition` signal |

The exception has no cause (the mismatch is a strategy-logic error, not a wrapped failure).

### 16.4 Behavioral scenarios

The v1.2 behavior is covered by the Gherkin scenarios in §16.5 (Block 7).

### 16.5 Block 7 — AddToPosition (pyramiding) behavior

```gherkin
Feature: v1.2 AddToPosition (pyramiding)

  Scenario: AddToPosition on an open long position accumulates at the weighted-average entry price
    Given a long position of quantity 10 opened at entryPrice 101
    And the SignalGenerator returns AddToPosition(quantity=5, direction=LONG)
      while that position is open
    When the add fills at the next bar open of 104
    Then the position quantity becomes 15
    And the position entryPrice becomes (10×101 + 5×104) / 15 = 102
    And the position entryTime is unchanged

  Scenario: AddToPosition on an open short position accumulates at the weighted-average entry price
    Given a short position of quantity 10 opened at entryPrice 101
    And the SignalGenerator returns AddToPosition(quantity=5, direction=SHORT)
      while that position is open
    When the add fills at the next bar open of 104
    Then the position quantity becomes 15
    And the position entryPrice becomes 102 (symmetric to the long case)

  Scenario: AddToPosition fills at the next bar open, like Buy/Sell
    Given a long position is open
    And the SignalGenerator returns AddToPosition at bar t
    When the backtest runs
    Then the add fills at the open of bar t+1, not at the signal bar

  Scenario: Position entryTime after AddToPosition remains the original entry time
    Given a position opened at bar B3
    And an AddToPosition that fills several bars later
    When the backtest runs
    Then the position entryTime still equals B3.time
      (the weighted-average applies to price only, not to time)

  Scenario: AddToPosition with no open position is a no-op
    Given no position is open
    And the SignalGenerator returns AddToPosition
    When the backtester processes the fill
    Then no position is opened and no Trade is appended
    And diagnostics.addToPositionOnNoPositionCount is incremented
    And diagnostics.addToPositionCount is NOT incremented

  Scenario: AddToPosition on an opposite-direction position throws InvalidAddToPositionDirectionException
    Given a SHORT position is open
    And the SignalGenerator returns AddToPosition(direction=LONG)
    When the backtester processes the fill
    Then InvalidAddToPositionDirectionException is thrown
    And the exception carries the fill bar index and time,
      openPositionDirection = SHORT and signalDirection = LONG

  Scenario: diagnostics.addToPositionCount increments on a successful fill
    Given a backtest where one AddToPosition signal successfully grows a position
    When the backtest completes
    Then diagnostics.addToPositionCount = 1

  Scenario: Mixing AddToPosition with Buy, ClosePosition and ClosePositionAtPrice in one backtest
    Given a backtest that issues Buy, AddToPosition, ClosePosition, Buy, then ClosePositionAtPrice
    When I run the backtest
    Then both round trips produce Trade records
    And diagnostics.addToPositionCount = 1
    And diagnostics.forcedClosesAtExplicitPrice = 1

  Scenario: Multiple consecutive AddToPosition signals compound the weighted-average entry price
    Given a long position and two successive AddToPosition signals
    When the backtest runs
    Then the entry price is the weighted average compounded across both additions
    And diagnostics.addToPositionCount = 2

  Scenario: A v1/v1.1-only backtest is unaffected by the v1.2 extension
    Given a backtest using only v1/v1.1 Signal variants (no AddToPosition)
    When I run the backtest
    Then the results are identical to pre-v1.2 behavior
    And diagnostics.addToPositionCount = 0
    And diagnostics.addToPositionOnNoPositionCount = 0
```

## 17. Implementation delegation to Claude Code

Claude Code is responsible for:

- Package layout (suggested: `<group>.frauholle` with subpackages `port` for `MarketDataSource` and `SignalGenerator`, `spec` for `BacktestSpec` and its builder, `model` for `Signal`/`Position`/`Trade`/`EquityPoint`/`BarContext`, `result` for `BacktestResult`/`BacktestMetrics`/`BacktestDiagnostics`, `engine` for the `Backtester` class, `error` for exceptions, `internal` for metrics calculators and the simulation loop)
- Implementing the `Backtester` simulation loop per §4, §11, §10 (fill timing, end-of-series handling, signal application)
- Implementing the `BacktestMetrics` calculators per §3 using `MathContext.DECIMAL64`
- Implementing `Timeframe` inference from bar spacing per §3.1 and V5 validation
- Implementing canonical constructors with `Objects.requireNonNull` and range checks
- Test infrastructure for the Gherkin scenarios above (Cucumber for Java, executed via JUnit Platform; feature files under src/test/resources/features/, step definitions under src/test/java/)

What Claude Code MUST NOT do unilaterally:

- Add slippage / commission models in v1
- Add multi-instrument support
- Add a non-opaque strategy abstraction (no rule-based DSL, no function-composition layer in the core)
- Auto-close positions at end of series (must be mark-to-market only)
- Fill at the close of the signal bar (must be next bar open)
- Use `double` or `float` in any monetary or ratio arithmetic
- Add reflective bean wiring or DI annotations
- Add static mutable state
- Expose `internal` calculators as public API
- Catch and swallow `SignalGenerationException` — must propagate

## 18. v1.3 additive extensions

> **Status: IMPLEMENTED in 0.55.0-alpha.** Adds the exact `winningTrades()` /
> `losingTrades()` counts to `BacktestMetrics` for the Wichtelm-app consumer, which
> previously reconstructed the win count as `round(winRate × numTrades)` —
> off-by-one-prone and able to disagree with the trade list.

All v1.3 extensions are strictly **additive** and **non-breaking** (japicmp-clean):
the pre-0.55 10-argument `BacktestMetrics` constructor is preserved as an overload;
the new field and accessors are pure additions. A consumer reading only the prior
ten metrics is unaffected.

### 18.1 New BacktestMetrics accessors

| Accessor | Type | Definition |
|---|---|---|
| `winningTrades()` | `int` | number of trades with `pnl > 0` — identical to the `winRate` numerator |
| `losingTrades()` | `int` | `numTrades() − winningTrades()` |

Invariant: `winningTrades() + losingTrades() == numTrades()` exactly. Break-even
trades (`pnl == 0`) are bucketed into `losingTrades()` — consistent with `winRate`,
whose numerator counts only `pnl > 0`. `winningTrades` is the sole new stored field
(the 11th record component); `losingTrades()` is derived from it and `numTrades`, so
the invariant cannot be violated by construction.

The canonical constructor validates `0 ≤ winningTrades ≤ numTrades`. The retained
10-argument constructor approximates `winningTrades` as `round(winRate × numTrades)`
(the only signal available without the trade list); it exists solely for
binary/source compatibility — the engine populates the exact count via the canonical
constructor.

### 18.2 Block 8 — winning/losing trade counts

```gherkin
Feature: BacktestMetrics winning/losing trade counts

  Scenario: Mixed outcomes split into exact winning and losing counts
    Given 10 trades: 6 with pnl > 0 and 4 with pnl < 0
    When I compute the metrics
    Then metrics.winningTrades = 6
    And metrics.losingTrades = 4
    And metrics.winningTrades + metrics.losingTrades = metrics.numTrades

  Scenario: Break-even trades count as losses
    Given trades with pnls 100, 100, 0, -50, 0
    When I compute the metrics
    Then metrics.winningTrades = 2
    And metrics.losingTrades = 3
    And metrics.winRate = 0.4

  Scenario: No trades yields zero winning and losing
    Given a backtest where no trades are taken
    Then metrics.winningTrades = 0
    And metrics.losingTrades = 0
```
