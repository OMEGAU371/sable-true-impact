package io.github.omegau371.trueimpact.damage;

/**
 * Deferred event: a Sable sublevel collided with a possible Create dynamic structure.
 *
 * bodyWorldX/Y/Z is the sublevel body's position in Minecraft world-space at the time of
 * contact (from BodySnapshot.posX/Y/Z). Used by the application phase to AABB-search for
 * KinematicContraption entities in the real world (not in the embedded plot coordinate space).
 *
 * cpLocalX/Y/Z is the contact point in the sublevel's body-COM-local frame (±0.5 range).
 * Retained for potential future use (e.g. direction-aware anchor selection).
 */
public record DeferredContraptionDamageEvent(
        String levelKey,
        double cpLocalX,
        double cpLocalY,
        double cpLocalZ,
        double bodyWorldX,
        double bodyWorldY,
        double bodyWorldZ,
        double kImpact,
        int sublevelRuntimeId
) {}
