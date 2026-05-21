# Changelog

All notable changes to this repository are documented here. Versions are
shared across all reactor modules (`commons`, `indicators`, `heerwisch-api`,
`heerwisch-jfreechart`, `frau-holle`, `frau-holle-csv`, `frau-holle-eodhd`,
`nachtkrapp`).

## 0.48.1-alpha

### Changed

- **heerwisch-jfreechart:** sub-pane Y axis labels now read the indicator and its parameters — `RSI(14)`, `MACD(12,26,9)`, `Stoch(14,3,3)`, `ADX(14)`, `ATR(14)`, `BB(20)`, `SMA(20)`, `EMA(50)`, `Volume` — instead of the generic `SUBPLOT_1` / `SUBPLOT_2` fallback. Multiple indicators on one pane are joined with `" / "` (e.g. `RSI(14) / RSI(21)`). A pane with no indicator falls back to the `Pane` name (should not occur for a referenced subplot). Aligns with TradingView and similar tools, where the pane label names the indicator and its period. Spec: `heerwisch-jfreechart/CLAUDE.md` §7.1.

### Added

- **frau-holle-eodhd:** live smoke tests against EODHD's public demo endpoint (`api_token=demo`, AAPL.US) — `EodhdEodLiveSmokeIT` (1d) and `EodhdIntradayLiveSmokeIT` (1h). They run in Maven's `integration-test` phase via the Failsafe plugin (`*IT.java`); `./mvnw verify -DskipITs` skips them. In CI they run in a dedicated **soft-fail** job on push / pull_request (a red run surfaces the error log without blocking the workflow); the release pipeline runs the same ITs **hard-fail** so a broken live path blocks publishing. No EODHD subscription required — the demo token covers AAPL.US for free. Assert shape and invariants only (non-empty, OHLC valid, strictly ascending timestamps), never specific prices.

### Compatibility

- All changes additive / operational. No API surface change. japicmp clean.

## 0.48.0-alpha

### Added

- **frau-holle-eodhd:** support for EODHD's `/api/intraday` endpoint alongside the existing `/api/eod` endpoint. `EodhdMarketDataSource` now handles intraday timeframes `1m`, `5m`, `1h` in addition to daily / weekly / monthly. Endpoint dispatch is by `Timeframe.unit()`: `SECOND` / `MINUTE` / `HOUR` route to `/api/intraday`; `DAY` / `WEEK` / `MONTH` / `YEAR` route to `/api/eod`. Single class, single public entry point — consumers do not choose at construction.
- **frau-holle-eodhd:** intraday-specific behavior — `from` / `to` are passed as UNIX epoch **seconds** (not `YYYY-MM-DD`, EODHD's different convention for this endpoint); bar `time` is `Instant.ofEpochSecond(row["timestamp"])` (bar start in UTC, EODHD's `gmtoffset` and `datetime` fields ignored); pre-market and after-hours bars for US tickers are passed through as-is (no RTH filtering — filter downstream); intraday call cost is 5 EODHD API credits per request (vs 1 for EOD); 5m / 1h history available from October 2020, 1m varies by ticker.
- **frau-holle-eodhd:** unsupported intraday intervals (e.g. `15m`, `30m`, `2h`) are rejected with `MarketDataSchemaException` naming the supported set `{1m, 5m, 1h}`. Unsupported daily timeframes (e.g. `3d`, `2w`) are rejected with the supported set `{1d, 1w, 1M}`. The prior blanket "intraday rejected" message is replaced by these targeted messages.

### Compatibility

- Additive. Existing consumers passing `1d` / `1w` / `1M` continue to work unchanged (same endpoint, same URL shape, same response shape). Consumers passing intraday timeframes — previously rejected — now receive bars instead of an exception. japicmp clean (no public API surface change in commons or heerwisch-api; `frau-holle-eodhd`'s `EodhdMarketDataSource` constructor and `fetchHistory` signature are unchanged).

## 0.47.0-alpha

### Added

- **heerwisch-api:** three new `FillColor` enum values for `Annotation.TimeRangeHighlight` — `WIN`, `LOSS`, `OPEN` — for outcome-oriented trade-duration shading (the TradingView convention: green band = winning trade, red band = losing trade, neutral band = still-open trade at backtest end). Distinct from the existing direction-oriented values (`LONG_POSITION`, `SHORT_POSITION`, `NEUTRAL`, `CAUTION`); consumers pick the variant that matches their semantic intent. The enum-name carries the contract.
- **heerwisch-jfreechart:** three new `ThemeConstants` — `TIME_RANGE_WIN`, `TIME_RANGE_LOSS`, `TIME_RANGE_OPEN` — for the new `FillColor` variants. Currently share RGBs with `TIME_RANGE_LONG` / `TIME_RANGE_SHORT` / `TIME_RANGE_NEUTRAL` (green / red / blue-grey, the universally-recognized semantic palette); the renderer may differentiate the tones in future without API change.

### Compatibility

- Additive. Existing consumers of `FillColor.LONG_POSITION` / `SHORT_POSITION` / `NEUTRAL` / `CAUTION` continue to work unchanged. Adding enum values is structurally significant for exhaustive-switch consumers (they recompile to add the new arms or stop being exhaustive) but binary-additive — the same pattern PR #27 used cleanly when `FillColor` was originally introduced.

## 0.46.0-alpha

### Fixed

- **heerwisch-jfreechart:** the RSI sub-pane now renders the two horizontal threshold lines at the configured `overbought` and `oversold` values, and bounds the sub-pane Y axis to `[0, 100]` when the pane contains only RSI indicator(s). Both behaviors have always been specified by `heerwisch-jfreechart/CLAUDE.md` §7; the code previously rendered only the line. Existing consumers gain the threshold lines and bounded axis the spec already promised them. Mixed panes (RSI combined with an unbounded indicator such as MACD/ATR) preserve auto-range so unbounded siblings are not clipped.

### Added

- **heerwisch-api:** new optional `RsiVisualization` configuration record carried as a fifth optional argument on `Indicator.RSI`. Currently exposes a single `dangerZones` boolean that, when `true`, shades the regions above `overbought` and below `oversold` to highlight the danger zones. A backward-compatible 4-argument `RSI(period, overbought, oversold, priceSource)` constructor is preserved as an overload; existing callers built against it continue to work unchanged. The danger-zone toggle pattern is intended to generalize to other bounded indicators (e.g. `Stochastic`) in future PRs.
- **heerwisch-api:** three new `InvalidChartSpecException` rules:
  - **V19** — `RSI.overbought` MUST be ≤ 100.
  - **V20** — `RSI.oversold` MUST be ≥ 0.
  - **V21** — `RSI.oversold` MUST be strictly less than `RSI.overbought`.
  The canonical `RSI` constructor's prior bounds enforcement (positivity on `overbought` and `oversold`) moved into V19/V20/V21 so the malformed-input path produces `InvalidChartSpecException` uniformly. The canonical now enforces only nullness + `period ≥ 1`. `oversold = 0` (rejected previously) is now valid.
- **heerwisch-jfreechart:** two new `ThemeConstants` for RSI danger-zone fills: `RSI_OVERBOUGHT_ZONE` and `RSI_OVERSOLD_ZONE` (15% alpha, semantic red / green).
- **heerwisch-api:** new `Annotation` subtype `EntryExitMarkerAuto(Instant time, MarkerDirection direction, GlyphStyle glyphStyle)` for auto-positioned trade markers. The renderer derives the Y position from the bar at `time`: `LONG_ENTRY` / `SHORT_EXIT` glyphs sit below the bar's low, `LONG_EXIT` / `SHORT_ENTRY` glyphs sit above the bar's high. Matches industry convention (TradingView and similar tools); avoids the visual overlap that occurs when consumers pass `bar.close()` as the explicit price to `EntryExitMarker`. **Recommended** for visualizing trade entries and exits. V16 generalizes to both `EntryExitMarker` and `EntryExitMarkerAuto`.
- **heerwisch-jfreechart:** new `ThemeConstants.GLYPH_OFFSET_FACTOR_BAR` (default `1.5`) controls the offset distance of the auto-positioned glyph from the bar's high/low. Sized in glyph-half-height units so it adapts to the chart's aspect ratio via the same machinery that sizes the glyph itself.
- **heerwisch-api:** existing `EntryExitMarker(time, price, direction, glyphStyle)` remains first-class and unchanged. Recommended for pinning markers to specific Y coordinates not tied to a bar's high/low — target levels, limit-order prices, indicator-driven alerts.

### Compatibility

- Additive across the board — consumers of v0.45.0-alpha rebuild without changes. Adding a record component to `RSI` is structurally significant; japicmp reports it clean against 0.45.0-alpha because the 4-arg overload constructor preserves the original constructor signature and the new `visualization()` accessor is purely additive. Adding `EntryExitMarkerAuto` to the sealed `Annotation` hierarchy is also clean: the same pattern PR #27 used when adding `EntryExitMarker` and `TimeRangeHighlight` — consumers with exhaustive `switch (annotation)` blocks recompile, but binary callers are unaffected.

## 0.45.0-alpha

### Added

- **heerwisch-api:** two new `Annotation` subtypes for trade visualization:
  - `EntryExitMarker(Instant time, BigDecimal price, MarkerDirection direction, GlyphStyle glyphStyle)` — a chunky semantic glyph at a specific bar; `MarkerDirection` is `LONG_ENTRY`/`LONG_EXIT`/`SHORT_ENTRY`/`SHORT_EXIT`, `GlyphStyle` is `UP_TRIANGLE`/`DOWN_TRIANGLE`/`ARROW_UP`/`ARROW_DOWN`. Color is renderer-applied from `MarkerDirection`.
  - `TimeRangeHighlight(Instant startTime, Instant endTime, FillColor fillColor, BigDecimal opacity)` — a semi-transparent shaded band over a closed time interval; `FillColor` is `LONG_POSITION`/`SHORT_POSITION`/`NEUTRAL`/`CAUTION`; `opacity` in `[0, 1]`.
  - Three new closed enums: `MarkerDirection`, `GlyphStyle`, `FillColor`. `MarkerDirection` (not `Direction`) avoids a simple-name clash with `org.hatrack.frauholle.model.Direction`.
- **heerwisch-api:** three new `InvalidChartSpecException` rules:
  - **V16** — every `EntryExitMarker.time` must equal some `bar.time` in the series (symmetric with V7).
  - **V17** — `TimeRangeHighlight.startTime < endTime` and the range must overlap the series.
  - **V18** — `TimeRangeHighlight.opacity` must be in `[0, 1]` inclusive.
- **heerwisch-jfreechart:** renderer support for the two new subtypes via JFreeChart's `XYShapeAnnotation` (glyph at `(time, price)`) and `IntervalMarker` on the domain axis at `Layer.BACKGROUND` (time-range band drawn behind candles).
- **heerwisch-jfreechart:** four new `ThemeConstants` for `TimeRangeHighlight` fills: `TIME_RANGE_LONG`, `TIME_RANGE_SHORT`, `TIME_RANGE_NEUTRAL`, `TIME_RANGE_CAUTION` (RGB only; opacity is applied per-instance from the annotation).
- **heerwisch-api:** `GlyphStyle` now carries a documented semantic contract: `UP_TRIANGLE`/`DOWN_TRIANGLE` for **scheduled** (strategy-scenario-driven) entry/exit events; `ARROW_UP`/`ARROW_DOWN` for **forced** (stop-loss / take-profit / trailing-stop / time-based / end-of-backtest) events. Consumers should map their exit categories per the reference table in `heerwisch-api/CLAUDE.md` §1.3.1. The renderer's geometry reinforces the distinction — triangles render as compact solid shapes (visually prominent, signaling a deliberate decision), arrows as a lighter chevron+shaft silhouette (signaling mechanical execution). The asymmetry is the API contract; future drivers must preserve it.

### Changed

- **heerwisch-jfreechart:** `EntryExitMarker` glyph half-extents are now computed adaptively from the series' smallest bar interval and the chart's pixel aspect, rather than fixed at ≈12h on the time axis and ≈0.5% of price on the value axis. The glyph width tracks one candle on any chart density, any aspect ratio, any device; the height is scaled so the glyph reads as roughly square in pixel space. The previous fixed extents produced flat horizontal slivers on zoomed-in charts (e.g. 1h bars over ~2 days) and bloated markers on wide-aspect layouts. The TRIANGLE-vs-ARROW visual asymmetry (reinforcing the scheduled-vs-forced semantic contract from §1.3.1) is preserved at every size. Single-bar series — legal under V2 — fall back to the previous fixed extents (no period or aspect signal to scale to). Spec: `heerwisch-jfreechart/CLAUDE.md` §8.1.

### Fixed

- **heerwisch-jfreechart docs:** `CLAUDE.md` §8 previously described `BarHighlight` as *"a small triangle marker at (time, price) colored by direction"* — the implementation has always been a text-only `XYTextAnnotation`. The doc is now corrected to match the code. The directional-glyph use case is served by the new `EntryExitMarker`.

### Compatibility

- Additive across the board — consumers of v0.43.0-alpha rebuild without changes. The `Annotation` switch in any downstream code remains exhaustive only if the consumer wrote one against a sealed interface (Java will require new arms for the new variants on recompile); consumers that consume `Annotation` polymorphically (just iterating) are unaffected.

## 0.43.0-alpha

- **All modules:** `-sources.jar` is now built in the default build and
  published to Maven Central alongside the main jar, so consumers get
  IDE-readable API without decompiling. `-javadoc.jar` continues to be
  attached only on tagged releases via the `-Prelease` profile (javadoc
  generation and GPG signing remain release-only to keep day-to-day builds
  fast).
- **nachtkrapp:** `PatternMatch` now declares an explicit `permits` clause
  listing all 19 concrete subtypes. No behavioral or binary change — the
  interface was already implicitly sealed via nested compilation-unit
  inference; the explicit clause makes the closed set visible at the
  declaration and in generated javadoc. External code must not implement
  `PatternMatch` directly; new variants are requested by addition to the
  `permits` clause.
