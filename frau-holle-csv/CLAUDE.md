# CLAUDE.md — `frau-holle-csv` module

This is the nested spec for the `frau-holle-csv` module. It is a reference implementation of the `MarketDataSource` port declared in `frau-holle/CLAUDE.md` §2.1. The repo-wide rules live in the root `CLAUDE.md`.

## 0. Goal and scope

`frau-holle-csv` reads OHLC bars from local CSV files on the filesystem. It is the baseline data source for `frau-holle`: zero HTTP dependencies, deterministic, suitable for testing, demos, and offline backtesting on historical data the consumer has obtained out-of-band.

Out of scope: streaming CSV, zip / gzip decompression, multi-file aggregation, watch-and-reload, online schema migration, writing CSV.

Dependencies: `frau-holle`, `commons`, JDK only.

## 1. CSV format

The driver supports one canonical CSV format:

| Aspect | Choice |
|---|---|
| Header row | required; column names case-insensitive |
| Required columns | `time`, `open`, `high`, `low`, `close` |
| Optional column | `volume` |
| Column separator | comma (`,`) — fixed in v1 |
| Decimal separator | dot (`.`) — fixed |
| Quoting | not used (values must not contain commas) |
| Encoding | UTF-8 |
| Line endings | `\n` or `\r\n` (both accepted) |
| Empty lines | ignored (skip blank lines, no error) |
| Comment lines | lines starting with `#` are ignored |

### 1.1 `time` column

| Aspect | Choice |
|---|---|
| Format | ISO-8601 instant — `2024-01-15T00:00:00Z` |
| Timezone | UTC required (suffix `Z`); offset suffixes (`+02:00`) are rejected in v1 |
| Granularity | second precision; sub-second values are accepted but discouraged |

### 1.2 Price columns (`open`, `high`, `low`, `close`)

| Aspect | Choice |
|---|---|
| Format | decimal number; scientific notation accepted (`1.5E2`) |
| Sign | must be positive (enforced by OHLC invariants on construction) |
| Precision | arbitrary; parsed into `BigDecimal` |

### 1.3 `volume` column

| Aspect | Choice |
|---|---|
| Optional | column may be absent; if absent, all bars have `volume = Optional.empty()` |
| Empty cell | `volume` cell may be empty within a row; that bar gets `Optional.empty()` |
| Format | non-negative decimal number |

### 1.4 Example file

```
# AAPL daily, OHLCV from EODHD export
time,open,high,low,close,volume
2024-01-02T00:00:00Z,187.15,188.44,183.89,185.64,82488682
2024-01-03T00:00:00Z,184.22,185.88,183.43,184.25,58414460
2024-01-04T00:00:00Z,182.15,183.09,180.88,181.91,71983566
```

## 2. Driver constructor and configuration

The driver is a class `CsvMarketDataSource` implementing `MarketDataSource`. It takes a configuration object at construction:

| Field | Type | Meaning |
|---|---|---|
| `baseDirectory` | `Path` | root directory under which CSV files live |
| `fileNamePattern` | `String` | a template that maps `(symbol, timeframe)` to a filename. Default: `"{symbol}_{timeframe}.csv"`. Placeholders: `{symbol}`, `{timeframe}` (using the `Timeframe.wire()` representation) |

Example resolution: with `baseDirectory = /data/bars` and default pattern, a call `fetchHistory("AAPL", Timeframe.of("1d"), …, …)` reads `/data/bars/AAPL_1d.csv`.

## 3. `fetchHistory` behavior

| Aspect | Behavior |
|---|---|
| File location | resolved as `baseDirectory.resolve(fileNamePattern.replace("{symbol}", symbol).replace("{timeframe}", timeframe.wire()))` |
| File not found | throws `MarketDataNotFoundException` with `symbol`, plus the resolved path in the message |
| File present, malformed | throws `MarketDataSchemaException` with the line number and a description of the parse failure |
| Filtering | bars are filtered to those with `since ≤ time ≤ until` (both endpoints inclusive) |
| Ordering | the driver does NOT sort. The file MUST already be ordered. If unordered, V3 check in `frau-holle.BacktestSpec.builder().build()` will catch it later (this driver does not police) |
| Empty range | returns `emptyList()` (NOT an error) — for example, requesting bars before the earliest in the file or after the latest |
| Volume column absent | all bars have `volume = Optional.empty()` |
| OHLC invariants | the driver does NOT call `validateInvariants()` — that is the consumer's responsibility downstream. Production data is generally valid; calling validateInvariants() per bar would penalize bulk loads |

## 4. Validation rules — file-level

The driver validates structure but not semantics:

| # | Rule | On violation |
|---|---|---|
| F1 | Header row is present and contains `time`, `open`, `high`, `low`, `close` (case-insensitive) | `MarketDataSchemaException` |
| F2 | Each non-blank, non-comment row has at least the required columns | `MarketDataSchemaException` with line number |
| F3 | `time` parses as ISO-8601 UTC instant | `MarketDataSchemaException` with line number and offending value |
| F4 | `open`, `high`, `low`, `close` parse as a strictly-positive `BigDecimal` (> 0) | `MarketDataSchemaException` with line number |
| F5 | `volume` (if column present and cell non-empty) parses as non-negative `BigDecimal` (≥ 0; zero volume is legal) | `MarketDataSchemaException` with line number |

The driver does NOT check OHLC invariants (per §3), ordering, or unique times. Those are checked by `frau-holle.BacktestSpec.builder()` at V3/V5/V7. F4 is a *schema* check: a price must be a positive number. The fuller OHLC invariant set (high ≥ low, etc.) is a downstream spec-builder concern, not the CSV driver's.

## 5. Block 1 — Basic reads

```gherkin
Feature: CsvMarketDataSource basic behavior

  Scenario: Read a well-formed CSV file
    Given a CSV file at /data/bars/AAPL_1d.csv with 5 rows
    And a CsvMarketDataSource configured with baseDirectory = /data/bars
    When I call fetchHistory("AAPL", "1d", first row's time, last row's time)
    Then the result is a List<OHLCBar> of 5 entries
    And each bar matches the corresponding row

  Scenario: Filtering by since/until is inclusive
    Given a CSV file with bars at T0, T1, T2, T3, T4
    When I call fetchHistory(symbol, tf, T1, T3)
    Then the result contains exactly 3 bars (T1, T2, T3)

  Scenario: Range outside file returns empty
    Given a CSV file with bars from 2024-01-01 to 2024-12-31
    When I call fetchHistory(symbol, tf, 2025-06-01, 2025-12-31)
    Then the result is empty
    And no exception is thrown

  Scenario: File not found raises MarketDataNotFoundException
    Given no file at the resolved path
    When I call fetchHistory("UNKNOWN", "1d", any, any)
    Then MarketDataNotFoundException is thrown
    And the exception names the symbol and the resolved path

  Scenario: Volume column absent produces Optional.empty volumes
    Given a CSV with header "time,open,high,low,close" (no volume)
    When I fetch
    Then every bar has volume = Optional.empty()

  Scenario: Volume cell empty produces Optional.empty for that bar
    Given a CSV with the volume column present but the volume cell empty on one row
    When I fetch
    Then that bar has volume = Optional.empty()
    And other bars have their respective Optional.of values
```

## 6. Block 2 — Schema errors

```gherkin
Feature: Schema error reporting

  Scenario: Missing required column
    Given a CSV with header "time,open,high,close" (no "low")
    When I fetch
    Then MarketDataSchemaException is thrown
    And the message mentions the missing column "low"

  Scenario: Non-ISO timestamp
    Given a row with time = "2024-01-15 00:00:00" (space, not 'T', no Z)
    When I fetch
    Then MarketDataSchemaException is thrown
    And the message includes the line number and the offending value

  Scenario: Non-numeric price
    Given a row with open = "N/A"
    When I fetch
    Then MarketDataSchemaException is thrown with line number

  Scenario: Zero OHLC price is rejected as a schema error
    Given a row with open = "0" (the other prices valid)
    When I fetch
    Then MarketDataSchemaException is thrown with line number
    And the message indicates the price must be strictly positive

  Scenario: Negative OHLC price is rejected as a schema error
    Given a row with open = "-5" (the other prices valid)
    When I fetch
    Then MarketDataSchemaException is thrown with line number

  Scenario: Row with too few columns is rejected (F2)
    Given a data row with fewer cells than the header requires
    When I fetch
    Then MarketDataSchemaException is thrown with line number

  Scenario: Non-numeric or negative volume cell is rejected (F5)
    Given the volume column is present and a row's volume cell is "N/A" or "-100"
    When I fetch
    Then MarketDataSchemaException is thrown with line number

  Scenario: Comment line ignored
    Given a CSV with lines starting with "#"
    When I fetch
    Then those lines are skipped without error

  Scenario: Blank line ignored
    Given a CSV with a blank line in the middle
    When I fetch
    Then the blank line is skipped without error
```

## 7. Block 3 — Configuration

```gherkin
Feature: File name pattern

  Scenario: Default pattern resolves files
    Given baseDirectory = /data and default pattern "{symbol}_{timeframe}.csv"
    When I fetchHistory("AAPL", "1d", ...)
    Then the driver reads /data/AAPL_1d.csv

  Scenario: Custom pattern resolves files
    Given baseDirectory = /data and pattern "{timeframe}/{symbol}.csv"
    When I fetchHistory("AAPL", "1d", ...)
    Then the driver reads /data/1d/AAPL.csv

  Scenario: Pattern without symbol placeholder
    Given a pattern that omits {symbol}
    When the driver is constructed
    Then IllegalArgumentException is thrown at construction
```

## 8. Out of scope for `frau-holle-csv`

- Streaming / chunked reads (the entire file is read into memory; suitable for normal historical data sizes)
- Compressed files (.gz, .zip, .bz2) — out of scope, consumer decompresses out-of-band
- Multi-file aggregation (one symbol+timeframe = one file)
- Writing CSV (this driver is read-only)
- Alternative separators (semicolon, tab) — fixed comma in v1
- Alternative decimal separators (European comma) — fixed dot
- Quoted values containing commas — not supported (use a different export format)
- Locale-aware parsing — explicitly NOT locale-aware
- Network paths via SMB / NFS — works as long as the filesystem is mounted, but no special handling
- Hot reload (re-reading the file if it changes during a long-running process)
- CSV-with-headers detection (CSV without header is not supported)
- Caching (each `fetchHistory` re-reads the file; consumer caches if needed)

## 9. Implementation delegation to Claude Code

Claude Code is responsible for:

- Package layout (suggested: `<group>.frauholle.csv` with a single public class `CsvMarketDataSource` plus an `internal` package for the parser)
- Implementing CSV parsing using JDK only — no external CSV libraries needed for this simple format
- Implementing the file name pattern substitution
- Mapping rows to `OHLCBar` instances using `BigDecimal` and `Instant`
- Implementing error reporting with line numbers
- Test infrastructure with example CSV fixtures (small files in test resources)
- Running the shared `MarketDataSourceContract` suite (from the `frau-holle` test-jar) via a concrete subclass — see `frau-holle/CLAUDE.md` §2.1.1. The CSV driver's own normalization behavior (no sort, no OHLC-invariant check, no ordering police — those are downstream `BacktestSpec.builder()` concerns) stays in this module's dedicated tests, separate from the shared contract

What Claude Code MUST NOT do unilaterally:

- Add support for other separators, decimal formats, encodings
- Add support for compressed files
- Add a CSV writer
- Add caching (per-`fetchHistory` re-reads)
- Bring in an external CSV library (e.g. OpenCSV, commons-csv) — JDK is sufficient
- Validate OHLC invariants in the parser (consumer's responsibility)
- Auto-detect file encoding (assume UTF-8)
- Trim or normalize symbol values (passed through verbatim)
- Add reflective bean wiring or DI annotations
