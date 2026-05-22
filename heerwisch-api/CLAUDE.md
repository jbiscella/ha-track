# CLAUDE.md — `heerwisch-api` module

This is the nested spec for the `heerwisch-api` module. The repo-wide rules (architecture, API style, code style, dependency edges) live in the root `CLAUDE.md`. This file specifies what is internal to `heerwisch-api`: the public surface (types and port), the field contracts of every spec type, the builder behavior, the validation rules, and the exception hierarchy.

## 0. Goal and scope

`heerwisch-api` is the abstract layer of the plotting library. It defines:

- The immutable spec types that describe a chart (`ChartSpec`, `Indicator`, `Annotation`, `LayoutSpec`). `Series` is consumed from `commons` (see §1.1), not defined here.
- The builder for `ChartSpec`.
- The driver port `ChartRenderer` that consumes a `ChartSpec` and produces a `ChartImage`.
- The checked exception hierarchy rooted at `ChartRenderException`.

Out of scope: any concrete rendering (that's `heerwisch-jfreechart`), any I/O (the driver returns bytes; the caller writes them), any DI annotations.

Dependencies: only `commons` and JDK.

## 1. Public types

### 1.1 `Series` (sealed) — imported from `commons`

`Series` (`sealed interface Series permits OHLCSeries, HASeries`) is **not defined in `heerwisch-api`**. It is a shared `commons` type (see `commons/CLAUDE.md` §1.5), because `nachtkrapp` needs the same type and the cross-module rules forbid `nachtkrapp → heerwisch-api`. `heerwisch-api` consumes it.

| Variant | Fields | Notes |
|---|---|---|
| `OHLCSeries` | `List<OHLCBar> bars` | defensively copied to an immutable list at construction (in `commons`) |
| `HASeries` | `List<HABar> bars` | same |

The `commons` constructor enforces only the defensive copy. The ordering / no-duplicate-`time` / non-empty constraints are NOT enforced by the `Series` record; they are validated eagerly by `ChartSpecBuilder.build()` (rules V2–V4 in §3).

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
| `RSI` | `int period`, `BigDecimal overbought`, `BigDecimal oversold`, `PriceSource priceSource`, `Optional<RsiVisualization> visualization` | `SUBPLOT_1` |
| `ADX` | `int period` | `SUBPLOT_1` |
| `Stochastic` | `int kPeriod`, `int dPeriod`, `int smoothing` | `SUBPLOT_1` |
| `ATR` | `int period` | `SUBPLOT_1` |
| `VolumePane` | (no parameters) | `SUBPLOT_1` |

All `period` and period-like int fields MUST be ≥ 1 (rejected by canonical constructor). All `BigDecimal` ratio fields MUST be > 0. `priceSource` MUST be non-null where present.

`VolumePane` is special: it reads `volume` from the underlying `Series` rather than computing from prices. If the series has no volume on its bars, the chart spec is invalid (rejected eager — see §3).

#### 1.2.1 `RsiVisualization` — optional sub-pane rendering knobs for `RSI`

`RSI` accepts an `Optional<RsiVisualization>` 5th argument that controls renderer-applied visual decisions with no impact on the indicator's numeric values:

| Field | Type | Meaning |
|---|---|---|
| `dangerZones` | `boolean` | When `true`, the driver shades the regions above `overbought` and below `oversold` to highlight the danger zones. When `false` (default), the sub-pane shows the RSI line and the two threshold lines only |

Constants: `RsiVisualization.DEFAULT` (`dangerZones = false`) and `RsiVisualization.DANGER_ZONES_ON` (`dangerZones = true`).

**Backward compatibility:** an explicit 4-argument `RSI(period, overbought, oversold, priceSource)` constructor is preserved as an overload; it delegates to the canonical 5-arg form with `visualization = Optional.empty()`. Existing callers built against the 4-arg signature continue to work unchanged.

**Independent of the RSI threshold-line rendering:** threshold lines at `overbought` and `oversold` are always drawn (per `heerwisch-jfreechart/CLAUDE.md` §7), regardless of `RsiVisualization`. The visualization knob controls only the optional danger-zone shading.

The danger-zone toggle pattern is designed to generalize to other bounded indicators in future PRs (e.g. a `StochasticVisualization`); RSI is the first instance.

### 1.3 `Annotation` (sealed)

```
sealed interface Annotation permits BarHighlight, HorizontalLevel, FibRetracement, PivotPointLevels,
                                    EntryExitMarker, EntryExitMarkerAuto, TimeRangeHighlight
```

The `permits` clause is implicit in source (all six variants are nested records in `Annotation.java`), consistent with `Indicator` and `LayoutSpec`. The full set is enumerated above for documentation and javadoc.

| Variant | Fields | Notes |
|---|---|---|
| `BarHighlight` | `Instant time`, `BigDecimal price`, `String label` | Text annotation at `(time, price)`. The label string is rendered as a text annotation; the driver does NOT draw a glyph for `BarHighlight`. For a directional glyph use `EntryExitMarker`. `time` must exist in the series (rule V7); `label` may be empty |
| `HorizontalLevel` | `BigDecimal price`, `String label`, `LevelStyle style`, `Optional<FillColor> fillColor` | Solid/dashed/dotted horizontal line at a given price. `LevelStyle` enum: `SOLID`, `DASHED`, `DOTTED`. The optional `FillColor` selects a semantic line color (entry `NEUTRAL`, stop-loss `LOSS`, take-profit `WIN`, etc.) per the industry convention used by TradingView and similar platforms; when empty (3-arg constructor) the driver's default reference color is used. A backward-compatible 3-arg constructor `(price, label, style)` is preserved |
| `FibRetracement` | `BigDecimal swingHigh`, `BigDecimal swingLow`, `List<BigDecimal> levels` | Draws Fibonacci levels between two prices. `levels` are fractions in `[0, 1]`. A constant `FibRetracement.STANDARD_LEVELS` = `[0.236, 0.382, 0.5, 0.618, 0.786]` is provided |
| `PivotPointLevels` | `PivotPointVariant variant`, `OHLCBar previousPeriodBar` | Levels computed from the previous period's H/L/C. `PivotPointVariant` enum: `STANDARD`, `CAMARILLA`, `WOODIE` |
| `EntryExitMarker` | `Instant time`, `BigDecimal price`, `MarkerDirection direction`, `GlyphStyle glyphStyle` | A semantic glyph (triangle or arrow) at a specific bar, colored by direction. The caller chooses the Y position via `price`. Use when the marker should sit at a specific Y coordinate — e.g. a target level, limit-order price, indicator alert — rather than be positioned relative to the bar. `time` must exist in the series (rule V16) |
| `EntryExitMarkerAuto` | `Instant time`, `MarkerDirection direction`, `GlyphStyle glyphStyle` | A semantic glyph whose Y position is computed by the renderer from the bar at `time`. `LONG_ENTRY` / `SHORT_EXIT` sit below the bar's low (semantic "up" arrow pointing at the bar from underneath); `LONG_EXIT` / `SHORT_ENTRY` sit above the bar's high. Matches industry convention (TradingView and similar tools): trade markers sit outside the candle so they do not occlude price action. **Recommended** for visualizing trade entries and exits. `time` must exist in the series (rule V16, shared with `EntryExitMarker`) |
| `TimeRangeHighlight` | `Instant startTime`, `Instant endTime`, `FillColor fillColor`, `BigDecimal opacity` | A semi-transparent shaded band over a closed time interval drawn behind the chart. Use for "in-position" bands or alert highlights. `startTime < endTime`, range must overlap the series (rule V17). `opacity` in `[0, 1]` inclusive (rule V18) |

`PivotPointLevels` is computed by the driver from the `previousPeriodBar` (a pure function of three numbers — no pivot detection algorithm, no lookahead). The driver renders the computed levels as horizontal lines.

`MarkerDirection`, `GlyphStyle`, and `FillColor` are closed enums in `org.hatrack.heerwisch.api.spec`:

| Enum | Values | Semantic |
|---|---|---|
| `MarkerDirection` | `LONG_ENTRY`, `LONG_EXIT`, `SHORT_ENTRY`, `SHORT_EXIT` | Renderer convention: `LONG_ENTRY` and `SHORT_EXIT` use the bullish theme color (semantic green); `SHORT_ENTRY` and `LONG_EXIT` use the bearish theme color (semantic red). Named `MarkerDirection` (not `Direction`) to avoid a simple-name clash with `org.hatrack.frauholle.model.Direction` in consumer code that imports both modules |
| `GlyphStyle` | `UP_TRIANGLE`, `DOWN_TRIANGLE`, `ARROW_UP`, `ARROW_DOWN` | Carries shape **and** a scheduled-vs-forced semantic (see §1.3.1). The accompanying `MarkerDirection` carries the long/short direction |
| `FillColor` | `LONG_POSITION`, `SHORT_POSITION`, `NEUTRAL`, `CAUTION`, `WIN`, `LOSS`, `OPEN` | Two intent groups. **Direction-oriented** — `LONG_POSITION` (light green), `SHORT_POSITION` (light red), `NEUTRAL` (light blue/grey), `CAUTION` (light yellow/amber). **Outcome-oriented** (TradingView convention) — `WIN` (light green, semantic positive), `LOSS` (light red, semantic negative), `OPEN` (light grey, still-open trade at backtest end). Consumers pick the variant whose name matches their semantic intent; the enum-name carries the contract. The renderer currently maps `WIN`/`LOSS`/`OPEN` to the same RGBs as `LONG_POSITION`/`SHORT_POSITION`/`NEUTRAL`, but may differentiate the tones in future without API change |

The API exposes no explicit color parameters on the new subtypes — color is renderer-applied from the semantic enum, matching the read-only `ThemeConstants` design of the JFreeChart driver.

#### 1.3.1 `GlyphStyle` semantic contract — scheduled vs forced

The `GlyphStyle` enum carries a documented semantic distinction beyond shape: TRIANGLE for **scheduled** trade events (those triggered by an explicit strategy scenario / authored entry-exit condition), ARROW for **forced** trade events (those triggered by mechanical risk management). Consumers should map their exit categories per the table below.

| Exit type | `GlyphStyle` to use |
|---|---|
| Scenario-triggered entry/exit | `UP_TRIANGLE` (entry-up / long-entry) or `DOWN_TRIANGLE` (exit-down / long-exit) |
| Stop-loss | `ARROW_DOWN` on a long position, `ARROW_UP` on a short position |
| Take-profit | `ARROW_UP` on a long position, `ARROW_DOWN` on a short position |
| Trailing stop | Same as stop-loss (`ARROW_DOWN` on long, `ARROW_UP` on short) |
| Time-based exit | `ARROW` matching the position's exit direction |
| End-of-backtest forced close | `ARROW` matching the position's exit direction |
| Manual / out-of-band intervention | Out of scope; consumer decides |

The renderer's geometry reinforces the distinction (triangles render as compact solid shapes, arrows as a lighter chevron+shaft silhouette). The asymmetry is intentional and is part of the API contract — drivers MUST NOT equalize the visual weight, and consumers can rely on TRIANGLE reading as more prominent than ARROW.

This semantic is independent of `MarkerDirection`: `MarkerDirection` answers "long or short side?" and "entry or exit?"; `GlyphStyle` answers "was this a deliberate strategy decision, or a mechanical forced event?". The two are orthogonal.

### 1.4 `Pane`

Closed enum: `MAIN`, `SUBPLOT_1`, `SUBPLOT_2`, `SUBPLOT_3`, `SUBPLOT_4`, `SUBPLOT_5`, `SUBPLOT_6`, `SUBPLOT_7`, `SUBPLOT_8`.

Eight subplot slots are the hard ceiling. Beyond that, charts become unreadable.

### 1.5 `IndicatorPlacement`

Record with three fields:

| Field | Type |
|---|---|
| `indicator` | `Indicator` (non-null) |
| `pane` | `Pane` (non-null) |
| `label` | `Optional<String>` (non-null; default `Optional.empty()`) |

Produced by `ChartSpecBuilder` to associate each indicator with its pane assignment and an optional label override. Exposed in `ChartSpec.indicators()`. A backward-compatible 2-arg constructor `(indicator, pane)` defaults `label` to `Optional.empty()`. When `label` is present it overrides the driver's auto-derived label (e.g. `"SMA(20)"`) in sub-pane axis labels and legend entries; when empty the auto-derived label is used. Set via the builder's `addIndicator(Indicator, Pane, String label)` overload.

### 1.6 `LayoutSpec` (sealed)

```
sealed interface LayoutSpec permits AutoLayoutSpec, ExplicitLayoutSpec
```

| Variant | Fields | Behavior |
|---|---|---|
| `AutoLayoutSpec` | `int widthPx` (≥ 100), `int heightPx` (≥ 100), `ImageFormat format` | The driver auto-distributes pane heights: main pane 60%, the rest evenly across subplots |
| `ExplicitLayoutSpec` | `int widthPx`, `int heightPx`, `BigDecimal mainPaneHeight`, `Map<Pane, BigDecimal> subplotHeights`, `ImageFormat format` | Caller controls heights; sum must equal 1.0 (within an explicit tolerance of `10^-6`) |

`ImageFormat` is a closed enum (`PNG`, `JPEG`) declaring the output image format. It lives in `heerwisch-api` because it is part of the `LayoutSpec` shape; drivers consume it.

`LayoutSpec.defaults()` returns `AutoLayoutSpec(900, 500, PNG)`. **PNG is the default output format** (since 0.51.0-alpha; it was JPEG through 0.50.0-alpha — see the CHANGELOG behavior-change note).

`LayoutSpec.builder()` is an entry point that asks for auto or explicit and exposes the relevant fields; the format defaults to `PNG` when not set. `LayoutSpecBuilder.build()` produces an `ExplicitLayoutSpec` when a main-pane height or any subplot height is set, and an `AutoLayoutSpec` otherwise; setting subplot heights without a main-pane height is rejected as rule V14 (see §3), not as a raw `NullPointerException`.

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
| `legend` | `List<LegendEntry>` (defensively copied to an immutable list) |

Defensive copy of `bytes` on construction and on access (it's a `byte[]`, not immutable). A backward-compatible 4-arg constructor `(bytes, contentType, widthPx, heightPx)` defaults `legend` to an empty list.

### 1.9 `LegendEntry`

Record describing one rendered series for consumer-side legend rendering:

| Field | Type |
|---|---|
| `placement` | `IndicatorPlacement` (non-null) |
| `label` | `String` (non-null; the placement's label override or the auto-derived indicator label) |
| `rgb` | `int` (plain 24-bit `0xRRGGBB`, no alpha — engine-neutral, keeps `heerwisch-api` free of `java.awt`) |
| `pane` | `Pane` (non-null) |

Exposed via `ChartImage.legend()`. One entry per rendered series in `ChartSpec.indicators()` insertion order; single-line indicators yield one entry per placement, dual-line indicators (`MACD`, `Stochastic`) yield two entries that share the placement but carry distinct `label` and `rgb` (the line and its signal / `%D`). Consumers can group rows by `pane()` for per-pane legend sections. The colors and labels are a deterministic function of the spec; the driver computes them as it renders.

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
| V13 | when the series is an `OHLCSeries`, every `OHLCBar` MUST satisfy its OHLC invariants (the `commons` invariant set — positive prices, `high ≥ low`, `high ≥ open/close`, `low ≤ open/close`, `volume ≥ 0` when present) | a bar with `high < low` |
| V14 | a `LayoutSpecBuilder` with one or more subplot heights set MUST also have a main-pane height set | `LayoutSpec.builder().addSubplotHeight(SUBPLOT_1, 0.4).build()` with no `withMainPaneHeight(...)` |
| V15 | with an `ExplicitLayoutSpec`, every non-`MAIN` `Pane` targeted by an indicator MUST have an entry in `subplotHeights` | an indicator placed at `SUBPLOT_1` while `subplotHeights` has no `SUBPLOT_1` key |
| V16 | every `EntryExitMarker.time` and every `EntryExitMarkerAuto.time` MUST equal some `bar.time` in the series | marker references a non-existent bar (symmetric with V7 for `BarHighlight`). Both variants share V16 — the renderer needs the bar to exist (`EntryExitMarkerAuto` needs its high/low for Y positioning; `EntryExitMarker` is on the same axis as the bar) |
| V17 | every `TimeRangeHighlight` MUST have `startTime` strictly before `endTime` AND its range MUST overlap the series time span | reversed or zero-width range, or a range entirely outside the series. The endpoints are NOT required to be bar times — any `Instant` within the overlap is valid (a trade can end mid-bar) |
| V18 | every `TimeRangeHighlight.opacity` MUST be in `[0, 1]` inclusive | negative opacity, or opacity > 1 |
| V19 | `RSI.overbought` MUST be ≤ 100 | `new Indicator.RSI(14, BigDecimal.valueOf(120), …)` |
| V20 | `RSI.oversold` MUST be ≥ 0 | `new Indicator.RSI(14, …, BigDecimal.valueOf(-5), …)` |
| V21 | `RSI.oversold` MUST be strictly less than `RSI.overbought` | `new Indicator.RSI(14, BigDecimal.valueOf(30), BigDecimal.valueOf(70), …)` (swapped) |

V12 is a soft rule the `heerwisch-api` documents but does not enforce universally — different drivers MAY support different mixings. The default driver `heerwisch-jfreechart` enforces V12 strictly. If a driver does NOT support a given placement, it must throw `UnsupportedFeatureException` (see §4) at render time, not pretend to render.

V13 enforces, at the spec boundary, the `OHLCBar.validateInvariants()` contract that `commons` leaves opt-in. `ChartSpecBuilder.build()` calls `validateInvariants()` for each `OHLCBar`; on `OHLCInvariantViolationException` it throws `InvalidChartSpecException` with `violatedRule = "V13"` and the offending bar's index and time in `offendingValue`. (V13, not V12: V12 is the pre-existing soft overlay-compatibility identifier.) `HASeries` has no equivalent check — `HABar` has no documented invariant set in `commons`.

V14 is enforced by `LayoutSpecBuilder.build()` (not `ChartSpecBuilder.build()`). An `ExplicitLayoutSpec` requires a non-null `mainPaneHeight`; when subplot heights are present but no main-pane height was set, `build()` throws `InvalidChartSpecException` with `violatedRule = "V14"` rather than letting the record's canonical constructor raise a raw `NullPointerException` — keeping layout construction inside the spec-validation error model.

V15 is the converse of V11. V11 rejects a `subplotHeights` entry for a pane no indicator uses; V15 rejects an indicator placed at a non-`MAIN` pane that `subplotHeights` does not size. Together they require an `ExplicitLayoutSpec` to declare a height for exactly the set of non-`MAIN` panes the indicators occupy. `MAIN` is exempt — its height is the dedicated `mainPaneHeight` field, not a `subplotHeights` entry. V15 is enforced by `ChartSpecBuilder.build()` (in the explicit-layout check), so a spec that would be unrenderable for lack of a pane height fails at `build()` rather than at render time.

## 4. Exception hierarchy

All checked. All extend `ChartRenderException` (root).

| Exception | Cause | Carrier fields |
|---|---|---|
| `ChartRenderException` (root) | abstract — never thrown directly | `String message`, `Throwable cause` |
| `InvalidChartSpecException` | Spec malformed (any V1–V11, V13, V14, V15, V16, V17, V18, V19, V20, or V21 rule violation) | `String violatedRule`, `Object offendingValue` (may be null) |
| `UnsupportedFeatureException` | Driver doesn't support a requested feature | `String featureName`, `String driverName` |
| `InsufficientDataException` | Data insufficient for an indicator at render time (escape hatch — preferably caught at build via V6) | `String indicatorName`, `int requiredBars`, `int availableBars` |
| `DriverInternalException` | Underlying driver internal error | `Throwable cause` is mandatory; carries the original exception |

`InvalidChartSpecException` is thrown only from `build()` — `ChartSpecBuilder.build()` (rules V1–V11, V13, V15, V16, V17, V18, V19, V20, V21) and `LayoutSpecBuilder.build()` (rule V14). The other three are thrown only from `ChartRenderer.render()`.

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

  Scenario: Non-positive annotation price fails build
    Given a HorizontalLevel with price ≤ 0, or a FibRetracement with a swing price ≤ 0
    When I addAnnotation(annotation) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V8"

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

  Scenario: OHLC invariant violation in series fails build
    Given an OHLCSeries one of whose bars has high < low
    When I call build()
    Then InvalidChartSpecException is thrown with violatedRule = "V13"

  Scenario: Subplot heights without main pane height is rejected as a domain error, not NPE
    Given a LayoutSpecBuilder with a subplot height set but no main-pane height
    When I call build()
    Then InvalidChartSpecException is thrown with violatedRule = "V14"
    And no NullPointerException is thrown

  Scenario: Indicator at a pane with no declared height fails build
    Given an ExplicitLayoutSpec whose subplotHeights does not size SUBPLOT_1
    And an indicator placed at SUBPLOT_1
    When I withLayout(layout) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V15"

  Scenario: EntryExitMarker at a non-existent bar time fails build
    Given a series whose bar times do not include T
    And an EntryExitMarker with time = T
    When I addAnnotation(marker) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V16"

  Scenario: TimeRangeHighlight with reversed times fails build
    Given a TimeRangeHighlight with startTime ≥ endTime
    When I addAnnotation(range) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V17"

  Scenario: TimeRangeHighlight entirely outside the series time span fails build
    Given a series spanning [B0.time, Bn.time]
    And a TimeRangeHighlight whose entire range is before B0.time or after Bn.time
    When I addAnnotation(range) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V17"

  Scenario: TimeRangeHighlight with out-of-range opacity fails build
    Given a TimeRangeHighlight with opacity < 0 or > 1
    When I addAnnotation(range) and build()
    Then InvalidChartSpecException is thrown with violatedRule = "V18"
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
- Test infrastructure for the Gherkin scenarios above (Cucumber for Java, executed via JUnit Platform; feature files under src/test/resources/features/, step definitions under src/test/java/)

What Claude Code MUST NOT do unilaterally:

- Add a public constructor on `ChartSpec` bypassing the builder
- Add convenience overloads not specified above (no "smart factories", no `ChartSpec.simple(series)`)
- Add fields to indicator records beyond the ones in §1.2
- Allow the `LayoutSpec` heights tolerance to drift from `10^-6`
- Relax sealed declarations to make consumer extension possible
- Use `double` or `float` anywhere a `BigDecimal` is specified
- Add reflective bean wiring, DI annotations, or static mutable state
