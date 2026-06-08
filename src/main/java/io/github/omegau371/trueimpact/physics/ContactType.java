package io.github.omegau371.trueimpact.physics;

/**
 * Diagnostic classification tag for a contact event.
 *
 * This enum is a PIPELINE FILTER, not a formula parameter.
 * The DamageResolver uses contactType to skip non-impact records via early return,
 * but the actual damage formula must NOT branch on contactType to choose parameters.
 * The threshold that separates ACTIVE_IMPACT from ACTIVE_SUSTAINED lives in the
 * contact capture layer (SableImpactCapture), not in the resolver.
 *
 * The distinction between ACTIVE_IMPACT and ACTIVE_SUSTAINED is based on
 * estimatedImpulseJ per contact pair -- a diagnostic heuristic, not a physical constant.
 * It MUST NOT become a hardcoded damage constant.
 */
public enum ContactType {
    /** Active sub-level vs active sub-level; impulse above diagnostic threshold.
     *  Primary input for structure-vs-structure damage. */
    ACTIVE_IMPACT,

    /** Active sub-level vs active sub-level; impulse below threshold.
     *  Resting or sliding contact. Not used for one-shot damage peak calibration. */
    ACTIVE_SUSTAINED,

    /** Terrain/world body vs active sub-level. Out of scope for Phase 1B. */
    WORLD_VS_ACTIVE,

    /** No active sub-level on either contact side. Skip entirely. */
    UNKNOWN
}
