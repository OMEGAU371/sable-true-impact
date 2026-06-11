# True Impact (Rewrite)

Physics-based impact damage for Sable physical structures.  
NeoForge 1.21.1 · Java 21 · Mod ID: `true_impact`

> **Status: Phase 1C — Damage Calculation Diagnostics (automated gate passed 2026-06-12)**  
> Phase 1C is diagnostic-only: DamageResolver always returns NONE. No block is cracked, accumulated, or destroyed.  
> See `docs/phase-1c-damage-model.md` for the canonical model and `docs/acceptance-gates.md` for the gate checklist.

## Requirements

| Dependency | Version | Required |
|---|---|---|
| Minecraft | 1.21.1 | Yes |
| NeoForge | 21.1.228+ | Yes |
| Sable | 1.2.2+ | Optional (detected at runtime) |

## Commands

```
/trueimpact status
```

Prints mod version, Minecraft/NeoForge version, Sable detection status, and runtime environment.

### Diagnostics (Phase 1C)

```
/trueimpact debug status
/trueimpact debug contacts on|off
/trueimpact debug all off
```

`debug status` shows 10 color-coded lines covering: capture counters, last-record metrics,
T-8 rolling ratio stats, Phase 1C canonical `kineticImpactEnergyJ` + `kBand`, and T-9
material threshold comparison. **All output is read-only diagnostic only — no game effects.**

`contacts on` is required for velocity-derived `kineticImpactEnergyJ` (tick-start velocities).
Without it, `kImpact=NaN` and `exceedsThreshold=false`.

## Building

```powershell
.\gradlew.bat build           # compile + test + jar + sources jar
.\gradlew.bat test            # tests only (20 total: unit + arch + deploy-safety)
.\gradlew.bat clean test build # full clean rebuild
.\gradlew.bat deploy          # build + deploy to mods dirs + desktop
```

JAR produced at `build/libs/true_impact-<version>.jar`.  
Sources JAR at `build/libs/true_impact-<version>-sources.jar`.

## Deploy targets

- `D:\.minecraft\versions\1.21.1-NeoForge_21.1.228\mods`
- `C:\Users\l\Desktop`

**Safety:** the deploy task only removes JARs that contain `TI-Project-Line: rewrite2`
in their `MANIFEST.MF`. Legacy v1.x JARs and other mods are never touched.

## Version scheme

`MAJOR.MINOR.PATCH-PHASE[-build]`

- `0.x.x` — rewrite line (this project)
- `1.x.x` — legacy line (archived at `Projects/TI/archived/pr-4-test`)
- Phase labels: `foundation`, `alpha`, `beta`, `rc`, `release`

The version is defined once in `gradle.properties` (`mod_version`).
`TrueImpactVersion.java` is **auto-generated** from that value; never edit it directly.

## Architecture

See `docs/architecture.md` for the full layer model.  
See `docs/rewrite-roadmap.md` for the phased rebuild plan.  
See `docs/acceptance-gates.md` for per-phase verification checklists.

## License

LGPL-3.0-only
