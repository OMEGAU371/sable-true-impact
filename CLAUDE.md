# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

**True Impact (Rewrite)** -- NeoForge 1.21.1 / Java 21 mod adding physics-based impact damage
on top of the Sable physics engine.
Mod ID: `true_impact` | Package root: `io.github.omegau371.trueimpact` | Current phase: `0.12.0-phase2e`

> This is a from-scratch rewrite. The legacy codebase lives in
> `C:\Users\l\Desktop\Projects\TI\archived\pr-4-test` (read-only reference).
> Do NOT copy legacy implementations directly.

## Commands

```powershell
# Build (compiles + runs tests + produces JAR)
.\gradlew.bat build

# Tests only
.\gradlew.bat test
.\gradlew.bat test --rerun-tasks   # bypass test cache

# Compile only
.\gradlew.bat compileJava

# Build + deploy to mods dirs + desktop
.\gradlew.bat deploy

# Dev client run
.\gradlew.bat runClient

# Dedicated server run
.\gradlew.bat runServer
```

JAR is produced at `build/libs/true_impact-<mod_version>.jar`.

## Deploy workflow

After each build that should be tested in-game:

1. Bump `mod_version` in `gradle.properties` (patch number)
2. Run `.\gradlew.bat deploy`
3. The `deploy` task removes old `true_impact-*.jar` from both targets and copies the new JAR

Deploy targets:
- `D:\.minecraft\versions\1.21.1-NeoForge_21.1.228\mods`
- `C:\Users\l\Desktop`

## Package structure

```
io.github.omegau371.trueimpact
|-- TrueImpactMod           -- @Mod entry point; wires NeoForge event bus
|-- TrueImpactVersion       -- MOD_ID and VERSION constants
|-- platform/
|   +-- DistInfo            -- dist detection, Sable presence, mod version queries
|-- command/
|   |-- StatusCommand       -- /trueimpact status implementation
|   |-- DiagnosticCommand   -- /trueimpact debug and experiment subcommands
|   +-- KImpactBand         -- kBand label logic (no Minecraft imports; extracted for unit-test safety)
|-- observation/            -- read-only state snapshots; no physics side effects
|-- diagnostic/             -- experiment logging (T-1 through T-7); rate-limited output
|-- sable/                  -- Sable bridge: body reader, event bridge, T-4 commands
|-- mixin/                  -- SpongeMixin hooks for Sable physics pipeline
|-- physics/                -- damage input contract + metrics (pure data, no side effects)
|   |-- ContactType.java    -- enum: ACTIVE_IMPACT / ACTIVE_SUSTAINED (active-vs-active only)
|   |-- ImpactRecord.java   -- immutable record: the damage input contract
|   +-- ImpactMetrics.java  -- 28-field record: Phase 1C diagnostic + canonical outputs
+-- damage/                 -- resolver skeleton + Phase 1C threshold profiles
    |-- DamageResolver.java -- skeleton: always returns NONE
    +-- MaterialThresholdProfile.java -- T-9 material classes + placeholder thresholds (no Minecraft imports)
```

ArchUnit layer rules (enforced at build time -- see FoundationArchTest.java):

  physics/     must NOT depend on ANY other TI internal package
               (command, platform, sable, mixin, damage, diagnostic, observation).
               Single comprehensive rule R9. physics/ is a pure data contract.

  damage/      must NOT depend on diagnostic/ or observation/ (R11).
               Note: damage/ vs command/sable/platform/mixin not yet guarded;
               add rules if those dependencies ever appear (they are design smells).

  diagnostic/  must NOT depend on damage/ (R7).
  observation/ must NOT depend on damage/ (R5).
  All production packages: no net.minecraft.client.* references.

Future packages (Phase 1D+):
- `damage/BlockHardnessProfile`  -- single source for vanilla hardness reads; real victim block detection
- `damage/DamageAccumulator`     -- per-block crack state
- `damage/ImpactBreakQueue`      -- deferred destroyBlock (never inside Rapier step)
- `compat/`                      -- Create integration, etc.

## Architecture rules (enforced by ArchUnit)

`src/test/java/.../arch/FoundationArchTest.java` mechanically enforces:

- `platform` must NOT depend on `command` (one-way dependency direction)
- `command` and `platform` must NOT reference `net.minecraft.client.*`
- Root mod class must NOT reference client-only classes (unless `@OnlyIn`)
- `physics/` must NOT depend on any other TI internal package (R9)
- `damage/` must NOT depend on `diagnostic/` or `observation/` (R11)

Build fails on violation. Add a new `@ArchTest` before writing code that would otherwise
violate a new rule.

## Key gotchas inherited from legacy project

These already cost hours -- respect them in future phases:

- **Dedicated server class loading**: `RuntimeDistCleaner` aggressively rejects any class
  whose bytecodes reference client-only types. When doing reflection over Sable classes,
  catch `Throwable` (not `ReflectiveOperationException`) -- Sable classes have `ClientLevel`
  overloads, and loading them can throw `ExceptionInInitializerError`.
- **`presetMode = "auto"` silently resets config**: Any toggle-based diagnostic must first
  set `presetMode = custom` in the Sable server config, or the preset re-applies and
  invalidates the test.
- **Plot-embedded coords are ~4e7 from origin**: Never call `level.getBlockState(pos)` on
  sub-level embedded coords without a `level.hasChunkAt(pos)` guard -- forces vanilla
  chunkgen at that offset, causing aquifer AIOOBE on the worker thread.
- **`localhost` -> IPv6 on Windows**: Connect to test server via `127.0.0.1`, not `localhost`.

## Version scheme

`MAJOR.MINOR.PATCH-PHASE[-build]`
`0.x.x` = rewrite line | `1.x.x` = legacy line (archived)
Always read `mod_version` in `gradle.properties` for the current build version.
