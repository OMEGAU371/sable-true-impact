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
 *
 * contactType filter rule:
 *   The resolver uses impact.contactType() only as a filter guard (skip ACTIVE_SUSTAINED).
 *   It MUST NOT branch on contactType to choose formula parameters.
 *   The threshold that classifies ACTIVE_IMPACT vs ACTIVE_SUSTAINED lives in the
 *   capture layer (SableImpactCapture). The resolver must not reproduce it.
 *
 * contactCount rule:
 *   MUST NOT appear in any formula in this class until a dedicated experiment confirms
 *   that contactCount correlates with contact surface area.
 *   It is available on ImpactRecord as diagnostic metadata; do not use it here yet.
 *
 * impulseAlongNormalJ rule:
 *   MUST NOT be used as primary formula input. T-6 normal direction is unconfirmed.
 *   Use totalImpulseJ (canonical) instead.
 *
 * Future formula direction (Phase 1C -- NOT YET):
 *   Use: totalImpulseJ, effectiveMassKpg, massAKpg, massBKpg
 *   Skip: contactCount (unconfirmed area proxy), impulseAlongNormalJ (T-6 unconfirmed)
 *   Route output to DamageAccumulator (per-block state, Phase 1C).
 *   destroyBlock deferred to ImpactBreakQueue (Phase 1D, never inside Rapier step).
 */
public final class DamageResolver {

    private DamageResolver() {}

    public enum DamageEvent {
        /** No damage this tick. Phase 1B always produces this. */
        NONE
    }

    /**
     * Resolves one active-vs-active ImpactRecord into a DamageEvent.
     *
     * Phase 1B: always returns NONE after the contactType guard.
     * The guard ensures sustained contacts never reach the accumulator (Phase 1C+).
     */
    public static DamageEvent resolve(ImpactRecord impact) {
        // Filter: sustained contacts never enter the damage path.
        // contactType is a filter tag, not a formula parameter.
        if (impact.contactType() != ContactType.ACTIVE_IMPACT) {
            return DamageEvent.NONE;
        }
        // Phase 1B observation only. totalImpulseJ is available for Phase 1C formula.
        return DamageEvent.NONE;
    }
}
