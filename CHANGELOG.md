# Changelog

All notable changes to this project will be documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).  
Version scheme: `MAJOR.MINOR.PATCH-PHASE[-build]`

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
