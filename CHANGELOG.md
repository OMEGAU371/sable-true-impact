# Changelog

All notable changes to this project will be documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).  
Version scheme: `MAJOR.MINOR.PATCH-PHASE[-build]`

---

## [0.4.9-phase1c] — 2026-06-12

Gate 1C (automated): Phase 1C damage calculation diagnostics. All outputs diagnostic-only.
DamageResolver remains NONE throughout. No block damage in any 0.4.x build.

### Key Phase 1C conclusions

- **forceAmountRaw retired as canonical** (T-8 audit, 0.4.4): `E_current = J^2/(2mEff)` is ~1000x
  larger than measured `kDelta`. Root cause: `forceAmountRaw` is a Sable solver/contact diagnostic
  with unresolved JNI units, not a physical impulse. Retained as `[TI capture T8-audit]` reference
  diagnostic only. Must NOT enter any damage formula.
- **Velocity-derived kImpact selected as canonical**: `kineticImpactEnergyJ = abs(kBefore - kAfter)`
  where `kBefore`/`kAfter` are tick-start PRE_STEP and last-substep POST_STEP relative kinetic
  energies. Uses `abs()` not `max(0,...)`: Rapier spring model + gravity makes `kAfter > kBefore`
  in genuine impacts.
- **kBand calibration labels** (0.4.6/0.4.7): TOUCH[0,1) / LIGHT[1,5) / MEDIUM[5,20) / HEAVY[20,80) /
  SEVERE[>=80]. Display-only; never enter formula. Calibrated from observed data
  (0.016->TOUCH, 13.9->MEDIUM, 156->SEVERE). SEVERE boundary raised from 50 to 80 in 0.4.7.
- **T-9 diagnostic infrastructure** (0.4.8): `MaterialThresholdProfile` in `damage/` (no Minecraft
  imports; R11 safe). Material classes: SOFT_SOIL/WOOD/STONE/METAL/HIGH_STRENGTH/GENERIC. Threshold
  values (5/20/50/120/300) are calibration targets; manual T-9 sessions required to finalize.
- **Capture gate performance fix** (0.4.9 P0): `SableImpactCapture.captureGate` volatile boolean.
  When `DiagnosticConfig.ENABLED=false`, both the contact loop and `SableEventBridge.onPostStep()`
  sub-level loop are skipped. Phase 1C only -- marked for removal in Phase 2.
- **Colored status output** (0.4.9 P1): `/trueimpact debug status` uses per-line ChatFormatting
  colors (AQUA capture, GOLD impact, LIGHT_PURPLE canonical/T8, RED threshold exceedance, DARK_GRAY paused).

### Added
- `physics/ImpactMetrics` -- 28-field data record: solver diagnostic, velocity-derived canonical,
  T-8 velocity flags/kinetic, unit audit, T-6 UC, threshold comparison
- `SableImpactCapture.computeMetrics()` -- ImpactMetrics computation per active-vs-active record
- `SableImpactCapture` T-8 rolling stats: 32-entry circular window, min/max/avg/p50;
  fixed circular buffer index arithmetic (bug: stale values caused avg>max when window not full)
- `command/KImpactBand` -- kBand label logic, no Minecraft imports (extracted from DiagnosticCommand
  to avoid `NoClassDefFoundError` when unit tests load `DiagnosticCommand`)
- `damage/MaterialThresholdProfile` -- T-9 material class thresholds; `wouldExceed(double, MaterialClass)`
- `SableImpactCapture.captureGate` -- Phase 1C performance gate (volatile boolean, R13-safe)
- 18 KImpactBandTest + 34 MaterialThresholdProfileTest + 13+ new SableImpactCaptureTest cases

### Architecture constraints preserved
- `DamageResolver.resolve()` always returns NONE (test-verified)
- `contactCount` not used in any formula (diagnostic metadata only)
- `impulseAlongNormalJ` not primary input (T-6 unconfirmed)
- ArchUnit R9/R11/R13 all 0 violations
- All string literals and comments ASCII-only

---

## [0.3.6-phase1b] — 2026-06-10

Gate 1B: Phase 1B damage pipeline skeleton implemented and verified in-game.

### Added
- `physics/` package: `ImpactRecord` (immutable data contract), `ContactType` enum
  (ACTIVE_IMPACT / ACTIVE_SUSTAINED; active-vs-active only)
- `damage/DamageResolver` skeleton -- always returns `DamageEvent.NONE`
- `sable/SableImpactCapture` -- contact pipeline: raw contacts -> ImpactRecord assembly
  (mEff, J = forceAmountRaw * substepDt, contactCount, contactType classification)
- `sable/SableEventBridge.onPostStep()` -- per-substep body snapshot rebuild (`lastPostSnaps`)
- ArchUnit R9 (physics/ no TI internal deps), R11 (damage/ no diagnostic/observation deps),
  R13 (SableImpactCapture no DiagnosticConfig dep) -- all enforced at build time
- `/trueimpact debug status` lines: `[TI capture]`, `[TI capture last-hit]`
- 25+ new unit tests: SableImpactCaptureTest, DamageResolverTest

### Safety verified
- DamageResolver returns NONE for all inputs (test-verified)
- Capture pipeline independent of all diagnostic flags (ArchUnit R13)
- World-vs-active and unknown body-pair types discarded at capture layer
- In-game (2026-06-10, 0.3.6-phase1b): active-vs-active collision detected; zero block breaks

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
