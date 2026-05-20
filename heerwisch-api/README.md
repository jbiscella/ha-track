<img src="../heerwisch-logo.png" alt="heerwisch" height="90">

# heerwisch-api

The **plotting library** — the part that defines *what a chart is*. Immutable
spec types and the `ChartRenderer` port. It does no rendering itself; pair it
with a driver such as [`heerwisch-jfreechart`](../heerwisch-jfreechart).

> Human-friendly guide. Authoritative contract: [`CLAUDE.md`](CLAUDE.md).
> Background: [`../docs/concepts.md`](../docs/concepts.md).

## What it is for

You describe the chart you want — a series, indicators and where to put them,
annotations, a layout — as an immutable `ChartSpec`. A driver turns that spec
into an image. `heerwisch-api` is the *vocabulary*; the driver is the *engine*.

The split exists so the rendering backend is swappable: JFreeChart today, a
vector engine tomorrow, without changing how you describe charts.

## Adding the dependency

```xml
<dependency>
    <groupId>net.jacopobiscella</groupId>
    <artifactId>heerwisch-api</artifactId>
    <version>0.45.0-alpha</version>
</dependency>
```

Packages: `spec`, `port`, `error` under `org.hatrack.heerwisch.api`.

## Describing a chart — `ChartSpec`

Built with a fluent builder:

```java
import org.hatrack.heerwisch.api.spec.*;

ChartSpec spec = ChartSpec.builder()
        .withSeries(new OHLCSeries(bars))
        .addIndicator(new Indicator.SMA(20, PriceSource.CLOSE))   // → MAIN pane by default
        .addIndicator(new Indicator.RSI(14, new BigDecimal("70"),
                new BigDecimal("30"), PriceSource.CLOSE))         // → SUBPLOT_1 by default
        .addIndicator(new Indicator.MACD(12, 26, 9, PriceSource.CLOSE), Pane.SUBPLOT_2)
        .addAnnotation(new Annotation.HorizontalLevel(
                new BigDecimal("185"), "support", LevelStyle.DASHED))
        .withLayout(LayoutSpec.defaults())                        // 900×500, JPEG
        .build();                                                  // throws InvalidChartSpecException
```

There is no public `ChartSpec` constructor — the builder is the only way in,
and it validates everything up front.

### Indicators — `Indicator`

A sealed hierarchy of nine variants: `SMA`, `EMA`, `BollingerBands`, `MACD`,
`RSI`, `ADX`, `Stochastic`, `ATR`, `VolumePane`. Period and ratio parameters
are checked by the record constructor (a non-positive period throws
immediately).

Each indicator has a *default pane*: overlay indicators (`SMA`, `EMA`,
`BollingerBands`) default to `MAIN`; oscillators default to `SUBPLOT_1`. Use
`addIndicator(indicator, pane)` to override, or
`ChartSpecBuilder.defaultPaneFor(indicator)` to inspect the default.

### Annotations — `Annotation`

`BarHighlight` (a marker on one bar), `HorizontalLevel` (a price line, with a
`LevelStyle`), `FibRetracement` (Fibonacci levels — `FibRetracement.STANDARD_LEVELS`
is provided), `PivotPointLevels` (levels computed from a previous period's bar).

### Layout — `LayoutSpec`

`LayoutSpec.defaults()` gives a 900×500 **JPEG** auto-layout. For control:

```java
LayoutSpec layout = LayoutSpec.builder()
        .withSize(1200, 800)
        .withFormat(ImageFormat.PNG)
        .withMainPaneHeight(new BigDecimal("0.6"))
        .addSubplotHeight(Pane.SUBPLOT_1, new BigDecimal("0.4"))
        .build();   // throws InvalidChartSpecException (V14) if subplot heights are set with no main height
```

`AutoLayoutSpec` lets the driver distribute pane heights; `ExplicitLayoutSpec`
gives you the heights, which must sum to 1.0.

## Rendering — the `ChartRenderer` port

```java
import org.hatrack.heerwisch.api.port.ChartRenderer;

ChartRenderer renderer = ... ;                  // e.g. new JFreeChartRenderer()
ChartImage image = renderer.render(spec);        // throws ChartRenderException
Files.write(Path.of("chart.jpg"), image.bytes());
```

`ChartImage` carries the raw `bytes()`, the `contentType()` (`"image/png"` /
`"image/jpeg"`) and the pixel dimensions. The renderer never writes a file —
*you* decide where the bytes go (disk, HTTP response, e-mail attachment).

## Validation and errors

`ChartSpecBuilder.build()` rejects malformed specs eagerly with
`InvalidChartSpecException`, whose `violatedRule` names the broken rule —
**V1–V11 and V13**: unset/empty/unordered series, priceSource/series-type
mismatch, too few bars for an indicator, a highlight on a non-existent bar,
explicit layout heights not summing to 1, OHLC invariant violations, and more.
`LayoutSpecBuilder.build()` adds **V14**.

Rule **V12** (oscillators on the `MAIN` pane) is *not* a `build()` rule — it is
a **soft, driver-specific** rule. `heerwisch-api` does not reject it at spec
construction; a driver may, failing later at render time with
`UnsupportedFeatureException` (the default driver, `heerwisch-jfreechart`,
enforces it strictly).

The full hierarchy (all checked, rooted at `ChartRenderException`):

| Exception | When |
|---|---|
| `InvalidChartSpecException` | malformed spec — from `build()` |
| `UnsupportedFeatureException` | the driver cannot render a requested feature |
| `InsufficientDataException` | data too short for an indicator at render time |
| `DriverInternalException` | an internal driver failure; wraps the cause |

A `null` spec passed to `render()` is a programmer error → `NullPointerException`.

## Notes

- `Series` comes from `commons` — `heerwisch-api` consumes it, does not define it.
- The default output format is **JPEG** (`LayoutSpec.defaults()`).
- The contract is single-threaded; serialize concurrent `render()` calls yourself.
