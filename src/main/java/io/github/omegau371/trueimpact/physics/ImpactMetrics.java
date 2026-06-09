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
 * contactType is included so diagnostic consumers can distinguish the two categories
 * without re-deriving the threshold comparison.
 */
public record ImpactMetrics(
        long serverTick,
        long bodyPairKey,
        ContactType contactType,           // ACTIVE_IMPACT or ACTIVE_SUSTAINED
        double impactEnergyJ,
        double normalImpulseJ,
        double contactPressureProxy,
        double candidateStressEstimate,
        double materialThresholdJ,
        boolean exceedsThreshold
) {}
