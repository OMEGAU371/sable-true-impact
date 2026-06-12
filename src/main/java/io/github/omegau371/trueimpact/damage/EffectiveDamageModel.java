package io.github.omegau371.trueimpact.damage;

/**
 * Phase 2C: converts raw single-hit kImpact to bounded effective damage.
 *
 * Single-hit cap formula: effective = min(rawKImpact, threshold * capMultiplier)
 *
 * The cap prevents a single catastrophic impact from saturating the accumulator
 * in one hit. Per-material multipliers reflect how much a single impact can
 * constructively exceed the base threshold before further energy is absorbed:
 *
 *   SOFT_SOIL:     4x  -- compresses readily; large single impacts still partially absorbed
 *   WOOD:          3x  -- moderate single-hit limit
 *   STONE:         2x  -- hard surface; significant but capped energy transfer per hit
 *   METAL:         1.5x -- high resistance, limited plastic deformation per impact
 *   HIGH_STRENGTH: 1x  -- near-impenetrable; threshold is a hard cap per hit
 *   GENERIC:       2x  -- same as STONE as conservative fallback
 *
 * Raw kImpact is always preserved in Result for diagnostic display.
 * Phase 2A compaction gate (ImpactBlockApplicator) continues to use raw kImpact > threshold.
 * Only the accumulator uses effectiveDamageJ.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class EffectiveDamageModel {

    private EffectiveDamageModel() {}

    /** Pairing of raw and capped-effective damage from one computation. */
    public record Result(double rawImpactJ, double effectiveDamageJ) {
        /** True when effective damage was reduced by the cap (raw > cap). */
        public boolean wasCapped() { return effectiveDamageJ < rawImpactJ; }
    }

    /**
     * Computes bounded effective damage for a single impact event.
     *
     * @param rawKImpact  raw kinetic impact energy (Phase 1C canonical, kineticImpactEnergyJ)
     * @param mc          material class of the victim block
     * @param thresholdJ  material threshold for this class
     * @return Result with rawImpactJ == rawKImpact and effectiveDamageJ capped at threshold * multiplier
     */
    public static Result compute(double rawKImpact, MaterialThresholdProfile.MaterialClass mc,
                                 double thresholdJ) {
        double cap = thresholdJ * capMultiplier(mc);
        double effective = Math.min(rawKImpact, cap);
        return new Result(rawKImpact, effective);
    }

    /** Returns the single-hit cap multiplier for the given material class. */
    static double capMultiplier(MaterialThresholdProfile.MaterialClass mc) {
        return switch (mc) {
            case SOFT_SOIL     -> 4.0;
            case WOOD          -> 3.0;
            case STONE         -> 2.0;
            case METAL         -> 1.5;
            case HIGH_STRENGTH -> 1.0;
            case GENERIC       -> 2.0;
        };
    }
}
