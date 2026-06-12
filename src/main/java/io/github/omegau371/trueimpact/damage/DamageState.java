package io.github.omegau371.trueimpact.damage;

/**
 * Phase 2C: diagnostic classification of accumulated block damage.
 *
 * Ratio = accumulatedEffectiveDamageJ / thresholdJ.
 *
 * States are diagnostic-only in Phase 2C -- no mutation for STONE/WOOD/etc.
 * Phase 2D+ will use CRACKED/CRITICAL to trigger visual overlays or destruction.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public enum DamageState {
    /** ratio < 0.25: no meaningful damage accumulated. */
    INTACT,
    /** 0.25 <= ratio < 0.60: minor damage; structural integrity mostly intact. */
    BRUISED,
    /** 0.60 <= ratio < 1.00: significant damage; approaching break threshold. */
    CRACKED,
    /** ratio >= 1.00: threshold exceeded; block would break in a destructive phase. */
    CRITICAL;

    /**
     * Classifies a damage ratio into a DamageState.
     * Non-finite ratio (NaN/Infinity) returns INTACT as a safe fallback.
     */
    public static DamageState of(double ratio) {
        if (!Double.isFinite(ratio) || ratio < 0.25) return INTACT;
        if (ratio < 0.60) return BRUISED;
        if (ratio < 1.00) return CRACKED;
        return CRITICAL;
    }
}
