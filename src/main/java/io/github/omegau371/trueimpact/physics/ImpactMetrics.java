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
 * Phase 1C canonical energy: velocity-derived kinetic metric
 *   T-8 unit audit (0.4.4-phase1c) showed that the Sable forceAmountRaw formula
 *   (E = J^2/(2*mEff) where J = rawSumForce*substepDt) produces values ~1000x larger than
 *   the measured kinetic energy change.  Neither E_current nor E_noDt (= rawSum^2/(2*mEff))
 *   approach kDelta.  Conclusion: Sable forceAmountRaw is a solver/contact diagnostic value,
 *   NOT a direct physical impulse that can feed the damage formula.
 *
 *   The Phase 1C canonical damage energy is now:
 *     kineticImpactEnergyJ = abs(kBefore - kAfter)  [velocity-derived; NaN when vels unavail]
 *   with supporting field:
 *     velocityDerivedImpulseJ = mEff * ||deltaVRel_3D||  [momentum change proxy]
 *
 *   forceAmountRaw / impactEnergyJ / rawSumForce are retained as SOLVER DIAGNOSTIC ONLY
 *   and must NOT enter any damage formula branch.
 *
 * kineticImpactEnergyJ uses abs() not max(0,...):
 *   Rapier's spring contact model and the tick-window timing (kBefore = substep-0 PRE_STEP,
 *   kAfter = final POST_STEP) mean kAfter can exceed kBefore even during genuine impacts
 *   (gravity + restitution overshoot).  Using abs() gives a non-zero signal in all impact cases.
 *   The sign question is deferred to Phase 1D.  When kineticImpactEnergyJ is NaN (velocity
 *   data unavailable because 'debug contacts on' is off), candidateStressEstimate = NaN and
 *   exceedsThreshold = false -- no false positives.
 *
 * Field groups:
 *
 *   Identification
 *   Solver diagnostic (NOT canonical energy; forceAmount-based; ~1000x off from kDelta)
 *   Threshold comparison (diagnostic only; now driven by velocity-derived canonical)
 *   T-6 UNCONFIRMED -- normal-projected fields
 *   T-8 velocity availability flags
 *   T-8 3D relative speed
 *   T-8 kinetic energy comparison
 *   Unit audit -- raw inputs for candidate formula comparison
 *   Phase 1C canonical -- velocity-derived kinetic energy
 */
public record ImpactMetrics(

        // -- identification --
        long        serverTick,
        long        bodyPairKey,
        ContactType contactType,

        // -- solver diagnostic: forceAmount-derived (NOT canonical damage energy) --
        // T-8 audit showed these are ~1000x larger than measured kDelta.
        // Retained for contact-strength classification (ACTIVE_IMPACT vs ACTIVE_SUSTAINED)
        // and for future investigation into the Rapier JNI unit conventions.
        // Do NOT use impactEnergyJ or rawSumForce in any damage formula branch.
        double totalImpulseJ,           // rawSumForce * substepDtUsed [SOLVER DIAGNOSTIC]
        double effectiveMassKpg,        // 1/(1/mA+1/mB); used by both solver and velocity paths
        double massAKpg,
        double massBKpg,
        double impactEnergyJ,           // J^2/(2*mEff) [SOLVER DIAGNOSTIC -- ~1000x off]

        // -- threshold comparison (diagnostic only) --
        // candidateStressEstimate now = kineticImpactEnergyJ (velocity-derived).
        // NaN when velocity data unavailable -- exceedsThreshold will be false in that case.
        double  candidateStressEstimate, // = kineticImpactEnergyJ [velocity-derived canonical]
        double  materialThresholdJ,      // placeholder [T-9 calibration pending]
        boolean exceedsThreshold,        // diagnostic; NEVER triggers game effect

        // -- T-6 UNCONFIRMED: normal-projected fields --
        // Normal direction convention not verified. Do NOT use as primary formula input.
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
        double relativeSpeedBeforeMagnitude,  // ||vA_start - vB_start||
        double relativeSpeedAfterMagnitude,   // ||vA_post - vB_post||
        double deltaRelativeSpeedMagnitude,   // |relBefore - relAfter|; NaN if either unavail

        // -- T-8 kinetic: energy comparison (raw measurement) --
        double kineticBeforeJ,          // 0.5*mEff*relBefore^2; NaN if start vels unavail
        double kineticAfterJ,           // 0.5*mEff*relAfter^2; NaN if post vels unavail
        double kineticDeltaMagnitudeJ,  // |kBefore - kAfter|; NaN if either unavail

        // -- unit audit: raw inputs for candidate formula comparison --
        int    contactCount,            // raw Rapier contact entries for this pair this tick
        double rawSumForce,             // sum of forceAmountRaw before x substepDtUsed
        double substepDtUsed,           // 0.05 / substepsPerTick; used to produce totalImpulseJ

        // -- Phase 1C canonical: velocity-derived kinetic energy --
        // These fields are the primary Phase 1C damage energy candidates.
        // Both require velocity snapshots (debug contacts on); NaN when unavailable.
        //
        // kineticImpactEnergyJ = abs(kBefore - kAfter):
        //   Uses abs() not max(0,...) because Rapier's spring contact model + gravity over
        //   the tick window can make kAfter > kBefore even in real impacts.
        //   Equals kineticDeltaMagnitudeJ in value; exposed separately as the canonical field.
        //
        // velocityDerivedImpulseJ = mEff * ||deltaVRel_3D||:
        //   3D relative velocity change between tick-start and post-last-substep.
        //   deltaVRel = (vA_after - vA_before) - (vB_after - vB_before) in world space.
        //   Requires all 4 body velocity readings; NaN if any missing.
        double kineticImpactEnergyJ,    // abs(kBefore-kAfter); Phase 1C canonical; NaN if unavail
        double velocityDerivedImpulseJ  // mEff*||deltaVRel_3D||; NaN if any vel missing

) {}
