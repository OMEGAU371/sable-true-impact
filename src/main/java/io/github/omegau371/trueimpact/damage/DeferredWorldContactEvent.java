package io.github.omegau371.trueimpact.damage;

/**
 * Deferred event: a Sable sublevel's Rapier body contacted a static world body.
 *
 * worldCpX/Y/Z is the world body's contact point in its own local frame, which equals
 * world-space (static Rapier bodies have identity orientation). This lets the application
 * phase (TrueImpactMod) resolve the hit BlockPos without needing access to Sable internals.
 *
 * Produced by SableImpactCapture on the physics thread; consumed by applyDeferredWorldContacts
 * on the server tick thread (safe world-access window).
 */
public record DeferredWorldContactEvent(
        String levelKey,
        double worldCpX, double worldCpY, double worldCpZ,
        double kImpact
) {}
