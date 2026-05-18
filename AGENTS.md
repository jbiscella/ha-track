# AGENTS.md — ha-track repo

This file is read by AI agents that work on this repository (in particular OpenAI Codex code review). It captures the review guidelines and project conventions that the author expects every reviewer (human or AI) to apply.

## Project overview

`ha-track` is a Java 25 Maven multi-module repository containing seven independent library modules plus a future end-user application. The modules implement, in different roles, a financial backtesting / charting / pattern-detection stack rooted in Heikin Ashi candle analysis. The naming theme is continental Germanic folklore (Heerwisch, Frau Holle, Nachtkrapp; the consumer application is Wichtelm-app).

|Module                |Role                                                                    |
|----------------------|------------------------------------------------------------------------|
|`commons`             |shared kernel — JDK-only types, zero external dependencies              |
|`heerwisch-api`       |plotting library port + spec types                                      |
|`heerwisch-jfreechart`|default plotting driver, JFreeChart 1.5                                 |
|`frau-holle`          |backtesting library, with `SignalGenerator` and `MarketDataSource` ports|
|`frau-holle-csv`      |reference data driver, local CSV files                                  |
|`frau-holle-eodhd`    |reference data driver, EODHD End-of-Day API                             |
|`nachtkrapp`          |pattern detection library — HA patterns + MA/RSI/MACD primitives        |

A `wichtelm-app` module (or sibling repo) is planned as the end-user backtesting application; its design is captured in a draft outside this repo.

## Authoritative specifications

The repository is **spec-driven**. Each module has its own `CLAUDE.md` file that is the authoritative specification for that module’s behavior. The repo root has a `CLAUDE.md` that is the authoritative source for repo-wide architectural decisions, dependency rules, and code style. These files take precedence over inline code comments, README content, or general best practices.

When reviewing changes, always cross-check the modification against the relevant `CLAUDE.md`. Drift between code and spec is a high-priority finding. If a change is correct against the spec but the spec is wrong, flag both: the code review and the spec review are distinct.

## Review guidelines

These guidelines override default review behavior where they conflict. They are listed in rough priority order.

### Critical — flag as P0

- **Spec drift**: any change that contradicts the relevant `CLAUDE.md` (root or module). If the change is correct and the spec is outdated, say so explicitly — do not silently approve.
- **Lookahead-safety violations**: this is a repo-wide invariant. No value, signal, or pattern match at bar time T may depend on bars at times > T. The repo has explicit, tested guarantees for this (see `frau-holle/CLAUDE.md` §15 and `nachtkrapp/CLAUDE.md` §6). Any change that introduces a path through which future data leaks into a present computation must be flagged.
- **Decimal arithmetic correctness**: prices, ratios, quantities, and P&L values use `BigDecimal` with `MathContext.DECIMAL64`. Any introduction of `double` or `float` in business logic is a defect. Any `BigDecimal` operation without explicit `MathContext` is a defect.
- **OHLC invariant enforcement at spec boundaries**: data sources do NOT validate per bar (performance), but spec builders (BacktestSpec, DetectionSpec, ChartSpec) MUST validate OHLC invariants of the input series. Bypassing this is a defect.
- **Concurrent state**: `nachtkrapp.PatternDetector` MUST be thread-safe; other library entry points have their own threading contracts documented in their `CLAUDE.md`. Any introduction of static mutable state or non-thread-safe state in a type that requires thread-safety is a defect.

### High — flag as P1

- **Sealed hierarchy violations**: `Signal`, `DetectionRule`, `PatternMatch`, `Series`, `Indicator`, `Annotation`, `LayoutSpec` and similar are `sealed` interfaces. Consumers cannot extend them. New variants in the same module are additive and must be reflected in both the spec and exhaustive `switch` callers.
- **Builder validation gaps**: builders use eager validation in `build()`. Each builder enumerates explicit validation rules (V1, V2, V3, …) in its `CLAUDE.md`. A new builder method that does not extend the validation rule list is suspicious.
- **Exception hierarchy purity**: each module has a single root exception (e.g. `BacktestException`, `DetectionException`, `ChartRenderException`). New error conditions must subclass the module root, carry typed contextual fields, and be documented in the spec.
- **Test infrastructure**: the repo uses **Cucumber for Java + JUnit Platform**. Feature files live in `src/test/resources/features/` of each module. New behavioral changes require new Gherkin scenarios; the Gherkin scenarios in the spec (`CLAUDE.md`) and the feature files must stay aligned.
- **Naming conventions**: builder methods use `withX` for cardinality 1 (replace) and `addX` / `addAllX` for cardinality N (append). Spec types are records or sealed records. Implementation classes that should be DI-friendly have no-arg or all-arg constructors with `final` fields and no static state.
- **No DI framework in library modules**: the seven library modules MUST NOT depend on Spring, Micronaut, Guice, or any DI framework. The libraries are framework-agnostic; the consumer wires beans externally.

### Medium — flag as P2

- **Performance regressions in hot paths**: bar-by-bar loops in backtester, indicator calculators, chart rendering should not allocate unnecessary objects inside the loop. Loops over series should not have O(N²) when O(N) is achievable.
- **Documentation drift**: JavaDoc that contradicts the spec or behavior is a defect, even when the code is correct.
- **Dependency creep**: each module’s allowed dependencies are listed in the root `CLAUDE.md` §2. Adding a dependency outside that list is a defect. The `commons` module in particular has zero external dependencies and that constraint is absolute.
- **Test coverage gaps**: math-heavy code (indicators, metrics) and edge cases (empty series, single-bar series, no-trade backtests, division-by-zero, NaN/infinity propagation) deserve explicit scenarios. Acceptance-level tests through Cucumber are the standard, but reviewers should call out edge cases that are not covered.

### Low priority — context-dependent

- Style consistency, naming clarity, JavaDoc completeness on private members, etc. Flag only if egregious.
- Suggestions for refactoring that do not affect correctness: include only if the existing code is genuinely hard to follow.

## Things that look wrong but are deliberate

These are conventions that an AI reviewer might flag as defects but are correct per the spec. Do NOT flag them:

- **`profitFactor` returns `BigDecimal.ZERO` when there are no losing trades** — this is a documented sentinel value meaning “undefined”. Per spec, infinity is not representable as BigDecimal.
- **Indicator calculators are duplicated between `nachtkrapp` and `heerwisch-jfreechart`** — this is acknowledged in the root `CLAUDE.md` §6 as a v1 tolerated duplication, to be resolved by extracting a shared `indicators` module in v1.1 if drift emerges.
- **`OHLCBar.validateInvariants()` is opt-in and not called by the canonical constructor** — this is deliberate; validation happens at spec-builder boundaries and at data-source schema parse time (CSV), NOT per-bar.
- **EODHD API token is in the URL query string** — EODHD does not support header-based authentication. The mitigation is “never log the URL at INFO+”, documented in `frau-holle-eodhd/CLAUDE.md`.
- **`frau-holle-csv` and `frau-holle-eodhd` do NOT validate OHLC invariants** — by design, validation is downstream at spec boundaries.
- **Java 25 is the target version** — deliberate, not under review. Do not suggest downgrades.
- **`commons` has zero external dependencies (not even SLF4J for logging)** — absolute constraint, do not suggest adding any.

## Things the reviewer is encouraged to check that may not be obvious

- Frequent ground-truth mistake to verify: any new `BigDecimal` arithmetic without `MathContext.DECIMAL64`, especially in `divide()` which throws on non-terminating decimals by default.
- Verify that `equals()` and `hashCode()` on records with `BigDecimal` fields handle scale correctly (two `BigDecimal` values “10” and “10.00” are NOT equal under default `equals`). If the codebase uses `compareTo()` or normalizes scale before comparison, leave it; flag if a record relies on default `BigDecimal.equals()` for business-critical equality.
- Verify that `Instant` comparisons use `isBefore`/`isAfter`/`equals` rather than ordinal comparison.
- Verify that `Optional` fields in records are never `null` (records cannot enforce non-null on `Optional`; the canonical constructor must validate).
- Verify that defensive copies of collections in records use `List.copyOf(...)` to produce immutable copies, NOT `new ArrayList<>(...)`.

## What NOT to do

- Do NOT suggest replacing `BigDecimal` with `double` for “performance reasons”. The precision requirement is non-negotiable.
- Do NOT suggest replacing `Instant` with `LocalDateTime` or `long` for “simplicity”. The UTC discipline is non-negotiable.
- Do NOT suggest replacing the Cucumber test infrastructure with pure JUnit. The BDD/Gherkin approach is deliberate.
- Do NOT suggest adding a DI framework to the library modules.
- Do NOT suggest splitting the modules differently. The 7-module structure was deliberated and documented.
- A GitHub Actions `build & verify` workflow exists (`.github/workflows/ci.yml`). Do NOT suggest additional CI/CD pipeline files unless explicitly asked.
- Maven Central publishing under the `net.jacopobiscella` namespace is in scope; the root POM `release` profile carries the setup (source/javadoc jars, GPG signing, `central-publishing-maven-plugin`). The modules are still SNAPSHOT — do NOT flip versions to a release or actually publish artifacts unless explicitly asked.
- Do NOT suggest implementing planned future features (anything labeled “reserved for future enhancement” or in module §15 “Planned extensions”) as part of a PR review. Flag only if the current PR claims to implement them and does so incompletely.

## Severity legend used by this project

- **P0** — must fix before merge. Spec drift, correctness, safety violations.
- **P1** — should fix before merge unless an explicit decision is recorded to defer.
- **P2** — should fix soon; can defer with comment.
- **P3** (nit) — optional improvement, no blocker.

Codex is configured to flag only P0 and P1 by default; if the reviewer (Codex or human) finds material P2/P3 items, they may be raised explicitly.

## Working protocol context (informative)

The repository author (Jacopo) works with the following protocol that the reviewer should respect when worded suggestions are made:

- Direct, concise communication. Conclusions first, then arguments.
- Decisions presented as tables: options as columns, criteria as rows; cells short.
- Behavioral specifications written in Gherkin Given/When/Then (English).
- One open question at a time when consulting the author.
- All factual claims about libraries / versions / pricing must be web-verified, not memory-based.
- No architectural defaults assumed (no REST, no Postgres, no Docker, no CI/CD) unless explicitly in the spec.

Review comments should follow the same protocol where applicable: short, factual, evidence-based, and prioritized.
