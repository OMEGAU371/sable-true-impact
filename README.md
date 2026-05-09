# Sable True Impact

Sable True Impact is an experimental NeoForge add-on for Sable that adds impact damage, cumulative cracking, entity impact damage, and structural fracture behavior to Sable physical sub-levels.

It is designed for Minecraft 1.21.1, NeoForge, and Sable 1.2.2+.

## Features

- Impact-based terrain damage using collision force, material hardness, blast resistance, mass, and support.
- Cumulative block cracking before full block breakage.
- Structural fracture for Sable sub-levels, using Sable's native splitting system.
- Local structural strength analysis:
  - straight mixed-material seams are more likely to split,
  - checkerboard/interlocked materials resist fracture,
  - beams, girders, frames, supports, and chassis-like blocks strengthen local structure,
  - Create super glue entities and honey/sticky/glue-like blocks greatly reinforce connections.
- Fracture analysis is internally split into a local world snapshot and a pure candidate calculation step, preparing the system for safe async analysis later.
- Entity impact damage based on relative closing speed, with standing-on-vehicle filtering to avoid hurting riders.
- Step-contact forgiveness so vehicles do not easily destroy terrain when driving over small height differences.
- Server-side TOML configuration for damage, fracture, terrain protection, entity damage, and structure-strength tuning.

## Dependencies

Required at runtime:

- Minecraft 1.21.1
- NeoForge 21.1.x
- Sable 1.2.2 or newer

Optional integrations are detected by block/entity identifiers, including Create super glue, honey, stickers, girders, beams, frames, and similar support blocks.

## Building

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

## Configuration

Server configuration is generated at:

```text
serverconfig/sabletrueimpact-server.toml
```

Existing worlds keep their old config values. Delete or edit the server config after updating if you want new defaults.

Useful feature switches:

- `enableTrueImpact`: master switch for all added behavior.
- `impact.enableCracks`: visual cracks and cumulative crack marks.
- `impact.enableBlockBreaking`: direct impact block breaking.
- `impact.movingStructuresBreakBlocks`: whether moving Sable physical structures may break or cumulatively damage normal world blocks. Defaults to `true`.
- `impact.impactVelocityExponent`: velocity exponent for block impact damage. Values above `2.0` make fast impacts much more destructive.
- `impact.enableCrackPropagation`: crack spread from catastrophic hits.
- `terrainImpact.enableTerrainImpactDamage`: Sable sub-level damage against normal terrain.
- `terrainImpact.terrainImpactForceExponent`: force exponent for terrain impact damage. Values above `1.0` make violent impacts dig harder.
- `entityImpact.enableEntityImpactDamage`: damage dealt to living entities.
- `entityImpact.entityImpactScanIntervalTicks`: direct entity impact scan interval. Higher values reduce CPU load.
- `subLevelFracture.enableSubLevelFracture`: internal fracture of Sable physical structures.
- `subLevelFracture.subLevelFractureForceExponent`: force exponent for internal structure fracture. Values above `1.0` make high-force crashes split structures more aggressively.
- `subLevelFracture.subLevelFractureMaxCandidateChecks`: maximum nearby positions inspected by one fracture event.
- `subLevelFracture.subLevelFractureMaxCandidates`: maximum fracture candidates kept for sorting and chance checks.
- `subLevelFracture.enableAsyncFractureAnalysis`: experimental background fracture candidate calculation. World reads and block changes still run on the server thread.
- `subLevelFracture.asyncFractureMaxQueuedJobs`: queue limit for async fracture jobs.
- `subLevelFracture.asyncFractureMaxAppliedJobsPerTick`: completed async fracture jobs applied per server tick.
- `pairReaction.enablePairReaction`: elastic pair-collision counter impulse.
- `cumulativeDamage.enableCumulativeBlockDamage`: repeated crack-level hits accumulate until breakage.
- `performance.enablePerformanceLogging`: periodic low-level counters for collision, fracture, and entity scan cost.
- `performance.performanceLogIntervalTicks`: logging interval when performance logging is enabled.

## Safety

This mod intentionally changes physical collision outcomes and can break terrain, split structures, and damage entities. Back up worlds before testing.

## Disclaimer

This is an unofficial add-on for Sable. It is not affiliated with or endorsed by Sable's original author.
