# ha-track

**ha-track** is a Java toolkit for **technical analysis of financial price
data**, rooted in Heikin Ashi candle analysis. It gives an application three
capabilities — and a shared vocabulary to connect them:

- **see** the market — chart prices and indicators (*heerwisch*)
- **recognise** the market — detect patterns and indicator events (*nachtkrapp*)
- **test** an idea — backtest a trading strategy over history (*frau-holle*)

The three libraries are independent; an application picks the ones it needs and
composes the results. They share one small kernel of data types (*commons*) so
a bar of price data means the same thing everywhere. The modules are named
after continental Germanic folklore — each a helper that can also mislead.

ha-track is a set of **libraries**, not an application, and is deliberately
framework-agnostic (no DI container). The end-user product is a separate
consumer application.

## Documentation

| Start here | For |
|---|---|
| **[docs/concepts.md](docs/concepts.md)** | what ha-track is for and the ideas behind it — read this first |
| **[docs/modules.md](docs/modules.md)** | per-module functional guide, with code examples |
| **[docs/getting-started.md](docs/getting-started.md)** | build, test, and a runnable end-to-end example |
| [`CLAUDE.md`](CLAUDE.md) (root + per module) | the authoritative, rule-by-rule behavioural specification |
| [`AGENTS.md`](AGENTS.md) | code-review guidelines for AI and human reviewers |

## Modules

| Module | Role |
|---|---|
| [`commons`](commons/CLAUDE.md) | shared kernel — JDK-only data types and pure functions, zero external dependencies |
| [`heerwisch-api`](heerwisch-api/CLAUDE.md) | plotting library — immutable chart spec types and the `ChartRenderer` port |
| [`heerwisch-jfreechart`](heerwisch-jfreechart/CLAUDE.md) | default plotting driver — renders a `ChartSpec` to a PNG/JPEG `ChartImage`, headless |
| [`frau-holle`](frau-holle/CLAUDE.md) | backtesting library — the `Backtester`, with `MarketDataSource` and `SignalGenerator` ports |
| [`frau-holle-csv`](frau-holle-csv/CLAUDE.md) | reference data driver — reads OHLC bars from local CSV files |
| [`frau-holle-eodhd`](frau-holle-eodhd/CLAUDE.md) | reference data driver — fetches bars from the EODHD End-of-Day API |
| [`nachtkrapp`](nachtkrapp/CLAUDE.md) | pattern detection library — Heikin Ashi patterns plus MA/RSI/MACD primitives |

## Build

```bash
./mvnw -DskipTests=false verify
```

Requires **JDK 25**. A Maven Wrapper is committed, so a system Maven
installation is optional. See [docs/getting-started.md](docs/getting-started.md)
for details.
