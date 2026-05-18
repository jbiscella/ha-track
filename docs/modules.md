# ha-track — module guide

A functional, per-module guide: what each module is for, when you would reach
for it, its key public types, and a short worked example. For the conceptual
background read [`concepts.md`](concepts.md) first; for the authoritative
behavioural contract read each module's `CLAUDE.md`.

All examples assume the `org.hatrack.*` packages are imported. Builder
`build()` methods and library entry points throw typed checked exceptions
(noted per module); production code must handle them.

---

## commons — the shared kernel

**Role.** The vocabulary every other module speaks. Pure data types and pure
functions, JDK-only, zero external dependencies, no I/O, no clock, no state.

**When you use it.** Always — directly or transitively. You construct
`OHLCBar`s and `Series`, and you may call `HeikinAshiCalculator`.

**Key types.**

| Type | Purpose |
|---|---|
| `OHLCBar` | one raw price bar; `validateInvariants()` checks the OHLC rules (opt-in) |
| `HABar` | one Heikin Ashi bar |
| `Series` → `OHLCSeries`, `HASeries` | a sealed pair wrapping a defensively-copied bar list |
| `Timeframe` | bar period; `Timeframe.fromWire("1d")` / `tf.wire()` |
| `PriceSource` | which channel an indicator reads — `OPEN/HIGH/LOW/CLOSE` and `HA_*` |
| `HeikinAshiCalculator` | `compute(prev, ohlc)` and `computeChain(prev, ohlcs)` |
| `OHLCInvariantViolationException` | thrown by `validateInvariants()` on a bad bar |

```java
OHLCBar bar = new OHLCBar(
        Instant.parse("2024-01-02T00:00:00Z"),
        new BigDecimal("187.15"), new BigDecimal("188.44"),
        new BigDecimal("183.89"), new BigDecimal("185.64"),
        Optional.of(new BigDecimal("82488682")));

List<HABar> ha = HeikinAshiCalculator.computeChain(Optional.empty(), List.of(bar));
```

**Note.** `OHLCBar`'s constructor only null-checks; it does *not* enforce the
OHLC invariants. That is deliberate — bulk loads stay fast, and validation
happens once, later, at the spec-builder boundary.

---

## heerwisch-api — the plotting port and spec types

**Role.** Defines *what a chart is* — immutable spec types and the
`ChartRenderer` port — with no rendering of its own.

**When you use it.** When you want to describe a chart. Pair it with a driver
(`heerwisch-jfreechart`) to actually produce an image.

**Key types.**

| Type | Purpose |
|---|---|
| `ChartSpec` + `ChartSpecBuilder` | the immutable chart description; builder validates rules V1–V13 |
| `Indicator` | sealed: `SMA, EMA, BollingerBands, MACD, RSI, ADX, Stochastic, ATR, VolumePane` |
| `Annotation` | sealed: `BarHighlight, HorizontalLevel, FibRetracement, PivotPointLevels` |
| `Pane` | `MAIN` plus `SUBPLOT_1..8` |
| `LayoutSpec` + `LayoutSpecBuilder` | `AutoLayoutSpec` / `ExplicitLayoutSpec`; image format (default **JPEG**) |
| `ChartImage` | rendered output — bytes, content type, dimensions |
| `ChartRenderer` | the driver port: `ChartImage render(ChartSpec)` |
| `ChartRenderException` | checked root: `InvalidChartSpecException`, `UnsupportedFeatureException`, `InsufficientDataException`, `DriverInternalException` |

```java
ChartSpec spec = ChartSpec.builder()
        .withSeries(new OHLCSeries(bars))
        .addIndicator(new Indicator.SMA(20, PriceSource.CLOSE))      // defaults to MAIN
        .addIndicator(new Indicator.RSI(14, new BigDecimal("70"),
                new BigDecimal("30"), PriceSource.CLOSE))            // defaults to SUBPLOT_1
        .addAnnotation(new Annotation.HorizontalLevel(
                new BigDecimal("185"), "support", LevelStyle.DASHED))
        .build();   // throws InvalidChartSpecException on a malformed spec
```

The builder eagerly rejects malformed specs — unset/empty/unordered series,
priceSource/series-type mismatch, too few bars for an indicator, a highlight on
a non-existent bar, explicit layout heights that do not sum to 1, and OHLC
invariant violations in the series.

---

## heerwisch-jfreechart — the default chart driver

**Role.** Implements `ChartRenderer` with JFreeChart 1.5. Consumes a
`ChartSpec`, produces a PNG or JPEG `ChartImage`.

**When you use it.** When you actually need a chart image and the default
look is acceptable.

**Key types.** `JFreeChartRenderer` (the `ChartRenderer` implementation),
`ThemeConstants` (the read-only colour and stroke palette).

```java
ChartRenderer renderer = new JFreeChartRenderer();   // throws DriverInternalException if the font fails to load
ChartImage image = renderer.render(spec);
Files.write(Path.of("chart.jpg"), image.bytes());
```

**Properties worth knowing.**
- *Headless* — no X server needed; runs in a server or AWS Lambda.
- *Deterministic* — an embedded DejaVu Sans font means the same spec renders
  byte-identical output on any machine.
- *Strict V12* — it rejects oscillator indicators (RSI, MACD, …) placed on the
  `MAIN` price pane with an `UnsupportedFeatureException`.

---

## nachtkrapp — pattern detection

**Role.** Detects Heikin Ashi patterns and MA/RSI/MACD primitives in a series.

**When you use it.** When you want to be told "this happened here" — for
alerts, for strategy inputs, for scanning.

**Key types.**

| Type | Purpose |
|---|---|
| `DetectionRule` | sealed, 9 variants — what to look for (`HAColorChangeRule`, `PriceMACrossRule`, `RSIThresholdRule`, …) |
| `PatternMatch` | sealed, 19 variants — what was found, with a diagnostic payload |
| `MatchFlavor` | `EVENT` (a transition) or `STATE` (a holding condition) |
| `DetectionSpec` + `DetectionSpecBuilder` | series + rules; builder validates V1–V10 |
| `PatternDetector` → `RuleBasedPatternDetector` | the entry point: `DetectionResult detect(DetectionSpec)` |
| `DetectionException` | checked root: `InvalidDetectionSpecException`, `InsufficientDataException`, `DetectionInternalException` |

```java
DetectionSpec spec = DetectionSpec.builder()
        .withSeries(new HASeries(haBars))
        .addRule(new DetectionRule.HAColorChangeRule(3))   // 3-bar streak then a colour change
        .withTimeframe(Timeframe.fromWire("1d"))           // optional provenance tag
        .build();                                          // throws InvalidDetectionSpecException

PatternDetector detector = new RuleBasedPatternDetector();
DetectionResult result = detector.detect(spec);            // throws DetectionException
for (PatternMatch m : result.matches()) {
    System.out.println(m.getClass().getSimpleName() + " at " + m.time());
}
```

`PatternDetector` is **thread-safe** — one instance may be shared across
threads. Detection is deterministic and lookahead-safe: a match at time *t*
depends only on bars up to *t*.

---

## frau-holle — the backtester

**Role.** Replays a strategy over a historical series and reports performance.

**When you use it.** When you want to know how a trading idea would have done.

**Key types.**

| Type | Purpose |
|---|---|
| `MarketDataSource` | port — supplies `List<OHLCBar>` for a symbol/timeframe/range |
| `SignalGenerator` | port — the strategy: `Signal generate(BarContext)` |
| `Signal` | sealed: `Hold, Buy, Sell, ClosePosition, ClosePositionAtPrice` (v1.1) |
| `BarContext` | what the strategy sees each bar — current bar, history, position, cash, equity |
| `BacktestSpec` + `BacktestSpecBuilder` | series + strategy + initial cash; builder validates V1–V7 |
| `Backtester` | the engine: `BacktestResult run(BacktestSpec)` |
| `BacktestResult` | metrics, trades, equity curve, open position, diagnostics |
| `BacktestMetrics` | the ten core metrics |
| `BacktestException` | checked root: `InvalidBacktestSpecException`, `MarketDataException` (+ 3 subtypes), `SignalGenerationException`, `BacktestInternalException`, `InvalidExplicitFillException` |

```java
SignalGenerator strategy = context -> context.barIndex() == 0
        ? new Signal.Buy(new BigDecimal("10"))   // buy on bar 0…
        : new Signal.Hold();                     // …then hold

BacktestSpec spec = BacktestSpec.builder()
        .withSeries(bars)
        .withSignalGenerator(strategy)
        .withInitialCash(new BigDecimal("10000"))
        .build();                                 // throws InvalidBacktestSpecException

BacktestResult result = new Backtester().run(spec);   // throws BacktestException
BacktestMetrics m = result.metrics();
System.out.printf("return=%s  trades=%d  maxDD=%s%n",
        m.totalReturn(), m.numTrades(), m.maxDrawdown());
```

The strategy is *opaque*: frau-holle knows nothing about how it decides — it
can be a hand-coded `if`/`else`, an ML model, or a compiled DSL. A `Buy`/`Sell`
fills at the next bar's open; an open position at the end is marked-to-market.

---

## frau-holle-csv — CSV data source

**Role.** A `MarketDataSource` that reads OHLC bars from local CSV files.
JDK-only, read-only, deterministic — the baseline source for tests, demos and
offline backtests.

**Key type.** `CsvMarketDataSource`.

```java
MarketDataSource source = new CsvMarketDataSource(Path.of("/data/bars"));
// reads /data/bars/AAPL_1d.csv
List<OHLCBar> bars = source.fetchHistory("AAPL", Timeframe.fromWire("1d"),
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-12-31T00:00:00Z"));
```

The file is `time,open,high,low,close[,volume]`, ISO-8601 UTC timestamps,
`#` comment lines and blank lines ignored. Schema errors (`MarketDataSchemaException`)
carry the offending line number. The filename is resolved from a configurable
`{symbol}_{timeframe}.csv` pattern.

---

## frau-holle-eodhd — EODHD data source

**Role.** A `MarketDataSource` that fetches end-of-day bars from the EODHD HTTP
API.

**Key types.** `EodhdMarketDataSource`, plus the injectable `HttpExecutor` and
`JsonReader` ports (each with a JDK-only default implementation, so no HTTP or
JSON library is pinned).

```java
MarketDataSource source = new EodhdMarketDataSource("YOUR_API_TOKEN");
List<OHLCBar> bars = source.fetchHistory("AAPL.US", Timeframe.fromWire("1d"),
        since, until);
```

It maps `1d/1w/1M` to the EODHD `d/w/m` period, parses numbers exactly into
`BigDecimal`, and maps HTTP failures to typed `MarketDataException` subtypes
(404 → not found; 401/403/429/5xx → unavailable; bad JSON → schema). It does
**not** retry — that is the consumer's concern.

---

## Composing the modules

The libraries are independent; the consumer connects them. A typical flow:

```
CsvMarketDataSource ──fetchHistory──▶ List<OHLCBar>
                                         │
            ┌────────────────────────────┼────────────────────────────┐
            ▼                            ▼                            ▼
   HeikinAshiCalculator          BacktestSpec + Backtester     ChartSpec + JFreeChartRenderer
            │                            │                            │
            ▼                            ▼                            ▼
   HASeries ▶ DetectionSpec        BacktestResult               ChartImage (bytes)
            ▶ PatternDetector       (metrics, trades…)
            ▼
   DetectionResult (matches)
```

See [`getting-started.md`](getting-started.md) for this wired up as runnable
code.
