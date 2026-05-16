# Sable: True Impact

**Sable: True Impact**, or **TI** for short, is an experimental NeoForge add-on for Sable. It adds physics-based impact damage, cumulative cracking, terrain destruction, entity impact damage, and early structure fracture behavior to Sable physical sub-levels.

TI is built for Minecraft 1.21.1, NeoForge, and Sable 1.2.2+. It also includes early compatibility logic for Create and Create Aeronautics style moving structures.

> Current public version: **1.0.0-gamma**
>
> This is a preview release for testing and feedback. Back up worlds before using it.

<img width="2672" height="1024" alt="STICOVER" src="https://github.com/user-attachments/assets/21864da7-ce8c-422b-87c9-eda0640dc7f0" />

## What TI Does

Minecraft blocks normally survive most large moving-structure impacts unless another mod handles the damage. TI adds a damage layer on top of Sable physics so that heavy physical structures can interact with the world in a more readable and destructive way.

In the gamma version, TI can:

- damage terrain when a Sable physical structure hits normal world blocks,
- show cumulative cracking before some blocks break,
- fracture Sable physical structures under strong impacts,
- damage entities hit by moving physical structures,
- apply early explosion impulse and explosion fracture behavior,
- estimate material strength using hardness, blast resistance, friction, toughness, brittleness, and block-category rules,
- reduce false static-contact damage so decorations and side-mounted blocks are less likely to break while resting,
- detect some Create contraption anchors and apply impact damage to bearings, pistons, pulleys, gantries, minecart assemblers, tracks, supports, and related blocks.

## Current Features

### Terrain Impact Damage

When a Sable physical structure collides with the normal world, TI samples contact positions and converts collision force into terrain damage. Large impacts can crack, weaken, or destroy nearby blocks.

The gamma release includes improved large-surface sampling compared with earlier builds. Flat structures should no longer damage only four corner points in most common tests, but this system is still being tuned.

<!-- Recommended image:
![Terrain impact before and after](docs/images/terrain-impact-before-after.png)
Theme: side-by-side or sequence screenshot showing terrain before and after a large impact.
-->

### Cumulative Cracking

Blocks do not always break instantly. Repeated near-threshold impacts can accumulate damage and show Minecraft crack progress before the block finally breaks.

Very small hits are filtered out so strong materials should not be slowly destroyed by tiny weak impacts.

### Material-Aware Damage

TI compares colliding materials instead of treating every block equally. Strong blocks are protected from weak materials more aggressively than in earlier beta builds.

Current material logic considers:

- block hardness,
- blast resistance,
- friction,
- explosion resistance,
- toughness,
- brittleness,
- sticky or glue-like support,
- beam, frame, girder, chassis, and support-like blocks,
- same-material and mixed-material structural connections.

This is not a full stress simulation yet. It is a practical material model for the gamma version.

### Sable Structure Fracture

Physical structures can split when impact force is high enough. TI uses Sable's native sub-level splitting behavior instead of inventing a separate physics entity system.

To avoid the old self-destruction bug, structure fracture now checks relative speed and closing speed. Static support force, resting contact, and slow rubbing should not normally cause fracture.

Important current rules:

- structure fracture needs enough force,
- two structures must be moving toward each other fast enough,
- fracture candidates are filtered to the target sub-level's own blocks,
- explosion fracture does not use the same relative-speed gate because explosions are external force sources.

### Entity Impact Damage

Moving physical structures can hurt nearby living entities when relative speed and closing speed are high enough.

TI filters common false positives such as riders standing on a moving structure.

### Explosion Interaction

Explosions can apply early shockwave-style behavior to nearby Sable physical structures. This includes fracture pressure and optional impulse.

This is still an early system. A deeper explosion rework is planned for future versions.

### Create Integration

TI has early support for Create-related dynamic structures and anchor blocks. When impacts occur near Create mechanisms, the mod can apply damage to likely load-bearing parts.

Current examples include:

- mechanical bearings,
- pistons,
- rope pulleys and pulley lines,
- gantries,
- minecart assemblers and minecarts,
- train tracks,
- nearby support blocks.

This is not a full Create contraption physics rewrite. Vanilla Create behavior for normal block collision is left to Create itself.

<!-- Recommended image:
![Create anchor damage example](docs/images/create-anchor-damage.png)
Theme: a bearing, pulley, gantry, minecart assembler, or track being damaged by a physical impact.
-->

## Configuration

TI generates server and client config files in the Minecraft instance config folder.

Server config:

```text
config/sabletrueimpact-server.toml
```

Client config:

```text
config/sabletrueimpact-client.toml
```

The internal mod id is still `sabletrueimpact` for compatibility. The public name is **Sable: True Impact**.

If you update from an older build and want the newest defaults, delete the old TI config files and restart the game.

### Presets

The gamma version includes preset-based tuning.

Main mode:

- `presetMode = auto`: applies the selected performance and destruction presets on config load.
- `presetMode = custom`: keeps advanced values untouched.

Destruction presets:

- `off`: disables TI damage behavior.
- `low`: lighter damage, fewer expensive effects.
- `medium`: default gamma balance.
- `high`: stronger and more visible destruction.
- `cinematic`: dramatic destruction for screenshots and test worlds.

Performance presets:

- `potato`
- `very_low`
- `low`
- `medium`
- `high`
- `very_high`
- `destructive`

Higher performance presets allow more fracture checks, terrain samples, explosion rays, entity scans, and cumulative damage records. They can be heavier on servers.

### Useful Config Options

General:

- `enableTrueImpact`: master switch.
- `enablePhysicalDestruction`: global toggle for internal physical-structure destruction.
- `enableWorldDestruction`: global toggle for terrain and normal world block destruction.
- `enableUniversalImpactCallback`: attaches TI collision handling through Sable collider data.

Impact and terrain:

- `impact.enableCracks`: enables visible cracks and cumulative crack marks.
- `impact.enableBlockBreaking`: allows direct impact block breaking.
- `impact.movingStructuresBreakBlocks`: allows moving Sable structures to damage normal world blocks.
- `impact.impactVelocityExponent`: makes fast impacts more destructive when raised.
- `terrainImpact.enableTerrainImpactDamage`: enables terrain damage from Sable sub-level impacts.
- `terrainImpact.terrainImpactForceExponent`: controls how strongly force increases terrain damage.
- `terrainImpact.terrainImpactMaxBlocks`: caps terrain blocks damaged per event.
- `terrainImpact.terrainImpactContactSamples`: controls contact sampling detail for terrain impacts.

Structure fracture:

- `subLevelFracture.enableSubLevelFracture`: enables Sable physical-structure fracture.
- `subLevelFracture.subLevelFractureMinRelativeSpeed`: minimum relative speed for structure-vs-structure fracture.
- `subLevelFracture.subLevelFractureMinClosingSpeed`: minimum speed along the contact normal.
- `subLevelFracture.subLevelFractureMaxCandidateChecks`: maximum nearby block positions inspected per fracture scan.
- `subLevelFracture.subLevelFractureMaxBlocks`: maximum internal blocks removed by one fracture event.
- `subLevelFracture.subLevelFractureFatigueScale`: stores near-miss fracture force as cumulative cracks.

Material behavior:

- `enableMaterialMatchupDamage`: compares colliding material strength before distributing damage.
- `materialMatchupExponent`: controls how strongly strong materials resist weak materials.
- `strongMaterialFatigueImmunityRatio`: ignores repeated weak impacts below this strength ratio.
- `strongMaterialSelfDamageCap`: caps self-damage when a stronger material hits a much weaker one.
- `enableMaterialToughness`: enables additional toughness and brittleness multipliers.

Entity damage:

- `entityImpact.enableEntityImpactDamage`: enables direct entity impact damage.
- `entityImpact.entityMovingImpactMinRelativeSpeed`: minimum relative speed for entity hits.
- `entityImpact.entityMovingImpactMinClosingSpeed`: minimum closing speed toward the entity.
- `entityImpact.entityImpactScanIntervalTicks`: scan interval. Higher values are cheaper.

Explosion behavior:

- `explosionImpact.enableExplosionImpactFracture`: explosions can crack or fracture nearby physical structures.
- `explosionImpact.enableExplosionImpulse`: explosions can push Sable physical structures.
- `explosionImpact.explosionImpactForceScale`: scales explosion pressure.
- `explosionImpact.enableImpactExplosions`: allows massive crashes to trigger Minecraft explosions. Disabled by default.

Create support:

- `create.enableCreateContraptionAnchorDamage`: enables anchor damage for Create mechanisms.
- `create.enableCreateContraptionLoadFailure`: estimates moving contraption load and amplifies anchor damage when overloaded.
- `create.createContraptionLoadSafetyFactor`: overload safety factor.
- `create.createContraptionOverloadAnchorDamageScale`: extra anchor damage scale when overloaded.

Performance:

- `performance.performancePreset`: global cost budget preset.
- `performance.enablePerformanceLogging`: logs periodic TI performance counters.
- `performance.performanceLogIntervalTicks`: performance log interval.
- `performance.enableAsyncFractureAnalysis`: experimental background fracture candidate calculation. World reads and block changes still run on the server thread.

## Recommended Testing

Use a creative test world first.

Basic terrain test:

1. Build or spawn a simple Sable physical structure.
2. Drop it onto grass, dirt, stone, or deepslate.
3. Check whether terrain damage appears near the real contact area.
4. Repeat with different `destructionPreset` and `performancePreset` values.

Static-contact test:

1. Place two Sable physical structures touching each other.
2. Let them rest for several minutes.
3. Side-mounted logs, chains, decorations, and weak blocks should not keep gaining cracks while nothing is moving.

Material test:

1. Hit dirt, grass, logs, stone, deepslate, iron, and netherite-like blocks with different physical structures.
2. Strong materials should resist weak terrain better than old beta builds.
3. Weak materials should still fail under large enough impacts.

Create test:

1. Place a bearing, pulley, gantry, minecart assembler, or track setup.
2. Hit it with a Sable physical structure.
3. Check whether the load-bearing Create block or nearby support takes damage.

Explosion test:

1. Place a physical structure near TNT or another explosion source.
2. Trigger the explosion.
3. Check for movement, cracking, and fracture.

## Known Limitations

The gamma version is playable for testing, but it is not a stable final physics system.

Known limitations:

- force transmission through an entire physical structure is still incomplete,
- internal stress simulation is not fully implemented,
- friction wear damage is planned but not implemented as a true sliding-wear system,
- very large or unusual contact surfaces may still need tuning,
- explosion shockwaves are early and will be rewritten later,
- Create support is anchor/load based, not a full contraption physics simulation,
- compatibility with large modpacks is not guaranteed,
- destructive configs can damage worlds quickly.

Back up worlds before testing TI.

## Compatibility Notes

Required:

- Minecraft 1.21.1
- NeoForge 21.1.x
- Sable 1.2.2 or newer

Recommended or optional:

- Create 6.0.x for Create interaction tests
- Create Aeronautics for Sable-based moving-structure gameplay

TI is an unofficial add-on. It is not affiliated with or endorsed by Sable, Create, or Create Aeronautics.

## Building From Source

This repository does not redistribute Sable's jar. To build locally, place the Sable jar here:

```text
libs/sable-neoforge-1.21.1-1.2.2.jar
```

Then run:

```powershell
.\gradlew.bat build
```

The built mod jar will be in:

```text
build/libs/
```

## Troubleshooting

### The mod feels too weak or too strong

Adjust `destructionPreset` first. Use advanced values only after presets are tested.

### The game stutters during large crashes

Lower `performancePreset`, reduce terrain contact samples, reduce fracture candidate checks, or disable async fracture if it causes issues in your environment.

### Old config values are still used after updating

Delete:

```text
config/sabletrueimpact-server.toml
config/sabletrueimpact-client.toml
```

Then restart the game.

### A crash happens with Create-related blocks

Send the full `latest.log`, `debug.log`, and crash report. Some crashes may be compatibility issues between Sable, Create, Petrolpark, Create Aeronautics, and block destruction timing.

## Roadmap

Short-term goals:

- improve remaining terrain contact sampling edge cases,
- tune material toughness and fatigue further,
- harden Create and Petrolpark compatibility,
- improve performance presets and config descriptions,
- add clearer visual and audio feedback for impacts.

Long-term goals:

- full stress simulation for physical structures,
- force transmission through connected blocks,
- realistic crack propagation,
- friction-based wear damage,
- soft-terrain deformation such as heavy objects sinking into sand,
- block indentation without spawning new physical structures,
- explosion shockwaves and impulse transfer through Sable internals,
- automatic physicalization of unsupported floating structures,
- delayed restoration of automatically generated sub-level fragments.

## License

Sable: True Impact is licensed under the **GNU Lesser General Public License v3.0**.

See [LICENSE](LICENSE) for the full license text.

## Disclaimer

Sable: True Impact is an unofficial add-on for Sable. It is not affiliated with or endorsed by Sable's original author.
