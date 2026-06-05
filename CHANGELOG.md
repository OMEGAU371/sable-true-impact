# Changelog

All notable changes to this project will be documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).  
Version scheme: `MAJOR.MINOR.PATCH-PHASE[-build]`

---

## [0.2.0-sable-study] — 2026-06-06

Gate 1A: Phase 1A documentation and diagnostic experiments implemented.
Git commit: (see below)

### Added
- Sable `compileOnly` dependency (`sable-common-1.21.1:1.2.2`, `sable-companion-common-1.21.1:1.6.0`)
- `true_impact.mixins.json` — mixin configuration for 3 observation mixins
- `observation/` package: `BodySnapshot`, `RawContactRecord`, `SnapshotPhase`, `BodyType`,
  `DiagnosticConfig`, `GlobalRateLimiter` — all pure Java, no Sable API, no client-class refs
- `diagnostic/` package: `ExperimentLog`, `ContactLogger`, `T4ApplyForceExperiment`
- `sable/` package: `SableBodyReader`, `SableEventBridge`, `SableT4Command`
  (only loaded when Sable present — optional-dependency pattern)
- `mixin/DiagnosticPhysicsStepMixin` — PRE/POST body snapshot capture via mixin on SubLevelPhysicsSystem
- `mixin/DiagnosticCallbackWrapperMixin` — T-1/T-2 callback thread + coord observation
- `mixin/DiagnosticContactCaptureMixin` — T-3/T-5/T-6 raw contact array capture
- `command/DiagnosticCommand` — `/trueimpact debug` and `/trueimpact experiment t4` commands
- 4 documentation files: `docs/sable-integration-study.md`, `docs/coordinate-systems.md`,
  `docs/physics-invariants.md`, `docs/phase-1-observation-proposal.md`
- 19 new tests (total 39): BodySnapshotTest (4), GlobalRateLimiterTest (5), RawContactRecordTest (6),
  FoundationArchTest extended to 8 (added Phase 1A arch rules)

### Safety verified
- No-Sable runServer: mixin targets not found → graceful WARN, not crash
- All diagnostics default OFF; no production behavior changes
- `observation/diagnostic` packages verified by ArchUnit to not depend on future `damage/`
- `GlobalRateLimiter` hard limits enforced unconditionally (tests prove no exceptions)

---

## [0.1.0-foundation] — 2026-06-05

Gate 0 passed. Verified: build, 20 tests, server loads clean.  
Git commit: `82bdddd`

### Added
- NeoForge 1.21.1 / Java 21 project scaffold (package `io.github.omegau371.trueimpact`)
- `src/main/templates-java/TrueImpactVersion.java` — auto-generated from `gradle.properties`;
  `mod_version` is the single source of truth, no hardcoded version constant in production code
- `platform/DistInfo` — dist detection, Sable presence, mod version lookup;
  no client-only class references, safe on dedicated server
- `command/StatusCommand` — `/trueimpact status` reads TI version from live `ModContainer`
- `TrueImpactMod` — `@Mod` entry point, NeoForge event bus wiring
- Sources JAR via `java.withSourcesJar()`
- JAR `MANIFEST.MF` embeds `TI-Project-Line: rewrite2` for deploy safety identification
- Gradle `deploy` task:
  - Selects main JAR by explicit name `${mod_id}-${mod_version}.jar` (no `listFiles()[0]`)
  - Deletes only JARs with `TI-Project-Line: rewrite2` manifest attribute
  - Never touches legacy v1.x JARs, Sable, Create, or other mods
  - Never copies `-sources` or `-javadoc` JARs to mods directories
- 20 tests (all pass):
  - 4 ArchUnit: layer-boundary rules (platform↛command, no client-class refs)
  - 12 DeployFilter: safety assertions covering legacy JARs, third-party mods, corrupt files
  - 4 Unit: MOD_ID and VERSION constant format validation
- Git repository initialized with initial commit
- Documentation: README, CLAUDE.md, docs/architecture.md, docs/rewrite-roadmap.md, docs/acceptance-gates.md
