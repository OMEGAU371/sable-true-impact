package io.github.omegau371.trueimpact.damage;

/**
 * Core logic for Phase 3A: sublevel block damage (Newton's 3rd law).
 *
 * Extracted from TrueImpactMod to enable unit testing without Minecraft runtime.
 * No Minecraft imports — safe to test with plain JUnit.
 *
 * Block coordinate derivation from body-local contact point (event.cpX/Y/Z):
 *   blockLocal = round(cp - 0.01 * signum(cp))
 *   The 0.01 epsilon pulls face contacts (cp ≈ ±0.5) slightly inward before rounding,
 *   ensuring that a contact at the face of block N maps to block N rather than N+1.
 *   Without this, floating-point imprecision near ±0.5 causes Math.round to flip.
 */
public final class Phase3ADamage {

    private Phase3ADamage() {}

    /** Small inward pull to correct rounding of face-contact points (cp ≈ ±0.5). */
    private static final double FACE_EPSILON = 0.01;

    /**
     * BlockDestroyer wraps accessor.destroyBlock without pulling in Minecraft types.
     * Returns true if a block was actually destroyed at the given plot-local coordinates.
     */
    @FunctionalInterface
    public interface BlockDestroyer {
        boolean destroy(int plotLocalX, int plotLocalY, int plotLocalZ);
    }

    /**
     * Applies Phase 3A damage to a sublevel block using the STONE break threshold.
     * Used by unit tests. Production code should call the overload with an explicit threshold.
     *
     * @param destroyer  wraps accessor.destroyBlock
     * @param event      the contact event (holds cpX/Y/Z in body-local space and kImpact)
     * @return true if a block was destroyed
     */
    public static boolean applyDamage(BlockDestroyer destroyer, DeferredSublevelDamageEvent event) {
        return applyDamage(destroyer, event, stoneBreakThreshold());
    }

    /**
     * Applies Phase 3A damage to a sublevel block using a caller-supplied break threshold.
     * The caller should derive the threshold from the actual block material via
     * MaterialThresholdProfile.classify() and threshold()/breakMultiplier().
     *
     * @param destroyer      wraps accessor.destroyBlock
     * @param event          the contact event (holds cpX/Y/Z in body-local space and kImpact)
     * @param breakThreshold joules required to break the block
     * @return true if a block was destroyed
     */
    public static boolean applyDamage(BlockDestroyer destroyer, DeferredSublevelDamageEvent event,
                                       double breakThreshold) {
        int localX = faceAwareRound(event.cpX());
        int localY = faceAwareRound(event.cpY());
        int localZ = faceAwareRound(event.cpZ());

        if (event.kImpact() > breakThreshold) {
            return destroyer.destroy(localX, localY, localZ);
        }
        return false;
    }

    /** Returns the STONE break threshold (J). Exposed for test parametrization. */
    public static double stoneBreakThreshold() {
        return MaterialThresholdProfile.threshold(MaterialThresholdProfile.MaterialClass.STONE)
                * MaterialThresholdProfile.breakMultiplier(MaterialThresholdProfile.MaterialClass.STONE);
    }

    /**
     * Rounds a body-local contact coordinate to the containing block index.
     * Pulls the value 0.01 toward zero before rounding so face-contacts (cp ≈ ±0.5)
     * stay in the block whose face was touched, not the adjacent block.
     */
    public static int faceAwareRound(double cp) {
        return (int) Math.round(cp - FACE_EPSILON * Math.signum(cp));
    }
}
