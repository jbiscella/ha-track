# CLAUDE.md — `commons` module

This is the nested spec for the `commons` module. The repo-wide rules (architecture, code style, dependency edges) live in the root `CLAUDE.md`. This file specifies only what is internal to `commons`: the exact shape of the public types, the formulas of the pure functions, the OHLC invariants, and the behavior under edge cases.

## 0. Goal and scope

`commons` is the shared kernel between `heerwisch-*` and `skuld-*`. It contains:

- General-purpose data types (`OHLCBar`, `HABar`, `Timeframe`, `PriceSource`).
- Pure stateless functions (`HeikinAshiCalculator`).
- The OHLC invariant exception (`OHLCInvariantViolationException`).

Out of scope: I/O, persistence, clocks, framework annotations, anything that requires an external dependency. JDK-only is a hard constraint enforced at the module's `pom.xml` level (no `<dependency>` block beyond JUnit for tests).

## 1. Data types

### 1.1 `OHLCBar`

Immutable record. Fields:

| Field | Type | Constraint |
|---|---|---|
| `time` | `Instant` | non-null |
| `open` | `BigDecimal` | non-null, validated by `validateInvariants()` |
| `high` | `BigDecimal` | non-null, validated by `validateInvariants()` |
| `low` | `BigDecimal` | non-null, validated by `validateInvariants()` |
| `close` | `BigDecimal` | non-null, validated by `validateInvariants()` |
| `volume` | `Optional<BigDecimal>` | non-null `Optional` (the value inside MAY be empty); when present MUST be ≥ 0 |

Behavior:

- The canonical constructor performs **null-check only** (via `Objects.requireNonNull` on each field). Construction with values that violate OHLC invariants succeeds — the caller must explicitly call `validateInvariants()` to assert correctness.
- Rationale: lazy validation lets callers build bars from raw parsed input and validate as a separate, traceable step. Constructors that throw on invariant violations would conflate parsing errors with shape errors.

### 1.2 `HABar`

Immutable record. Fields:

| Field | Type | Constraint |
|---|---|---|
| `time` | `Instant` | non-null |
| `haOpen` | `BigDecimal` | non-null |
| `haHigh` | `BigDecimal` | non-null |
| `haLow` | `BigDecimal` | non-null |
| `haClose` | `BigDecimal` | non-null |

Behavior:

- The canonical constructor performs null-check only.
- HA bars carry no separate validation method. By construction they come from `HeikinAshiCalculator` which already enforces formula invariants.

### 1.3 `Timeframe`

Open type, NOT a closed enum. Represented as a record:

| Field | Type | Constraint |
|---|---|---|
| `amount` | `int` | ≥ 1 |
| `unit` | `Unit` enum | non-null |

`Unit` enum members: `SECOND`, `MINUTE`, `HOUR`, `DAY`, `WEEK`, `MONTH`, `YEAR`.

Wire format (string serialization):

| Pattern | Unit |
|---|---|
| `<n>s` | SECOND |
| `<n>m` | MINUTE |
| `<n>h` | HOUR |
| `<n>d` | DAY |
| `<n>w` | WEEK |
| `<n>M` | MONTH (uppercase M — disambiguates from minute) |
| `<n>y` | YEAR |

`fromWire(String)` parses the wire format. `wire()` returns the canonical string. Round-trip is exact for any valid `Timeframe`.

### 1.4 `PriceSource`

Closed enum. Members: `OPEN`, `HIGH`, `LOW`, `CLOSE`, `HA_OPEN`, `HA_HIGH`, `HA_LOW`, `HA_CLOSE`.

Used by `heerwisch-api` to declare which series channel an indicator computes against, and used by `skuld-api` for the same purpose during signal generation.

### 1.5 `Series`

Closed `sealed` hierarchy shared by the libraries that consume ordered bar data (`heerwisch-api`, `nachtkrapp`). It lives in `commons` because more than one library needs it and the cross-module rules forbid those libraries from depending on each other.

```
sealed interface Series permits OHLCSeries, HASeries
```

| Variant | Fields | Constraint |
|---|---|---|
| `OHLCSeries` | `List<OHLCBar> bars` | non-null; defensively copied to an immutable list at construction |
| `HASeries` | `List<HABar> bars` | non-null; defensively copied to an immutable list at construction |

Behavior:

- The canonical constructor performs null-check only on the list reference, then replaces it with `List.copyOf(...)`. A caller's later mutation of their original list MUST NOT affect the record. A list that itself contains a `null` element is rejected with `NullPointerException` (a consequence of `List.copyOf`).
- `commons` does NOT enforce ordering, uniqueness, or non-emptiness of the bars. Those are domain rules validated by the consuming library's spec builder (e.g. `heerwisch-api` V2–V4, `nachtkrapp` V2–V4).

## 2. OHLC invariants

Enforced by `OHLCBar.validateInvariants()`. On violation, throws `OHLCInvariantViolationException` (extends `RuntimeException`).

The invariants:

| # | Invariant | Description |
|---|---|---|
| I1 | `open > 0` | open price is strictly positive |
| I2 | `high > 0` | high price is strictly positive |
| I3 | `low > 0` | low price is strictly positive |
| I4 | `close > 0` | close price is strictly positive |
| I5 | `high ≥ low` | high spans at least to the low |
| I6 | `high ≥ open` | high is at least the open |
| I7 | `high ≥ close` | high is at least the close |
| I8 | `low ≤ open` | low is at most the open |
| I9 | `low ≤ close` | low is at most the close |
| I10 | `volume ≥ 0` when present | volume may be zero (no trades) but never negative |

The exception carries the bar's `time` (for diagnostics) and the name of the first violated invariant. It does not carry the offending bar itself (avoid leaking parsed-but-unvalidated state up the call stack).

## 3. HeikinAshiCalculator

Public class with only static methods. No state. No I/O. No clock.

Arithmetic uses `MathContext.DECIMAL64` everywhere. No `double`. No `float`.

### 3.1 Canonical HA formulas

For a single bar at index `t`:

| Output | Formula |
|---|---|
| `haClose[t]` | `(open[t] + high[t] + low[t] + close[t]) / 4` |
| `haOpen[t]` (running, `t ≥ 1` with previous HA available) | `(haOpen[t-1] + haClose[t-1]) / 2` |
| `haOpen[t]` (seed, no previous HA) | `(open[t] + close[t]) / 2` |
| `haHigh[t]` | `max(high[t], haOpen[t], haClose[t])` |
| `haLow[t]` | `min(low[t], haOpen[t], haClose[t])` |

### 3.2 Public methods

| Method | Signature contract |
|---|---|
| `compute(prev, ohlc)` | Computes one `HABar` from optional previous `HABar` (seed if absent) and current `OHLCBar`. |
| `computeChain(prev, ohlcs)` | Computes a chain of `HABar` from optional previous `HABar` and an ordered list of `OHLCBar`. Returns a list of the same size as input. |

The previous-HA parameter is `Optional<HABar>` in both methods. The OHLC list parameter must be ordered ascending by `time`; the calculator does not sort.

### 3.3 Block 1 — Heikin Ashi calculation behavior

```gherkin
Feature: Heikin Ashi calculation

  Scenario: Seed bar from a single OHLC with no previous HA
    Given an OHLC bar with open=10, high=12, low=9, close=11
    And no previous HA bar
    When I call compute(empty, ohlc)
    Then haClose = (10+12+9+11)/4 = 10.5
    And haOpen = (10+11)/2 = 10.5
    And haHigh = max(12, 10.5, 10.5) = 12
    And haLow = min(9, 10.5, 10.5) = 9
    And the returned bar has the same time as the input OHLC

  Scenario: Running bar with previous HA available
    Given a previous HA bar with haOpen=10.5 and haClose=10.5
    And a current OHLC bar with open=11, high=13, low=10.5, close=12.5
    When I call compute(previous, ohlc)
    Then haClose = (11+13+10.5+12.5)/4 = 11.75
    And haOpen = (10.5+10.5)/2 = 10.5
    And haHigh = max(13, 10.5, 11.75) = 13
    And haLow = min(10.5, 10.5, 11.75) = 10.5

  Scenario: Chain seeded from empty previous over multiple bars
    Given no previous HA bar
    And an ordered list of 3 OHLC bars
    When I call computeChain(empty, ohlcs)
    Then the result is a list of 3 HA bars in the same order
    And the first HA bar is the seed (computed as if previous were absent)
    And each subsequent HA bar uses the previous HA bar of the chain as its predecessor

  Scenario: Chain seeded from an explicit previous over multiple bars
    Given a previous HA bar P
    And an ordered list of N OHLC bars
    When I call computeChain(P, ohlcs)
    Then the first computed HA bar uses P as its predecessor (running formula, not seed)
    And subsequent bars chain normally

  Scenario: Empty input chain
    When I call computeChain(any, emptyList)
    Then an empty list is returned
    And no exception is thrown

  Scenario: Time of the HA bar equals the time of the source OHLC
    Given any OHLC bar with time T
    When the HA bar for that OHLC is computed
    Then its time equals T exactly

  Scenario: Previous HA bar dominates the current bar's raw high/low
    Given a previous HA bar whose haOpen and haClose are both above the current bar's high
    When the running HA bar is computed
    Then haHigh = max(high, haOpen, haClose) is the previous-derived haOpen, not the raw high
    And symmetrically a previous HA bar below the raw low makes haLow the previous-derived value
```

## 4. OHLC invariant enforcement behavior

```gherkin
Feature: OHLC invariant validation

  Scenario: Valid bar passes
    Given an OHLC bar with open=10, high=12, low=9, close=11
    When I call validateInvariants()
    Then no exception is thrown

  Scenario: Negative price violates I1..I4
    Given an OHLC bar where open is -1 (and other fields valid)
    When I call validateInvariants()
    Then OHLCInvariantViolationException is thrown
    And the exception names the violated invariant as "open" (I1)

  Scenario: high lower than low violates I5
    Given an OHLC bar with high=8 and low=10 (rest valid)
    When I call validateInvariants()
    Then OHLCInvariantViolationException is thrown
    And the exception names the violated invariant as "high<low" (I5)

  Scenario: Construction with invalid values does NOT throw
    Given any OHLC field values (including invalid)
    When the OHLCBar canonical constructor runs
    Then it succeeds as long as no field is null
    And the caller is responsible for calling validateInvariants() to assert correctness

  Scenario: Null field is rejected at construction
    When the OHLCBar canonical constructor is called with any field set to null
    Then NullPointerException is thrown (from Objects.requireNonNull)

  Scenario: Volume present and negative violates I10
    Given an OHLC bar where volume is Optional.of(-1) (rest valid)
    When I call validateInvariants()
    Then OHLCInvariantViolationException is thrown
    And the exception names the violated invariant as "volume" (I10)

  Scenario: Volume absent is always valid
    Given an OHLC bar with volume = Optional.empty()
    And all other fields valid
    When I call validateInvariants()
    Then no exception is thrown
```

## 5. Timeframe parsing behavior

```gherkin
Feature: Timeframe wire format

  Scenario: Round-trip of standard timeframes
    Given the wire strings ["1s", "5m", "15m", "1h", "4h", "1d", "1w", "1M", "1y"]
    When each is parsed via fromWire() and re-serialized via wire()
    Then the result equals the original string exactly

  Scenario: Disambiguation between month and minute
    Given the wire string "1m"
    When I parse it via fromWire()
    Then the resulting Timeframe has unit = MINUTE
    And calling wire() on it returns "1m"

    Given the wire string "1M"
    When I parse it via fromWire()
    Then the resulting Timeframe has unit = MONTH
    And calling wire() on it returns "1M"

  Scenario: Reject zero or negative amount
    Given the wire string "0d" or "-5m"
    When I parse it via fromWire()
    Then an IllegalArgumentException is thrown

  Scenario: Reject unknown unit suffix
    Given the wire string "5x"
    When I parse it via fromWire()
    Then an IllegalArgumentException is thrown

  Scenario: Reject a null wire string
    Given a null wire string
    When I parse it via fromWire()
    Then a NullPointerException is thrown

  Scenario: Reject a blank, whitespace-padded, or otherwise malformed wire string
    Given a wire string that is empty, whitespace-only, whitespace-padded
      (e.g. "  1d  "), or does not match "<integer>[smhdwMy]" (e.g. "d1", "1.5d")
    When I parse it via fromWire()
    Then an IllegalArgumentException is thrown
```

`fromWire` is strict: the wire format is not trimmed. A leading or trailing space makes the string invalid. `null` is a programmer error and surfaces as `NullPointerException`; every other malformed input throws `IllegalArgumentException`.

## 6. Out of scope for `commons`

These belong elsewhere; do NOT add them to `commons`:

- Any HTTP client, JSON parser, file reader (no I/O)
- Any clock or `Instant.now()` calls (the consumer brings the clock)
- Any DI framework annotation (`@Singleton`, `@Inject`, …)
- Any logger declaration (consumer-side concern)
- Any indicator implementation (SMA, RSI, MACD, …) — those live in `heerwisch-api` and `skuld-api` as needed
- Pattern detection logic (color-change, strong-candle, doji, …) — domain-specific, lives in the consumer
- Any persistence concern (instrument id, source, ingestedAt, computedAt) — consumer-side

## 7. Implementation delegation to Claude Code

Claude Code is responsible for:

- Picking package layout (suggested: `<group>.commons` as the only package, with no internal subpackaging unless the file count justifies it)
- Choosing test framework conventions (JUnit Jupiter is the assumed default; the GWT scenarios above map naturally to `@Test` methods or parameterized tests)
- Writing the canonical constructors with `Objects.requireNonNull`
- Implementing the BigDecimal arithmetic with the prescribed `MathContext.DECIMAL64`
- Writing the parser for the `Timeframe` wire format (regex-based parse, with the documented edge cases as tests)

What Claude Code MUST NOT do unilaterally:

- Add dependencies beyond JDK + JUnit
- Add behavior not described in the Gherkin scenarios above (extra "convenience" methods, helpers, factory shortcuts)
- Use `double` or `float` in any arithmetic
- Eagerly validate OHLC invariants in the canonical constructor (the spec is explicit: lazy validation only)
