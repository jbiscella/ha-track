# indicators

The **technical-indicator calculator** kernel. A small, JDK-only set of
stateless functions that turn a price series into indicator values — SMA, EMA,
RSI, MACD, Bollinger Bands, ATR, Stochastic and ADX.

> Human-friendly guide. Authoritative contract: [`CLAUDE.md`](CLAUDE.md).

## What it is for

`indicators` is a shared kernel: the same calculators are needed by
[`nachtkrapp`](../nachtkrapp) (for indicator-based pattern primitives) and
[`heerwisch-jfreechart`](../heerwisch-jfreechart) (for rendering indicator
overlays), and by future consumers. It is pure computation — no I/O, no clock,
no state — so it is safe to call from anywhere, including concurrently.

It has zero external dependencies (JDK only), exactly like `commons`.

## Adding the dependency

```xml
<dependency>
    <groupId>net.jacopobiscella</groupId>
    <artifactId>indicators</artifactId>
    <version>0.45.0-alpha</version>
</dependency>
```

All types live in the single package `org.hatrack.indicators`.

## The calculators

Every calculator is a `static` method on `Indicators`. Each takes a
`List<BigDecimal>` price channel (or high/low/close channels) plus integer
periods, and returns an array indexed by bar — `null` for bars before the
indicator's warm-up window.

| Method | Returns |
|---|---|
| `sma(src, period)` | `BigDecimal[]` |
| `ema(src, period)` | `BigDecimal[]` |
| `rsi(src, period)` | `BigDecimal[]` |
| `macd(src, fast, slow, signal)` | `MacdResult` (macd line, signal line, histogram) |
| `bollinger(src, period, multiplier)` | `BollingerBands` (upper, middle, lower) |
| `atr(high, low, close, period)` | `BigDecimal[]` |
| `stochastic(high, low, close, kPeriod, dPeriod, smoothing)` | `StochasticResult` (%K, %D) |
| `adx(high, low, close, period)` | `BigDecimal[]` |

```java
List<BigDecimal> closes = ...;                       // your price channel
BigDecimal[] sma20 = Indicators.sma(closes, 20);
MacdResult macd = Indicators.macd(closes, 12, 26, 9);
```

## Things to keep in mind

- All arithmetic is `BigDecimal` with `MathContext.DECIMAL64` — no `double`,
  no `float`.
- Calculators are **batch**: each call recomputes the whole series. There is
  no incremental / streaming update API in v1.
- Arguments are validated eagerly — a `null` channel or a `period < 1` is
  rejected before any computation.
- The module exposes calculators, not indicator *spec* types; `nachtkrapp` and
  `heerwisch-api` keep their own spec hierarchies. See
  [`CLAUDE.md`](CLAUDE.md) §2.3 for the rationale.
