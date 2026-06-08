# Phase 1B -- Structure-vs-Structure Damage Pipeline Design

## Status: DESIGN (no damage implemented)

Phase 1B defines the data contract and pipeline shape.
No block breakage, crack accumulation, or collision response is added in this phase.

---

## Confirmed inputs from Phase 1A

| Finding | Source | Status |
|---|---|---|
| applyImpulse = direct impulse; ratio ~1.0 | T-4 manual (2026-06-06) | CONFIRMED |
| forceAmountRaw = per-substep force; J = raw * substepDt | T-3 manual (2026-06-09) | CONFIRMED |
| clearCollisions returns active-vs-active contacts | T-3 live test | CONFIRMED |
| contactCount correlates with contact surface area | NOT TESTED | UNCONFIRMED |
| T-6 contact normal direction convention | NOT TESTED | UNCONFIRMED |
| T-5 substep attribution inside clearCollisions | NOT TESTED | UNCONFIRMED |

---

## Data flow (Stage 0 to Stage 4)

```
[Stage 0: Rapier physics]
  Rapier3D.clearCollisions(sceneId)
    raw double[N*15]  idA, idB, forceAmountRaw, normalA, normalB, pointA, pointB

[Stage 1: Contact capture -- existing mixin hook, sable/ package]
  DiagnosticContactCaptureMixin.captureContactData()
    |
    +--> ContactLogger.onClearCollisions()    [diagnostic only; T-3/T-5/T-6 logs]
    |
    +--> SableImpactCapture (Phase 1B NEW)   [assembles ImpactRecord]
         Discards world-vs-active and unknown pairs silently (no ImpactRecord).
         For each remaining active-vs-active pair in this tick:
           totalImpulseJ       = sum(forceAmountRaw_i) * substepDt  [CANONICAL T-3]
           impulseAlongNormalJ = sum(mX * |dvX_n|) / 2 per pair    [T-6 UNCONFIRMED; abs]
           effectiveMassKpg    = 1 / (1/mA + 1/mB)
           contactCount        = Rapier record count for this pair   [UNCONFIRMED as area]
           contactType         = ACTIVE_IMPACT if totalImpulseJ/count > threshold
                                 ACTIVE_SUSTAINED otherwise
         Creates ImpactRecord per pair.
         Passes to DamageResolver.

[Stage 2: ImpactRecord -- physics/ package]
  Immutable data contract for ONE active-vs-active pair per tick.
  World-vs-active contacts never reach this stage.
  ContactType has exactly two values: ACTIVE_IMPACT | ACTIVE_SUSTAINED.
  See ImpactRecord.java for field-by-field calibration status.

[Stage 3: DamageResolver -- damage/ package, Phase 1B skeleton]
  input:  ImpactRecord (active-vs-active only)
  output: DamageEvent { NONE }

  Phase 1B contract (skeleton):
    - Always returns NONE.
    - Pure function; no side effects.
    - MUST NOT call Level.destroyBlock (directly or indirectly).
    - MUST NOT read DiagnosticConfig.* or any observation/ state.
    - contactType is used only as a filter guard (skip ACTIVE_SUSTAINED).
      Not a formula parameter; must not branch to choose formula values.
    - contactCount MUST NOT appear in any formula here until a dedicated
      experiment confirms contactCount ~ contact area. It is available on
      ImpactRecord as diagnostic metadata only.
    - impulseAlongNormalJ MUST NOT be used as primary formula input
      until T-6 (normal direction convention) is confirmed.

  Future formula direction (Phase 1C -- NOT YET):
    Formula-ready fields:  totalImpulseJ, effectiveMassKpg, massAKpg, massBKpg
    Deferred fields:       contactCount (needs area experiment), impulseAlongNormalJ (T-6)
    Output: DamageEvent to DamageAccumulator (Phase 1C).

[Stage 4: DamageAccumulator -- damage/ package, Phase 1C NOT YET]
[Stage 5: ImpactBreakQueue  -- damage/ package, Phase 1D NOT YET]
```

---

## ImpactRecord field contract

```
totalImpulseJ        CONFIRMED (T-3 2026-06-09). Canonical formula input.
                     = sumForceAmountRaw * substepDt.
                     Use this for all damage calculations in Phase 1C+.

impulseAlongNormalJ  UNCONFIRMED (T-6 normal direction not tested).
                     abs() applied at assembly. Do NOT use as primary formula input.
                     Keep for directional diagnostics only.

effectiveMassKpg     Ready. 1/(1/mA + 1/mB). NaN if unavailable.

contactCount         DIAGNOSTIC METADATA -- UNCONFIRMED as contact area proxy.
                     A dedicated experiment is required to confirm whether
                     contactCount correlates with contact surface area before
                     it may appear in any damage formula.
                     Present on ImpactRecord to avoid re-derivation later;
                     not ready for formula use in Phase 1B or 1C without evidence.

contactType          DIAGNOSTIC FILTER TAG only.
                     Two values: ACTIVE_IMPACT | ACTIVE_SUSTAINED.
                     Resolver early-return only. Never a formula branch.
                     Threshold lives in SableImpactCapture, not here.

substepDt            Reference only. Already baked into totalImpulseJ.
```

---

## ArchUnit boundaries (enforced at build time)

```
physics/    -- no TI internal dependencies at all (command, platform, sable, mixin,
               damage, diagnostic, observation all forbidden). Pure Java record + enum.
               Enforced by R9 (comprehensive single rule).

damage/     -- depends on physics/ only.
               Must NOT import diagnostic/, observation/, command/, platform/, sable/, mixin/.
               Enforced by R11 (diagnostic/observation) + general build structure.
               Note: command/ and sable/ are not yet explicitly guarded for damage/.
               If damage/ grows to depend on them it would be a design smell; add a rule.

sable/      -- bridge layer: may depend on physics/, diagnostic/, observation/.
               No explicit ArchUnit rule required; enforced by the rules above.

diagnostic/ -- must NOT depend on damage/ (R7). May depend on physics/ (not restricted).

observation/ -- must NOT depend on damage/ (R5). Pure read-only.
```

Rule coverage gap note (2026-06-09):
  damage/ is not explicitly guarded against command/, platform/, sable/, or mixin/.
  These should not be needed and would be a design smell.
  Add rules if/when damage/ grows toward those packages.

---

## What this phase does NOT do

- No destroyBlock calls.
- No crack overlay.
- No BlockHardnessProfile reads.
- No DamageAccumulator per-block state.
- No ImpactBreakQueue deferred execution.
- DamageResolver.resolve() always returns DamageEvent.NONE.
- contactCount is NOT used in any formula.
- impulseAlongNormalJ is NOT used as a primary formula input.
