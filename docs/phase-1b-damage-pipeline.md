# Phase 1B -- Structure-vs-Structure Damage Pipeline Design

## Status: DESIGN (no damage implemented)

Phase 1B defines the data contract and pipeline shape.
No block breakage, crack accumulation, or collision response is added in this phase.

---

## Confirmed inputs from Phase 1A

| Finding | Source | Status |
|---|---|---|
| `applyImpulse` = direct impulse semantics; ratio ~1.0 | T-4 manual (2026-06-06) | CONFIRMED |
| `forceAmountRaw` = per-substep force; `J = raw * substepDt` | T-3 manual (2026-06-09) | CONFIRMED |
| `clearCollisions` returns active-vs-active contacts | T-3 live test | CONFIRMED |
| T-6 contact normal direction convention | not yet tested | UNCONFIRMED |
| T-5 substep attribution inside `clearCollisions` | not yet tested | UNCONFIRMED |

---

## Data flow (Stage 0 -> Stage 4)

```
[Stage 0: Rapier physics]
  Rapier3D.clearCollisions(sceneId)
    raw double[N*15] -- idA, idB, forceAmountRaw, normalA, normalB, pointA, pointB

[Stage 1: Contact capture -- existing mixin hook, sable/ package]
  DiagnosticContactCaptureMixin.captureContactData()
    |
    +--> ContactLogger.onClearCollisions()    [diagnostic only, T-3/T-5/T-6 logs]
    |
    +--> SableImpactCapture (Phase 1B NEW)   [physics, ImpactRecord assembly]
         For each active-vs-active pair in this tick:
           totalImpulseJ       = sum(forceAmountRaw_i) * substepDt   [CANONICAL T-3]
           impulseAlongNormalJ = sum(mX * |dvX_n|) / 2 per pair      [T-6 dir UC; abs applied]
           effectiveMassKpg    = 1 / (1/mA + 1/mB)
           contactCount        = Rapier record count for this pair    [contact area proxy]
           contactType         = ACTIVE_IMPACT if totalImpulseJ/count > threshold
                                 ACTIVE_SUSTAINED otherwise

[Stage 2: ImpactRecord -- physics/ package]
  Immutable data contract.
  See ImpactRecord.java for field contract and invariants.

  Key invariants:
    totalImpulseJ >= 0
    impulseAlongNormalJ >= 0 (abs applied at assembly; T-6 sign unconfirmed)
    effectiveMassKpg > 0 or NaN if unavailable
    contactCount >= 1 for any record that reaches this stage
    contactType is a DIAGNOSTIC FILTER TAG only, not a formula parameter.
      Resolver uses it to skip non-impact records. The threshold that
      classifies ACTIVE_IMPACT lives in SableImpactCapture, never in the resolver.

[Stage 3: DamageResolver -- damage/ package, Phase 1B skeleton]
  input:  ImpactRecord
  output: DamageEvent { NONE }

  Phase 1B contract (skeleton):
    - Always returns NONE.
    - Pure function; no side effects.
    - MUST NOT call Level.destroyBlock (directly or indirectly).
    - MUST NOT read DiagnosticConfig.* or any observation/ state.
    - MUST NOT contain the contactType threshold (5.0 kpg*block/s per contact).
      That threshold is a diagnostic classification detail, not a damage constant.
    - When formula is implemented (Phase 1C+), use only:
        totalImpulseJ, effectiveMassKpg, contactCount, massAKpg, massBKpg
      Not impulseAlongNormalJ until T-6 is confirmed.

  Future formula sketch (Phase 1C, NOT YET):
    equivalentStressJ_per_contact = totalImpulseJ / contactCount
    -- compare against BlockHardnessProfile per block face in contact region
    -- requires Stage 4 for spatial block mapping

[Stage 4: DamageAccumulator -- damage/ package, Phase 1C NOT YET]
  - Per-block crack accumulation keyed by (level, blockPos)
  - Input: DamageEvent from resolver + contact point positions
  - Output: crack fraction delta per block face

[Stage 5: ImpactBreakQueue -- damage/ package, Phase 1D NOT YET]
  - Defers destroyBlock to ServerTickEvent.Post
  - NEVER called from inside a Rapier physics step
```

---

## ImpactRecord field contract

```
totalImpulseJ        Canonical impulse estimate (kpg*block/s).
                     = sumForceAmountRaw * substepDt
                     T-3 confirmed. Use this for all damage calculations.

impulseAlongNormalJ  Impulse along contact normal (kpg*block/s, always >= 0).
                     = (mA*|dvA_n| + mB*|dvB_n|) / 2 summed across contacts.
                     T-6 normal direction UNCONFIRMED; abs() applied.
                     Do NOT use as primary damage input until T-6 passes.
                     Useful for directional analysis only.

effectiveMassKpg     Reduced mass = 1/(1/mA + 1/mB).
                     Represents how "hard" the collision feels to each body.
                     NaN if either mass is unavailable.

contactCount         Number of Rapier contact records for this pair in this tick.
                     Proxy for contact surface area. More contacts = larger overlap.
                     1 contact = point contact; N contacts = extended surface.

contactType          Diagnostic classification tag.
                     ACTIVE_IMPACT:     above impulse-per-contact threshold.
                     ACTIVE_SUSTAINED:  below threshold (resting, sliding).
                     WORLD_VS_ACTIVE:   terrain vs sub-level.
                     UNKNOWN:           no active body on either side.
                     Purpose: pipeline filter only.
                     Do NOT use as a formula branch (no "if IMPACT use X, else use Y").
                     The resolver skips non-ACTIVE_IMPACT records via early return.

substepDt            Reference value (0.05 / substepsPerTick).
                     Included for completeness; not needed if formula uses totalImpulseJ.
```

---

## ArchUnit boundaries (Phase 1B additions)

```
physics/   -->  [nothing]          Pure data contract. No upstream dependencies.
damage/    -->  physics/ only      Resolver inputs only. No observation, no diagnostic.
sable/     -->  physics/, diagnostic/, observation/   Bridge layer.
diagnostic/ --> observation/, physics/  May assemble diagnostic data from contract types.
observation/ -> [nothing TI-internal]   Pure read-only data.
```

Enforced by: `FoundationArchTest.java` rules R5b-R5e (Phase 1B additions).

---

## What this phase does NOT do

- No `destroyBlock` calls.
- No crack overlay.
- No `BlockHardnessProfile` reads.
- No `DamageAccumulator` per-block state.
- No `ImpactBreakQueue` deferred execution.
- `DamageResolver.resolve()` always returns `DamageEvent.NONE`.

These are Phase 1C and 1D work.
