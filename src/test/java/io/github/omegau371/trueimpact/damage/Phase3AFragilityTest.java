package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quantifies Phase 3A fragility: how many hits at various kImpact levels does it take
 * to destroy WOOD / STONE / BRITTLE sublevel blocks, with and without confinement.
 *
 * Provides precise numbers to diagnose whether the system is too brittle.
 */
class Phase3AFragilityTest {

    private static final MaterialThresholdProfile.MaterialClass WOOD    = MaterialThresholdProfile.MaterialClass.WOOD;
    private static final MaterialThresholdProfile.MaterialClass STONE   = MaterialThresholdProfile.MaterialClass.STONE;
    private static final MaterialThresholdProfile.MaterialClass BRITTLE = MaterialThresholdProfile.MaterialClass.BRITTLE;

    @BeforeEach
    void clear() {
        SublevelDamageAccumulator.clear();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Simulate N identical hits and return the final ratio.
     * runtimeId=0, block at (0,0,0).
     */
    private double simulateHits(MaterialThresholdProfile.MaterialClass mc,
                                 double kImpact, double breakThreshold, int hits) {
        SublevelDamageAccumulator.Snapshot snap = null;
        for (int i = 0; i < hits; i++) {
            snap = SublevelDamageAccumulator.accumulate(0, 0, 0, 0, mc, kImpact, breakThreshold);
        }
        return snap == null ? 0.0 : snap.ratio();
    }

    /** How many hits (same kImpact) until ratio >= 1.0 (CRITICAL/destroyed)? */
    private int hitsToDestroy(MaterialThresholdProfile.MaterialClass mc,
                               double kImpact, double breakThreshold) {
        SublevelDamageAccumulator.clear();
        for (int i = 1; i <= 1000; i++) {
            SublevelDamageAccumulator.Snapshot s =
                    SublevelDamageAccumulator.accumulate(0, 0, 0, 0, mc, kImpact, breakThreshold);
            if (s.ratio() >= 1.0) return i;
        }
        return -1; // never
    }

    /** Break threshold with confinement factor applied (all 6 neighbours = same material). */
    private double confinedBreakThreshold(MaterialThresholdProfile.MaterialClass mc) {
        double crack = MaterialThresholdProfile.threshold(mc);
        double base  = crack * MaterialThresholdProfile.breakMultiplier(mc);
        double[] allSolid = new double[]{crack, crack, crack, crack, crack, crack};
        double cf = ConfinementFactor.compute(allSolid, crack);
        return base * (1.0 + cf);
    }

    private double isolatedBreakThreshold(MaterialThresholdProfile.MaterialClass mc) {
        double crack = MaterialThresholdProfile.threshold(mc);
        return crack * MaterialThresholdProfile.breakMultiplier(mc);
    }

    // ── print helpers ────────────────────────────────────────────────────────

    private void printTable(MaterialThresholdProfile.MaterialClass mc, double[] kImpacts) {
        double crackJ   = MaterialThresholdProfile.threshold(mc);
        double baseBreak = isolatedBreakThreshold(mc);
        double confBreak = confinedBreakThreshold(mc);

        // cap = breakThreshold (unified: min(kImpact, breakThreshold) for both paths)
        System.out.printf("%n=== %s  crack=%.0fJ  break(isolated)=%.0fJ  break(6-solid-neighbours)=%.1fJ ===%n",
                mc, crackJ, baseBreak, confBreak);
        System.out.printf("  %-12s  %-12s  %-14s  %-14s%n",
                "kImpact (J)", "effectiveJ", "hits(isolated)", "hits(6-confined)");
        System.out.println("  " + "-".repeat(56));

        for (double k : kImpacts) {
            double eff = Math.min(k, baseBreak);
            int hIso  = hitsToDestroy(mc, k, baseBreak);
            SublevelDamageAccumulator.clear();
            int hConf = hitsToDestroy(mc, k, confBreak);
            SublevelDamageAccumulator.clear();
            System.out.printf("  %-12.1f  %-12.1f  %-14d  %-14d%n", k, eff, hIso, hConf);
        }
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void printWoodFragilityTable() {
        printTable(WOOD, new double[]{50, 75, 100, 150, 200, 300, 500, 800, 1000});
        // no assertion -- this is a diagnostic printout; review console output
        assertTrue(true);
    }

    @Test
    void printStoneFragilityTable() {
        printTable(STONE, new double[]{50, 100, 150, 200, 300, 500, 800, 1000, 2000});
        assertTrue(true);
    }

    @Test
    void printBrittleFragilityTable() {
        printTable(BRITTLE, new double[]{20, 30, 50, 75, 100, 150, 200});
        assertTrue(true);
    }

    @Test
    void woodIsolated_lowImpact_survivesMultipleHits() {
        // Even at the detection threshold (40J), an isolated wood block should not shatter in < 3 hits.
        double breakJ = isolatedBreakThreshold(WOOD);
        int hits = hitsToDestroy(WOOD, 40.0, breakJ);
        System.out.printf("[ASSERT] WOOD isolated, kImpact=40J → %d hits to destroy%n", hits);
        assertTrue(hits < 0 || hits >= 3, "Wood should survive at least 3 hits at 40J; got " + hits);
    }

    @Test
    void woodConfined_lowImpact_requiresMultipleHits() {
        // kImpact=80J < confBreak=200J → effectiveJ=80J → needs 3 hits (80×3=240>200)
        double breakJ = confinedBreakThreshold(WOOD);
        int hits = hitsToDestroy(WOOD, 80.0, breakJ);
        System.out.printf("[ASSERT] WOOD 6-confined, kImpact=80J → %d hits to destroy%n", hits);
        assertTrue(hits < 0 || hits >= 3, "Confined wood should need ≥3 hits at 80J; got " + hits);
    }

    @Test
    void stoneIsolated_moderateImpact_notOneShot() {
        double breakJ = isolatedBreakThreshold(STONE);
        int hits = hitsToDestroy(STONE, 200.0, breakJ);
        System.out.printf("[ASSERT] STONE isolated, kImpact=200J → %d hits to destroy%n", hits);
        assertTrue(hits < 0 || hits >= 3, "Stone should not be one/two-shotted at 200J; got " + hits);
    }
}
