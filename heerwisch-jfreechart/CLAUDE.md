# CLAUDE.md — `heerwisch-jfreechart` module

This is the nested spec for the `heerwisch-jfreechart` module. The repo-wide rules live in the root `CLAUDE.md`. The abstract API contract lives in `heerwisch-api/CLAUDE.md`. This file specifies what is specific to the JFreeChart-backed implementation of the `ChartRenderer` port.

## 0. Goal and scope

`heerwisch-jfreechart` is the default driver of `heerwisch-api`. It:

- Implements the `ChartRenderer` port using JFreeChart 1.5.x as the underlying rendering engine.
- Produces `ChartImage` outputs in PNG or JPEG format.
- Runs headless: it does not require an X server and works inside AWS Lambda or any JVM with `java.awt.headless=true`.
- Embeds its own font for deterministic rendering across environments.
- Exposes the color palette and stroke conventions as read-only `ThemeConstants` for the consumer to introspect.

Out of scope: SVG / PDF output, theme customization, font customization, interactive charts (no Swing components, no event handlers).

Dependencies: `heerwisch-api`, `commons`, `indicators`, JFreeChart 1.5.x, an embedded font asset bundled in the JAR. The indicator calculators were extracted into the shared `indicators` module in v1.1; this driver now consumes them rather than carrying its own copy (see root `CLAUDE.md` §6).

## 1. Supported output formats

| Format | Content type | When used |
|---|---|---|
| PNG | `image/png` | Lossless, supports transparency. Available for the consumer's email-attachment use case |
| JPEG | `image/jpeg` | Default. Smaller payload when transparency is not needed. Compression level fixed at quality 0.9 |

The format is selected via a new optional field in `ChartSpec` — see §2.

SVG and PDF are out of scope for this driver. A future `heerwisch-jfreesvg` (or similar) driver may be added as a sibling module; that decision is not v1.

## 2. Format selection in `ChartSpec`

Since `heerwisch-api` declares `ChartImage` with a `contentType` field but does not specify how the format is selected, `heerwisch-jfreechart` introduces the following:

| Item | Behavior |
|---|---|
| Where format is declared | The `LayoutSpec` records (`AutoLayoutSpec`, `ExplicitLayoutSpec`) carry an `ImageFormat format` field (declared in `heerwisch-api`) |
| Enum `ImageFormat` | `PNG`, `JPEG` — declared in `heerwisch-api` |
| Default | `JPEG` (preserved when callers use `LayoutSpec.defaults()`) |
| Consumer who never specifies it | gets `JPEG` |

The `ImageFormat` enum and the `format` field live in `heerwisch-api`'s `LayoutSpec` (see `heerwisch-api/CLAUDE.md` §1.6). The default format is `JPEG`. The field is shared across both layout variants because it's not pane-specific.

## 3. Font strategy — deterministic rendering

The driver bundles **DejaVu Sans** (open-source, Bitstream Vera Fonts License, permissive) as an internal resource in the JAR. The font is loaded at driver construction via `Font.createFont(...)` and registered as the default font for every text element in the chart (axis labels, tick marks, legend, title if present, annotation labels).

| Aspect | Choice |
|---|---|
| Font family | DejaVu Sans |
| Font file | bundled as classpath resource under `/heerwisch-fonts/DejaVuSans.ttf` |
| Load timing | once at driver construction; cached for the lifetime of the JVM via a `static final Font` reference (the only static state allowed in this module, justified by the deterministic-rendering requirement) |
| Fallback if load fails | `DriverInternalException` with cause = `FontFormatException` or `IOException` |
| Customization in v1 | none — the font is hardcoded |
| Sizes | default JFreeChart sizes per element, applied to the embedded family |

Rationale: rendering tests, byte-identical comparisons, and email-attachment consistency all require that the output not vary based on system fonts. DejaVu Sans covers full Latin + extensive Unicode without licensing burden.

## 4. V12 enforcement — indicator/pane compatibility

`heerwisch-api` declares V12 as a soft rule. This driver enforces it **strict**.

| Indicator | Pane allowed |
|---|---|
| `SMA` | `MAIN` (overlay on price) or `SUBPLOT_*` |
| `EMA` | `MAIN` or `SUBPLOT_*` |
| `BollingerBands` | `MAIN` or `SUBPLOT_*` |
| `RSI` | `SUBPLOT_*` only |
| `MACD` | `SUBPLOT_*` only |
| `ADX` | `SUBPLOT_*` only |
| `Stochastic` | `SUBPLOT_*` only |
| `ATR` | `SUBPLOT_*` only |
| `VolumePane` | `SUBPLOT_*` only |

Attempting to render a `ChartSpec` with a forbidden placement throws `UnsupportedFeatureException` from `render()` with:

| Field | Value |
|---|---|
| `featureName` | string of the form `"<IndicatorClassName> on MAIN pane"` (e.g. `"RSI on MAIN pane"`) |
| `driverName` | `"heerwisch-jfreechart"` |

The check is done eagerly at the start of `render()`, before any JFreeChart object is constructed.

## 5. Color palette and theme

The driver exposes a public class `ThemeConstants` with `public static final` fields, **read-only**. Consumers can read the colors for documentation, external legend rendering, or UI alignment, but cannot override them in v1.

| Constant | Value | Used for |
|---|---|---|
| `BACKGROUND` | `#FFFFFF` (white) | chart background |
| `GRID` | `#E0E0E0` (light grey) | minor and major gridlines |
| `AXIS` | `#303030` (near-black) | axis lines and tick marks |
| `TEXT` | `#202020` (dark grey) | axis labels, legend text, annotation labels |
| `BULLISH_CANDLE` | `#26A69A` (teal-green) | bullish candle body fill (both OHLC and HA) |
| `BEARISH_CANDLE` | `#EF5350` (coral-red) | bearish candle body fill (both OHLC and HA) |
| `WICK` | `#303030` | candle wick lines |
| `SMA_LINE` | `#1976D2` (blue) | SMA overlay |
| `EMA_LINE` | `#F57C00` (orange) | EMA overlay |
| `BB_BAND` | `#9E9E9E` (medium grey) | Bollinger Bands upper / lower bands |
| `BB_FILL` | `#9E9E9E` at 10% alpha | optional fill between BB bands |
| `RSI_LINE` | `#7B1FA2` (purple) | RSI line |
| `RSI_OVERBOUGHT_LEVEL` | `#EF5350` at 60% alpha | horizontal line at the overbought threshold |
| `RSI_OVERSOLD_LEVEL` | `#26A69A` at 60% alpha | horizontal line at the oversold threshold |
| `MACD_LINE` | `#1976D2` | MACD line |
| `MACD_SIGNAL` | `#F57C00` | MACD signal line |
| `MACD_HISTOGRAM_UP` | `#26A69A` at 70% alpha | positive histogram bars |
| `MACD_HISTOGRAM_DOWN` | `#EF5350` at 70% alpha | negative histogram bars |
| `ADX_LINE` | `#7B1FA2` | ADX line |
| `STOCHASTIC_K` | `#1976D2` | Stochastic %K line |
| `STOCHASTIC_D` | `#F57C00` | Stochastic %D line |
| `ATR_LINE` | `#7B1FA2` | ATR line |
| `VOLUME_BAR_UP` | `#26A69A` at 60% alpha | volume bar when close ≥ open |
| `VOLUME_BAR_DOWN` | `#EF5350` at 60% alpha | volume bar when close < open |
| `ANNOTATION_BULLISH` | `#26A69A` | bar highlights and labels for bullish events |
| `ANNOTATION_BEARISH` | `#EF5350` | bar highlights and labels for bearish events |
| `ANNOTATION_NEUTRAL` | `#7B1FA2` | bar highlights and labels for neutral / doji events |
| `HORIZONTAL_LEVEL` | `#303030` at 60% alpha | horizontal level annotations |
| `FIB_LEVEL` | `#7B1FA2` at 60% alpha | Fibonacci retracement levels |
| `PIVOT_LEVEL` | `#1976D2` at 60% alpha | pivot point levels |

Strokes:

| Constant | Value |
|---|---|
| `STROKE_DEFAULT` | 1.0px solid |
| `STROKE_INDICATOR` | 1.5px solid |
| `STROKE_HORIZONTAL_LEVEL_DASHED` | 1.0px dashed (5px on, 3px off) |
| `STROKE_HORIZONTAL_LEVEL_DOTTED` | 1.0px dotted (1px on, 2px off) |

The constants are exposed as `java.awt.Color` for compatibility with JFreeChart, with hex notation in the documentation.

## 6. Layout rendering semantics

When `LayoutSpec` is an `AutoLayoutSpec`:

| Pane | Height fraction |
|---|---|
| `MAIN` | 0.60 |
| each subplot referenced by at least one indicator | `0.40 / N` where N is the number of distinct subplot panes referenced |

When `LayoutSpec` is an `ExplicitLayoutSpec`:

- The driver uses `mainPaneHeight` for the main pane.
- For each pane in `subplotHeights`, the driver creates a subplot at the specified fraction.
- Heights sum to 1.0 (validated by `heerwisch-api`).

Subplots are stacked vertically below the main pane, ordered by `Pane` enum natural order (SUBPLOT_1 first, SUBPLOT_8 last). The X axis is shared (CombinedDomainXYPlot pattern of JFreeChart).

## 7. Concrete behavior per indicator

The driver computes each indicator via the shared `indicators` module (`org.hatrack.indicators.Indicators`). The canonical formulas are documented in `indicators/CLAUDE.md` §3. Until v1.1 these formulas were duplicated in this module's `internal` package; that duplication is now resolved (see root `CLAUDE.md` §6).

| Indicator | Render strategy |
|---|---|
| `SMA(period, priceSource)` | Single line on the target pane using `SMA_LINE` color and `STROKE_INDICATOR` |
| `EMA(period, priceSource)` | Single line using `EMA_LINE` color |
| `BollingerBands(period, mult, priceSource)` | Three lines (upper, middle, lower); upper and lower in `BB_BAND` color; middle implicit (already shown as SMA if present, otherwise rendered) |
| `MACD(fast, slow, signal, priceSource)` | Two lines (MACD and signal) plus histogram bars |
| `RSI(period, ob, os, priceSource)` | Single line + two horizontal levels at `overbought` and `oversold` |
| `ADX(period)` | Single line |
| `Stochastic(k, d, smoothing)` | Two lines (%K and %D) |
| `ATR(period)` | Single line |
| `VolumePane()` | Vertical bars per bar, colored by `VOLUME_BAR_UP` or `VOLUME_BAR_DOWN` based on the underlying close vs open of the source series |

## 8. Concrete behavior per annotation

| Annotation | Render strategy |
|---|---|
| `BarHighlight(time, price, label)` | A text-only annotation (`XYTextAnnotation`) at `(time, price)`, rendered in `ANNOTATION_NEUTRAL` color. **No glyph is drawn** — for a directional glyph (triangle / arrow) use `EntryExitMarker` instead. The label string is the only visual element |
| `HorizontalLevel(price, label, style)` | A horizontal line across all panes at `price`, with stroke from `style` (`SOLID` / `DASHED` / `DOTTED`), color `HORIZONTAL_LEVEL`. Label rendered at the right margin |
| `FibRetracement(swingHigh, swingLow, levels)` | One horizontal line per `level` in the list, between `swingHigh` and `swingLow`, color `FIB_LEVEL`. Each line labeled with its fraction |
| `PivotPointLevels(variant, previousPeriodBar)` | Levels computed from `previousPeriodBar` per the formulas of `variant` (`STANDARD`, `CAMARILLA`, `WOODIE`). Rendered as horizontal lines colored `PIVOT_LEVEL`, each labeled (P, S1, S2, R1, R2, etc.) |
| `EntryExitMarker(time, price, direction, glyphStyle)` | A chunky semantic glyph drawn at `(time, price)` via JFreeChart's `XYShapeAnnotation`. Shape from `glyphStyle` (triangle/arrow, up or down); color from `direction` — `LONG_ENTRY` and `SHORT_EXIT` use `ANNOTATION_BULLISH` (semantic green), `SHORT_ENTRY` and `LONG_EXIT` use `ANNOTATION_BEARISH` (semantic red). Half-extents are computed adaptively from the series and layout (see §8.1 below) so the glyph stays proportional to the rendered candle width on any chart density, any aspect ratio, any device. `ARROW_UP` / `ARROW_DOWN` widen the chevron beyond the triangle's base (1.5× the triangle's half-width at the chevron tip) and narrow the shaft (¼ of the triangle's half-width) so the arrow silhouette stays distinguishable from the triangle while preserving equal filled area |
| `TimeRangeHighlight(startTime, endTime, fillColor, opacity)` | A shaded background band over `[startTime, endTime]` drawn via JFreeChart's `IntervalMarker` on the domain axis, placed at `Layer.BACKGROUND` so it sits behind candles and indicator lines. Base color from `fillColor` (`LONG_POSITION` → `TIME_RANGE_LONG`, `SHORT_POSITION` → `TIME_RANGE_SHORT`, `NEUTRAL` → `TIME_RANGE_NEUTRAL`, `CAUTION` → `TIME_RANGE_CAUTION`); per-instance `opacity` (`[0, 1]`) is applied as the fill alpha. No outline is drawn |

Annotations are drawn on the `MAIN` pane only in v1. Multi-pane annotations are not supported (a future API extension).

#### 8.1 EntryExitMarker glyph half-extents — adaptive computation

Half-extents are derived once per render from the series and the layout, not hardcoded. Given the series' minimum bar interval `Δt_min`, the series' price span `Δp = max(high) − min(low)`, the time span `T = lastBar.time − firstBar.time`, and the layout's `widthPx` / `heightPx`:

| Quantity | Value |
|---|---|
| `dx` (time-axis half-extent) | `0.4 · Δt_min` — yielding a glyph total width of ~80% of one candle. The driver matches the **smallest** bar interval rather than the average so the glyph still tracks candle width on irregular timelines (daily series with weekend gaps, intraday series with session breaks), where the average interval would overstate it. The 0.4 fraction balances "wide enough to read" against "narrow enough not to overlap an adjacent candle's marker". |
| `dy` (value-axis half-extent) | `dx · (Δp / T) · (widthPx / heightPx)` — chosen so the glyph reads as roughly square in pixel space (i.e. `2·dx / T · widthPx == 2·dy / Δp · heightPx`). Adapts to any chart aspect — narrow mobile portrait or ultra-wide desktop — without needing per-density tuning. |

`ARROW_UP` / `ARROW_DOWN` use `chevronHalf = 1.5·dx` at the chevron tip and `shaft = dx/4`. The widened chevron makes the arrow silhouette distinguishable from a triangle; the narrowed shaft holds the filled area equal to `2·dx·dy` (= triangle's filled area), so all four glyph styles render at the same visual weight.

Single-bar series fall back to a fixed `dx = 12h`, `dy = 0.5% · price`. A single bar provides no period or aspect information; the adaptive computation would have no signal to scale to.

#### EntryExitMarker glyph geometry — semantic asymmetry, not a cosmetic bug

The renderer deliberately draws `UP_TRIANGLE` / `DOWN_TRIANGLE` as compact solid shapes and `ARROW_UP` / `ARROW_DOWN` as a sparse chevron+shaft silhouette. The triangle's filled pixels are concentrated in a single block; the arrow's filled pixels are spread across a thin V plus a thin vertical shaft. Even when the **filled area** is held equal across glyph styles (which is the case in the current implementation — `(chevronHalf + 2·shaft)·dy = 2·dx·dy`), the triangle reads as visually heavier because solid shapes look chunkier than thin silhouettes of equal pixel count.

This asymmetry is intentional and matches the `GlyphStyle` semantic contract documented in `heerwisch-api/CLAUDE.md` §1.3.1: TRIANGLE = scheduled (strategy-scenario-driven) trade event; ARROW = forced (risk-managed) trade event. The renderer's geometry reinforces the semantic — bigger / chunkier = "the strategy made a deliberate decision here"; smaller / lighter = "this was a mechanical safety exit". Future driver implementations MUST preserve this asymmetry. Do not try to equalize the visual weight of triangles and arrows: the asymmetry is the API contract, not a rendering artifact.

### Pivot Points formulas (canonical)

For a previous-period bar with high H, low L, close C:

| Variant | Levels |
|---|---|
| `STANDARD` | P = (H + L + C) / 3; R1 = 2P - L; S1 = 2P - H; R2 = P + (H - L); S2 = P - (H - L); R3 = H + 2(P - L); S3 = L - 2(H - P) |
| `CAMARILLA` | R1 = C + (H - L) × 1.1/12; R2 = C + (H - L) × 1.1/6; R3 = C + (H - L) × 1.1/4; R4 = C + (H - L) × 1.1/2; S1 = C - (H - L) × 1.1/12; S2 = C - (H - L) × 1.1/6; S3 = C - (H - L) × 1.1/4; S4 = C - (H - L) × 1.1/2 |
| `WOODIE` | P = (H + L + 2C) / 4; R1 = 2P - L; S1 = 2P - H; R2 = P + (H - L); S2 = P - (H - L) |

## 9. Resource lifecycle

| Aspect | Behavior |
|---|---|
| Driver construction cost | One-time font load (~tens of ms); idempotent if called multiple times within the JVM (font cached statically) |
| Per-render allocation | A new `JFreeChart` object per render; disposed at end-of-method via try-finally to release Graphics2D |
| In-memory image buffer | `BufferedImage` of the requested dimensions; encoded to bytes via `ImageIO.write` and the buffer becomes eligible for GC at method return |
| File handles | None opened by the driver. The font is read once from classpath; no other file I/O |
| Network | None |
| Thread-safety | NOT required (consistent with `heerwisch-api` contract). The driver instance is safe to construct once and call from a single thread; callers needing concurrency must serialize externally |

## 10. Block 1 — Format selection

```gherkin
Feature: Output format selection

  Scenario: Default LayoutSpec produces JPEG
    Given a ChartSpec using LayoutSpec.defaults()
    When I call render(spec)
    Then ChartImage.contentType = "image/jpeg"
    And ChartImage.bytes is a valid JPEG (magic bytes 0xFF 0xD8 0xFF)

  Scenario: Explicit PNG format
    Given an ExplicitLayoutSpec with format = PNG
    When I call render(spec)
    Then ChartImage.contentType = "image/png"

  Scenario: Explicit JPEG format
    Given an AutoLayoutSpec with format = JPEG
    When I call render(spec)
    Then ChartImage.contentType = "image/jpeg"
    And ChartImage.bytes is a valid JPEG (magic bytes 0xFF 0xD8 0xFF)

  Scenario: Dimensions match spec
    Given an AutoLayoutSpec with widthPx = 1200, heightPx = 800
    When I call render(spec)
    Then ChartImage.widthPx = 1200 and ChartImage.heightPx = 800
```

## 11. Block 2 — V12 enforcement

```gherkin
Feature: Driver V12 strict enforcement

  Scenario: RSI on MAIN pane is rejected
    Given a ChartSpec placing RSI(14, 70, 30, CLOSE) at MAIN
    When I call render(spec)
    Then UnsupportedFeatureException is thrown
    And exception.featureName = "RSI on MAIN pane"
    And exception.driverName = "heerwisch-jfreechart"

  Scenario: MACD on MAIN pane is rejected
    Given a ChartSpec placing MACD(12, 26, 9, CLOSE) at MAIN
    When I call render(spec)
    Then UnsupportedFeatureException is thrown with featureName = "MACD on MAIN pane"

  Scenario: ADX, Stochastic, ATR, VolumePane on MAIN pane are rejected
    Given a ChartSpec placing any of {ADX, Stochastic, ATR, VolumePane} at MAIN
    When I call render(spec)
    Then UnsupportedFeatureException is thrown

  Scenario: SMA / EMA / BollingerBands on MAIN pane are accepted
    Given a ChartSpec placing SMA(20, CLOSE) at MAIN
    When I call render(spec)
    Then render succeeds and the SMA line is drawn on the main pane

  Scenario: Subplot indicators on a subplot are accepted
    Given a ChartSpec placing RSI(14, 70, 30, CLOSE) at SUBPLOT_1
    When I call render(spec)
    Then render succeeds and RSI is drawn on the subplot
```

## 12. Block 3 — Headless and Lambda compatibility

```gherkin
Feature: Headless rendering

  Scenario: Render works with java.awt.headless = true
    Given the system property java.awt.headless is set to "true"
    And a valid ChartSpec
    When I call render(spec)
    Then render succeeds
    And no HeadlessException is thrown

  Scenario: No Swing windowing is triggered
    Given any valid ChartSpec
    When I call render(spec)
    Then no java.awt.Window, JFrame, or JPanel is instantiated
    And no native peer is created
```

## 13. Block 4 — Deterministic font

```gherkin
Feature: Embedded font produces deterministic output

  Scenario: Same input produces byte-identical output across two renders
    Given a valid ChartSpec
    When I call render(spec) on the same driver instance twice
    Then both ChartImage.bytes are byte-identical

  Scenario: Two driver instances produce byte-identical output for the same spec
    Given two instances of the driver in the same JVM
    And the same valid ChartSpec
    When I call render(spec) on both
    Then both ChartImage.bytes are byte-identical

  Scenario: Font load failure is reported as DriverInternalException
    Given a JVM environment where the bundled font asset is unreadable
    When the driver is constructed
    Then DriverInternalException is thrown
    And the cause is FontFormatException or IOException
```

## 14. Block 5 — ThemeConstants exposure

```gherkin
Feature: ThemeConstants read-only access

  Scenario: Constants are public static final
    Given the ThemeConstants class
    Then BACKGROUND, GRID, BULLISH_CANDLE, BEARISH_CANDLE, and all other listed constants are public, static, and final
    And they are typed java.awt.Color

  Scenario: Constants match documented hex values
    Given the documented hex values in this spec
    Then BULLISH_CANDLE equals new Color(0x26, 0xA6, 0x4A) — i.e. #26A69A as RGB
    And BEARISH_CANDLE equals #EF5350
    And other constants match their documented values

  Scenario: Constants cannot be modified
    Given the ThemeConstants class
    Then no setter or mutation method exists
    And reflection access to modify them is not part of the public contract
```

## 15. Out of scope for `heerwisch-jfreechart`

- SVG output (a future `heerwisch-jfreesvg` or similar driver)
- PDF output (out of repo scope entirely)
- Theme customization via API (v1 is read-only `ThemeConstants`)
- Font customization (DejaVu Sans is hardcoded)
- Interactive charts (no Swing components, no event handlers)
- Animation, video, or any time-varying output
- Multi-pane annotations (annotations are MAIN-only in v1)
- Custom layouts beyond `AutoLayoutSpec` and `ExplicitLayoutSpec`
- Server-side caching of rendered images
- Logging of rendered chart content (privacy: consumer concern)

## 16. Implementation delegation to Claude Code

Claude Code is responsible for:

- Package layout (suggested: `<group>.heerwisch.jfreechart` with subpackages `internal` for driver-private helpers, `theme` for `ThemeConstants`, the driver class at the root; the indicator calculators live in the shared `indicators` module)
- Loading the embedded DejaVu Sans font from `/heerwisch-fonts/DejaVuSans.ttf` at driver construction
- Mapping each `Series` variant to the appropriate JFreeChart data structure (`OHLCSeriesCollection` for OHLC, custom dataset for HA)
- Setting up `CombinedDomainXYPlot` for the multi-pane layout
- Implementing the V12 strict pre-check at the start of `render()`
- Encoding the final image as PNG or JPEG via `ImageIO.write` (PNG) or `ImageIO.write(image, "jpg", baos)` with quality 0.9 for JPEG
- Implementing `ThemeConstants` as a final class with private constructor and public static final fields
- Test infrastructure for the Gherkin scenarios above (Cucumber for Java, executed via JUnit Platform; feature files under src/test/resources/features/, step definitions under src/test/java/)

What Claude Code MUST NOT do unilaterally:

- Add SVG / PDF output (out of scope)
- Add theme customization API (out of scope for v1)
- Allow font customization (DejaVu Sans hardcoded)
- Cache rendered images (no caching layer)
- Add INFO-level logging of chart data
- Open file handles to the filesystem during render
- Expose `ThemeConstants` as mutable
- Add static mutable state beyond the documented font cache
- Use `double` or `float` anywhere a `BigDecimal` is specified in indicator calculations
- Re-introduce a local copy of the indicator calculators (they live in the shared `indicators` module)
- Bundle a font other than DejaVu Sans
- Bundle font assets larger than ~2MB (DejaVu Sans Regular is ~750KB)
