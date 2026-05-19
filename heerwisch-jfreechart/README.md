# heerwisch-jfreechart

The **default chart driver** — the engine that turns a `ChartSpec` into an
actual image, backed by [JFreeChart](https://www.jfree.org/jfreechart/) 1.5.

> Human-friendly guide. Authoritative contract: [`CLAUDE.md`](CLAUDE.md).
> The chart vocabulary it implements is [`heerwisch-api`](../heerwisch-api).

## What it is for

`heerwisch-api` defines *what* a chart is; this module decides *how* to draw it.
It implements the `ChartRenderer` port: hand it a `ChartSpec`, get back a
`ChartImage` (PNG or JPEG bytes).

It is built for unattended, server-side use:

- **Headless** — needs no display; runs in a server, a container or AWS Lambda.
- **Deterministic** — it embeds its own font (DejaVu Sans), so the same spec
  renders **byte-identical** output on any machine. No dependence on system
  fonts.
- **Self-contained** — it returns bytes; it never opens a window and never
  writes a file.

## Adding the dependency

```xml
<dependency>
    <groupId>net.jacopobiscella</groupId>
    <artifactId>heerwisch-jfreechart</artifactId>
    <version>0.43.0-alpha</version>
</dependency>
```

This pulls in `heerwisch-api` and JFreeChart 1.5 transitively. Main class:
`org.hatrack.heerwisch.jfreechart.JFreeChartRenderer`.

## Usage

```java
import org.hatrack.heerwisch.api.port.ChartRenderer;
import org.hatrack.heerwisch.api.spec.*;
import org.hatrack.heerwisch.jfreechart.JFreeChartRenderer;

ChartRenderer renderer = new JFreeChartRenderer();   // throws DriverInternalException if the font fails to load

ChartSpec spec = ChartSpec.builder()
        .withSeries(new OHLCSeries(bars))
        .addIndicator(new Indicator.SMA(20, PriceSource.CLOSE))
        .build();

ChartImage image = renderer.render(spec);            // throws ChartRenderException
Files.write(Path.of("chart.jpg"), image.bytes());
```

Construct the renderer **once** and reuse it — the font load happens at
construction and is cached. The renderer is not required to be thread-safe;
serialize concurrent `render()` calls yourself.

### What it draws

- price as candlesticks (works for both `OHLCSeries` and `HASeries`);
- each indicator on its pane, in a stacked multi-pane layout with a shared time
  axis;
- annotations (highlights, levels, Fibonacci levels, pivot points) on the main
  pane;
- output encoded as PNG or JPEG per the spec's `LayoutSpec.format()` (JPEG at
  quality 0.9).

## V12 — strict indicator/pane checking

`heerwisch-api` leaves rule **V12** (indicators on the `MAIN` pane must be
overlay-compatible) as a soft, driver-specific rule. **This driver enforces it
strictly**: placing an oscillator (`RSI`, `MACD`, `ADX`, `Stochastic`, `ATR`,
`VolumePane`) on `MAIN` throws `UnsupportedFeatureException` at render time.
`SMA`, `EMA` and `BollingerBands` are valid on `MAIN`.

## `ThemeConstants`

The colour palette and stroke conventions are exposed read-only as
`public static final` fields on `org.hatrack.heerwisch.jfreechart.theme.ThemeConstants`
(typed `java.awt.Color` / `java.awt.Stroke`). Consumers may read them — for an
external legend, documentation, or UI alignment — but cannot override them; v1
has no theme-customisation API.

## Errors

`render()` throws subtypes of `ChartRenderException` (from `heerwisch-api`):
`UnsupportedFeatureException` (V12 violation), `DriverInternalException` (an
internal JFreeChart failure, cause attached). A spec is already validated by
`ChartSpecBuilder.build()`, so `InvalidChartSpecException` is normally raised
before you ever reach the renderer.

The constructor throws `DriverInternalException` (cause: `IOException` or
`FontFormatException`) if the bundled font cannot be loaded.

## Out of scope

SVG/PDF output, theme or font customisation, interactive charts. A future
vector-engine driver would be a separate sibling module.
