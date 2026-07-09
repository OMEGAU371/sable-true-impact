package io.github.omegau371.trueimpact.damage;

/**
 * Derives per-block impact thresholds from vanilla hardness and blast resistance.
 *
 * Replaces the hand-filled numeric table in MaterialThresholdProfile with a formula
 * driven by real block data:
 *
 *   crackThresholdJ  -- crack-overlay threshold; visual cracking starts above this ratio
 *   breakThresholdJ  -- accumulator denominator and single-hit cap; ratio >= 1.0 -> block breaks
 *
 * Blast resistance is preferred over hardness for impact calculations because:
 *   - hardness = mining time (tool efficiency, sustained force)
 *   - blast resistance = resistance to sudden impulse (closer to impact physics)
 *
 * Formulas (calibrated against in-game targets):
 *   crackThreshold = clamp(15 × blastResist^0.6,  3.0, 500.0)
 *   breakMult      = 5 + blastResist^0.4 × 3
 *   breakThreshold = crackThreshold × breakMult
 *
 * Special case: hardness < 0 (bedrock, barriers) → Double.MAX_VALUE (indestructible).
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 * Callers (TrueImpactMod) extract hardness/blastResist from BlockState and pass raw floats.
 */
public final class BlockHardnessProfile {

    private BlockHardnessProfile() {}

    static final double CRACK_MIN      =   3.0;
    static final double CRACK_MAX      = 500.0;
    static final double CRACK_COEFF    =  15.0;
    static final double CRACK_EXPONENT =   0.6;
    static final double BREAK_BASE     =   5.0;
    static final double BREAK_COEFF    =   3.0;
    static final double BREAK_EXPONENT =   0.4;

    /**
     * Crack-overlay threshold. Also used as the per-hit crack progress denominator in
     * CrackOverlayTracker (ratio = accumulatedEffective / crackThreshold).
     * hardness < 0  → Double.MAX_VALUE (indestructible).
     * blastResist ≤ 0 → CRACK_MIN (fragile; trivially damaged).
     */
    public static double crackThresholdJ(float hardness, float blastResist) {
        if (hardness < 0) return Double.MAX_VALUE;
        if (blastResist <= 0) return CRACK_MIN;
        double raw = CRACK_COEFF * Math.pow(blastResist, CRACK_EXPONENT);
        return Math.min(CRACK_MAX, Math.max(CRACK_MIN, raw));
    }

    /**
     * Total accumulated effective damage required to reach ratio >= 1.0 (CRITICAL / break).
     * hardness < 0  → Double.MAX_VALUE (block can never reach CRITICAL).
     */
    public static double breakThresholdJ(float hardness, float blastResist) {
        double crack = crackThresholdJ(hardness, blastResist);
        if (crack == Double.MAX_VALUE) return Double.MAX_VALUE;
        double breakMult = BREAK_BASE + Math.pow(Math.max(0, blastResist), BREAK_EXPONENT) * BREAK_COEFF;
        return crack * breakMult;
    }
}
