# Phase 1C -- Damage Calculation Model (Diagnostics Only)

## Status: DESIGN (no damage, no destroyBlock)

Phase 1C defines the first quantitative pass through the damage pipeline.
All outputs are diagnostic values only. DamageResolver still returns NONE.
No block is cracked, accumulated, or destroyed in this phase.

---

## T-8 Unit Audit Result (0.4.4-phase1c) -- CANONICAL FORMULA PIVOT

T-8 audit (manual test, 2026-06-10) proved the forceAmount-derived formula is invalid:

  J = rawSumForce * substepDt = 49.390 (kpg*block/s)
  E_current = J^2/(2*mEff) = 4061  (kpg*block^2/s^2)
  kDelta = abs(kBefore - kAfter) = 4.009
  ratio = kDelta / E_current = 0.0008  [target: ~1.0]

The ratio is ~0.0008, meaning E_current is ~1250x larger than the measured kinetic energy
change. E_noDt (treating rawSum as impulse directly) = 7.8M -- even worse.

Root cause: Sable forceAmountRaw is a solver/contact diagnostic value whose physical
units in the JNI layer are not consistent with a direct impulse measurement.  The Java
T-3 calibration confirmed forceAmountRaw scales as force (not impulse) but the absolute
magnitude is still ~1000x off from what velocity measurements indicate.

Resolution: Phase 1C canonical damage energy switches to velocity-derived kinetic metric.
forceAmountRaw / impactEnergyJ / rawSumForce are retained as SOLVER DIAGNOSTIC ONLY
and must NOT enter any damage formula branch.

---

## Inputs available from Phase 1B (ImpactRecord)

| field | status | use in Phase 1C |
|---|---|---|
| `totalImpulseJ` | T-3 CONFIRMED for scale | SOLVER DIAGNOSTIC ONLY -- ~1000x off from kDelta |
| `effectiveMassKpg` | derived, ready | Used by both solver and velocity-derived paths |
| `massAKpg`, `massBKpg` | available | secondary; needed if asymmetric formulas are added |
| `contactType` | diagnostic tag | split last-record vs last-impact diagnostics |
| `contactCount` | UNCONFIRMED as area proxy | diagnostic metadata ONLY; not in any formula |
| `impulseAlongNormalJ` | T-6 UNCONFIRMED | secondary diagnostic; not primary input |
| `substepDt` | reference (baked into J) | do not multiply again |

---

## Phase 1C Canonical Physics Model (post T-8 pivot)

### Velocity-derived kinetic energy

After the T-8 unit audit, the canonical Phase 1C damage energy is:

  kineticImpactEnergyJ = abs(kBefore - kAfter)
                       = abs(0.5*mEff*relBefore^2 - 0.5*mEff*relAfter^2)

where:
  relBefore = ||vA_start - vB_start||   (tick-start PRE_STEP velocities; requires contacts on)
  relAfter  = ||vA_post  - vB_post||    (last substep POST_STEP velocities; always available)
  mEff = 1/(1/mA + 1/mB)

Uses abs() not max(0,...): Rapier's spring contact model + gravity over the tick window
can make kAfter > kBefore even during genuine impacts (energy gain from bounce + gravity).
Using abs() preserves the signal; the sign question is deferred to Phase 1D.

Supporting field:
  velocityDerivedImpulseJ = mEff * ||deltaVRel_3D||
  where deltaVRel_3D = (vA_after-vA_before) - (vB_after-vB_before)
  This is the 3D relative velocity change magnitude * mEff, approximating impulse.
  Requires all 4 body velocity readings.

### Limitation: tick-window gravity contamination

kBefore uses tick-START velocities (PRE_STEP substep 0). This includes gravity and any
forces that acted on the bodies BEFORE the current contact. For falling blocks, kBefore
is inflated by pre-impact gravity. This introduces a systematic overestimate that will
need correction in Phase 1D (e.g., using per-substep pre/post pairs or normal projection).

### Solver diagnostic retained (NOT canonical)

impactEnergyJ = J^2/(2*mEff) where J = rawSumForce * substepDt
  retained for: contact-strength classification (IMPACT vs SUSTAINED threshold)
  retired from: canonical damage energy formula
  unit status: unknown -- forceAmountRaw JNI units unresolved

---

## Five diagnostic calculation outputs

All five are computed from ImpactRecord fields. None triggers any game effect in Phase 1C.

### 0. kineticImpactEnergyJ [Phase 1C CANONICAL]

```
kineticImpactEnergyJ = abs(kBefore - kAfter)
                     = kineticDeltaMagnitudeJ
```

Physical meaning: magnitude of kinetic energy change in the relative reference frame.
This is the Phase 1C canonical damage input after the T-8 unit audit.
NaN when velocity snapshots unavailable (contacts debug off); exceedsThreshold = false.

### 0b. velocityDerivedImpulseJ [Phase 1C canonical supporting field]

```
velocityDerivedImpulseJ = mEff * ||deltaVRel_3D||
```

Physical meaning: effective mass times the 3D relative velocity change magnitude.
Approximates the net momentum transfer at the contact.
NaN when any of the 4 velocity readings is missing.

### 1. impactEnergyJ [SOLVER DIAGNOSTIC -- NOT canonical]

```
impactEnergyJ = totalImpulseJ^2 / (2 * effectiveMassKpg)
```

Physical meaning: UNKNOWN -- forceAmountRaw unit in Sable JNI unresolved.
T-8 unit audit showed this is ~1000x larger than measured kDelta.
Use: retained for ACTIVE_IMPACT classification (contact strength); must NOT enter formula.
Unit: kpg * (block/s)^2 (Sable unit system; scale factor unresolved).

### 2. normalImpulseJ

```
normalImpulseJ = impulseAlongNormalJ   [T-6 UNCONFIRMED; abs applied at capture layer]
```

Physical meaning: impulse component along the contact normal (rough directional proxy).
Status: T-6 (normal direction convention) not yet confirmed.
Use: secondary diagnostic only. Do NOT substitute for totalImpulseJ as primary input.
Output marker: "(T-6 unconfirmed)" label in all diagnostic output.

### 3. contactPressureProxy

```
contactPressureProxy = totalImpulseJ / contactCount   [UNCONFIRMED: contactCount ~ area]
```

Physical meaning: impulse per contact point, proxy for distributed contact pressure.
Status: contactCount ~ contact area correlation UNCONFIRMED (needs dedicated experiment).
Use: diagnostic metadata only. Must NOT appear in any formula until the area experiment passes.
Output marker: "(area unconfirmed)" label in all diagnostic output.

### 4. candidateStressEstimate [now velocity-derived]

```
candidateStressEstimate = kineticImpactEnergyJ   [Phase 1C: velocity-derived, no geometry scaling]
```

Physical meaning: placeholder for the stress calculation that will eventually factor in
block geometry, contact area, and material properties.
In Phase 1C, this equals kineticImpactEnergyJ (velocity-derived canonical).
NaN when velocity data unavailable -- exceedsThreshold = false in that case.
Phase 1D will scale this by effective contact area once the area experiment (T-10) passes.

### 5. materialThresholdJ and exceedsThreshold

```
materialThresholdJ  = PLACEHOLDER (50.0; calibrate in T-9)
exceedsThreshold    = (kineticImpactEnergyJ > materialThresholdJ)
```

Physical meaning: comparison between velocity-derived impact energy and a material fracture threshold.
Phase 1C status: BlockHardnessProfile not yet implemented. Placeholder 50.0.
exceedsThreshold = false when kineticImpactEnergyJ is NaN (contacts debug off).

PLACEHOLDER value for Phase 1C diagnostic: materialThresholdJ = 50.0 (arbitrary; calibrate in T-9).

exceedsThreshold is logged/displayed as a boolean diagnostic.
DamageResolver must still return NONE regardless of exceedsThreshold.
This boolean must NOT trigger destroyBlock, crack overlay, or any game state change.

---

## Architecture for Phase 1C

### New: physics/ImpactMetrics (pure data record)

```java
package io.github.omegau371.trueimpact.physics;

public record ImpactMetrics(
    long serverTick,
    long bodyPairKey,
    double impactEnergyJ,          // J^2 / (2 * m_eff) [PRIMARY]
    double normalImpulseJ,         // impulseAlongNormalJ [T-6 UNCONFIRMED]
    double contactPressureProxy,   // J / contactCount   [area UNCONFIRMED]
    double candidateStressEstimate,// = impactEnergyJ    [no geometry yet]
    double materialThresholdJ,     // placeholder        [BlockHardnessProfile PENDING]
    boolean exceedsThreshold       // impactEnergyJ > threshold [diagnostic only]
) {}
```

Constraints:
  - Lives in physics/ -- no dependencies on any TI internal package (R9)
  - Immutable record; no side effects
  - All UNCONFIRMED fields must be accessible to diagnostics; must NOT flow into DamageResolver

### Compute location: SableImpactCapture

ImpactMetrics is computed in SableImpactCapture.process() for every active-vs-active ImpactRecord,
after ImpactRecord is assembled and before DamageResolver.resolve() is called.
SableImpactCapture stores the most recent ImpactMetrics as a field for status query.

### DamageResolver: no change

DamageResolver.resolve() still returns NONE for all inputs.
It must NOT receive ImpactMetrics -- metrics flow only to diagnostic output, never to the resolver.

### Diagnostic output

ImpactMetrics fields are added to SableImpactCapture.RuntimeStats and surfaced by
/trueimpact debug status as two output lines:

```
[TI capture last-record-metrics] tick=T type=ACTIVE_SUSTAINED energyJ=X normalJ=Y pressureProxy=Z stress=S thresholdJ=W exceeds=false
[TI capture last-impact-metrics] tick=T energy=X normalJ=Y pressureProxy=Z stress=S thresholdJ=W exceeds=false
```

`last-record-metrics` is the most recent active-vs-active record of any ContactType.
`last-impact-metrics` is the most recent ACTIVE_IMPACT only; ACTIVE_SUSTAINED records
must not overwrite it.

---

## Phase 1C experiment sequence

### T-8: impactEnergyJ scale validation -- CONCLUDED (FAILED; canonical pivot complete)

Goal: confirm impactEnergyJ = J^2/(2*m_eff) is proportional to observable velocity change.

Result (0.4.4-phase1c manual test):
  ratio = kDelta / E_current = 0.0008  [~1000x off; T-8 FAILED]

Conclusion: forceAmountRaw-derived energy is NOT a valid physical impulse energy for
damage purposes. Phase 1C canonical switched to velocity-derived kineticImpactEnergyJ.
The T-8 rolling stats remain in place to monitor the ratio for diagnostic reference,
but the formula is no longer the canonical damage energy.

Status output after pivot:
```
[TI capture T8-audit] rawSum=X substepDt=Y contactCount=N J=Z E_current=C E_noDt=D kDelta=K ratio_current=R ratio_noDt=S
[TI capture canonical] source=velocity-full kImpact=K kBefore=B kAfter=A kDelta=D dVRel3D=V velImpulse=I exceeds=T/F threshold=50.000
```

### T-9: materialThresholdJ calibration

Goal: find a threshold value that separates "clearly visible impact" from "no effect" on
a chosen reference block (stone, hardness=1.5).

Method:
  - Drop sub-levels of increasing mass / height
  - Record impactEnergyJ at each test
  - Identify the energy range below which stone is visibly undamaged in legacy testing
  - This gives a floor estimate for materialThresholdJ for stone

Note: the exceedsThreshold diagnostic boolean is the output of this comparison.
No block is actually broken in Phase 1C -- this is purely a calibration data point
for the Phase 1D threshold formula.

### T-10 (future): contactCount ~ contact area

Goal: confirm or deny that contactCount correlates with the geometric contact area
between two sub-levels.

Method:
  - Two cuboid sub-levels with controlled face-to-face contact area (1x1, 2x2, 4x4)
  - Record contactCount vs measured face area at each configuration
  - If correlation is linear: contactCount can be used as area proxy in Phase 1D+
  - If not: contactPressureProxy formula must be revised or dropped

Status: UNCONFIRMED. contactPressureProxy remains a diagnostic label until this passes.

---

## What Phase 1C does NOT do

- No destroyBlock calls.
- No crack overlay.
- No DamageAccumulator per-block state.
- DamageResolver.resolve() always returns DamageEvent.NONE.
- contactCount NOT used in any formula.
- exceedsThreshold does NOT trigger any game state change.
- normalImpulseJ NOT used as primary formula input (T-6 unconfirmed).
- candidateStressEstimate has no geometry scaling (placeholder only).
- materialThresholdJ is a hardcoded test value, not from BlockHardnessProfile.
