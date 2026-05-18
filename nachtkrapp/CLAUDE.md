# CLAUDE.md — `nachtkrapp` module

This is the nested spec for the `nachtkrapp` module. The repo-wide rules (architecture, API style, code style, dependency edges) live in the root `CLAUDE.md`. This file specifies what is internal to `nachtkrapp`: the public types (rules and matches), the builder, the validation rules, the entry-point port, and the exception hierarchy.

`nachtkrapp` is a **single module**: there is no separate API artifact and no "driver" abstraction. The module contains both the type definitions and the rule-based detection implementation.

## 0. Goal and scope

`nachtkrapp` is the pattern detection library. It defines:

- The immutable **`DetectionRule`** sealed hierarchy — what to look for.
- The immutable **`PatternMatch`** sealed hierarchy — what was found, with diagnostic payload.
- The **`DetectionSpec`** that bundles a series with a set of rules to apply.
- The fluent builder for `DetectionSpec`.
- The entry-point port **`PatternDetector`** — exposed as an interface so the consumer can mock it for its own tests. The module provides a single concrete implementation of this port.
- The checked exception hierarchy rooted at `DetectionException`.
- The internal detection logic that computes matches from a spec.

Out of scope: any I/O, any DI annotations, any compound rule DSL (consumers compose primitive matches in Java), any audit/persistence of emitted matches, multi-timeframe orchestration (consumer-side per repo-wide principle).

Dependencies: `commons`, `indicators`, and JDK. The indicator calculators (SMA, EMA, RSI, MACD) were extracted into the shared `indicators` module in v1.1; `nachtkrapp` now consumes them rather than carrying its own copy (see root `CLAUDE.md` §6).

## 1. Scope of v1 detection

The detection scope is **HA patterns + MA / RSI / MACD primitives**. These are the building blocks the consumer combines with Java boolean logic to compose accuracy-improving notification rules (e.g. "BullishReversal AND PriceAboveMA(50) AND NOT RSIOverbought" → alert).

Out of v1: candlestick classical patterns (hammer, engulfing, etc.), chart patterns (head & shoulders, triangles), Fibonacci patterns, harmonic patterns, divergences.

## 2. Public types

### 2.1 `MAType`

Closed enum: `SMA`, `EMA`. Additional MA types reserved for future minor versions.

### 2.2 `DetectionRule` (sealed)

```
sealed interface DetectionRule permits
    HAColorChangeRule, HAStrongCandleRule, HADojiRule,
    PriceVsMARule, PriceMACrossRule,
    RSIThresholdRule, RSILevel50CrossRule,
    MACDSignalCrossRule, MACDZeroCrossRule
```

Each rule is an immutable record. Each rule declares (a) its parameters and (b) the minimum bars required to produce its first match.

Rule record canonical constructors perform `Objects.requireNonNull` null-checks only. Parameter range and relationship constraints (e.g. `period >= 1`, `overbought` in `(50, 100)`, `slow > fast`) are NOT enforced by the constructor — they are validated eagerly by `DetectionSpecBuilder.build()` as rule V7. This is required by the Block 1 scenarios, which construct an out-of-range rule and expect the violation to surface at `build()` rather than at rule construction.

#### 2.2.1 HA family

| Rule | Fields | Min bars | Emits |
|---|---|---|---|
| `HAColorChangeRule` | `int minStreakLength` (≥ 1) | `minStreakLength + 1` | `HABullishReversal` or `HABearishReversal` |
| `HAStrongCandleRule` | `BigDecimal wickTolerance` (≥ 0), `BigDecimal minBodyRatio` (in (0, 1]) | 1 | `HABullishStrong` or `HABearishStrong` |
| `HADojiRule` | `BigDecimal maxBodyRatio` (in (0, 1]) | 1 | `HADoji` |

HA family rules require the series to be `HASeries`. Applied to `OHLCSeries` they are rejected eagerly (V5).

#### 2.2.2 Moving Average family

| Rule | Fields | Min bars | Emits |
|---|---|---|---|
| `PriceVsMARule` | `MAType maType`, `int period` (≥ 1), `PriceSource priceSource` | `period` | `PriceAboveMA` or `PriceBelowMA` on every bar (state) |
| `PriceMACrossRule` | `MAType maType`, `int period` (≥ 1), `PriceSource priceSource` | `period + 1` | `PriceCrossedAboveMA` or `PriceCrossedBelowMA` on the bar of the cross (event) |

#### 2.2.3 RSI family

| Rule | Fields | Min bars | Emits |
|---|---|---|---|
| `RSIThresholdRule` | `int period` (≥ 1), `BigDecimal overbought` (in (50, 100)), `BigDecimal oversold` (in (0, 50)), `PriceSource priceSource` | `period + 1` | `RSIOverbought`/`RSIOversold` on bars where the condition holds (state); `RSIExitedOverbought`/`RSIExitedOversold` on the bar of the exit (event) |
| `RSILevel50CrossRule` | `int period` (≥ 1), `PriceSource priceSource` | `period + 2` | `RSICrossedAbove50` or `RSICrossedBelow50` on the bar of the cross (event) |

#### 2.2.4 MACD family

| Rule | Fields | Min bars | Emits |
|---|---|---|---|
| `MACDSignalCrossRule` | `int fastPeriod`, `int slowPeriod`, `int signalPeriod` (all ≥ 1, slow > fast), `PriceSource priceSource` | `slowPeriod + signalPeriod + 1` | `MACDBullishCross` or `MACDBearishCross` on the bar of the cross (event) |
| `MACDZeroCrossRule` | `int fastPeriod`, `int slowPeriod`, `int signalPeriod` (all ≥ 1, slow > fast), `PriceSource priceSource` | `slowPeriod + 1` | `MACDCrossedAboveZero` or `MACDCrossedBelowZero` on the bar of the cross (event) |

### 2.3 `PatternMatch` (sealed)

```
sealed interface PatternMatch permits
    HABullishReversal, HABearishReversal,
    HABullishStrong, HABearishStrong, HADoji,
    PriceAboveMA, PriceBelowMA, PriceCrossedAboveMA, PriceCrossedBelowMA,
    RSIOverbought, RSIOversold, RSIExitedOverbought, RSIExitedOversold,
    RSICrossedAbove50, RSICrossedBelow50,
    MACDBullishCross, MACDBearishCross,
    MACDCrossedAboveZero, MACDCrossedBelowZero
```

Every match carries:

| Common accessor | Type | Meaning |
|---|---|---|
| `time()` | `Instant` | Time of the bar that triggered the match |
| `flavor()` | `MatchFlavor` enum: `EVENT`, `STATE` | `EVENT` for discrete transitions, `STATE` for continuous conditions |
| `timeframe()` | `Optional<Timeframe>` | Timeframe tag propagated from `DetectionSpec`. Empty if not specified. Enables the consumer to mix results from multiple `detect()` calls without losing provenance |

Plus variant-specific diagnostic payload:

#### 2.3.1 HA match payloads

| Variant | Extra fields | Flavor |
|---|---|---|
| `HABullishReversal` | `int streakLength`, `HABar bar` | `EVENT` |
| `HABearishReversal` | `int streakLength`, `HABar bar` | `EVENT` |
| `HABullishStrong` | `BigDecimal bodyRatio`, `BigDecimal lowerWickRatio`, `HABar bar` | `EVENT` |
| `HABearishStrong` | `BigDecimal bodyRatio`, `BigDecimal upperWickRatio`, `HABar bar` | `EVENT` |
| `HADoji` | `BigDecimal bodyRatio`, `HABar bar` | `EVENT` |

#### 2.3.2 MA match payloads

| Variant | Extra fields | Flavor |
|---|---|---|
| `PriceAboveMA` | `BigDecimal price`, `BigDecimal maValue`, `MAType maType`, `int period` | `STATE` |
| `PriceBelowMA` | `BigDecimal price`, `BigDecimal maValue`, `MAType maType`, `int period` | `STATE` |
| `PriceCrossedAboveMA` | `BigDecimal price`, `BigDecimal maValue`, `MAType maType`, `int period` | `EVENT` |
| `PriceCrossedBelowMA` | `BigDecimal price`, `BigDecimal maValue`, `MAType maType`, `int period` | `EVENT` |

#### 2.3.3 RSI match payloads

| Variant | Extra fields | Flavor |
|---|---|---|
| `RSIOverbought` | `BigDecimal rsiValue`, `BigDecimal threshold`, `int period` | `STATE` |
| `RSIOversold` | `BigDecimal rsiValue`, `BigDecimal threshold`, `int period` | `STATE` |
| `RSIExitedOverbought` | `BigDecimal rsiValue`, `BigDecimal threshold`, `int period` | `EVENT` |
| `RSIExitedOversold` | `BigDecimal rsiValue`, `BigDecimal threshold`, `int period` | `EVENT` |
| `RSICrossedAbove50` | `BigDecimal rsiValue`, `int period` | `EVENT` |
| `RSICrossedBelow50` | `BigDecimal rsiValue`, `int period` | `EVENT` |

#### 2.3.4 MACD match payloads

| Variant | Extra fields | Flavor |
|---|---|---|
| `MACDBullishCross` | `BigDecimal macdValue`, `BigDecimal signalValue`, `int fastPeriod`, `int slowPeriod`, `int signalPeriod` | `EVENT` |
| `MACDBearishCross` | `BigDecimal macdValue`, `BigDecimal signalValue`, `int fastPeriod`, `int slowPeriod`, `int signalPeriod` | `EVENT` |
| `MACDCrossedAboveZero` | `BigDecimal macdValue`, `int fastPeriod`, `int slowPeriod`, `int signalPeriod` | `EVENT` |
| `MACDCrossedBelowZero` | `BigDecimal macdValue`, `int fastPeriod`, `int slowPeriod`, `int signalPeriod` | `EVENT` |

### 2.4 `DetectionSpec`

Immutable record exposing:

| Accessor | Type |
|---|---|
| `series()` | `Series` (non-null, from `commons`) |
| `rules()` | `List<DetectionRule>` (defensively copied; non-empty) |
| `timeframe()` | `Optional<Timeframe>` (metadata tag; propagated to every match emitted from this spec) |

No public constructor. Built only via `DetectionSpec.builder()`.

### 2.5 `DetectionResult`

Immutable record exposing:

| Accessor | Type |
|---|---|
| `matches()` | `List<PatternMatch>` (defensively copied; may be empty) |

Matches are returned ordered ascending by `time()`. Within the same `time()`, the order is stable but unspecified.

## 3. Builder API

### 3.1 `DetectionSpec.builder()`

| Method | Effect |
|---|---|
| `withSeries(Series s)` | Sets the series (replaces prior value) |
| `withTimeframe(Timeframe tf)` | Sets the metadata timeframe tag (replaces prior value) |
| `addRule(DetectionRule r)` | Appends a rule |
| `addAllRules(Collection<DetectionRule> rs)` | Appends multiple rules |
| `build()` | Validates eagerly; returns `DetectionSpec` or throws `InvalidDetectionSpecException` |

If `withSeries` is never called, `build()` throws `InvalidDetectionSpecException`.

If no rules are added, `build()` throws `InvalidDetectionSpecException`.

`withTimeframe` is optional — if never called, all emitted matches have `timeframe() = Optional.empty()`.

## 4. Validation rules — `InvalidDetectionSpecException`

All of these are rejected eagerly in `build()`. The exception carries a `String violatedRule` identifying which rule.

| # | Rule | Violation example |
|---|---|---|
| V1 | `series` MUST be set | `builder.build()` with no `withSeries` |
| V2 | series MUST be non-empty | empty bar list |
| V3 | series bars MUST be ordered ascending by `time` | second bar's time ≤ first bar's time |
| V4 | series bars MUST have unique `time` | two bars with identical `time` |
| V5 | rule's required series type MUST match the actual series type | HA family rule applied to `OHLCSeries`; OHLC `priceSource` (`CLOSE`, `OPEN`, …) on `HASeries`; HA `priceSource` (`HA_CLOSE`, …) on `OHLCSeries` |
| V6 | series MUST have enough bars for every rule | series of 5 bars + `PriceVsMARule(SMA, 20, CLOSE)` is rejected (needs ≥ 20) |
| V7 | rule parameters MUST satisfy their declared constraints | `RSIThresholdRule(period=14, overbought=120, oversold=30, ...)` → overbought out of range |
| V8 | rules list MUST be non-empty | `builder.withSeries(s).build()` |
| V9 | duplicate rule entries are forbidden | `addRule(SMA20)` twice (same parameter tuple) |
| V10 | when the series is an `OHLCSeries`, every `OHLCBar` MUST satisfy its OHLC invariants (the `commons` invariant set — positive prices, `high ≥ low`, `high ≥ open/close`, `low ≤ open/close`, `volume ≥ 0` when present) | a bar with `high < low` |

V9 is forbidden because the same rule applied twice produces redundant matches. If a consumer wants two MAs (e.g. SMA(20) and SMA(50)) that is fine — they are distinct rules with different `period`.

V10 enforces, at the spec boundary, the `OHLCBar.validateInvariants()` contract that `commons` leaves opt-in. The builder calls `validateInvariants()` for each `OHLCBar`; on `OHLCInvariantViolationException` it throws `InvalidDetectionSpecException` with `violatedRule = "V10"` and the offending bar's index and time in `offendingValue`. `HASeries` has no equivalent check — `HABar` has no documented invariant set in `commons` — so for an `HASeries` V10 is a no-op.

## 5. Exception hierarchy

All checked. All extend `DetectionException` (root).

| Exception | Cause | Carrier fields |
|---|---|---|
| `DetectionException` (root) | abstract — never thrown directly | `String message`, `Throwable cause` |
| `InvalidDetectionSpecException` | Spec malformed (any V1–V10 violation) | `String violatedRule`, `Object offendingValue` (may be null) |
| `InsufficientDataException` | Data insufficient for a rule at runtime (escape hatch — preferably caught at build via V6) | `String ruleClassName`, `int requiredBars`, `int availableBars` |
| `DetectionInternalException` | Internal error inside the detection logic | `Throwable cause` is mandatory |

`InvalidDetectionSpecException` is thrown only from `build()`. The other two are thrown only from `PatternDetector.detect()`.

## 6. Entry point: `PatternDetector`

```
DetectionResult detect(DetectionSpec spec) throws DetectionException
```

`PatternDetector` is an interface, not a class. The module provides a concrete implementation; the consumer programs against the interface to enable testing/mocking.

Contract:

| Aspect | Required behavior |
|---|---|
| Input | `DetectionSpec` non-null. Null input throws `NullPointerException` |
| Output | `DetectionResult` non-null on success; `matches()` ordered ascending by `time()` |
| Thread-safety | **REQUIRED**. The detector MUST be stateless or use only thread-safe internal state. The same instance MUST be safe to call concurrently from multiple threads with different specs |
| Idempotency | Calling `detect(spec)` twice with the same spec MUST return equal `DetectionResult` objects (by value equality on `matches()`) |
| Determinism | For the same spec and the same module version, output is fully deterministic. No randomization, no clock dependence |
| Lookahead-safety | A match at time `t` MUST only depend on bars at times `≤ t`. This is the repo-wide invariant; the detector is the primary place it's enforced |
| Side effects | None. No filesystem write, no network, no logging at INFO+ |

## 7. Block 1 — Builder validation

```gherkin
Feature: DetectionSpecBuilder eager validation

  Scenario: Missing series fails build
    Given a builder with no series set
    When I call build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V1"

  Scenario: Empty series fails build
    Given a builder with series = OHLCSeries(emptyList())
    When I call build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V2"

  Scenario: Out-of-order series fails build
    Given a series whose second bar has time ≤ first bar's time
    When I call build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V3"

  Scenario: Duplicate time in series fails build
    Given a series with two bars sharing the same time
    When I call build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V4"

  Scenario: HA rule on OHLC series fails build
    Given an OHLCSeries
    And an HAColorChangeRule(minStreakLength = 3)
    When I addRule(rule) and build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V5"

  Scenario: HA priceSource on OHLC series fails build
    Given an OHLCSeries
    And a PriceVsMARule(SMA, 20, HA_CLOSE)
    When I addRule(rule) and build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V5"

  Scenario: OHLC priceSource on HA series fails build
    Given an HASeries
    And a PriceVsMARule(SMA, 20, CLOSE)
    When I addRule(rule) and build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V5"

  Scenario: Insufficient bars fails build
    Given an HASeries of 5 bars
    And a PriceVsMARule(SMA, 20, HA_CLOSE)
    When I addRule(rule) and build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V6"

  Scenario: Out-of-range RSI threshold fails build
    Given any series
    And an RSIThresholdRule(period = 14, overbought = 120, oversold = 30, priceSource = CLOSE)
    When I addRule(rule) and build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V7"

  Scenario: Empty rules fails build
    Given a builder with series set and no rules added
    When I call build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V8"

  Scenario: Duplicate rule fails build
    Given the same PriceVsMARule(SMA, 20, CLOSE) added twice
    When I call build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V9"

  Scenario: OHLC invariant violation in series is rejected by builder
    Given an OHLCSeries one of whose bars has high < low
    And a valid PriceVsMARule
    When I call build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V10"

  Scenario: MACD with slowPeriod ≤ fastPeriod fails build
    Given an MACDSignalCrossRule(fast = 26, slow = 12, signal = 9, priceSource = CLOSE)
    When I addRule(rule) and build()
    Then InvalidDetectionSpecException is thrown with violatedRule = "V7"

  Scenario: Timeframe tag is optional
    Given a valid builder with series and rules set, no withTimeframe call
    When I call build()
    Then build() succeeds
    And spec.timeframe() = Optional.empty()

  Scenario: Timeframe tag is propagated to matches
    Given a valid builder with series, rules, and withTimeframe("1d")
    When I detect
    Then every PatternMatch in the result has timeframe() = Optional.of("1d")
```

## 8. Block 2 — HA detection behavior

```gherkin
Feature: Heikin Ashi pattern detection

  Scenario: HABullishReversal after a bearish streak
    Given an HASeries with 4 bars: 3 red (haClose < haOpen) followed by 1 green (haClose ≥ haOpen)
    And HAColorChangeRule(minStreakLength = 3)
    When I detect
    Then matches contains exactly one HABullishReversal
    And its time equals the time of the 4th bar
    And its streakLength = 3
    And its bar is the 4th bar

  Scenario: HABearishReversal after a bullish streak
    Given an HASeries with 4 bars: 3 green followed by 1 red
    And HAColorChangeRule(minStreakLength = 3)
    When I detect
    Then matches contains exactly one HABearishReversal at the 4th bar

  Scenario: Streak shorter than minStreakLength is not a reversal
    Given an HASeries with 4 bars: 2 red followed by 1 red and 1 green
    And HAColorChangeRule(minStreakLength = 3)
    When I detect
    Then matches contains no HABullishReversal at the 4th bar

  Scenario: HABullishStrong on a clean bullish candle
    Given an HASeries with a bar where bodyRatio ≥ minBodyRatio and lowerWickRatio < wickTolerance
    And HAStrongCandleRule(wickTolerance, minBodyRatio)
    When I detect
    Then matches contains an HABullishStrong at that bar's time

  Scenario: HABearishStrong on a clean bearish candle
    Given an HASeries with a bar where bodyRatio ≥ minBodyRatio and upperWickRatio < wickTolerance
    And HAStrongCandleRule(wickTolerance, minBodyRatio)
    When I detect
    Then matches contains an HABearishStrong at that bar's time

  Scenario: HADoji on a small body
    Given an HASeries with a bar where bodyRatio ≤ maxBodyRatio
    And HADojiRule(maxBodyRatio)
    When I detect
    Then matches contains an HADoji at that bar's time
```

## 9. Block 3 — MA detection behavior

```gherkin
Feature: Moving Average pattern detection

  Scenario: PriceAboveMA emitted for every bar where price > MA
    Given a series of 30 bars and PriceVsMARule(SMA, 20, CLOSE)
    When I detect
    Then matches contains a PriceAboveMA for every bar from index 19 onward where close > SMA20
    And matches contains a PriceBelowMA for every bar from index 19 onward where close < SMA20
    And the first match's time is the 20th bar's time (index 19)

  Scenario: PriceCrossedAboveMA emitted only at the crossing bar
    Given a series where price crosses SMA20 from below at bar T
    And PriceMACrossRule(SMA, 20, CLOSE)
    When I detect
    Then matches contains exactly one PriceCrossedAboveMA with time = T
    And no PriceCrossedAboveMA is emitted on subsequent bars while price stays above

  Scenario: No match before sufficient bars
    Given a series of 30 bars and PriceVsMARule(SMA, 20, CLOSE)
    When I detect
    Then no match has time before the 20th bar's time (no value can be computed)

  Scenario: EMA produces different match values than SMA
    Given the same series and the same period
    And one PriceVsMARule(SMA, p, source) and one PriceVsMARule(EMA, p, source)
    When I detect
    Then the maValue payload differs between matches emitted by the two rules
```

## 10. Block 4 — RSI detection behavior

```gherkin
Feature: RSI pattern detection

  Scenario: RSIOverbought state on bars exceeding threshold
    Given a series whose RSI(14) > 70 from bar T to bar T+5
    And RSIThresholdRule(14, 70, 30, CLOSE)
    When I detect
    Then matches contains 6 RSIOverbought entries (one per bar from T to T+5)
    And each match has flavor = STATE

  Scenario: RSIExitedOverbought event at the exit bar
    Given a series whose RSI(14) is > 70 at bar T and ≤ 70 at bar T+1
    And RSIThresholdRule(14, 70, 30, CLOSE)
    When I detect
    Then matches contains an RSIExitedOverbought at time = T+1
    And its flavor = EVENT

  Scenario: RSICrossedAbove50 event at the crossing bar
    Given a series where RSI(14) crosses 50 from below at bar T
    And RSILevel50CrossRule(14, CLOSE)
    When I detect
    Then matches contains exactly one RSICrossedAbove50 at time = T
```

## 11. Block 5 — MACD detection behavior

```gherkin
Feature: MACD pattern detection

  Scenario: MACDBullishCross at the signal line cross
    Given a series where MACD line crosses above signal line at bar T
    And MACDSignalCrossRule(12, 26, 9, CLOSE)
    When I detect
    Then matches contains exactly one MACDBullishCross at time = T
    And its macdValue equals MACD(T) and signalValue equals signal(T)

  Scenario: MACDBearishCross at the signal line cross from above
    Given a series where MACD line crosses below signal line at bar T
    And MACDSignalCrossRule(12, 26, 9, CLOSE)
    When I detect
    Then matches contains exactly one MACDBearishCross at time = T

  Scenario: MACDCrossedAboveZero event at the zero-line cross
    Given a series where MACD line crosses above zero at bar T
    And MACDZeroCrossRule(12, 26, 9, CLOSE)
    When I detect
    Then matches contains exactly one MACDCrossedAboveZero at time = T
```

## 12. Block 6 — Detector contract

```gherkin
Feature: PatternDetector contract

  Scenario: Detect returns ordered matches
    Given a valid DetectionSpec
    When I call detector.detect(spec)
    Then matches() is ordered ascending by time()

  Scenario: Null spec is a programmer error
    When I call detector.detect(null)
    Then NullPointerException is thrown
    And NO DetectionException is thrown

  Scenario: Internal failure wraps original cause
    Given a DetectionSpec whose detection triggers an internal error
    When I call detector.detect(spec)
    Then DetectionInternalException is thrown
    And exception.getCause() is the original underlying exception

  Scenario: Idempotency
    Given a valid DetectionSpec
    When I call detector.detect(spec) twice in succession
    Then both DetectionResult outputs are equal by value

  Scenario: Determinism across detector instances
    Given two instances of the detector
    And the same valid DetectionSpec
    When I call detect on both
    Then both return equal DetectionResult

  Scenario: Concurrent calls on the same detector instance are safe
    Given a single detector instance
    And N different valid DetectionSpec inputs
    When I call detect concurrently from N threads
    Then each call returns its expected DetectionResult
    And no race condition occurs

  Scenario: Lookahead-safety
    Given a DetectionSpec built from a series truncated at bar T
    And the same DetectionSpec built from a series extended with bars T+1, T+2, ...
    When I call detect on both
    Then for every match in the truncated result with time ≤ T
    The corresponding match in the extended result is identical (same payload, same flavor, same time)
```

## 13. Detection logic — canonical formulas

The indicator calculators (SMA, EMA, RSI, MACD) live in the shared `indicators` module as of v1.1; `nachtkrapp` consumes `org.hatrack.indicators.Indicators`. The canonical formulas are now authoritatively documented in `indicators/CLAUDE.md` §3. They are reproduced here for the detection logic that builds on them. The implementation MUST use these canonical formulas. No variations.

| Indicator | Formula |
|---|---|
| `SMA(period, source)` at bar `t` | arithmetic mean of `source` over the window `[t - period + 1, t]` |
| `EMA(period, source)` at bar `t` | recursive smoothing with multiplier `k = 2 / (period + 1)`; seed = SMA of first `period` values |
| `RSI(period, source)` at bar `t` | Wilder's smoothing: average gain / average loss over `period`; seed = simple average of first `period` differences; RSI = 100 - 100 / (1 + RS) |
| `MACD(fast, slow, signal, source)` | `macdLine = EMA(fast, source) - EMA(slow, source)`; `signalLine = EMA(signal, macdLine)`; `histogram = macdLine - signalLine` |

A cross from below to above at bar `t` is detected when the value at `t-1` is `< threshold` and the value at `t` is `≥ threshold`. Symmetric for cross from above to below. The exact rule applies for `PriceMACrossRule`, `RSILevel50CrossRule`, `MACDSignalCrossRule`, `MACDZeroCrossRule`, `RSIExitedOverbought`, `RSIExitedOversold`.

## 14. HA pattern detection — canonical definitions

| Pattern | Definition |
|---|---|
| `HABullishReversal` at bar `t` | bars `t-minStreakLength` through `t-1` all bearish (`haClose < haOpen`) AND bar `t` bullish (`haClose ≥ haOpen`) |
| `HABearishReversal` at bar `t` | bars `t-minStreakLength` through `t-1` all bullish AND bar `t` bearish |
| `HABullishStrong` at bar `t` | bar `t` bullish AND `bodyRatio = (haClose - haOpen) / (haHigh - haLow) ≥ minBodyRatio` AND `lowerWickRatio = (haOpen - haLow) / (haHigh - haLow) < wickTolerance` |
| `HABearishStrong` at bar `t` | bar `t` bearish AND `bodyRatio = (haOpen - haClose) / (haHigh - haLow) ≥ minBodyRatio` AND `upperWickRatio = (haHigh - haOpen) / (haHigh - haLow) < wickTolerance` |
| `HADoji` at bar `t` | `bodyRatio = |haClose - haOpen| / (haHigh - haLow) ≤ maxBodyRatio` |

When `haHigh = haLow` (a bar of zero range — degenerate), the bar is ignored for HA-strong and HA-doji detection (no match emitted, no exception).

## 15. Out of scope for `nachtkrapp`

- Compound rules (AND, OR, NOT) — the consumer composes primitive matches in Java
- Pivot detection — out of v1 in this repo
- Candlestick classical patterns (hammer, engulfing, etc.) — reserved for v1.1+
- Chart structural patterns (head & shoulders, triangles, etc.) — reserved for v2
- Indicator value query API (e.g. "give me RSI series") — out of scope
- ML-based detection — would live in a separate, independent module
- Audit log of detected patterns — consumer-side concern
- Threshold/parameter discovery (auto-tuning) — consumer-side concern
- Multi-instrument scanning orchestration — consumer-side concern
- Multi-timeframe orchestration — consumer-side; this module only exposes the optional `timeframe()` tag for provenance
- Persistence of detected matches — consumer-side concern

## 16. Implementation delegation to Claude Code

Claude Code is responsible for:

- Package layout (suggested: `<group>.nachtkrapp` with subpackages `rule`, `match`, `spec`, `detector`, `error`, `internal` where `internal` holds the detection logic; the indicator calculators live in the shared `indicators` module)
- Writing canonical constructors with `Objects.requireNonNull` and field-level range checks
- Implementing defensive copies of collections in `record` canonical constructors and accessors
- Implementing `DetectionSpecBuilder.build()` with the V1–V9 validation rules
- Consuming the indicator calculators (SMA, EMA, RSI, MACD) from the shared `indicators` module; the canonical formulas in §13 are implemented there
- Implementing the detection logic for each rule type as specified in §13–§14
- Implementing the `PatternDetector` interface with a single concrete class
- Test infrastructure for the Gherkin scenarios above (Cucumber for Java, executed via JUnit Platform; feature files under src/test/resources/features/, step definitions under src/test/java/)

What Claude Code MUST NOT do unilaterally:

- Add a public constructor on `DetectionSpec` bypassing the builder
- Add rule variants beyond the ones in §2.2
- Add match variants beyond the ones in §2.3
- Add convenience factories not specified above
- Use `double` or `float` anywhere a `BigDecimal` is specified
- Use indicator formulas that differ from §13
- Use HA pattern definitions that differ from §14
- Add a compound-rule type or DSL
- Add reflective bean wiring, DI annotations, or static mutable state
- Relax the thread-safety contract documented in §6
- Re-introduce a local copy of the indicator calculators (they live in the shared `indicators` module)
