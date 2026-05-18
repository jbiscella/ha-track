# CLAUDE.md — Heerwisch / Frau Holle / Nachtkrapp (root)

This is the authoritative specification document for this repo, which hosts three independent Java libraries and a shared kernel, plus reference implementations of pluggable data drivers. All module names are drawn from **Continental Germanic folklore**, deliberately matching the convention of the consumer project (`hütchen` → kobold custodian / mischief-maker from West Germanic tradition). The dualism custodian / trickster is intentional and runs through every name: each library helps the user but can also mislead.

- **`heerwisch`** — chart plotting library (Low German variant of the will-o-the-wisp; the wandering bog light that reveals or conceals the path)
- **`frau-holle`** — backtesting library (West Germanic goddess of spinning, weaving, and the household; rewards diligent work and punishes negligence)
- **`nachtkrapp`** — pattern detection library (German nocturnal mythical raven; sees in the dark, recognizes who deserves what, brings tidings — true or punishing)

Claude Code reads this and uses it as the primary source of truth. Specifications are written at the level Claude Code cannot infer on its own; idiomatic Java code, file layout, naming, and library boilerplate are explicitly **delegated** to Claude Code.

The root document fixes only what applies to the whole repo. Per-module specs — including behavioral specifications in Gherkin — live in the nested `<module>/CLAUDE.md` files. Claude Code loads nested files lazily when it reads code in that module; the root survives `/compact`, the nested files reload on next access.

## How to read this document

| Section | What it gives you |
|---|---|
| Module map | The modules in this repo, their purpose, where to read deeper |
| ADR | Stack, build, API style — non-negotiable across the repo |
| Architectural principles | Design rules that hold across all modules |
| Cross-module dependency rules | What may depend on what; what must not |
| Shared data model | What `commons` exposes and what is forbidden to put there |
| API design conventions | Builder + spec, sealed hierarchies, validation, exceptions |
| Code style | Java idioms enforced repo-wide |
| Future direction | Roadmap visible from v1 onward |
| Out of scope of the root | What belongs in nested `CLAUDE.md` files |

---

## Module map

The repo has 7 modules in v1: 3 library API surfaces + 1 plotting driver + 2 reference market-data drivers + the shared kernel. The optional `frau-holle-<source>` slot can host additional market-data drivers in the future.

| Module | Purpose | Dependencies | Nested spec |
|---|---|---|---|
| `commons` | Data types + pure functions, stateless. Shared kernel between the libraries. Zero external dependencies (JDK only). | none | `commons/CLAUDE.md` |
| `heerwisch-api` | Port + immutable spec types (`ChartSpec`, `Series`, `Indicator`, `Annotation`, `LayoutSpec`) + checked exceptions for the plotting library. No driver implementation. | `commons` | `heerwisch-api/CLAUDE.md` |
| `heerwisch-jfreechart` | Default plotting driver. Consumes `ChartSpec`, produces `ChartImage` (PNG or JPEG). Headless, dual runtime (Lambda-compatible + writable to file by caller). | `heerwisch-api`, `commons`, JFreeChart 1.5.x | `heerwisch-jfreechart/CLAUDE.md` |
| `frau-holle` | Backtester. Exposes the `MarketDataSource` port for plug-in data providers and the `SignalGenerator` port for opaque consumer strategies. Single module: there is no separate API artifact and no separate "engine driver". | `commons` | `frau-holle/CLAUDE.md` |
| `frau-holle-csv` | Reference `MarketDataSource` implementation reading OHLC bars from local CSV files. Baseline, zero HTTP dependencies. | `frau-holle`, `commons` | `frau-holle-csv/CLAUDE.md` |
| `frau-holle-eodhd` | Reference `MarketDataSource` implementation hitting the EODHD End-of-Day API. Ported from the consumer project's existing EODHD adapter. | `frau-holle`, `commons` | `frau-holle-eodhd/CLAUDE.md` |
| `nachtkrapp` | Pattern detection. Exposes the `PatternDetector` entry point and contains its rule-based implementation. Single module: there is no separate API artifact, and there is no "driver" abstraction — alternative implementations (e.g. ML-based) would be **separate, independent modules**, not interchangeable plug-ins. | `commons` | `nachtkrapp/CLAUDE.md` |

The three libraries (`heerwisch`, `frau-holle`, `nachtkrapp`) are **independent**: none imports another. A consumer that needs more than one composes them externally.

### Why heerwisch splits into `-api` + `-jfreechart` but `frau-holle` and `nachtkrapp` do not

The split is justified only where multiple intentionally-interchangeable implementations are anticipated. For `heerwisch` the underlying rendering engine is genuinely swappable (JFreeChart today, a vector engine tomorrow), so the API/driver split pays for itself.

For `frau-holle` the equivalent of "swappable engine" is not multiple backtesters — backtesting is the library, not a strategy. Where backtesting genuinely needs pluggability is the **market data source**, captured by the `MarketDataSource` port in `frau-holle` and implemented in `frau-holle-csv`, `frau-holle-eodhd`, and any future sibling.

For `nachtkrapp`, a hypothetical ML-based pattern detector would emit probabilistic, confidence-scored outputs incompatible with the v1 `PatternMatch` sealed hierarchy; it would belong in its own module, not slot into the existing one.

---

## 1. Architecture Decision Record (ADR)

| Layer | Choice |
|---|---|
| Language | **Java 25** (Amazon Corretto, aligned with the consumer project that drove this work) |
| Build tool | **Maven**, multi-module |
| Module style | Each module is a separate Maven artifact, publishable independently |
| API style | **Builder fluent + immutable spec record**; sealed hierarchies for closed sets of variants; eager validation in `build()` |
| Decimal arithmetic | **`BigDecimal`** with `MathContext.DECIMAL64`. No `double`, no `float` in business logic |
| Time | **`Instant` UTC** end-to-end |
| External dependencies in `commons` | **forbidden** (JDK only) |
| Plotting underlying library | **JFreeChart 1.5.x** (in the `heerwisch-jfreechart` driver only; abstracted away from `heerwisch-api`) |
| Plotting driver naming | `heerwisch-<engine>` where `<engine>` identifies the rendering backend |
| Market data source pluggability | Captured by the `MarketDataSource` port in `frau-holle`; implementations live in `frau-holle-<source>` modules |
| Fill price in backtester simulation | **Real OHLC**, never HA values (structural property of order execution) |

### Architectural principles

| Principle | Practical consequence |
|---|---|
| Three independent libraries, one shared kernel | `heerwisch-*`, `frau-holle`, `nachtkrapp` never import each other; all import `commons` |
| `commons` is general-purpose, not domain-rich | Types contain only what every consumer would use; persistence/transport fields belong in consumer code |
| Immutability by default | `record` for data types and specs |
| Sealed hierarchies where the set is known | Closed variant sets are sealed; consumers cannot add variants |
| Builder produces immutable spec | Construction is the only side of the API where mutation is allowed |
| Eager validation | `build()` fails with a typed exception at the call site, not during render/backtest/detection |
| Pure functions for calculation | HA computation, indicators, indicator-derived signals — no I/O, no clocks, no state |
| Lambdas are not part of any spec | Specs are pure data and reasonably inspectable / loggable |
| Plotting output is destination-agnostic | The core produces `ChartImage` (bytes + content type + size); writing to file is the caller's responsibility |
| Libraries compose only at the consumer | The three libraries don't know about each other |
| API/driver split only where interchangeable implementations exist | Applied to `heerwisch`. NOT applied to `frau-holle` core or `nachtkrapp` |
| **Lookahead-safety** is a repo-wide invariant | No value, signal, or match at time `t` may depend on bars `> t`. Applies to indicator calculators, pattern matches, and backtest signals. Future pivot-detection algorithms (out of v1) MUST be repaint-safe |
| Multi-timeframe orchestration is consumer-side | No library in this repo natively orchestrates across multiple timeframes. The consumer loops over timeframes and composes results |

---

## 2. Cross-module dependency rules

These rules are enforced architecturally and must not be relaxed.

| Edge | Allowed? |
|---|---|
| `commons` → anything in this repo | no |
| `heerwisch-api` → `commons` | yes |
| `heerwisch-api` → `heerwisch-jfreechart` | no |
| `heerwisch-api` → `frau-holle*` or `nachtkrapp` | no |
| `heerwisch-jfreechart` → `heerwisch-api`, `commons` | yes |
| `heerwisch-jfreechart` → JFreeChart | yes |
| `heerwisch-jfreechart` → `frau-holle*` or `nachtkrapp` | no |
| `frau-holle` → `commons` | yes |
| `frau-holle` → `heerwisch-*` or `nachtkrapp` | no |
| `frau-holle-csv` → `frau-holle`, `commons` | yes |
| `frau-holle-csv` → JDK file I/O | yes |
| `frau-holle-csv` → external HTTP libraries | no (CSV is local-only) |
| `frau-holle-csv` → `heerwisch-*` or `nachtkrapp` | no |
| `frau-holle-eodhd` → `frau-holle`, `commons` | yes |
| `frau-holle-eodhd` → HTTP client + JSON parser of the implementor's choice | yes |
| `frau-holle-eodhd` → `heerwisch-*` or `nachtkrapp` | no |
| `nachtkrapp` → `commons` | yes |
| `nachtkrapp` → `heerwisch-*` or `frau-holle*` | no |
| Any module → reflection-based bean discovery | no |
| Library core modules (`commons`, `heerwisch-api`, `frau-holle`, `nachtkrapp`) → DI framework | no (the libraries are framework-agnostic; the consumer wires beans) |

Driver modules (`heerwisch-jfreechart`, `frau-holle-csv`, `frau-holle-eodhd`) MAY declare DI-friendly types (no-arg or all-arg constructors, `final` fields, no static state), but MUST NOT depend on a DI framework themselves. The consumer is responsible for bean wiring.

---

## 3. Shared data model in `commons`

`commons` exposes "slim" general-purpose types. Domain-specific fields (instrument IDs, ingestion timestamps, data provenance) belong to consumer code, not here.

| Type | Essential fields | Forbidden fields |
|---|---|---|
| `OHLCBar` | `Instant time`, `BigDecimal open/high/low/close`, `Optional<BigDecimal> volume`, plus `validateInvariants()` | instrument id, source/provenance, ingestedAt |
| `HABar` | `Instant time`, `BigDecimal haOpen/haHigh/haLow/haClose` | instrument id, computedAt |
| `Timeframe` | Open type — pattern-string-based (`"1m"`, `"5m"`, `"1h"`, `"1d"`, `"1w"`, `"1M"`, …) | closed enum bound to specific values |
| `HeikinAshiCalculator` | Pure static methods: `compute(prev, ohlc)`, `computeChain(prev, ohlcs)` | any I/O, any clock, any mutable state |
| `PriceSource` enum | `OPEN`, `HIGH`, `LOW`, `CLOSE`, `HA_OPEN`, `HA_HIGH`, `HA_LOW`, `HA_CLOSE` | — |
| `Series` | `sealed interface Series permits OHLCSeries, HASeries`; each variant wraps a defensively-copied bar list | ordering/uniqueness/non-emptiness checks (those are consumer-builder rules) |

Consumers that need a richer domain wrap or compose `commons` types in their own. The mapping happens at consumer boundaries, not inside `commons` or the lib modules.

OHLC invariants enforced by `validateInvariants()`: all four prices strictly positive; `high ≥ low`; `high ≥ open`; `high ≥ close`; `low ≤ open`; `low ≤ close`; `volume ≥ 0` when present. Violation throws an `OHLCInvariantViolationException` (defined in `commons`).

---

## 4. API design conventions

These conventions are repo-wide.

### 4.1 Builder + immutable spec

A public API surface that builds a structured input follows:

1. The input is an immutable record (or a `sealed` hierarchy of records) — the **spec**.
2. A fluent builder constructs the spec. `Builder.build()` returns the immutable record.
3. `build()` performs eager validation. Invalid combinations throw a typed exception at the call site.
4. The library code consumes the spec and produces an output.
5. The spec is **pure data**: no lambdas, no function references, no closures inside fields.

### 4.2 Sealed hierarchies

When a set of variants is closed and authored by the library (not the consumer), use `sealed interface … permits …` with `record` implementations.

Consumers cannot extend sealed hierarchies in v1.

### 4.3 Naming convention in builders

| Cardinality of the field set by the method | Method prefix |
|---|---|
| 1 (replaces previous value) | `withX` |
| N (appends) | `addX` |
| N (replaces with a collection) | `addAllX` / `withAllX` |

### 4.4 Exception hierarchy convention

Each library defines a single root exception, and concrete subtypes for each distinct failure cause. The root is checked. Causes that share the same handling MUST NOT be split into separate types; causes that differ in handling MUST be separate.

Distinct = the caller would write a different `catch` block for it. Same handling = single type.

### 4.5 Default behavior vs explicit override

Where a parameter has an obvious default that fits most consumers, expose a `defaults()` factory. Where the consumer wants to override, expose a sub-builder.

---

## 5. Code style

| Rule | Why |
|---|---|
| `record` for data types and specs | Equality, immutability, accessors for free |
| `sealed` + `record` for closed variant sets | Exhaustive pattern matching, no surprise subclasses |
| `BigDecimal` everywhere prices, ratios, volumes appear | No floating-point drift across the boundary between libraries |
| `Instant` UTC everywhere time appears | No ambiguity, no timezone leakage |
| Constructor-only injection in non-API types | Final fields, immutable, testable |
| No null in API surface — use `Optional` for optional values | Predictable contracts |
| Eager parameter validation (`Objects.requireNonNull`, range checks) | Errors at construction, not at usage |
| No checked-exception swallowing | Either handle or propagate |
| No `static` mutable state in any module | Thread-safety, testability |
| No reflective bean wiring inside the libraries | The libraries are framework-agnostic; the consumer brings DI |

---

## 6. Future direction (informs v1 choices)

The repo has a future direction that does not constrain v1 deliverables but justifies certain v1 design choices.

| Future scenario | v1 design choice it justifies |
|---|---|
| `nachtkrapp` may eventually be wrapped as a commercial pattern-detection service for brokers (B2B), competing in a market dominated by Autochartist and similar players | API surface MUST be stateless and thread-safe; sealed hierarchies leave room for additive evolution; library is framework-agnostic so a future service layer can wrap it without rewrite |
| Indicator calculator code (SMA, EMA, RSI, MACD, …) is needed by both `nachtkrapp` (for primitives like `PriceCrossedAboveMA`) and `heerwisch-jfreechart` (for rendering indicator overlays) | v1 tolerates duplication. If drift emerges, extract into a new `indicators` module in v1.1 — naming convention is reserved |
| `nachtkrapp` will add chart patterns (head & shoulders, triangles, …) in v2, requiring pivot detection | Pivot detection is out of v1 in this repo. When added in v2, it MUST be repaint-safe (lookahead-safety invariant) |
| A vector plotting engine (SVG) may eventually be added as an alternative to JFreeChart | `heerwisch-api` is already split from its driver; a new `heerwisch-<engine>` module can be added without touching the API |
| A YAML / DSL strategy compiler may eventually be built on top of `frau-holle` | The `SignalGenerator` port is opaque — a future `frau-holle-yaml` module can compile YAML into a `SignalGenerator` implementation without touching the core |

This section is **forward-looking**, not binding.

---

## 7. Out of scope of the root

The root **does not** specify:

- Per-module behavioral scenarios (Gherkin GWT) — those live in `<module>/CLAUDE.md`
- Per-module data model details (exact field lists, computed properties) — those live in `<module>/CLAUDE.md`
- Test pyramid, coverage gates, test infrastructure — out of v1
- CI behaviour beyond the committed `build & verify` workflow — a GitHub Actions workflow (`.github/workflows/ci.yml`) runs the full test suite on every push and pull request; the root does not specify CI/CD further
- Release cadence and version-bump policy — Maven Central publishing **is** in scope (artifacts published under the `net.jacopobiscella` namespace; the machinery lives in the root POM `release` profile), but *when* releases are cut and how versions are bumped is left unspecified
- Pivot detection (any algorithm) — out of v1; reserved for v2 when chart-structural patterns become scope
- Compound rule DSL for pattern composition — explicitly out of v1; consumers compose primitives in their own code
- Multi-timeframe orchestration — explicitly consumer-side, not a library concern in v1
- Slippage / commission / fill-timing models in backtester — `frau-holle` v1 is frictionless; documented escape hatch in `frau-holle/CLAUDE.md`

When a consumer or another agent needs information that is not in the root, they descend into the relevant module's nested `CLAUDE.md`.
