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

<p align="center">
  <img src="heerwisch-logo.png" alt="heerwisch" height="72">
  &nbsp;&nbsp;&nbsp;
  <img src="nachtkrapp-logo.png" alt="nachtkrapp" height="72">
  &nbsp;&nbsp;&nbsp;
  <img src="frau-holle-logo.png" alt="frau-holle" height="72">
</p>

## Documentation

| Start here | For |
|---|---|
| **[docs/concepts.md](docs/concepts.md)** | what ha-track is for and the ideas behind it — read this first |
| **[docs/modules.md](docs/modules.md)** | per-module functional guide, with code examples |
| **[docs/getting-started.md](docs/getting-started.md)** | build, test, and a runnable end-to-end example |
| [`CLAUDE.md`](CLAUDE.md) (root + per module) | the authoritative, rule-by-rule behavioural specification |
| [`AGENTS.md`](AGENTS.md) | code-review guidelines for AI and human reviewers |

## Modules

Each module has its own `README.md` (human-friendly guide, with API usage) and
a `CLAUDE.md` (the authoritative behavioural specification).

| Module | Role |
|---|---|
| [`commons`](commons/README.md) | shared kernel — JDK-only data types and pure functions, zero external dependencies |
| [`indicators`](indicators/README.md) | shared kernel — JDK-only technical-indicator calculators (SMA, EMA, RSI, MACD, …) |
| [`heerwisch-api`](heerwisch-api/README.md) | plotting library — immutable chart spec types and the `ChartRenderer` port |
| [`heerwisch-jfreechart`](heerwisch-jfreechart/README.md) | default plotting driver — renders a `ChartSpec` to a PNG/JPEG `ChartImage`, headless |
| [`frau-holle`](frau-holle/README.md) | backtesting library — the `Backtester`, with `MarketDataSource` and `SignalGenerator` ports |
| [`frau-holle-csv`](frau-holle-csv/README.md) | reference data driver — reads OHLC bars from local CSV files |
| [`frau-holle-eodhd`](frau-holle-eodhd/README.md) | reference data driver — fetches bars from the EODHD End-of-Day API |
| [`nachtkrapp`](nachtkrapp/README.md) | pattern detection library — Heikin Ashi patterns plus MA/RSI/MACD primitives |

## Build

```bash
./mvnw -DskipTests=false verify
```

Requires **JDK 25**. A Maven Wrapper is committed, so a system Maven
installation is optional. See [docs/getting-started.md](docs/getting-started.md)
for details.

## Disclaimer

This software is a tool for **historical analysis only**.
It is NOT a trading platform, brokerage, financial advice service,
or investment product.

**No warranties.** The software is provided "AS IS" under its license.
The author makes no warranties regarding accuracy, completeness,
reliability, or fitness for any purpose.

**Past performance is not indicative of future results.** Results
obtained with this software reflect hypothetical performance on
historical data and have inherent limitations:
- They cannot account for all market conditions
- They cannot model unforeseen events
- They cannot guarantee future returns

**Use at your own risk.** Any trading decisions based on output
from this software are made solely at the user's discretion and
risk. The author accepts no liability for any financial losses,
regulatory issues, or other consequences arising from use of this
software.

**No financial advice.** Nothing in this software, its documentation,
its output, or its associated artifacts constitutes financial,
investment, legal, or tax advice. Users should consult qualified
professionals before making investment decisions.

**No regulatory endorsement.** This software is not registered with,
endorsed by, or approved by any financial regulatory authority.
Users are solely responsible for compliance with applicable laws
and regulations in their jurisdiction.
