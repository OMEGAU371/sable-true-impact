package io.github.omegau371.trueimpact.damage;

import io.github.omegau371.trueimpact.physics.ContactType;
import io.github.omegau371.trueimpact.physics.ImpactRecord;

/**
 * Stateless resolver: ImpactRecord -> DamageEvent.
 *
 * Phase 1B skeleton: always returns NONE.
 * No block damage, crack accumulation, or destroyBlock calls in this phase.
 *
 * [arch] MUST NOT call Level.destroyBlock directly or indirectly.
 * [arch] MUST NOT read DiagnosticConfig, GlobalRateLimiter, or any observation/ state.
 * [arch] MUST NOT import from diagnostic/ or observation/ packages.
 * [arch] MUST NOT branch on ImpactRecord.contactType() to choose formula PARAMETERS.
 *         contactType is used only as a pipeline filter (skip non-impact records).
 *         The threshold that separates ACTIVE_IMPACT from ACTIVE_SUSTAINED is a
 *         diagnostic heuristic and must NOT appear in this class.
 *
 * Future formula direction (Phase 1C -- NOT YET):
 *   Input fields to use:    totalImpulseJ, effectiveMassKpg, contactCount
 *   Input fields to defer:  impulseAlongNormalJ  (needs T-6 normal direction confirmation)
 *   Formula sketch:
 *     equivalentStressJ = totalImpulseJ / contactCount  (per contact point)
 *     compare against BlockHardnessProfile per block face in the contact region
 *     -> crack fraction delta per block, accumulated in DamageAccumulator
 *   destroyBlock deferred to ImpactBreakQueue (never called inside Rapier physics step).
 */
public final class DamageResolver {

    private DamageResolver() {}

    public enum DamageEvent {
        /** No damage this tick (resting contact, below threshold, or Phase 1B skeleton). */
        NONE
    }

    /**
     * Resolves an ImpactRecord into a DamageEvent.
     *
     * Phase 1B: always returns NONE.
     * Non-ACTIVE_IMPACT records are filtered here; do not reach accumulator.
     */
    public static DamageEvent resolve(ImpactRecord impact) {
        if (impact.contactType() != ContactType.ACTIVE_IMPACT) {
            return DamageEvent.NONE;
        }
        // Phase 1B: observation only.
        // Future: compute damage from impact.totalImpulseJ() and impact.effectiveMassKpg().
        return DamageEvent.NONE;
    }
}
