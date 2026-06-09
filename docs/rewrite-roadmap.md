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

## Phase 1A -- Observation Layer (Sable Impulse Study) -- COMPLETE (2026-06-09)

Goal: calibrate Sable physics quantities needed for damage formula.

- [x] T-4: applyImpulse/applyForce semantics -- MANUALLY PASSED (ratio ~1.0)
- [x] T-3: forceAmountRaw dimension -- MANUALLY PASSED (J = forceAmountRaw * substepDt confirmed)
- [x] SableEventBridge: PRE_STEP / POST_STEP body snapshot infrastructure
- [x] DiagnosticContactCaptureMixin: clearCollisions interception
- [x] ContactLogger: T-3/T-5/T-6 diagnostic logging
- [x] DiagnosticCommand: /trueimpact debug subcommands

---

## Phase 1B -- Damage Pipeline Skeleton -- COMPLETE (2026-06-10)

Goal: data contract and unconditional capture pipeline; no destruction.

- [x] `physics/ImpactRecord` -- immutable record, active-vs-active only, all fields calibrated
- [x] `physics/ContactType` -- ACTIVE_IMPACT / ACTIVE_SUSTAINED (exactly two values)
- [x] `sable/SableImpactCapture` -- active-vs-active aggregation; world/unknown pairs discarded
- [x] `damage/DamageResolver` -- skeleton; always returns NONE
- [x] ArchUnit R9-R13 -- physics/ isolation, damage/ isolation, capture no-diagnostic-config rule
- [x] `lastPostSnaps` populated unconditionally (independent of LOG_BODY_SNAPSHOTS)
- [x] `SableImpactCapture` runs before diagnostic gate (PATH A always active)
- [x] Runtime counters + last-hit fields in RuntimeStats
- [x] /trueimpact debug status shows [TI capture] and [TI capture last-hit] lines

Acceptance gate: MANUALLY PASSED (2026-06-10) -- see docs/acceptance-gates.md Gate 1B.

---

## Phase 1C -- Damage Calculation (Diagnostics Only) -- IN PROGRESS

Goal: quantitative pass through damage model; all outputs diagnostic only; no destruction.

See `docs/phase-1c-damage-model.md` for full design.

- [x] `physics/ImpactMetrics` -- five calculation outputs plus source metadata (pure data record)
- [x] SableImpactCapture computes ImpactMetrics for every active-vs-active ImpactRecord
- [x] /trueimpact debug status shows [TI capture last-record-metrics] and [TI capture last-impact-metrics] lines
- [x] /trueimpact debug status shows [TI capture T8-stats] rolling calibration ratios
- [ ] T-8: impactEnergyJ = J^2/(2*m_eff) vs 3D relative kinetic delta validation
- [ ] T-9: materialThresholdJ calibration (one reference block type)
- [x] DamageResolver still NONE; exceedsThreshold is logged only, never triggers game effect

Acceptance gate: T-8 and T-9 manually passed; /trueimpact debug status shows all five metrics.

---

## Phase 1D -- BlockHardnessProfile + Threshold Formula *(future)*

Goal: material-aware threshold; still no destruction.

- [ ] `damage/BlockHardnessProfile` -- single source for vanilla hardness reads
- [ ] Per-block materialThresholdJ derived from hardness
- [ ] DamageResolver returns `CRACK` signal (not NONE) when threshold exceeded
- [ ] ArchUnit rule: BlockHardnessProfile is the only class allowed to read vanilla hardness

---

## Phase 2 -- Crack Accumulation (no breaks) *(future)*

Goal: visual crack progress; no block destruction.

- [ ] `damage/DamageAccumulator` -- tracks crack progress per (level, pos)
- [ ] Crack overlay displays correctly on client
- [ ] Dedicated server runs without client-side overlay references

---

## Phase 3 -- Destruction Pipeline *(future)*

Goal: full Reducer -> Resolver -> Accumulator -> destroyBlock path.

- [ ] `DamageResolver` outputs `BREAK` / `HEAVY_BREAK`
- [ ] `ImpactBreakQueue` -- defer ALL `destroyBlock` calls to `ServerTickEvent.Post`
- [ ] `RopeBindingRegistry` -- gate for constraint-anchored blocks
- [ ] Narrow-phase crash guard (see legacy [[narrow-phase-crash]] memory)

---

## Phase 4 -- Sable Adapters *(future)*

Goal: full Sable engine adapter layer.

- [ ] `mixin/RapierVoxelColliderBakeryMixin`
- [ ] `mixin/RapierPhysicsPipelineMixin`
- [ ] `PhysicsStepGate`
- [ ] `clampRunawaySubLevels`

---

## Phase 5 -- Create Compatibility *(future)*

Goal: Create contraption integration.

- [ ] Conveyor belt damage
- [ ] Bearing / pulley / piston anchor damage
- [ ] `CreateContraptionAnchorDamage`

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
