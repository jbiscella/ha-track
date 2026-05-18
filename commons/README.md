# commons

The shared kernel of ha-track. A tiny set of **immutable data types** and
**pure functions** that every other module speaks. JDK-only — zero external
dependencies, no I/O, no clock, no mutable state.

> This README is the human-friendly guide. The authoritative, rule-by-rule
> behavioural contract is [`CLAUDE.md`](CLAUDE.md). For the big picture see
> [`../docs/concepts.md`](../docs/concepts.md).

## What it is for

`commons` exists so that "a bar of price data" means exactly the same thing in
the charting library, the backtester and the pattern detector. It is the
common currency: you build its types, pass them between modules, and never
worry about conversion.

It deliberately stays *slim* — it holds only what every consumer needs.
Domain-specific fields (instrument ids, data provenance, ingestion timestamps)
belong in your own code, not here.

## Adding the dependency

```xml
<dependency>
    <groupId>org.hatrack</groupId>
    <artifactId>commons</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

All types live in the single package `org.hatrack.commons`.

## The types

### `OHLCBar` — one raw price bar

```java
OHLCBar bar = new OHLCBar(
        Instant.parse("2024-01-02T00:00:00Z"),   // time  (UTC)
        new BigDecimal("187.15"),                 // open
        new BigDecimal("188.44"),                 // high
        new BigDecimal("183.89"),                 // low
        new BigDecimal("185.64"),                 // close
        Optional.of(new BigDecimal("82488682"))); // volume — Optional, may be empty
```

The constructor only checks for `null`. It does **not** reject a bar that
breaks the OHLC invariants — that is a deliberate choice so bulk loads stay
fast. Validate explicitly when you need to:

```java
bar.validateInvariants();   // throws OHLCInvariantViolationException if malformed
```

The invariants: every price strictly positive, `high ≥ low`, `high ≥ open`,
`high ≥ close`, `low ≤ open`, `low ≤ close`, and `volume ≥ 0` when present. In
practice you rarely call `validateInvariants()` yourself — the spec builders in
`frau-holle`, `nachtkrapp` and `heerwisch-api` call it for you at their
boundary.

### `HABar` — one Heikin Ashi bar

Same shape as `OHLCBar` but with `haOpen/haHigh/haLow/haClose`. You normally do
not construct these by hand — `HeikinAshiCalculator` produces them.

### `Series` — an ordered run of bars

A sealed pair: `OHLCSeries` wraps a `List<OHLCBar>`, `HASeries` wraps a
`List<HABar>`. Both defensively copy the list, so the series is immutable even
if you keep mutating your original list.

```java
OHLCSeries raw = new OHLCSeries(bars);
HASeries   ha  = new HASeries(haBars);
```

`Series` does not enforce ordering or non-emptiness — those are domain rules
the consuming library's builder checks.

### `Timeframe` — the bar period

An *open* type (any `amount` + `unit`), with a compact wire format:

```java
Timeframe daily = Timeframe.fromWire("1d");   // parse
String    wire  = daily.wire();               // "1d"
```

Units: `s m h d w M y` (note the uppercase `M` for month — lowercase `m` is
minute). A zero/negative amount or an unknown suffix throws
`IllegalArgumentException`.

### `PriceSource`

An enum naming which channel an indicator reads: `OPEN, HIGH, LOW, CLOSE` and
the Heikin Ashi variants `HA_OPEN, HA_HIGH, HA_LOW, HA_CLOSE`. Used by
`heerwisch-api` and `nachtkrapp`.

## `HeikinAshiCalculator` — converting raw bars to Heikin Ashi

A stateless utility with two static methods. Use `computeChain` for a whole
series:

```java
List<HABar> haBars = HeikinAshiCalculator.computeChain(Optional.empty(), bars);
```

`Optional.empty()` means "no previous Heikin Ashi bar" — the first bar is
*seeded*. If you are computing incrementally (e.g. one new bar arrived), pass
the last `HABar` you already have and use `compute`:

```java
HABar next = HeikinAshiCalculator.compute(Optional.of(lastHaBar), newOhlcBar);
```

The input list must already be ordered ascending by time — the calculator does
not sort. All arithmetic is `BigDecimal` with `MathContext.DECIMAL64`.

## Errors

`OHLCInvariantViolationException` (a `RuntimeException`) is thrown only by
`OHLCBar.validateInvariants()`. It carries the bar's `time()` and the name of
the first `violatedInvariant()` — it does not carry the bad bar itself.

## Things to keep in mind

- **Prices are `BigDecimal`, never `double`.** Floating-point drift has no
  place in money maths.
- **Time is always `Instant` (UTC).** No `LocalDateTime`, no timezones.
- **`volume` is an `Optional`, and the `Optional` itself is never `null`** —
  pass `Optional.empty()` for "no volume", not `null`.
- **Construction is cheap and unvalidated; validation is a separate step.**
