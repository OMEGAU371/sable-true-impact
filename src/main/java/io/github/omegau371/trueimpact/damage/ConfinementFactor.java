package io.github.omegau371.trueimpact.damage;

/**
 * D-3: Confinement Factor — situational strength scaling for world blocks.
 *
 * An embedded block is harder to destroy than an isolated one because surrounding
 * solid blocks provide structural confinement. The effective break threshold is:
 *
 *   effectiveBreakThreshold = baseBreakThreshold × (1 + confinementFactor)
 *
 * Two modes:
 *   Isotropic (no direction): all 6 face neighbors weighted equally. Used as fallback
 *     when the impact direction is unavailable.
 *   Directional (impactDir known): face neighbors are weighted by how much they resist
 *     the actual impact direction. The block being pushed INTO contributes most;
 *     the face the impactor came FROM contributes nothing.
 *
 * Weight formula per face:
 *   w = clamp(DIRECTION_BASE + DIRECTION_AMPLITUDE × dot(faceNormal, impactDir), 0, 1)
 *   impactDir = normalized velocity of the moving body before impact
 *   dot = +1 → face is in the impact/push direction → maximum support weight
 *   dot = -1 → face is in the impactor's origin direction → zero weight (no confinement)
 *
 * Both modes produce comparable magnitudes because directional normalization uses
 * sum(all face weights) rather than a fixed 6, keeping the scale consistent.
 */
public final class ConfinementFactor {
    private ConfinementFactor() {}

    /** Maximum contribution of a single neighbor relative to the victim's crack threshold. */
    public static final double PER_FACE_CAP = 3.0;

    /** Isotropic baseline weight per face when no direction is available. */
    static final double DIRECTION_BASE      = 0.5;
    /** Amplitude of directional modulation added to DIRECTION_BASE × dot product. */
    static final double DIRECTION_AMPLITUDE = 0.5;

    /**
     * Face outward normals in the order used by TrueImpactMod.sampleNeighborCrackThresholds:
     *   [0] above  (0, +1,  0)
     *   [1] below  (0, -1,  0)
     *   [2] north  (0,  0, -1)
     *   [3] south  (0,  0, +1)
     *   [4] east  (+1,  0,  0)
     *   [5] west  (-1,  0,  0)
     */
    static final double[][] FACE_NORMALS = {
        { 0,  1,  0},
        { 0, -1,  0},
        { 0,  0, -1},
        { 0,  0,  1},
        { 1,  0,  0},
        {-1,  0,  0},
    };

    // ---- public API -------------------------------------------------------------

    /**
     * Isotropic confinement (direction unknown). All faces weighted equally.
     * Used as fallback when DeferredDamageEvent.hasDirection() == false.
     */
    public static double compute(double[] neighborCrackJ, double victimCrackJ) {
        return compute(neighborCrackJ, victimCrackJ, Double.NaN, Double.NaN, Double.NaN);
    }

    /**
     * Direction-aware confinement.
     *
     * @param neighborCrackJ crack thresholds of up to 6 face neighbors (order matches
     *                       FACE_NORMALS): 0 = air/missing, MAX_VALUE = indestructible
     * @param victimCrackJ   crack threshold of the victim block; must be finite and > 0
     * @param dirX dirY dirZ normalized impact direction vector (moving body pre-impact
     *             velocity direction). NaN → falls back to isotropic weighting.
     */
    public static double compute(double[] neighborCrackJ, double victimCrackJ,
                                 double dirX, double dirY, double dirZ) {
        if (victimCrackJ <= 0 || !Double.isFinite(victimCrackJ)) return 0.0;

        int n = Math.min(neighborCrackJ.length, FACE_NORMALS.length);
        boolean directional = Double.isFinite(dirX) && Double.isFinite(dirY) && Double.isFinite(dirZ);

        double sum       = 0.0;
        double weightSum = 0.0; // sum of weights for all faces (used as denominator)

        for (int i = 0; i < n; i++) {
            double weight;
            if (directional) {
                double[] fn = FACE_NORMALS[i];
                double dot = fn[0] * dirX + fn[1] * dirY + fn[2] * dirZ;
                weight = Math.max(0.0, Math.min(1.0, DIRECTION_BASE + DIRECTION_AMPLITUDE * dot));
            } else {
                weight = 1.0; // isotropic: equal weight per face
            }
            weightSum += weight;

            if (neighborCrackJ[i] > 0 && weight > 0) {
                double ratio = Double.isFinite(neighborCrackJ[i])
                        ? neighborCrackJ[i] / victimCrackJ
                        : PER_FACE_CAP;
                sum += weight * Math.min(ratio, PER_FACE_CAP);
            }
        }

        return weightSum > 0 ? sum / weightSum : 0.0;
    }

    /**
     * Energy-driven dynamic sampling radius (D-3.1).
     * R=1 (6 face neighbors): impactEnergyJ ≤ ~100J.
     * R=5 (~sphere): impactEnergyJ ≥ ~1600J.
     */
    public static int dynamicRadius(double impactEnergyJ) {
        if (impactEnergyJ <= 0) return 1;
        int r = (int) Math.ceil(Math.log(impactEnergyJ / 50.0) / Math.log(2.0));
        return Math.max(1, Math.min(5, r));
    }
}
