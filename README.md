# ha-track

Heerwisch / Frau Holle / Nachtkrapp — three independent Java libraries and a
shared kernel, plus reference pluggable data drivers.

The authoritative specification lives in [`CLAUDE.md`](CLAUDE.md) at the repo
root, with per-module specs in each `<module>/CLAUDE.md`.

## Modules

| Module | Description |
|---|---|
| [`commons`](commons/CLAUDE.md) | Data types + pure functions, stateless. Shared kernel between the libraries. Zero external dependencies (JDK only). |
| [`heerwisch-api`](heerwisch-api/CLAUDE.md) | Port + immutable spec types + checked exceptions for the plotting library. No driver implementation. |
| [`heerwisch-jfreechart`](heerwisch-jfreechart/CLAUDE.md) | Default plotting driver. Consumes `ChartSpec`, produces `ChartImage` (PNG or JPEG). Headless. |
| [`frau-holle`](frau-holle/CLAUDE.md) | Backtester. Exposes the `MarketDataSource` port for data providers and the `SignalGenerator` port for consumer strategies. |
| [`frau-holle-csv`](frau-holle-csv/CLAUDE.md) | Reference `MarketDataSource` reading OHLC bars from local CSV files. Zero HTTP dependencies. |
| [`frau-holle-eodhd`](frau-holle-eodhd/CLAUDE.md) | Reference `MarketDataSource` hitting the EODHD End-of-Day API. |
| [`nachtkrapp`](nachtkrapp/CLAUDE.md) | Pattern detection. Exposes the `PatternDetector` entry point and its rule-based implementation. |

## Build

```
mvn -DskipTests verify
```

Requires Java 25 and Maven.
