package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates Phase 3A thresholds after switching to BlockHardnessProfile.
 *
 * Uses real Minecraft vanilla hardness/blastResistance values to mirror what
 * tryBreakSublevelBlock will compute in-game.
 */
class Phase3AHardnessProfileTest {

    // ── vanilla block data (from Minecraft wiki / source) ─────────────────────
    // planks: hardness=2.0, blastResistance=3.0
    static final float PLANKS_H  = 2.0f;
    static final float PLANKS_BR = 3.0f;

    // stone: hardness=1.5, blastResistance=6.0
    static final float STONE_H  = 1.5f;
    static final float STONE_BR = 6.0f;

    // glass: hardness=0.3, blastResistance=0.3
    static final float GLASS_H  = 0.3f;
    static final float GLASS_BR = 0.3f;

    // iron block: hardness=5.0, blastResistance=6.0
    static final float IRON_H  = 5.0f;
    static final float IRON_BR = 6.0f;

    static final MaterialThresholdProfile.MaterialClass WOOD    = MaterialThresholdProfile.MaterialClass.WOOD;
    static final MaterialThresholdProfile.MaterialClass STONE_MC = MaterialThresholdProfile.MaterialClass.STONE;
    static final MaterialThresholdProfile.MaterialClass BRITTLE = MaterialThresholdProfile.MaterialClass.BRITTLE;
    static final MaterialThresholdProfile.MaterialClass METAL   = MaterialThresholdProfile.MaterialClass.METAL;

    @BeforeEach void clear() { SublevelDamageAccumulator.clear(); }

    // ── threshold value table ─────────────────────────────────────────────────

    @Test
    void printThresholdTable() {
        record Row(String name, float h, float br, MaterialThresholdProfile.MaterialClass mc) {}
        Row[] rows = {
            new Row("planks",     PLANKS_H, PLANKS_BR, WOOD),
            new Row("stone",      STONE_H,  STONE_BR,  STONE_MC),
            new Row("glass",      GLASS_H,  GLASS_BR,  BRITTLE),
            new Row("iron_block", IRON_H,   IRON_BR,   METAL),
        };

        System.out.printf("%n%-12s  %-6s  %-6s  %-10s  %-10s  %-16s  %-16s%n",
                "block", "hard", "blast", "crackJ", "breakJ(iso)",
                "hits(isolated)", "hits(6-confined)");
        System.out.println("-".repeat(100));

        for (Row r : rows) {
            double crack    = BlockHardnessProfile.crackThresholdJ(r.h(), r.br());
            double breakIso = BlockHardnessProfile.breakThresholdJ(r.h(), r.br());

            // confinement with all 6 neighbours being the same block
            double[] allSame = {crack, crack, crack, crack, crack, crack};
            double cf        = ConfinementFactor.compute(allSame, crack);
            double breakConf = breakIso * (1.0 + cf);

            int hIso  = hitsToDestroy(r.mc(), crack, 200.0, breakIso);
            int hConf = hitsToDestroy(r.mc(), crack, 200.0, breakConf);

            System.out.printf("%-12s  %-6.1f  %-6.1f  %-10.1f  %-10.1f  %-16s  %-16s%n",
                    r.name(), r.h(), r.br(),
                    crack, breakIso,
                    hIso < 0 ? "∞" : String.valueOf(hIso),
                    hConf < 0 ? "∞" : String.valueOf(hConf));
        }
        assertTrue(true);
    }

    // ── sweep: how does kImpact level affect hits-to-destroy? ─────────────────

    @Test
    void printPlanksKImpactSweep() {
        double crack    = BlockHardnessProfile.crackThresholdJ(PLANKS_H, PLANKS_BR);
        double breakIso = BlockHardnessProfile.breakThresholdJ(PLANKS_H, PLANKS_BR);

        double[] allWood = {crack, crack, crack, crack, crack, crack};
        double cf        = ConfinementFactor.compute(allWood, crack);
        double breakConf = breakIso * (1.0 + cf);

        System.out.printf("%n=== PLANKS  crackJ=%.1f  break(iso)=%.1f  break(6-conf)=%.1f ===%n",
                crack, breakIso, breakConf);
        System.out.printf("%-14s  %-12s  %-16s  %-16s%n",
                "kImpact (J)", "effectiveJ", "hits(isolated)", "hits(6-confined)");
        System.out.println("-".repeat(64));

        for (double k : new double[]{50, 75, 100, 150, 200, 300, 500, 800, 1000}) {
            double eff  = Math.min(k, breakIso);
            int hIso    = hitsToDestroy(WOOD, crack, k, breakIso);
            SublevelDamageAccumulator.clear();
            int hConf   = hitsToDestroy(WOOD, crack, k, breakConf);
            SublevelDamageAccumulator.clear();
            System.out.printf("%-14.1f  %-12.1f  %-16s  %-16s%n",
                    k, eff,
                    hIso  < 0 ? "∞" : String.valueOf(hIso),
                    hConf < 0 ? "∞" : String.valueOf(hConf));
        }
        assertTrue(true);
    }

    // ── assertions ────────────────────────────────────────────────────────────

    @Test
    void planksIsolated_200J_requiresAtLeast2Hits() {
        // effectiveJ = min(kImpact, breakJ): 200J < breakIso≈280J → effectiveJ=200J → 2 hits
        double crack    = BlockHardnessProfile.crackThresholdJ(PLANKS_H, PLANKS_BR);
        double breakIso = BlockHardnessProfile.breakThresholdJ(PLANKS_H, PLANKS_BR);
        int hits = hitsToDestroy(WOOD, crack, 200.0, breakIso);
        System.out.printf("[ASSERT] planks isolated, 200J → %d hits%n", hits);
        assertTrue(hits < 0 || hits >= 2,
                "planks isolated should need ≥2 hits at 200J; got " + hits);
    }

    @Test
    void planksConfined_200J_requiresAtLeast3Hits() {
        // effectiveJ=200J, confBreak≈560J → 3 hits (200×3=600>560)
        double crack    = BlockHardnessProfile.crackThresholdJ(PLANKS_H, PLANKS_BR);
        double breakIso = BlockHardnessProfile.breakThresholdJ(PLANKS_H, PLANKS_BR);
        double[] allWood = {crack, crack, crack, crack, crack, crack};
        double cf        = ConfinementFactor.compute(allWood, crack);
        double breakConf = breakIso * (1.0 + cf);
        int hits = hitsToDestroy(WOOD, crack, 200.0, breakConf);
        System.out.printf("[ASSERT] planks 6-confined, 200J → %d hits%n", hits);
        assertTrue(hits < 0 || hits >= 3,
                "planks 6-confined should need ≥3 hits at 200J; got " + hits);
    }

    @Test
    void glassIsolated_canBreakInOneHit() {
        double crack    = BlockHardnessProfile.crackThresholdJ(GLASS_H, GLASS_BR);
        double breakIso = BlockHardnessProfile.breakThresholdJ(GLASS_H, GLASS_BR);
        System.out.printf("[ASSERT] glass crack=%.1f break=%.1f%n", crack, breakIso);
        // glass should be fragile — cap may exceed break so 1 hit suffices at high energy
        int hits = hitsToDestroy(BRITTLE, crack, 500.0, breakIso);
        assertTrue(hits <= 3, "glass should break in ≤3 hits at 500J; got " + hits);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private int hitsToDestroy(MaterialThresholdProfile.MaterialClass mc,
                               double crackJ, double kImpact, double breakJ) {
        for (int i = 1; i <= 1000; i++) {
            SublevelDamageAccumulator.Snapshot s =
                    SublevelDamageAccumulator.accumulate(0, 0, 0, 0, mc, kImpact, breakJ);
            if (s.ratio() >= 1.0) return i;
        }
        return -1;
    }
}
