# CLAUDE.md — `heerwisch-api` module

This is the nested spec for the `heerwisch-api` module. The repo-wide rules (architecture, API style, code style, dependency edges) live in the root `CLAUDE.md`. This file specifies what is internal to `heerwisch-api`: the public surface (types and port), the field contracts of every spec type, the builder behavior, the validation rules, and the exception hierarchy.

## 0. Goal and scope

`heerwisch-api` is the abstract layer of the plotting library. It defines:

- The immutable spec types that describe a chart (`ChartSpec`, `Series`, `Indicator`, `Annotation`, `LayoutSpec`).
- The builder for `ChartSpec`.
- The driver port `ChartRenderer` that consumes a `ChartSpec` and produces a `ChartImage`.
- The checked exception hierarchy rooted at `ChartRenderException`.

Out of scope: any concrete rendering (that's `heerwisch-jfreechart`), any I/O (the driver returns bytes; the caller writes them), any DI annotations.

Dependencies: only `commons` and JDK.

## 1. Public types

### 1.1 `Series` (sealed)

```
sealed interface Series permits OHLCSeries, HASeries
```

| Variant | Fields | Notes |
|---|---|---|
| `OHLCSeries` | `List<OHLCBar> bars` | bars must be ordered ascending by `time`, no duplicate `time`, non-empty |
| `HASeries` | `List<HABar> bars` | same ordering constraints |

The list is defensively copied at record construction (immutability is observable: a caller's later mutation of their list MUST NOT affect the record).

### 1.2 `Indicator` (sealed)

```
sealed interface Indicator permits SMA, EMA, MACD, RSI, BollingerBands, ADX, Stochastic, ATR, VolumePane
```

| Variant | Fields | Default `Pane` |
|---|---|---|
| `SMA` | `int period`, `PriceSource priceSource` | `MAIN` |
| `EMA` | `int period`, `PriceSource priceSource` | `MAIN` |
| `BollingerBands` | `int period`, `BigDecimal stdDevMultiplier`, `PriceSource priceSource` | `MAIN` |
| `MACD` | `int fastPeriod`, `int slowPeriod`, `int signalPeriod`, `PriceSource priceSource` | `SUBPLOT_1` |
| `RSI` | `int period`, `BigDecimal overbought`, `BigDecimal oversold`, `PriceSource priceSource` | `SUBPLOT_1` |
| `ADX` | `int period` | `SUBPLOT_1` |
| `Stochastic` | `int kPeriod`, `int dPeriod`, `int smoothing` | `SUBPLOT_1` |
| `ATR` | `int period` | `SUBPLOT_1` |
| `VolumePane` | (no parameters) | `SUBPLOT_1` |

All `period` and period-like int fields MUST be ≥ 1 (rejected by canonical constructor). All `BigDecimal` ratio fields MUST be > 0. `priceSource` MUST be non-null where present.

`VolumePane` is special: it reads `volume` from the underlying `Series` rather than computing from prices. If the series has no volume on its bars, the chart spec is invalid (rejected eager — see §3).

### 1.3 `Annotation` (sealed)

```
sealed interface Annotation permits BarHighlight, HorizontalLevel, FibRetracement, PivotPointLevels
```

| Variant | Fields | Notes |
|---|---|---|
| `BarHighlight` | `Instant time`, `BigDecimal price`, `String label` | Marks a single bar with a labeled arrow at `(time, price)`. `time` must exist in the series; `label` may be empty |
| `HorizontalLevel` | `BigDecimal price`, `String label`, `LevelStyle style` | Solid/dashed/dotted horizontal line at a given price. `LevelStyle` enum: `SOLID`, `DASHED`, `DOTTED` |
| `FibRetracement` | `BigDecimal swingHigh`, `BigDecimal swingLow`, `List<BigDecimal> levels` | Draws Fibonacci levels between two prices. `levels` are fractions in `[0, 1]`. A constant `FibRetracement.STANDARD_LEVELS` = `[0.236, 0.382, 0.5, 0.618, 0.786]` is provided |
| `PivotPointLevels` | `PivotPointVariant variant`, `OHLCBar previousPeriodBar` | Levels computed from the previous period's H/L/C. `PivotPointVariant` enum: `STANDARD`, `CAMARILLA`, `WOODIE` |

`PivotPointLevels` is computed by the driver from the `previousPeriodBar` (a pure function of three numbers — no pivot detection algorithm, no lookahead). The driver renders the computed levels as horizontal lines.

### 1.4 `Pane`

Closed enum: `MAIN`, `SUBPLOT_1`, `SUBPLOT_2`, `SUBPLOT_3`, `SUBPLOT_4`, `SUBPLOT_5`, `SUBPLOT_6`, `SUBPLOT_7`, `SUBPLOT_8`.

Eight subplot slots are the hard ceiling. Beyond that, charts become unreadable.

### 1.5 `IndicatorPlacement`

Record with two fields:

| Field | Type |
|---|---|
| `indicator` | `Indicator` (non-null) |
| `pane` | `Pane` (non-null) |

Internal type produced by `ChartSpecBuilder` to associate each indicator with its pane assignment. Exposed in `ChartSpec.indicators()`.

### 1.6 `LayoutSpec` (sealed)

```
sealed interface LayoutSpec permits AutoLayoutSpec, ExplicitLayoutSpec
```

| Variant | Fields | Behavior |
|---|---|---|
| `AutoLayoutSpec` | `int widthPx` (≥ 100), `int heightPx` (≥ 100) | The driver auto-distributes pane heights: main pane 60%, the rest evenly across subplots |
| `ExplicitLayoutSpec` | `int widthPx`, `int heightPx`, `BigDecimal mainPaneHeight`, `Map<Pane, BigDecimal> subplotHeights` | Caller controls heights; sum must equal 1.0 (within an explicit tolerance of `10^-6`) |

`LayoutSpec.defaults()` returns `AutoLayoutSpec(900, 500)`.

`LayoutSpec.builder()` is an entry point that asks for auto or explicit and exposes the relevant fields.

### 1.7 `ChartSpec`

Immutable record exposing:

| Accessor | Type |
|---|---|
| `series()` | `Series` (non-null) |
| `indicators()` | `List<IndicatorPlacement>` (defensively copied; may be empty) |
| `annotations()` | `List<Annotation>` (defensively copied; may be empty) |
| `layout()` | `LayoutSpec` (non-null) |

No public constructor. Built only via `ChartSpec.builder()`.

### 1.8 `ChartImage`

Immutable record exposing:

| Field | Type |
|---|---|
| `bytes` | `byte[]` |
| `contentType` | `String` (e.g. `"image/png"`) |
| `widthPx` | `int` |
| `heightPx` | `int` |

Defensive copy of `bytes` on construction and on access (it's a `byte[]`, not immutable).

## 2. Builder API

### 2.1 `ChartSpec.builder()`

Returns a fresh `ChartSpecBuilder`. The builder is mutable; the produced `ChartSpec` is not.

Builder methods (naming follows root §4.3):

| Method | Effect |
|---|---|
| `withSeries(Series s)` | Sets the series (cardinality 1; replaces prior value) |
| `withLayout(LayoutSpec l)` | Sets the layout (cardinality 1; replaces prior value) |
| `addIndicator(Indicator i)` | Appends an indicator placed at `defaultPane(i)` |
| `addIndicator(Indicator i, Pane p)` | Appends an indicator placed at an explicit `Pane` |
| `addAnnotation(Annotation a)` | Appends an annotation |
| `build()` | Validates eagerly; returns `ChartSpec` or throws `InvalidChartSpecException` |

If `withLayout` is never called, `build()` defaults to `LayoutSpec.defaults()`.

If `withSeries` is never called, `build()` throws `InvalidChartSpecException`.

### 2.2 `defaultPane(Indicator)` mapping

Defined in §1.2 ("Default `Pane`" column). The static method `ChartSpecBuilder.defaultPaneFor(Indicator)` is exposed for callers who want to inspect the mapping without constructing.

## 3. Validation rules — `InvalidChartSpecException`

All of these are rejected eagerly in `build()`. The exception carries a `String violatedRule` identifying which rule.

| # | Rule | Violation example |
|---|---|---|
| V1 | `series` MUST be set | `builder.build()` with no `withSeries` |
| V2 | series MUST be non-empty | `OHLCSeries(emptyList())` |
| V3 | series bars MUST be ordered ascending by `time` | second bar's time ≤ first bar's time |
| V4 | series bars MUST have unique `time` | two bars with identical `time` |
| V5 | indicator `priceSource` MUST be compatible with series type | `SMA(20, HA_CLOSE)` with `OHLCSeries`, or `SMA(20, CLOSE)` with `HASeries` |
| V6 | series MUST have enough bars for every indicator placed | e.g. `SMA(period=20)` requires ≥ 20 bars |
| V7 | every `BarHighlight.time` MUST equal some `bar.time` in the series | annotation references a non-existent bar |
| V8 | every `HorizontalLevel.price`, every `FibRetracement` level price MUST be > 0 | negative price |
| V9 | `VolumePane` requires the series bars to carry volume (`Optional` populated) | `OHLCSeries` whose bars have `volume = Optional.empty()` |
| V10 | `ExplicitLayoutSpec` heights MUST sum to 1.0 ± 10^-6 | `mainPaneHeight + sum(subplotHeights) ≠ 1.0` |
| V11 | every `Pane` referenced in `ExplicitLayoutSpec.subplotHeights` MUST have at least one indicator placed there | declaring a height for `SUBPLOT_3` while no indicator targets that pane |
| V12 | indicators placed at `MAIN` MUST be overlay-compatible | `RSI` placed at `MAIN` (RSI's value range is unbounded relative to price) — driver-flexible; if a future driver supports this, the rule relaxes per-driver |

V12 is a soft rule the `heerwisch-api` documents but does not enforce universally — different drivers MAY support different mixings. The default driver `heerwisch-jfreechart` enforces V12 strictly. If a driver does NOT support a given placement, it must throw `UnsupportedFeatureException` (see §4) at render time, not pretend to render.

## 4. Exception hierarchy

All checked. All extend `ChartRenderException` (root).

| Exception | Cause | Carrier fields |
|---|---|---|
| `ChartRenderException` (root) | abstract — never thrown directly | `String message`, `Throwable cause` |
| `InvalidChartSpecException` | Spec malformed (any V1–V11 rule violation) | `String violatedRule`, `Object offendingValue` (may be null) |
| `UnsupportedFeatureException` | Driver doesn't support a requested feature | `String featureName`, `String driverName` |
| `InsufficientDataException` | Data insufficient for an indicator at render time (escape hatch — preferably caught at build via V6) | `String indicatorName`, `int requiredBars`, `int availableBars` |
| `DriverInternalException` | Underlying driver internal error | `Throwable cause` is mandatory; carries the original exception |

`InvalidChartSpecException` is thrown only from `build()`. The other three are thrown only from `ChartRenderer.render()`.

## 5. Port: `ChartRenderer`

```
ChartImage render(ChartSpec spec) throws ChartRenderException
```

Contract:

| Aspect | Required behavior |
|---|---|
| Input | `ChartSpec` non-null. Null input throws `NullPointerException` (programmer error, not a render error) |
| Output | `ChartImage` non-null on success |
| Thread-safety | NOT required to be thread-safe. The contract is single-threaded; concurrent callers MUST serialize externally. Drivers MAY document thread-safety as an extension |
| Idempotency | Calling `render(spec)` twice with the same spec MUST produce byte-identical output (modulo fonts loaded by the JVM and any non-determinism in the underlying lib). Drivers MAY relax this if they document why |
| Side effects | None. No filesystem write, no logging at INFO+, no network |
| Resource cleanup | Driver MUST not leak file handles or graphics contexts; render is a function call with bounded resource use |

## 6. Block 1 — Builder validation

```gherkin
Feature: ChartSpecBuilder eager validation

  Scenario: Missing series fails build
    Given a builder with no series set
    When I call build()
    Then InvalidChartSpecException is thrown with violatedRule = "V1"

  Scenario: Empty series fails build
    Given a builder with series = OHLCSeries(emptyList())
    When I call build()
    Then InvalidChartSpecException is thrown with violatedRule = "V2"

  Scenario: Out-of-order series fails build
    Given a series whose second bar has time ≤ first bar's time
    When I call build()
    Then InvalidChartSpecException is thrown with violatedRule = "V3"

  Scenario: Duplicate time in series fails build
    Given a series with two bars sharing the same time
    When I call build()
    Then InvalidChartSpecException is thrown with violatedRule = "V4"

  Scenario: HA priceSource on OHLC series fails build
    Given an OHLCSeries
    And an SMA with priceSource = HA_CLOSE
    When I addIndicator(sma) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V5"

  Scenario: OHLC priceSource on HA series fails build
    Given an HASeries
    And an SMA with priceSource = CLOSE
    When I addIndicator(sma) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V5"

  Scenario: Insufficient bars for indicator fails build
    Given a series of 5 bars
    And an SMA with period = 20
    When I addIndicator(sma) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V6"

  Scenario: BarHighlight at non-existent time fails build
    Given a series whose bar times do not include T
    And a BarHighlight with time = T
    When I addAnnotation(highlight) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V7"

  Scenario: VolumePane on series without volume fails build
    Given an OHLCSeries whose bars all have volume = Optional.empty()
    And a VolumePane indicator
    When I addIndicator(volumePane) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V9"

  Scenario: Explicit layout heights not summing to 1.0 fails build
    Given an ExplicitLayoutSpec with mainPaneHeight = 0.5 and one subplotHeight = 0.3
    When I withLayout(layout) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V10"

  Scenario: Explicit layout declares height for unused pane fails build
    Given an ExplicitLayoutSpec with a height entry for SUBPLOT_3
    And no indicator placed at SUBPLOT_3
    When I withLayout(layout) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V11"
```

## 7. Block 2 — Default pane assignment

```gherkin
Feature: Default pane assignment for indicators

  Scenario: Overlay indicators default to MAIN
    When I addIndicator(SMA(20, CLOSE)) on an OHLCSeries-based builder
    Then the built ChartSpec has an IndicatorPlacement with indicator = SMA and pane = MAIN

    When I addIndicator(EMA(50, CLOSE)) on an OHLCSeries-based builder
    Then the placement has pane = MAIN

    When I addIndicator(BollingerBands(20, 2.0, CLOSE)) on an OHLCSeries-based builder
    Then the placement has pane = MAIN

  Scenario: Subplot indicators default to SUBPLOT_1
    When I addIndicator(RSI(14, 70, 30, CLOSE))
    Then the placement has pane = SUBPLOT_1

    When I addIndicator(MACD(12, 26, 9, CLOSE))
    Then the placement has pane = SUBPLOT_1

    When I addIndicator(ADX(14))
    Then the placement has pane = SUBPLOT_1

    When I addIndicator(Stochastic(14, 3, 3))
    Then the placement has pane = SUBPLOT_1

    When I addIndicator(ATR(14))
    Then the placement has pane = SUBPLOT_1

    When I addIndicator(VolumePane())
    Then the placement has pane = SUBPLOT_1

  Scenario: Explicit pane overrides default
    When I addIndicator(RSI(14, 70, 30, CLOSE), SUBPLOT_2)
    Then the placement has pane = SUBPLOT_2

  Scenario: Multiple indicators can share a pane
    When I addIndicator(SMA(20, CLOSE)) and addIndicator(EMA(50, CLOSE))
    Then both placements have pane = MAIN
    And the ChartSpec.indicators() has both entries in insertion order
```

## 8. Block 3 — Layout behavior

```gherkin
Feature: Layout spec

  Scenario: Defaults provide auto layout
    When I call LayoutSpec.defaults()
    Then the result is an AutoLayoutSpec with widthPx = 900 and heightPx = 500

  Scenario: Builder omits layout, defaults applied
    Given a valid ChartSpecBuilder with series and indicators set, no withLayout call
    When I call build()
    Then the resulting ChartSpec has layout = LayoutSpec.defaults()

  Scenario: Auto-distribution semantics documented in API
    Given an AutoLayoutSpec
    And N subplot panes referenced by indicators
    Then the driver renders main pane at 60% height
    And distributes the remaining 40% evenly across the N subplots

  Scenario: Explicit layout heights exactly summing to 1.0 passes
    Given mainPaneHeight = 0.6 and subplotHeights = {SUBPLOT_1: 0.4}
    And an indicator placed at SUBPLOT_1
    When I withLayout(layout) and build()
    Then build() succeeds
```

## 9. Block 4 — Render contract

```gherkin
Feature: ChartRenderer port contract

  Scenario: Render returns a ChartImage on a valid spec
    Given a valid ChartSpec
    When I call driver.render(spec)
    Then the result is a non-null ChartImage
    And ChartImage.bytes is non-empty
    And ChartImage.contentType is a recognized MIME type (e.g. "image/png")
    And ChartImage.widthPx > 0 and heightPx > 0

  Scenario: Null spec is a programmer error, not a render error
    When I call driver.render(null)
    Then NullPointerException is thrown
    And NO ChartRenderException is thrown

  Scenario: Driver-unsupported feature throws UnsupportedFeatureException
    Given a ChartSpec containing a feature the chosen driver does not support
    When I call driver.render(spec)
    Then UnsupportedFeatureException is thrown
    And the exception names the feature and the driver

  Scenario: Driver internal failure wraps original cause
    Given a ChartSpec whose render triggers an internal error in the underlying lib
    When I call driver.render(spec)
    Then DriverInternalException is thrown
    And exception.getCause() is the original underlying exception

  Scenario: Idempotency
    Given a valid ChartSpec
    When I call driver.render(spec) twice in succession on the same driver instance
    Then both ChartImage outputs are byte-identical (modulo documented exceptions)
```

## 10. Out of scope for `heerwisch-api`

- Any rendering logic (that is `heerwisch-jfreechart` and any future driver)
- Any file I/O — `ChartImage` returns bytes; the caller writes them
- Any DI framework annotation
- Any custom indicator authored by the consumer (sealed hierarchy is closed)
- Indicator value calculations — the driver computes; the API only declares which indicator goes where with which parameters
- Color, theme, fonts — driver concerns; the API does not let the consumer specify them in v1

## 11. Implementation delegation to Claude Code

Claude Code is responsible for:

- Package layout (suggested: a small set of subpackages — `spec`, `port`, `error` — kept inside `<group>.heerwisch.api`)
- Writing canonical constructors with `Objects.requireNonNull` and field-level range checks
- Implementing defensive copies of collections in `record` canonical constructors and accessors
- Implementing `ChartSpecBuilder.build()` with the V1–V11 validation rules
- Implementing `defaultPaneFor(Indicator)` using exhaustive `switch` over the sealed hierarchy
- Test infrastructure for the Gherkin scenarios above (JUnit Jupiter assumed)

What Claude Code MUST NOT do unilaterally:

- Add a public constructor on `ChartSpec` bypassing the builder
- Add convenience overloads not specified above (no "smart factories", no `ChartSpec.simple(series)`)
- Add fields to indicator records beyond the ones in §1.2
- Allow the `LayoutSpec` heights tolerance to drift from `10^-6`
- Relax sealed declarations to make consumer extension possible
- Use `double` or `float` anywhere a `BigDecimal` is specified
- Add reflective bean wiring, DI annotations, or static mutable state
