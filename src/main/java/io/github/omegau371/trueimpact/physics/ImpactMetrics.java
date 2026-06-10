package io.github.omegau371.trueimpact.physics;

/**
 * Diagnostic-only calculations derived from one ImpactRecord.
 *
 * Phase 1C contract:
 *   - pure data; no gameplay effect
 *   - not passed into DamageResolver
 *   - contactCount remains diagnostic-only and must not become a damage multiplier
 *   - computed for ALL active-vs-active ImpactRecords (ACTIVE_IMPACT and ACTIVE_SUSTAINED)
 *
 * Field groups (see inline comments for calibration status):
 *
 *   Identification
 *   Canonical energy formula (T-3 confirmed)
 *   Threshold comparison (diagnostic only)
 *   T-6 UNCONFIRMED -- normal-projected fields (separate from T-8)
 *   T-8 -- velocity availability flags (explicit; never inferred from other values)
 *   T-8 -- 3D relative speed (no normal projection; T-6 independent)
 *   T-8 -- kinetic energy comparison
 *   Unit audit -- raw inputs for candidate formula comparison
 *
 * T-8 design note:
 *   The canonical impulse energy E=J^2/(2*mEff) is validated by comparing it with the
 *   OBSERVED kinetic energy change kineticDeltaMagnitudeJ = |kBefore - kAfter|.
 *   Both are computed WITHOUT projecting onto the contact normal, so the comparison is
 *   independent of T-6 (normal direction convention).
 *   If ratio = kineticDelta / impulseEnergy ~= 1.0, the formula is consistent with
 *   the measured velocity change.
 *   Timing caveat: relBefore uses tick-start velocity (substep 0 PRE_STEP), which
 *   includes gravity and other forces over the full tick -- not purely the contact impulse.
 */
public record ImpactMetrics(

        // -- identification --
        long        serverTick,
        long        bodyPairKey,
        ContactType contactType,

        // -- canonical energy formula (T-3 confirmed) --
        double totalImpulseJ,           // from ImpactRecord; primary formula input
        double effectiveMassKpg,        // 1/(1/mA+1/mB); formula denominator
        double massAKpg,
        double massBKpg,
        double impactEnergyJ,           // J^2/(2*mEff) [PRIMARY; T-3 canonical]

        // -- threshold comparison (diagnostic only) --
        double  candidateStressEstimate, // = impactEnergyJ [no geometry yet]
        double  materialThresholdJ,      // placeholder [T-9 calibration pending]
        boolean exceedsThreshold,        // diagnostic; NEVER triggers game effect

        // -- T-6 UNCONFIRMED: normal-projected fields --
        // Normal direction convention not verified. Do NOT use as primary formula input.
        // Kept for T-6 validation when that experiment runs.
        double normalImpulseJ,          // impulseAlongNormalJ [T-6 UC; abs applied]
        double contactPressureProxy,    // J/contactCount [area UC]

        // -- T-8 kinetic: velocity availability flags --
        // Explicit booleans -- never inferred from the values themselves.
        boolean hasStartVelA,           // tickStartVels contained body A at substep-0
        boolean hasStartVelB,           // tickStartVels contained body B at substep-0
        boolean hasPostVelA,            // lastPostSnaps body A had velocityReadValid=true
        boolean hasPostVelB,            // lastPostSnaps body B had velocityReadValid=true

        // -- T-8 kinetic: 3D relative speed magnitude (no normal projection) --
        // velBefore = tickStartVels (substep-0 PRE_STEP); velAfter = lastPostSnaps (final substep).
        // NaN when the corresponding availability flag(s) are false.
        // Timing note: relBefore covers the full tick, including gravity and other forces.
        double relativeSpeedBeforeMagnitude,  // ||vA_start - vB_start||
        double relativeSpeedAfterMagnitude,   // ||vA_post - vB_post||
        double deltaRelativeSpeedMagnitude,   // |relBefore - relAfter|; NaN if either unavail

        // -- T-8 kinetic: energy comparison --
        // T-8 calibration: ratio = kineticDeltaMagnitudeJ / impactEnergyJ
        //   ~1.0 -> formula consistent with measured velocity change
        //   << 1.0 or >> 1.0 -> systematic offset (gravity contamination, timing mismatch, etc.)
        double kineticBeforeJ,          // 0.5*mEff*relBefore^2; NaN if start vels unavail
        double kineticAfterJ,           // 0.5*mEff*relAfter^2; NaN if post vels unavail
        double kineticDeltaMagnitudeJ,  // |kBefore - kAfter|; NaN if either unavail

        // -- unit audit: raw inputs for candidate formula comparison --
        // rawSumForce and substepDtUsed let the diagnostic command reconstruct and compare
        // multiple energy candidate formulas against kineticDeltaMagnitudeJ:
        //   E_current  = (rawSumForce * substepDtUsed)^2 / (2*mEff) == impactEnergyJ
        //   E_noDt     = rawSumForce^2 / (2*mEff)  [if rawSum were already the impulse]
        // Whichever E is closest to kDelta identifies the correct unit interpretation.
        int    contactCount,            // raw Rapier contact entries for this pair this tick
        double rawSumForce,             // sum of forceAmountRaw before x substepDtUsed
        double substepDtUsed            // 0.05 / substepsPerTick; used to produce totalImpulseJ

) {}
