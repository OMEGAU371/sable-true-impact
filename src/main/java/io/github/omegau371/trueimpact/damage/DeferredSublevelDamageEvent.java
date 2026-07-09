package io.github.omegau371.trueimpact.damage;

/**
 * Immutable candidate damage event for a sublevel block (Phase 3A: Newton's 3rd law).
 *
 * Produced when an active sublevel impacts a static world block.
 * The block on the sublevel's contact surface also takes damage.
 *
 * cpX/Y/Z: contact point in plot-local space (bodyLocalCp + comOff).
 *   Used by faceAwareRound to derive which sublevel block to damage.
 *
 * visX/Y/Z: approximate visual Minecraft contact position.
 *   = snap.posX/Y/Z + rotateVec(bodyOrientation, bodyLocalCp)
 *   Used to filter spurious contacts from embedded-plot walls/terrain:
 *   if the Minecraft world block at this position is AIR, no damage is applied.
 *   NaN = unknown (filter skipped — conservative, applies damage).
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public record DeferredSublevelDamageEvent(
        long   serverTick,
        String levelKey,
        int    sublevelRuntimeId,
        double cpX,
        double cpY,
        double cpZ,
        double kImpact,
        /**
         * Normalized pre-impact velocity direction of the sublevel body, WORLD space.
         * Consumed ONLY by world-space checks (phantom terrain filter). NaN = unknown.
         */
        double impactDirX,
        double impactDirY,
        double impactDirZ,
        /** Visual Minecraft contact position (for spurious-contact filter). NaN = unknown. */
        double visX,
        double visY,
        double visZ,
        /**
         * Impact direction rotated into PLOT-GRID space (R⁻¹ × world dir). The plot block
         * grid does not rotate with the body, so every grid operation — surface snap,
         * face-spread plane, inward fallback, confinement direction weighting — must use
         * this, not the world direction. For an unrotated body the two are identical;
         * for a tumbling corner landing they can differ by 180° (the "hit the corner,
         * the opposite corner cracked" bug). NaN = unknown.
         */
        double gridDirX,
        double gridDirY,
        double gridDirZ,
        /** Body mass (Sable kpg) at contact time. Phase 3C penetration: v = √(2E/m). 0 = unknown. */
        double massKpg,
        /**
         * Active-vs-active only: runtimeId of the OTHER body in the pair, so the apply
         * phase can read the opposing contact face's material for the hardness-based
         * energy split and the material yield clamp. -1 = not a body-vs-body event.
         */
        int    otherRuntimeId,
        /** Other body's contact point in ITS plot-local space (comOff + cp). NaN when otherRuntimeId=-1. */
        double otherCpX,
        double otherCpY,
        double otherCpZ
) {}
