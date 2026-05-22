<img src="../frau-holle-logo.png" alt="frau-holle" height="90">

# frau-holle

The **backtesting library**. Replay a trading strategy over historical price
data and measure how it would have performed.

> Human-friendly guide. Authoritative contract: [`CLAUDE.md`](CLAUDE.md).
> Background: [`../docs/concepts.md`](../docs/concepts.md).

## What it is for

You have a trading idea and a history of bars. `frau-holle` runs the idea
bar-by-bar over that history — opening and closing positions as your strategy
signals — and hands back the trades, the equity curve and a set of performance
metrics.

It is **event-driven** (one bar at a time, single-threaded) and **frictionless**
in v1 (no commission, no slippage). It exposes two *ports* so the data and the
strategy are pluggable:

- `MarketDataSource` — where bars come from (see `frau-holle-csv`, `frau-holle-eodhd`);
- `SignalGenerator` — your strategy.

## Adding the dependency

```xml
<dependency>
    <groupId>net.jacopobiscella</groupId>
    <artifactId>frau-holle</artifactId>
    <version>0.51.0-alpha</version>
</dependency>
```

> All ha-track modules share a single version — currently `0.51.0-alpha`.

Packages: `port`, `model`, `spec`, `result`, `engine`, `error` under
`org.hatrack.frauholle`.

## Writing a strategy — `SignalGenerator`

`SignalGenerator` is a functional interface: each bar, it receives a
`BarContext` and returns a `Signal`.

```java
import org.hatrack.frauholle.port.SignalGenerator;
import org.hatrack.frauholle.model.Signal;

SignalGenerator strategy = context -> {
    if (context.currentPosition().isEmpty() && context.barIndex() > 20) {
        return new Signal.Buy(new BigDecimal("10"));   // open a 10-unit long
    }
    return new Signal.Hold();                          // do nothing
};
```

`BarContext` is what the strategy sees: `currentBar()`, `history()` (all prior
bars), `currentPosition()`, `currentCash()`, `currentEquity()`, `barIndex()`.
The strategy is **opaque** — frau-holle does not care how it decides; it may be
hand-coded, an ML model, or compiled from a DSL. It may hold internal state.

### Signals — `Signal`

| Variant | Effect |
|---|---|
| `Hold` | no action this bar |
| `Buy(quantity)` | open a long; ignored if a position is already open |
| `Sell(quantity)` | open a short; ignored if a position is already open |
| `ClosePosition` | close the open position at the next bar's open |
| `ClosePositionAtPrice(price, fillTime)` | **v1.1** — close at an explicit intrabar price/time |

A `Buy`/`Sell`/`ClosePosition` decided on bar *t* **fills at the open of bar
t+1** — you cannot trade at a close that already happened. A signal at the very
last bar cannot fill.

## Running a backtest

```java
import org.hatrack.frauholle.spec.BacktestSpec;
import org.hatrack.frauholle.engine.Backtester;
import org.hatrack.frauholle.result.*;

BacktestSpec spec = BacktestSpec.builder()
        .withSeries(bars)                             // List<OHLCBar>, pre-loaded
        .withSignalGenerator(strategy)
        .withInitialCash(new BigDecimal("10000"))
        .build();                                      // throws InvalidBacktestSpecException

BacktestResult result = new Backtester().run(spec);    // throws BacktestException
```

`build()` validates eagerly — rules **V1–V7**: series set, non-empty, ≥ 2 bars,
uniform spacing that maps to a timeframe, a strategy and a positive initial
cash, and OHLC invariants on every bar. The series is *pre-loaded* — the
`Backtester` does not call `MarketDataSource` itself; you fetch the bars and
pass the list, which lets you cache or transform data freely.

## Reading the result — `BacktestResult`

| Accessor | Contents |
|---|---|
| `metrics()` | the ten core metrics (see below) |
| `trades()` | every completed (entered and exited) `Trade` |
| `equityCurve()` | one `EquityPoint` per bar |
| `openPositionAtEnd()` | a position still open at the last bar, if any (marked-to-market, not in `trades()`) |
| `diagnostics()` | counters: ignored / no-op / unfilled signals, `forcedClosesAtExplicitPrice` |

`BacktestMetrics`: `totalReturn`, `winRate`, `numTrades`, `maxDrawdown`,
`sharpeRatio`, `sortinoRatio`, `profitFactor`, `avgWin`, `avgLoss`,
`calmarRatio`.

> **`profitFactor` sentinel:** it returns `BigDecimal.ZERO` when there are *no
> losing trades*. Zero here means **"undefined"** (an infinite profit factor is
> not representable as `BigDecimal`) — not "a profit factor of zero".

An open position at the end of the series is **marked-to-market**, never
force-closed. v1 is frictionless, so the metrics overstate live performance —
that is documented, not hidden.

## Errors

All checked, rooted at `BacktestException`:

| Exception | When |
|---|---|
| `InvalidBacktestSpecException` | malformed spec — from `build()`; carries `violatedRule` |
| `MarketDataException` (+ `MarketDataNotFoundException`, `MarketDataUnavailableException`, `MarketDataSchemaException`) | thrown by `MarketDataSource` implementations |
| `SignalGenerationException` | your strategy threw; carries the `barIndex` |
| `BacktestInternalException` | an internal engine failure |
| `InvalidExplicitFillException` | a `ClosePositionAtPrice` with an out-of-range `fillTime` |

A `null` spec passed to `run()` is a programmer error → `NullPointerException`.

## v1.1 — intrabar fills

`ClosePositionAtPrice(price, fillTime)` closes a position at an explicit price
and time (e.g. a stop-loss hit between two bar closes) instead of at the next
bar's open. To stay lookahead-safe, `fillTime` must be **strictly between** the
signal bar and the next bar — a fill in the past, or at/after the next bar, is
rejected with `InvalidExplicitFillException`. Every other signal is unchanged.

## Out of scope (v1)

Commission/slippage models, multi-instrument portfolios, optimisation sweeps,
live trading. Slippage and commission are reserved for v1.1+ as opt-in ports.
