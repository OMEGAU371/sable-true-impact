# Rewrite Roadmap

This document tracks the phased rebuild of True Impact.  
Each phase is complete only when its **acceptance gate** passes (see `docs/acceptance-gates.md`).

## Phase 0 — Foundation (current)

Goal: reliable engineering base; no physics features.

- [x] NeoForge 1.21.1 project scaffold
- [x] Clean package structure with ArchUnit guards
- [x] `/trueimpact status` command
- [x] Unit test + arch test baseline
- [x] Gradle `deploy` task
- [x] CHANGELOG + version scheme

Acceptance gate: `.\gradlew.bat build` passes; `/trueimpact status` prints correct output in-game.

---

## Phase 1 — Impact Event Model

Goal: define the data model for impact events; no destruction yet.

- [ ] `physics/ImpactEvent` — record (level, pos, energyJoules, sourceId, victimId, crackId)
- [ ] `physics/ImpactReducer` — per-tick aggregation, thread-safe, flushes at `LevelTickEvent.Post`
- [ ] `DamageResolver` skeleton — accepts `ImpactEvent`, always returns `NONE` (gate only)
- [ ] ArchUnit rule: source paths must not call `Level.destroyBlock` directly
- [ ] Sable hook (read-only): log first 10 impact events per tick to LOGGER at DEBUG

Acceptance gate: in-game with Sable loaded, impact events are logged; nothing breaks.

---

## Phase 2 — Material Profiles

Goal: single hardness source; no destruction yet.

- [ ] `damage/BlockHardnessProfile` — strength (J), toughness (multiplier), brittleness [0,1]
- [ ] Derived from vanilla hardness + blast resistance + sound type
- [ ] `DamageResolver` consults `BlockHardnessProfile`; still returns `NONE`
- [ ] ArchUnit rule: `BlockHardnessProfile` is the only class allowed to read vanilla hardness

---

## Phase 3 — Crack Accumulation (no breaks)

Goal: visual crack progress; no block destruction.

- [ ] `damage/BlockDamageAccumulator` — tracks crack progress per (level, pos)
- [ ] `DamageResolver` outputs `CRACK` when yieldRatio > threshold
- [ ] Crack overlay displays correctly on client
- [ ] Dedicated server runs without client-side overlay references

---

## Phase 4 — Destruction Pipeline

Goal: full Reducer → Resolver → Accumulator → destroyBlock path.

- [ ] `DamageResolver` outputs `BREAK` / `HEAVY_BREAK`
- [ ] `ImpactBreakQueue` — defer ALL `destroyBlock` calls to `ServerTickEvent.Post`
- [ ] `RopeBindingRegistry` — gate for constraint-anchored blocks
- [ ] Narrow-phase crash guard (see legacy [[narrow-phase-crash]] memory)

---

## Phase 5 — Sable Adapters

Goal: full Sable engine adapter layer.

- [ ] `mixin/RapierVoxelColliderBakeryMixin`
- [ ] `mixin/RapierPhysicsPipelineMixin`
- [ ] `PhysicsStepGate`
- [ ] `clampRunawaySubLevels`

---

## Phase 6 — Create Compatibility

Goal: Create contraption integration.

- [ ] Conveyor belt damage
- [ ] Bearing / pulley / piston anchor damage
- [ ] `CreateContraptionAnchorDamage`

---

## Future

See `C:\Users\l\Desktop\Projects\TI\archived\TIPlan.md` for the full community-sourced feature roadmap (material inheritance, stress system, denting, friction damage, world physics, etc.).
