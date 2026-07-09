package io.github.omegau371.trueimpact.stress;

import io.github.omegau371.trueimpact.damage.MaterialThresholdProfile;
import io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.MaterialClass;

import java.util.*;

/**
 * BFS stress propagation from a destroyed sublevel block (local coordinates).
 *
 * Each hop attenuates by the receiving block's transmissionFactor.
 * Two perceptual corrections applied on top of the base BFS:
 *
 *   1. Direction weighting — initial stress and propagation are biased toward the
 *      impact direction. Forward neighbors receive full stress; perpendicular 20%;
 *      backward 0%. This matches player intuition that force travels through structures.
 *
 *   2. Structural vulnerability — blocks with fewer solid neighbors (thin necks, corners)
 *      have a lower effective stress limit than fully-embedded blocks of the same material.
 *      A single-connection neck can fracture at 35% of the normal threshold.
 *
 * No Minecraft imports — safe to unit-test without game runtime.
 */
public final class StressPropagator {

    private StressPropagator() {}

    public static final int DEFAULT_MAX_RADIUS = 5;

    /**
     * Maximum stress seed energy (J) regardless of kImpact.
     *
     * Without this cap a fast-moving sublevel produces kImpact in the thousands of J,
     * and every block within radius 5 would exceed its stress limit and be destroyed —
     * causing complete self-disintegration in mid-air.
     *
     * Value chosen so that:
     *   • stone NECK   (solidNeighbors=1): limit=35J,  seed×0.60=72J > 35J → fractures ✓
     *   • stone SOLID  (solidNeighbors≥5): limit=100J, seed×0.60=72J < 100J → survives ✓
     *   • BRITTLE any:                     limit=7.5J,  seed×0.80=96J >> 7.5J → fractures ✓
     *   • METAL solid:                     limit=360J,  seed×0.75=90J < 360J → survives ✓
     */
    public static final double STRESS_SEED_CAP_J = 120.0;

    /** Below this stress, propagation stops (prunes negligible energy waves). */
    static final double MIN_STRESS_J = 0.5;

    /** Looks up material class at a sublevel-local position; returns null for air or OOB. */
    @FunctionalInterface
    public interface BlockSampler {
        MaterialClass classify(int lx, int ly, int lz);
    }

    /** A sublevel-local block identified as exceeding its stress limit. */
    public record FractureCandidate(int lx, int ly, int lz) {}

    static final int[][] DIRS = {
        {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}
    };

    // ── Material stress data ──────────────────────────────────────────────────

    /**
     * Fraction of incoming stress transmitted to each face-neighbor per hop.
     * Low value = material absorbs stress; high value = transmits freely.
     */
    static double transmissionFactor(MaterialClass mc) {
        return switch (mc) {
            case SOFT_SOIL     -> 0.10;  // sand/dirt damps almost everything
            case BRITTLE       -> 0.80;  // glass passes stress easily but fails fast
            case WOOD          -> 0.50;
            case STONE         -> 0.60;
            case METAL         -> 0.75;
            case HIGH_STRENGTH -> 0.85;
            case GENERIC       -> 0.55;
        };
    }

    /**
     * Stress failure limit = threshold(mc) × stressLimitMultiplier(mc) × vulnerabilityMultiplier.
     * Low value = brittle (fails under modest stress); high value = tough.
     */
    static double stressLimitMultiplier(MaterialClass mc) {
        return switch (mc) {
            case SOFT_SOIL     -> 3.0;   // absorbs, hard to fracture via stress
            case BRITTLE       -> 0.5;   // glass fails at half its crack threshold
            case WOOD          -> 1.5;
            case STONE         -> 2.0;
            case METAL         -> 3.0;
            case HIGH_STRENGTH -> 5.0;
            case GENERIC       -> 1.8;
        };
    }

    /**
     * Structural vulnerability correction based on solid-neighbor count.
     * A block exposed on more faces is a structural weak point and fractures more easily.
     *
     * solidNeighbors = number of the 6 face-neighbors that are not air.
     * Returns a multiplier on the effective stress limit:
     *   1.00 = fully embedded (hardest to break)
     *   0.35 = single-connection neck (breaks at 35% of normal threshold)
     */
    static double vulnerabilityMultiplier(int solidNeighbors) {
        return switch (solidNeighbors) {
            case 6 -> 1.00;  // fully enclosed
            case 5 -> 1.00;  // one exposed face (surface block)
            case 4 -> 1.00;  // edge block — two faces exposed, still well-connected
            case 3 -> 0.90;  // corner block — three faces exposed, solid structures have these at corners
            case 2 -> 0.80;  // two connections: thin rod or a corner neighbor of a destroyed block
            case 1 -> 0.35;  // single-connection neck — true structural weak point
            default -> 1.00; // isolated (0) or invalid
        };
    }

    /**
     * Direction weight for stress propagation toward a face-neighbor in direction d.
     * impactDx/Dy/Dz is the normalized impact direction (zero vector → uniform spread).
     *
     * cosAngle = 1  (same as impact):       weight = 1.00
     * cosAngle = 0  (perpendicular):         weight = 0.20
     * cosAngle = -1 (against impact):        weight = 0.00
     */
    static double directionWeight(int[] d, double impactDx, double impactDy, double impactDz) {
        double cos = d[0] * impactDx + d[1] * impactDy + d[2] * impactDz;
        return cos > 0 ? 0.20 + 0.80 * cos : 0.20 * (1.0 + cos);
    }

    // ── Core propagation ─────────────────────────────────────────────────────

    /**
     * BFS stress propagation — uniform direction spread (backward compat overload).
     * Equivalent to propagate(..., 0, 0, 0).
     */
    public static List<FractureCandidate> propagate(
            int lx, int ly, int lz, double kImpactJ,
            BlockSampler sampler, int maxRadius) {
        return propagate(lx, ly, lz, kImpactJ, sampler, maxRadius, 0.0, 0.0, 0.0);
    }

    // visible for tests
    static double effectiveSeed(double kImpactJ) {
        return Math.min(kImpactJ, STRESS_SEED_CAP_J);
    }

    /**
     * BFS stress propagation with directional bias.
     *
     * @param lx/ly/lz        local position of the block already destroyed (Phase 3A)
     * @param kImpactJ        kinetic energy of the impact (J)
     * @param sampler         material lookup; null = air or out-of-bounds
     * @param maxRadius       maximum BFS radius in blocks (exclusive)
     * @param impactDx/Dy/Dz  normalized direction the impact propagates INTO the structure;
     *                        zero vector disables directional bias (uniform spread)
     * @return candidates whose stress exceeds their effective failure limit
     */
    public static List<FractureCandidate> propagate(
            int lx, int ly, int lz, double kImpactJ,
            BlockSampler sampler, int maxRadius,
            double impactDx, double impactDy, double impactDz) {

        kImpactJ = effectiveSeed(kImpactJ); // cap to prevent cascade self-destruction at high speed
        if (kImpactJ <= 0.0 || maxRadius <= 0) return List.of();

        boolean hasDir = (impactDx * impactDx + impactDy * impactDy + impactDz * impactDz) > 0.001;

        // visited: local key → stress arriving at that block
        Map<Long, Double> visited = new HashMap<>();
        Deque<int[]> queue = new ArrayDeque<>();

        // Seed direct neighbors of the destroyed block, weighted by impact direction
        for (int[] d : DIRS) {
            int nx = lx + d[0], ny = ly + d[1], nz = lz + d[2];
            MaterialClass mc = sampler.classify(nx, ny, nz);
            if (mc == null) continue;
            double dirW = hasDir ? directionWeight(d, impactDx, impactDy, impactDz) : 1.0;
            double s = kImpactJ * transmissionFactor(mc) * dirW;
            if (s < MIN_STRESS_J) continue;
            if (visited.putIfAbsent(pack(nx, ny, nz), s) == null) {
                queue.add(new int[]{nx, ny, nz});
            }
        }

        List<FractureCandidate> candidates = new ArrayList<>();

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cx = cur[0], cy = cur[1], cz = cur[2];
            double stress = visited.getOrDefault(pack(cx, cy, cz), 0.0);

            MaterialClass mc = sampler.classify(cx, cy, cz);
            if (mc == null) continue;

            // Structural vulnerability: count solid face-neighbors
            int solidNeighbors = 0;
            for (int[] nd : DIRS) {
                if (sampler.classify(cx + nd[0], cy + nd[1], cz + nd[2]) != null) solidNeighbors++;
            }
            double effectiveLimit = MaterialThresholdProfile.threshold(mc)
                    * stressLimitMultiplier(mc)
                    * vulnerabilityMultiplier(solidNeighbors);

            // Fracture eligibility: BRITTLE can always shatter; for all other materials only
            // single-connection necks (solidNeighbors ≤ 1) are eligible. Blocks with 2+ solid
            // neighbors are structurally integral — fracturing them produces an octahedron-shaped
            // (staircase) removal pattern that looks wrong for solid rectangular structures.
            boolean fractureEligible = (mc == MaterialClass.BRITTLE) || (solidNeighbors <= 1);
            if (fractureEligible && stress > effectiveLimit) {
                candidates.add(new FractureCandidate(cx, cy, cz));
                continue; // fracture points absorb the wave — no further propagation
            }

            // Distance check (squared)
            int dx = cx - lx, dy = cy - ly, dz = cz - lz;
            if (dx * dx + dy * dy + dz * dz >= maxRadius * maxRadius) continue;

            // Propagate to face-neighbors, maintaining directional bias
            for (int[] d : DIRS) {
                int nx = cx + d[0], ny = cy + d[1], nz = cz + d[2];
                long nKey = pack(nx, ny, nz);
                if (visited.containsKey(nKey)) continue;
                MaterialClass nmc = sampler.classify(nx, ny, nz);
                if (nmc == null) continue;
                double dirW = hasDir ? directionWeight(d, impactDx, impactDy, impactDz) : 1.0;
                double nStress = stress * transmissionFactor(nmc) * dirW;
                if (nStress < MIN_STRESS_J) continue;
                visited.put(nKey, nStress);
                queue.add(new int[]{nx, ny, nz});
            }
        }

        return candidates;
    }

    /**
     * Packs sublevel-local coordinates into a long key.
     * Valid for |x|, |y|, |z| < 524288 (sublevel blocks are always far within this range).
     */
    static long pack(int x, int y, int z) {
        return ((long)(x & 0xFFFFF)) | (((long)(y & 0xFFFFF)) << 20) | (((long)(z & 0xFFFFF)) << 40);
    }
}
