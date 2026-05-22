# frau-holle-csv

A reference **`MarketDataSource`** for [`frau-holle`](../frau-holle) that reads
OHLC bars from **local CSV files**. JDK-only, read-only, deterministic.

> Human-friendly guide. Authoritative contract: [`CLAUDE.md`](CLAUDE.md).

## What it is for

The baseline data source: no network, no credentials, fully reproducible. Use
it for tests, demos, and offline backtests on historical data you already have
on disk. For a live HTTP source see [`frau-holle-eodhd`](../frau-holle-eodhd).

## Adding the dependency

```xml
<dependency>
    <groupId>net.jacopobiscella</groupId>
    <artifactId>frau-holle-csv</artifactId>
    <version>0.51.0-alpha</version>
</dependency>
```

Main class: `org.hatrack.frauholle.csv.CsvMarketDataSource`.

## Usage

```java
import org.hatrack.frauholle.csv.CsvMarketDataSource;
import org.hatrack.frauholle.port.MarketDataSource;

// default file-name pattern: {symbol}_{timeframe}.csv
MarketDataSource source = new CsvMarketDataSource(Path.of("/data/bars"));

List<OHLCBar> bars = source.fetchHistory(
        "AAPL", Timeframe.fromWire("1d"),
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-12-31T00:00:00Z"));
// → reads /data/bars/AAPL_1d.csv, keeps bars with since ≤ time ≤ until
```

A custom file-name pattern is also supported (it must contain `{symbol}`):

```java
new CsvMarketDataSource(Path.of("/data"), "{timeframe}/{symbol}.csv");
// → reads /data/1d/AAPL.csv
```

## The CSV format

```
# comments start with '#' and are ignored; blank lines too
time,open,high,low,close,volume
2024-01-02T00:00:00Z,187.15,188.44,183.89,185.64,82488682
2024-01-03T00:00:00Z,184.22,185.88,183.43,184.25,58414460
```

- a header row is **required**; column names are case-insensitive;
- required columns: `time, open, high, low, close`; `volume` is optional
  (absent column → all bars have no volume; empty cell → that bar has none);
- `time` is an ISO-8601 UTC instant (must end in `Z`);
- prices and volume are decimals parsed exactly into `BigDecimal`;
- comma separator, dot decimal, UTF-8, `\n` or `\r\n`.

## Behaviour worth knowing

- **Filtering is inclusive** on both `since` and `until`.
- A range with no matching bars returns an **empty list** — not an error.
- A missing file throws `MarketDataNotFoundException` (naming the symbol and the
  resolved path).
- The driver does **not** sort and does **not** check OHLC invariants — that is
  downstream, at `BacktestSpec.builder().build()`. The driver's job is *schema*
  validation only.
- Each `fetchHistory` re-reads the file; add caching yourself if you need it.

## Schema validation (F1–F5)

A structurally broken file throws `MarketDataSchemaException` with the offending
**line number**: missing required column (F1/F2), a non-ISO timestamp (F3), a
price that is not a strictly-positive number (F4), a negative volume (F5).

## Out of scope

Compressed files, alternative separators/decimals, streaming, multi-file
aggregation, CSV writing, caching.
