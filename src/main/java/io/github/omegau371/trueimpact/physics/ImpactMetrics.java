package io.github.omegau371.trueimpact.physics;

/**
 * Diagnostic-only calculations derived from one ImpactRecord.
 *
 * Phase 1C contract:
 *   - pure data; no gameplay effect
 *   - not passed into DamageResolver
 *   - contactCount remains diagnostic-only and must not become a damage multiplier
 */
public record ImpactMetrics(
        long serverTick,
        long bodyPairKey,
        double impactEnergyJ,
        double normalImpulseJ,
        double contactPressureProxy,
        double candidateStressEstimate,
        double materialThresholdJ,
        boolean exceedsThreshold
) {}
