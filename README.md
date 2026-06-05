# True Impact (Rewrite)

Physics-based impact damage for Sable physical structures.  
NeoForge 1.21.1 · Java 21 · Mod ID: `true_impact`

> **Status: Phase 0 — Foundation (Gate 0 passed 2026-06-05)**  
> No physics features are active yet.  
> Establishes project scaffold, test infrastructure, and `/trueimpact status` diagnostics.

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

Prints:
- Mod version (read from live `ModContainer`, always matches the deployed JAR)
- Minecraft / NeoForge version
- Sable detection status and version
- Runtime environment (Client / Dedicated Server)

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
