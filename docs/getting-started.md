# ha-track — getting started

How to build the project, run the tests, and wire the modules together into a
working end-to-end example. For background read [`concepts.md`](concepts.md);
for the per-module reference read [`modules.md`](modules.md).

---

## 1. Prerequisites

| Requirement | Notes |
|---|---|
| **JDK 25** | a deliberate, fixed choice — the build targets Java 25 and will not compile on an earlier JDK |
| **Maven** | a Maven Wrapper is committed, so a system Maven is optional |

The Maven Wrapper (`./mvnw`) pins the Maven version, so contributors and CI all
build with the same toolchain. Use it in preference to a system `mvn`.

---

## 2. Build and test

From the repository root:

```bash
# compile + run every module's Cucumber suite
./mvnw -DskipTests=false verify

# compile only, skip tests
./mvnw -DskipTests verify

# build (or test) a single module and what it depends on
./mvnw -pl nachtkrapp -am test
```

A green build compiles all eight modules and runs the full behavioural test
suite — every module is specified and tested with **Cucumber feature files**
(`<module>/src/test/resources/features/`) executed on the JUnit Platform.

All eight modules share a single version (`0.47.0-alpha`), managed centrally
in the root POM.

---

## 3. The mental model in one minute

1. Get **bars** — a `List<OHLCBar>` — from a `MarketDataSource` (CSV or EODHD),
   or build them yourself.
2. Wrap bars in a **`Series`** (`OHLCSeries`, or `HASeries` after converting
   with `HeikinAshiCalculator`).
3. Feed a series to whichever library you need:
   - **chart it** — build a `ChartSpec`, render with `JFreeChartRenderer`;
   - **scan it** — build a `DetectionSpec`, run a `PatternDetector`;
   - **backtest on it** — build a `BacktestSpec` with a `SignalGenerator`, run
     the `Backtester`.
4. Each library hands back an immutable result you inspect.

The libraries do not know about each other; your application is the glue.

---

## 4. End-to-end example

A single program that loads bars from CSV, then (a) detects Heikin Ashi
reversals, (b) backtests a toy strategy, and (c) renders a chart. It is written
to read top-to-bottom; in real code, handle the checked exceptions explicitly.

```java
import org.hatrack.commons.*;
import org.hatrack.frauholle.csv.CsvMarketDataSource;
import org.hatrack.frauholle.engine.Backtester;
import org.hatrack.frauholle.model.Signal;
import org.hatrack.frauholle.port.MarketDataSource;
import org.hatrack.frauholle.port.SignalGenerator;
import org.hatrack.frauholle.result.BacktestResult;
import org.hatrack.frauholle.spec.BacktestSpec;
import org.hatrack.heerwisch.api.port.ChartRenderer;
import org.hatrack.heerwisch.api.spec.*;
import org.hatrack.heerwisch.jfreechart.JFreeChartRenderer;
import org.hatrack.nachtkrapp.detector.*;
import org.hatrack.nachtkrapp.rule.DetectionRule;
import org.hatrack.nachtkrapp.spec.DetectionSpec;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class HaTrackDemo {

    public static void main(String[] args) throws Exception {

        // ── 1. load bars ───────────────────────────────────────────────
        // reads /data/bars/AAPL_1d.csv
        MarketDataSource source = new CsvMarketDataSource(Path.of("/data/bars"));
        List<OHLCBar> bars = source.fetchHistory(
                "AAPL", Timeframe.fromWire("1d"),
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-12-31T00:00:00Z"));

        // ── 2. detect Heikin Ashi reversals ────────────────────────────
        List<HABar> haBars = HeikinAshiCalculator.computeChain(Optional.empty(), bars);
        DetectionSpec detectionSpec = DetectionSpec.builder()
                .withSeries(new HASeries(haBars))
                .addRule(new DetectionRule.HAColorChangeRule(3))
                .withTimeframe(Timeframe.fromWire("1d"))
                .build();
        PatternDetector detector = new RuleBasedPatternDetector();
        DetectionResult detection = detector.detect(detectionSpec);
        System.out.println("HA reversals found: " + detection.matches().size());

        // ── 3. backtest a toy buy-and-hold strategy ────────────────────
        SignalGenerator strategy = context -> context.barIndex() == 0
                ? new Signal.Buy(new BigDecimal("10"))
                : new Signal.Hold();
        BacktestSpec backtestSpec = BacktestSpec.builder()
                .withSeries(bars)
                .withSignalGenerator(strategy)
                .withInitialCash(new BigDecimal("10000"))
                .build();
        BacktestResult result = new Backtester().run(backtestSpec);
        System.out.println("total return: " + result.metrics().totalReturn());

        // ── 4. render a chart ──────────────────────────────────────────
        ChartSpec chartSpec = ChartSpec.builder()
                .withSeries(new OHLCSeries(bars))
                .addIndicator(new Indicator.SMA(20, PriceSource.CLOSE))
                .build();
        ChartRenderer renderer = new JFreeChartRenderer();
        ChartImage image = renderer.render(chartSpec);
        Files.write(Path.of("aapl-2024.jpg"), image.bytes());
        System.out.println("chart written: " + image.bytes().length + " bytes");
    }
}
```

What this shows:

- the **same `List<OHLCBar>`** feeds all three libraries — `commons` is the
  shared currency;
- pattern detection runs on the **Heikin Ashi** view, charting and backtesting
  on the **raw OHLC** view — both are first-class;
- each library is reached through a **builder → immutable spec → engine/driver**
  shape, and hands back an **immutable result**;
- the application is the only place the three libraries meet.

---

## 5. Maven coordinates

All artifacts share the `net.jacopobiscella` group id.

| Artifact | Version | Depends on |
|---|---|---|
| `commons` | `0.47.0-alpha` | — (JDK only) |
| `indicators` | `0.47.0-alpha` | — (JDK only) |
| `heerwisch-api` | `0.47.0-alpha` | `commons` |
| `heerwisch-jfreechart` | `0.47.0-alpha` | `heerwisch-api`, `indicators`, JFreeChart 1.5 |
| `nachtkrapp` | `0.47.0-alpha` | `commons`, `indicators` |
| `frau-holle` | `0.47.0-alpha` | `commons` |
| `frau-holle-csv` | `0.47.0-alpha` | `frau-holle` |
| `frau-holle-eodhd` | `0.47.0-alpha` | `frau-holle` |

A consumer depends only on the modules it actually uses — e.g. a charting-only
app needs `heerwisch-api` + `heerwisch-jfreechart`; a backtesting app needs
`frau-holle` + one data driver.

---

## 6. Further reading

| Topic | Where |
|---|---|
| concepts & functional overview | [`concepts.md`](concepts.md) |
| per-module functional guide | [`modules.md`](modules.md) |
| repo-wide architecture & dependency rules | root [`CLAUDE.md`](../CLAUDE.md) |
| a module's authoritative behavioural spec | `<module>/CLAUDE.md` |
| AI / human code-review guidelines | [`AGENTS.md`](../AGENTS.md) |
