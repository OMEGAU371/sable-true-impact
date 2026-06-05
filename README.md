# True Impact (Rewrite)

Physics-based impact damage for Sable physical structures.  
NeoForge 1.21.1 · Java 21 · Mod ID: `true_impact`

> **Status: Phase 0 — Foundation**  
> This is a clean rewrite. No physics features are active yet.  
> Current build establishes the project scaffold, CI baseline, and `/trueimpact status` diagnostics.

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

Prints mod version, Minecraft/NeoForge version, Sable detection result, and current runtime environment.

## Building

```powershell
.\gradlew.bat build         # compile + test + jar
.\gradlew.bat test          # tests only
.\gradlew.bat deploy        # build + deploy to mods dirs + desktop
```

The JAR is produced at `build/libs/true_impact-<version>.jar`.

## Deploy targets

- `D:\.minecraft\versions\1.21.1-NeoForge_21.1.228\mods`
- `C:\Users\l\Desktop`

Old `true_impact-*.jar` files are removed from each target before copying.

## Version scheme

`MAJOR.MINOR.PATCH-PHASE[-build]`

- `0.x.x` — rewrite line (this project)
- `1.x.x` — legacy line (archived)
- Phase labels: `foundation`, `alpha`, `beta`, `rc`, `release`

## License

LGPL-3.0-only
