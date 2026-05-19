# Changelog

All notable changes to this repository are documented here. Versions are
shared across all reactor modules (`commons`, `indicators`, `heerwisch-api`,
`heerwisch-jfreechart`, `frau-holle`, `frau-holle-csv`, `frau-holle-eodhd`,
`nachtkrapp`).

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
