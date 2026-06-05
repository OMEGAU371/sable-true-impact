# Changelog

All notable changes to this project will be documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).  
Version scheme: `MAJOR.MINOR.PATCH-PHASE[-build]`

---

## [0.1.0-foundation] — 2026-06-05

### Added
- NeoForge 1.21.1 / Java 21 project scaffold
- Package structure: `io.github.omegau371.trueimpact.{command,platform}`
- `/trueimpact status` command — reports version, NeoForge version, Sable detection, runtime environment
- `DistInfo` — environment query utility (dist, Sable presence, mod version lookup)
- `TrueImpactVersion` — mod ID and version constants
- Unit tests: `TrueImpactVersionTest` (4 assertions, pure Java)
- Architecture tests: `FoundationArchTest` (ArchUnit, 4 rules enforcing layer boundaries and no-client-class constraints)
- Gradle `deploy` task — builds and deploys to Minecraft mods dir + desktop
- Documentation: README, CLAUDE.md, docs/architecture.md, docs/rewrite-roadmap.md, docs/acceptance-gates.md
