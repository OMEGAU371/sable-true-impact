package io.github.omegau371.trueimpact.physics;

/**
 * Immutable damage-input contract for one contact event between two active sub-levels.
 *
 * All quantities are derived from Phase 1A calibration:
 *
 *   totalImpulseJ        = sumForceAmountRaw * substepDt
 *                          T-3 CONFIRMED (2026-06-09). Canonical. Use for damage formula.
 *
 *   impulseAlongNormalJ  = (mA*|dvA_n| + mB*|dvB_n|) / 2 summed across contacts.
 *                          T-6 UNCONFIRMED: normal direction convention not yet verified.
 *                          abs() applied at assembly to absorb sign ambiguity.
 *                          Do NOT use as primary damage input until T-6 passes.
 *
 *   effectiveMassKpg     = 1 / (1/mA + 1/mB).
 *                          Represents collision inertia. NaN if mass is unavailable.
 *
 *   contactCount         = number of Rapier contact records for this pair in this tick.
 *                          Used as proxy for contact surface area.
 *                          Larger structures in contact produce more records.
 *
 *   contactType          = DIAGNOSTIC FILTER TAG only.
 *                          The resolver uses it only to skip non-ACTIVE_IMPACT records.
 *                          The threshold (kpg*block/s per contact) that classifies
 *                          ACTIVE_IMPACT lives in the capture layer, not here.
 *                          The resolver MUST NOT branch on contactType to choose formula
 *                          parameters -- that would bake a diagnostic heuristic into physics.
 *
 * Invariants enforced at construction site (SableImpactCapture, Phase 1B):
 *   totalImpulseJ >= 0
 *   impulseAlongNormalJ >= 0  (abs applied)
 *   contactCount >= 1
 *   substepDt > 0
 */
public record ImpactRecord(
        long        serverTick,
        long        bodyPairKey,        // orderedBodyPairKey = min(idA,idB)<<32 | max(idA,idB)
        int         idA,
        int         idB,
        double      massAKpg,
        double      massBKpg,
        double      effectiveMassKpg,   // 1/(1/mA + 1/mB); NaN if unavailable
        int         contactCount,       // Rapier record count -- proxy for contact area
        double      totalImpulseJ,      // sumForce * substepDt  [CANONICAL]
        double      impulseAlongNormalJ,// dv reconstruction along normal [T-6 dir UC; abs applied]
        double      substepDt,          // reference only; baked into totalImpulseJ already
        ContactType contactType
) {}
