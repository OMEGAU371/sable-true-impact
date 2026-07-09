# True Impact (Rewrite)

Physics-based impact damage for [Sable](https://github.com/ryanhcode/sable) physical structures.
NeoForge 1.21.1 · Java 21 · Mod ID: `true_impact`

> **Status: 0.4.0-delta** — active development.
> World blocks and physics structures (Sable sublevels) crack, accumulate damage, and break
> from real impacts, derived from vanilla block hardness / blast resistance — not a hand-tuned
> table. Structures can damage each other (Newton's third law), drill through terrain when the
> impact energy demands it, and destroy Create contraption anchors (bearings, pistons, trains,
> minecarts) on high-speed collision.
>
> This is a from-scratch rewrite of the legacy `1.x` line (archived, see `gamma-legacy` branch).
> The legacy code is read-only reference — do not port implementations from it directly.

## What it does

- **World block damage** — Path 1: real Rapier contacts drive per-block crack accumulation and
  destruction, weighted by each contact's own recorded speed (not split evenly across every
  block a body happens to be touching that tick).
- **Physics structure damage** — Phase 3A: a sublevel's own blocks take damage from impacts,
  spread across the contact face, with an elastic fatigue floor and stress-relaxation decay so
  gentle repeated contact doesn't grind a structure to dust.
- **Stress propagation & fracture** — Phase 3B: a broken block can trigger a stress-driven split
  of the sublevel into independent pieces via Sable's `SubLevelAssemblyHelper`.
- **Penetration dynamics** — Phase 3C: high-energy impacts crush a footprint and re-inject
  leftover kinetic energy as velocity, letting heavy bodies drill through terrain layer by layer
  instead of stopping elastically at first contact — gated by a mass-independent minimum contact
  speed so settling bodies don't ratchet-sink.
- **Structure-vs-structure damage** — two physics structures colliding damage both sides, using
  relative-velocity direction and the opposing face's material hardness (soft strikers can't
  crush harder victims).
- **Create contraption damage** — Phase 4A/4B: bearings/pistons/pulleys/etc. accumulate crack
  damage and are destroyed on overload; trains derail and ridden minecart contraptions are
  killed on high-speed sublevel impact.
- **Confinement (D-3)** — a block's effective break threshold scales with how much surrounding
  material supports it, directionally weighted toward the impact's push direction.

## Requirements

| Dependency | Version | Required |
|---|---|---|
| Minecraft | 1.21.1 | Yes |
| NeoForge | 21.1.228+ | Yes |
| Sable | 2.0.1 | Yes (mod loads without it, but does nothing) |
| Create | 6.0.10+ | Optional (enables Phase 4A/4B contraption damage) |

## Commands

```
/trueimpact status
```

Prints mod version, Minecraft/NeoForge version, Sable detection status, and runtime environment.

```
/trueimpact damage inspect last
/trueimpact damage inspect here
/trueimpact damage clear
/trueimpact damage breaking on|off
```

Inspect accumulated damage state at the last hit position or the player's current position;
clear all accumulated damage; toggle whether damage actually destroys blocks (crack overlay and
accumulation still run either way).

```
/trueimpact debug status
/trueimpact debug contacts on|off
/trueimpact debug callbacks on|off
/trueimpact debug bodies on|off
/trueimpact debug all off
```

Diagnostic logging toggles — see `[高级.调试]` in the config for the full per-path logging matrix
(world block damage, physics structure damage, dynamic structure damage, raw physics capture,
body snapshots, energy summary). All are opt-in and independent of whether damage effects apply.

## Configuration

Server config lives at `config/true_impact-server.toml` (global, not per-world — NeoForge 21.x
moved it out of the world folder). Edit in-game via NeoForge's built-in config screen. Key
sections: `[总体]` (master toggles + drop mode), `[高级.材质]` (per-material multipliers),
`[高级.物理结构]` (penetration dynamics, structure-vs-structure), `[高级.平衡]` (damage presets:
MILD/CONSERVATIVE/DEFAULT/INTENSE/DRAMATIC), `[高级.受伤倍率]`, `[高级.兼容性]` (Create
integration), `[高级.裂纹]`, `[高级.掘取]` (tool-tier drop thresholds), `[高级.调试]`.

## Building

```powershell
.\gradlew.bat build            # compile + test + jar + sources jar
.\gradlew.bat test              # unit tests + ArchUnit
.\gradlew.bat test --rerun-tasks # bypass test cache
.\gradlew.bat clean test build  # full clean rebuild
.\gradlew.bat deploy            # build + deploy to mods dirs + desktop
.\gradlew.bat copySableToRunMods runServer   # dev server with RCON (see CLAUDE.md)
.\gradlew.bat runGameTestServer              # automated @GameTest suite
```

JAR produced at `build/libs/true_impact-<version>.jar`.
Sources JAR at `build/libs/true_impact-<version>-sources.jar`.

## Deploy targets

- `D:\.minecraft\versions\1.21.1-NeoForge_21.1.228\mods`
- `C:\Users\l\Desktop`

**Safety:** the deploy task only removes JARs that identify themselves via
`TI-Project-Line: rewrite2` in their `MANIFEST.MF`. Legacy v1.x JARs and other mods are never
touched.

## Version scheme

`MAJOR.MINOR.PATCH-PHASE`

- `0.x.x` — rewrite line (this project)
- `1.x.x` — legacy line (archived, see the `gamma-legacy` branch — read-only reference)
- Phase labels track the current focus area (`foundation`, `phase1`–`phase3`, `dynstruct`,
  `delta`, ...); see `CHANGELOG.md` for the full history.

The version is defined once in `gradle.properties` (`mod_version`).
`TrueImpactVersion.java` is **auto-generated** from that value; never edit it directly.

## Architecture

See `CLAUDE.md` for the full damage-pipeline breakdown (Path 1–4), Sable integration gotchas,
and the ArchUnit-enforced package boundaries.
See `docs/architecture.md`, `docs/coordinate-systems.md`, and `docs/physics-invariants.md` for
the underlying design docs.

## License

LGPL-3.0-only
