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
 * Field groups:
 *
 *   Classification:
 *     contactType          -- ACTIVE_IMPACT or ACTIVE_SUSTAINED
 *
 *   Phase 1C energy formula (canonical, T-3 confirmed):
 *     totalImpulseJ        -- J from ImpactRecord; primary formula input
 *     effectiveMassKpg     -- 1/(1/mA+1/mB); formula denominator
 *     massAKpg, massBKpg   -- individual body masses for user to compute E_expected
 *     impactEnergyJ        -- J^2 / (2*mEff) [PRIMARY; canonical T-3]
 *     candidateStressEstimate -- = impactEnergyJ [no geometry scaling yet]
 *     materialThresholdJ   -- placeholder threshold [T-9 calibration pending]
 *     exceedsThreshold     -- diagnostic boolean; NEVER triggers game effect
 *
 *   Secondary (T-6 UNCONFIRMED):
 *     normalImpulseJ       -- impulseAlongNormalJ; abs applied; NOT primary input
 *     contactPressureProxy -- J / contactCount [area UNCONFIRMED]
 *
 *   T-8 velocity reconstruction (diagnostic; requires tick-start velocities):
 *     velReconAvailable    -- true iff tick-start vels were captured (LOG_RAW_CONTACTS on)
 *                            false -> relSpeedReconEstimate and reconKineticDeltaJ are NaN
 *     relSpeedReconEstimate -- impulseAlongNormalJ / mEff; proxy for pre-impact relative speed
 *                            [T-6 UNCONFIRMED; only valid when velReconAvailable=true]
 *     reconKineticDeltaJ   -- 0.5*mEff*relSpeed^2; independent energy estimate from velocity
 *                            [NaN when velReconAvailable=false]
 *                            Calibration check: reconKineticDeltaJ / impactEnergyJ should be ~1.0
 *                            if T-3 (force) and velocity-reconstruction methods agree.
 */
public record ImpactMetrics(
        // -- identification --
        long serverTick,
        long bodyPairKey,
        ContactType contactType,

        // -- canonical energy formula (T-3 confirmed) --
        double totalImpulseJ,           // from ImpactRecord; primary input
        double effectiveMassKpg,        // 1/(1/mA+1/mB)
        double massAKpg,
        double massBKpg,
        double impactEnergyJ,           // J^2/(2*mEff) [PRIMARY]

        // -- threshold comparison (diagnostic only) --
        double candidateStressEstimate, // = impactEnergyJ [no geometry yet]
        double materialThresholdJ,      // placeholder [T-9 pending]
        boolean exceedsThreshold,       // diagnostic; never triggers game effect

        // -- T-6 secondary (UNCONFIRMED) --
        double normalImpulseJ,          // impulseAlongNormalJ [T-6 UC]
        double contactPressureProxy,    // J/contactCount [area UC]

        // -- T-8 velocity reconstruction (requires LOG_RAW_CONTACTS) --
        boolean velReconAvailable,      // false -> relSpeed/reconKinetic are NaN
        double relSpeedReconEstimate,   // impulseAlongNormalJ/mEff [T-6+velAvail only]
        double reconKineticDeltaJ       // 0.5*mEff*relSpeed^2 [NaN if unavail]
) {}
