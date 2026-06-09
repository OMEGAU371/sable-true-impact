# Phase 1C -- Damage Calculation Model (Diagnostics Only)

## Status: DESIGN (no damage, no destroyBlock)

Phase 1C defines the first quantitative pass through the damage pipeline.
All outputs are diagnostic values only. DamageResolver still returns NONE.
No block is cracked, accumulated, or destroyed in this phase.

---

## Inputs available from Phase 1B (ImpactRecord)

| field | status | use in Phase 1C |
|---|---|---|
| `totalImpulseJ` | T-3 CONFIRMED | PRIMARY -- all Phase 1C formulas derive from this |
| `effectiveMassKpg` | derived, ready | PRIMARY -- reduces to a single mass for energy formula |
| `massAKpg`, `massBKpg` | available | secondary; needed if asymmetric formulas are added later |
| `contactType` | diagnostic tag | split last-record vs last-impact diagnostics |
| `contactCount` | UNCONFIRMED as area proxy | diagnostic metadata ONLY; not in any formula |
| `impulseAlongNormalJ` | T-6 UNCONFIRMED | secondary diagnostic; not primary input |
| `substepDt` | reference (baked into J) | do not multiply again |

---

## Canonical physics model

### Why J^2/(2*m_eff) and not 0.5*m_eff*v_rel^2

Both forms are equivalent, but `J` is the directly measured quantity.
`v_rel_pre` (pre-impact relative velocity along normal) is not directly available:
  - Post-step closing velocity is near zero (solver resolved the gap constraint).
  - Tick-start velocity reconstruction gives a proxy but not the true pre-impact value.

Using J avoids this unknown. The conversion is:

  J = m_eff * v_rel_pre           (impulse-momentum, restitution e=0 approximation)
  E = 0.5 * m_eff * v_rel_pre^2
    = 0.5 * m_eff * (J / m_eff)^2
    = J^2 / (2 * m_eff)

Restitution note: Sable appears to use near-zero restitution for rigid body collisions
based on T-3 observations (bodies do not bounce after collision). The e=0 approximation
is conservative and appropriate until T-8 validates the energy scale.

---

## Five diagnostic calculation outputs

All five are computed from ImpactRecord fields. None triggers any game effect in Phase 1C.

### 1. impactEnergyJ

```
impactEnergyJ = totalImpulseJ^2 / (2 * effectiveMassKpg)
```

Physical meaning: kinetic energy transferred at the contact pair this tick.
This is the primary candidate for the Phase 1D damage input.
Unit: kpg * (block/s)^2 (Sable unit system; absolute value TBD by T-8).

Validity: effectiveMassKpg must be > 0 and finite. If NaN, impactEnergyJ = NaN (propagate).

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

### 4. candidateStressEstimate

```
candidateStressEstimate = impactEnergyJ   [Phase 1C: no geometry scaling yet]
```

Physical meaning: placeholder for the stress calculation that will eventually factor in
block geometry, contact area, and material properties.
In Phase 1C, this equals impactEnergyJ because no geometry model or area estimate exists.
Phase 1D will scale this by effective contact area once the area experiment (T-10) passes.
Output marker: "(no geometry scaling)" label.

### 5. materialThresholdJ and exceedsThreshold

```
materialThresholdJ  = PLACEHOLDER_HARDNESS_FACTOR * CALIBRATION_CONST
exceedsThreshold    = (impactEnergyJ > materialThresholdJ)
```

Physical meaning: comparison between impact energy and a material-derived fracture threshold.
Phase 1C status: BlockHardnessProfile not yet implemented. Use a hardcoded test value for
one reference block type (e.g., stone: hardness=1.5, threshold to be calibrated by T-9).

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

### T-8: impactEnergyJ scale validation

Goal: confirm impactEnergyJ = J^2/(2*m_eff) is proportional to observable kinetic energy.

Method:
  - Controlled drop from known height H; expected E_kin = mA * g * H
  - Compare impactEnergyJ at contact with expected E_kin
  - Calibration ratio = impactEnergyJ / (mA * g * H) should be ~1.0 if formula is correct

Expected output: a calibration factor CF such that E_physical = impactEnergyJ * CF.
If CF ~= 1.0, the Sable mass/velocity units are consistent with our formula.

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
