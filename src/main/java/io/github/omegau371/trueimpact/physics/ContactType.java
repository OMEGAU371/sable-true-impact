package io.github.omegau371.trueimpact.physics;

/**
 * Diagnostic classification tag for an active-vs-active contact event.
 *
 * ContactType has exactly two values because ImpactRecord is only ever constructed
 * for active-vs-active pairs. World-vs-active and unknown pairs are discarded at the
 * capture layer (SableImpactCapture) before an ImpactRecord is created.
 *
 * This enum is a PIPELINE FILTER TAG, not a formula parameter.
 * The resolver uses it only for an early return (skip ACTIVE_SUSTAINED records).
 * The threshold that separates ACTIVE_IMPACT from ACTIVE_SUSTAINED is a
 * diagnostic heuristic that lives in the capture layer, never here or in the resolver.
 * The resolver MUST NOT branch on ContactType to choose formula parameters.
 */
public enum ContactType {
    /**
     * Active sub-level vs active sub-level; impulse/contact above the diagnostic threshold.
     * Primary candidate for structure-vs-structure damage computation.
     */
    ACTIVE_IMPACT,

    /**
     * Active sub-level vs active sub-level; impulse/contact below the diagnostic threshold.
     * Resting or sliding contact. Not used for one-shot damage peak calibration.
     */
    ACTIVE_SUSTAINED
}
