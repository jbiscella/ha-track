# Changelog

All notable changes to this repository are documented here. Versions are
shared across all reactor modules (`commons`, `indicators`, `heerwisch-api`,
`heerwisch-jfreechart`, `frau-holle`, `frau-holle-csv`, `frau-holle-eodhd`,
`nachtkrapp`).

## 0.44.0-alpha

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
