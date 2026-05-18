# ha-track — concepts & functional overview

This document explains **what ha-track is for** and the ideas behind it, before
any code. It is the place to start if you are new to the project. For the
authoritative, rule-by-rule behavioural specification of each module see the
`CLAUDE.md` files; for a per-module functional guide see [`modules.md`](modules.md);
to build and run see [`getting-started.md`](getting-started.md).

---

## 1. The problem ha-track solves

ha-track is a toolkit for **technical analysis of financial price data**. It
gives a Java application three capabilities that traders and analysts
repeatedly need, and a shared vocabulary to connect them:

| Need | "I want to…" | ha-track answer |
|---|---|---|
| **See** the market | …look at a price chart with indicators drawn on it | the *heerwisch* plotting library |
| **Recognise** the market | …be told when a known pattern appears (a reversal, a moving-average cross, an overbought reading…) | the *nachtkrapp* pattern-detection library |
| **Test** an idea | …replay a trading strategy over historical data and measure how it would have performed | the *frau-holle* backtesting library |

The three libraries are **independent** — none imports another. An application
picks the ones it needs and composes them. They share one small kernel of data
types (*commons*) so a bar of price data means the same thing everywhere.

The whole stack is **rooted in Heikin Ashi candle analysis** (see §3): the data
model, the pattern catalogue and the charting all understand both ordinary
candles and Heikin Ashi candles.

### Who uses it

ha-track is a set of **libraries**, not an application. The end-user product is
a separate consumer application (*Wichtelm-app*, planned) that wires these
libraries together behind a user interface and a strategy DSL. Because the
libraries are framework-agnostic (no Spring/Micronaut/Guice, no DI container),
that consumer is free to wire them however it likes.

---

## 2. The naming theme

Every module is named from **continental Germanic folklore**, and the choice is
deliberate: each name pairs a *helper* with a *trickster*, because each library
both helps the user and can mislead them if misused.

| Module | Folklore | Why |
|---|---|---|
| **heerwisch** | the will-o'-the-wisp (Low German) | the wandering bog light that *reveals or conceals* the path — a chart shows the market but can also mislead |
| **frau-holle** | the household goddess of spinning | *rewards diligent work, punishes negligence* — a backtest rewards a sound strategy and exposes a careless one |
| **nachtkrapp** | the nocturnal raven | *sees in the dark, brings tidings* — true or punishing — a detector surfaces patterns, accurate or not |

The naming is cosmetic, but it signals an attitude: these tools are honest only
if used honestly (see *lookahead-safety*, §7).

---

## 3. Core domain concepts

### 3.1 OHLC bars and series

The atom of market data is the **bar** — one period's price action:

- `time` — when the period starts (always a UTC `Instant`)
- `open`, `high`, `low`, `close` — the four prices
- `volume` — optionally, how much traded

A bar must satisfy the **OHLC invariants**: every price is strictly positive,
`high ≥ low`, `high ≥ open` and `high ≥ close`, `low ≤ open` and `low ≤ close`,
and `volume ≥ 0` when present. A bar that breaks these is malformed data.

A **series** is an ordered run of bars for one instrument at one timeframe.

### 3.2 Timeframe

A **timeframe** is the period each bar covers — `1m`, `5m`, `1h`, `1d`, `1w`,
`1M` (month), `1y`. It is an *open* type (any `<n><unit>` combination), written
in a compact wire form, not a fixed enum.

### 3.3 Heikin Ashi candles

**Heikin Ashi** ("average bar" in Japanese) is an alternative candle that
*smooths* the raw OHLC series to make trends easier to read. Each Heikin Ashi
bar is computed from the raw bar and the previous Heikin Ashi bar:

```
haClose = (open + high + low + close) / 4
haOpen  = (previous haOpen + previous haClose) / 2     (running)
haOpen  = (open + close) / 2                           (first bar — the "seed")
haHigh  = max(high, haOpen, haClose)
haLow   = min(low,  haOpen, haClose)
```

The effect: noisy single-bar reversals are dampened, sustained trends show as
long runs of same-coloured candles. ha-track treats Heikin Ashi as a
first-class series type — patterns and charts work on it directly.

### 3.4 Indicators

An **indicator** is a derived series computed from prices. ha-track knows the
common ones:

| Indicator | What it tells you |
|---|---|
| SMA / EMA | the average price over a window — the trend |
| Bollinger Bands | a volatility envelope around a moving average |
| RSI | momentum, 0–100; high = overbought, low = oversold |
| MACD | the relationship between a fast and a slow EMA — momentum shifts |
| ADX | how strong the trend is (regardless of direction) |
| Stochastic | where the close sits within the recent high–low range |
| ATR | average true range — raw volatility |
| Volume | how much traded per bar |

All indicator maths uses `BigDecimal` for exactness (see §7).

### 3.5 Patterns

A **pattern** is a named, recognisable situation in the data. ha-track v1
detects two families:

- **Heikin Ashi patterns** — a bullish/bearish *reversal* (a colour change after
  a streak), a *strong candle* (a clean trending bar with little wick), a *doji*
  (a near-bodyless indecision bar).
- **Indicator primitives** — price crossing a moving average, RSI entering or
  leaving overbought/oversold, RSI crossing 50, MACD crossing its signal line
  or zero.

Each match is tagged **EVENT** (a discrete transition that happened on one bar —
"a cross occurred") or **STATE** (a continuous condition that holds — "price is
above the MA"). A consumer combines primitive matches with ordinary Java
boolean logic to build higher-level alerts (e.g. *bullish reversal AND price
above the 50-MA AND not overbought*).

---

## 4. The charting model (heerwisch)

You describe the chart you want as an immutable **`ChartSpec`** — a series, the
indicators to draw and which *pane* each goes in (the main price pane or one of
eight subplots), any annotations (highlights, horizontal levels, Fibonacci
levels, pivot points), and a layout (size and image format). A **driver** then
renders the spec to a **`ChartImage`** — raw bytes plus a content type.

The split is deliberate: `heerwisch-api` defines *what* a chart is; a driver
decides *how* to draw it. The default driver, `heerwisch-jfreechart`, uses
JFreeChart, runs head­less (works in a server or AWS Lambda) and embeds its own
font so the same spec always produces byte-identical output.

The renderer never writes a file — it returns bytes; the caller decides where
they go (a file, an HTTP response, an email attachment).

---

## 5. The backtesting model (frau-holle)

A **backtest** replays a strategy over a historical series and reports how it
would have performed.

- The **strategy** is a `SignalGenerator`: bar by bar it receives a `BarContext`
  (the current bar, all prior bars, the open position, cash, equity) and returns
  a **`Signal`** — `Hold`, `Buy`, `Sell`, `ClosePosition`, or (v1.1)
  `ClosePositionAtPrice`.
- **Fill timing is realistic**: a signal decided on bar *t* fills at the *open of
  bar t+1*. You cannot trade at a close that has already happened. A signal at
  the very last bar simply cannot fill.
- v1 is **frictionless** — no commission, no slippage, infinite liquidity. The
  metrics therefore overstate live performance; this is documented, not hidden.
- An open position at the end of the series is **marked-to-market**, never
  force-closed.

The result is a **`BacktestResult`**: ten core **metrics** (total return, win
rate, number of trades, max drawdown, Sharpe, Sortino, profit factor, average
win, average loss, Calmar), the list of completed **trades**, the **equity
curve** (one point per bar), any still-open position, and **diagnostics**
(counts of ignored or unfilled signals).

The `MarketDataSource` port supplies the bars. Two reference drivers exist:
`frau-holle-csv` (local CSV files) and `frau-holle-eodhd` (the EODHD
End-of-Day HTTP API). A consumer can write its own.

> **Reading a metric:** `profitFactor` returns `BigDecimal.ZERO` when there are
> *no losing trades*. Zero here is a sentinel meaning **"undefined"** (an
> infinite profit factor cannot be a `BigDecimal`) — not "a profit factor of
> zero". Interpret it accordingly.

### v1.1 — intrabar fills

v1.1 adds the `ClosePositionAtPrice` signal: a strategy can close a position at
an explicit price and an explicit *intrabar* time (e.g. a stop-loss hit between
two bar closes), instead of waiting for the next bar's open. To stay honest the
fill time must fall **strictly between** the signal bar and the next bar — a
fill in the past, or as far ahead as the next bar, is rejected.

---

## 6. How the modules fit together

```
                ┌─────────────┐
                │   commons   │  OHLCBar, HABar, Series, Timeframe,
                │ (shared     │  PriceSource, HeikinAshiCalculator
                │  kernel)    │
                └──────┬──────┘
        ┌──────────────┼───────────────┬───────────────┐
        │              │               │               │
 ┌──────▼──────┐ ┌─────▼──────┐ ┌──────▼──────┐  ┌──────▼──────┐
 │ heerwisch-  │ │ nachtkrapp │ │ frau-holle  │  │  (consumer  │
 │    api      │ │ (patterns) │ │ (backtester)│  │   composes  │
 └──────┬──────┘ └────────────┘ └──────┬──────┘  │   them)     │
        │                              │         └─────────────┘
 ┌──────▼──────────┐         ┌─────────┴─────────┐
 │ heerwisch-      │         │ frau-holle-csv    │
 │ jfreechart      │         │ frau-holle-eodhd  │
 │ (chart driver)  │         │ (data drivers)    │
 └─────────────────┘         └───────────────────┘
```

Every module depends only on `commons` (and a driver depends on its API). The
three libraries never depend on each other — a consumer that needs more than
one composes the results itself. This keeps each library small, independently
versioned and independently testable.

---

## 7. Cross-cutting principles

These rules hold everywhere and explain a lot of the design:

- **Lookahead-safety.** No value, signal or pattern match at bar time *t* may
  depend on any bar after *t*. A backtest or detection that "sees the future"
  is worthless; the libraries are built so that cannot happen by accident.
- **Exact arithmetic.** Every price, ratio, quantity and P&L is a `BigDecimal`
  with `MathContext.DECIMAL64`. No `double`, no `float` — floating-point drift
  has no place in money maths.
- **UTC time everywhere.** Every timestamp is an `Instant`; there is no
  timezone ambiguity to leak.
- **Immutability and eager validation.** Specs are immutable records built by
  fluent builders; a builder's `build()` validates everything up front and
  fails at the call site with a typed exception, never deep inside a render or
  a backtest.
- **Spec-driven.** Each module's `CLAUDE.md` is the authoritative behavioural
  spec, written partly as Gherkin scenarios; the code and its Cucumber tests
  follow the spec, not the other way round.

---

## 8. Where to go next

| You want to… | Read |
|---|---|
| understand one module in depth, with code examples | [`modules.md`](modules.md) |
| build the project and run an end-to-end example | [`getting-started.md`](getting-started.md) |
| the exact, rule-by-rule behavioural contract | the module's `CLAUDE.md` |
| the repo-wide architecture decisions | the root [`CLAUDE.md`](../CLAUDE.md) |
