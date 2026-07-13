package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlockHardnessProfile formulas.
 * No Minecraft runtime required -- tests operate on raw float inputs.
 *
 * Calibration targets (vanilla hardness / blast resistance → expected thresholds):
 *   Glass     0.3 / 0.3   → crack ~7J,   break ~48J   (shatters in 2 hits)
 *   Stone     1.5 / 6.0   → crack ~46J,  break ~507J  (breaks in ~6 hits)
 *   Oak plank 2.0 / 3.0   → crack ~29J,  break ~280J  (breaks in ~4 hits)
 *   Iron      5.0 / 10.0  → crack ~60J,  break ~748J  (breaks in ~9 hits)
 *   Obsidian 50.0 / 1200  → crack 500J,  break ~24800J (breaks in ~50 hits)
 *   Bedrock   -1  / any   → Double.MAX_VALUE (indestructible)
 */
class BlockHardnessProfileTest {

    // -- bedrock / indestructible --------------------------------------------------

    @Test
    void bedrock_crack_threshold_is_max_value() {
        assertEquals(Double.MAX_VALUE,
                BlockHardnessProfile.crackThresholdJ(-1f, 3_600_000f));
    }

    @Test
    void bedrock_break_threshold_is_max_value() {
        assertEquals(Double.MAX_VALUE,
                BlockHardnessProfile.breakThresholdJ(-1f, 3_600_000f));
    }

    @Test
    void negative_hardness_always_produces_max_value_regardless_of_blast() {
        assertEquals(Double.MAX_VALUE, BlockHardnessProfile.crackThresholdJ(-1f, 0f));
        assertEquals(Double.MAX_VALUE, BlockHardnessProfile.crackThresholdJ(-1f, 1200f));
        assertEquals(Double.MAX_VALUE, BlockHardnessProfile.breakThresholdJ(-1f, 0f));
    }

    // -- zero blast resistance (leaves, plants, etc.) ------------------------------

    @Test
    void zero_blast_resist_clamps_to_minimum_crack_threshold() {
        double t = BlockHardnessProfile.crackThresholdJ(0.2f, 0f);
        assertEquals(ImpactRuntimeConfig.CRACK_MIN, t, 1e-9,
                "zero blastResist must return CRACK_MIN floor");
    }

    @Test
    void negative_blast_resist_treated_as_zero() {
        double t = BlockHardnessProfile.crackThresholdJ(0.5f, -1f);
        assertEquals(ImpactRuntimeConfig.CRACK_MIN, t, 1e-9);
    }

    // -- glass (hardness=0.3, blastResist=0.3) ------------------------------------

    @Test
    void glass_crack_threshold_is_around_7J() {
        // 15 × 0.3^0.6 ≈ 7.0J
        double t = BlockHardnessProfile.crackThresholdJ(0.3f, 0.3f);
        assertEquals(7.0, t, 0.5, "glass crack threshold should be ~7J");
    }

    @Test
    void glass_break_threshold_is_around_48J() {
        // crackT ≈ 7J, breakMult = 5 + 0.3^0.4*3 ≈ 6.87, breakT ≈ 48J
        double t = BlockHardnessProfile.breakThresholdJ(0.3f, 0.3f);
        assertEquals(48.0, t, 3.0, "glass break threshold should be ~48J");
    }

    // -- stone (hardness=1.5, blastResist=6.0) ------------------------------------

    @Test
    void stone_crack_threshold_is_around_46J() {
        // 15 × 6^0.6 ≈ 45.5J
        double t = BlockHardnessProfile.crackThresholdJ(1.5f, 6.0f);
        assertEquals(45.5, t, 2.0, "stone crack threshold should be ~46J");
    }

    @Test
    void stone_break_threshold_is_around_507J() {
        // crack ≈ 45.5, mult ≈ 11.14, break ≈ 507J
        double t = BlockHardnessProfile.breakThresholdJ(1.5f, 6.0f);
        assertEquals(507.0, t, 20.0, "stone break threshold should be ~507J");
    }

    // -- oak planks (hardness=2.0, blastResist=3.0) --------------------------------

    @Test
    void oak_planks_crack_threshold_is_around_29J() {
        double t = BlockHardnessProfile.crackThresholdJ(2.0f, 3.0f);
        assertEquals(29.0, t, 2.0);
    }

    @Test
    void oak_planks_break_threshold_less_than_stone() {
        // Wood (blastResist=3) should be easier to break than stone (blastResist=6)
        double wood  = BlockHardnessProfile.breakThresholdJ(2.0f, 3.0f);
        double stone = BlockHardnessProfile.breakThresholdJ(1.5f, 6.0f);
        assertTrue(wood < stone,
                "oak planks break threshold (" + wood + ") must be < stone (" + stone + ")");
    }

    // -- iron block (hardness=5.0, blastResist=10.0) ------------------------------

    @Test
    void iron_block_crack_threshold_is_around_60J() {
        double t = BlockHardnessProfile.crackThresholdJ(5.0f, 10.0f);
        assertEquals(59.7, t, 2.0);
    }

    @Test
    void iron_block_harder_than_stone() {
        assertTrue(
                BlockHardnessProfile.breakThresholdJ(5.0f, 10.0f) >
                BlockHardnessProfile.breakThresholdJ(1.5f, 6.0f),
                "iron block must have higher break threshold than stone");
    }

    // -- obsidian / netherite (hardness=50, blastResist=1200) ---------------------

    @Test
    void obsidian_crack_threshold_capped_at_500J() {
        // 15 × 1200^0.6 ≈ 670J → clamped at CRACK_MAX=500J
        double t = BlockHardnessProfile.crackThresholdJ(50f, 1200f);
        assertEquals(ImpactRuntimeConfig.CRACK_MAX, t, 1e-9,
                "high blastResist must be clamped at CRACK_MAX");
    }

    @Test
    void obsidian_break_threshold_is_around_28000J() {
        // crack=500J (capped), breakMult = 5 + 1200^0.4*3 ≈ 56.1, break ≈ 28050J
        // 1200^0.4 = exp(0.4 × ln1200) ≈ 17.05, so mult ≈ 5 + 51.15 = 56.15
        double t = BlockHardnessProfile.breakThresholdJ(50f, 1200f);
        assertEquals(28000.0, t, 500.0);
    }

    @Test
    void obsidian_min_hits_to_break_is_at_least_40() {
        // With crack=500J cap per hit (HIGH_STRENGTH capMult=1.0), min hits = break/cap
        double crack = BlockHardnessProfile.crackThresholdJ(50f, 1200f);
        double breakT = BlockHardnessProfile.breakThresholdJ(50f, 1200f);
        // capMult for HIGH_STRENGTH = 1.0, so cap per hit = crack * 1.0 = crack
        double minHits = breakT / (crack * 1.0);
        assertTrue(minHits >= 40, "obsidian should require at least 40 hits, got " + minHits);
    }

    // -- ordering: harder materials need more hits --------------------------------

    @Test
    void break_thresholds_increase_with_blast_resistance() {
        double glass    = BlockHardnessProfile.breakThresholdJ(0.3f,  0.3f);
        double stone    = BlockHardnessProfile.breakThresholdJ(1.5f,  6.0f);
        double iron     = BlockHardnessProfile.breakThresholdJ(5.0f, 10.0f);
        double obsidian = BlockHardnessProfile.breakThresholdJ(50f, 1200f);
        assertTrue(glass < stone,    "glass < stone");
        assertTrue(stone < iron,     "stone < iron");
        assertTrue(iron  < obsidian, "iron < obsidian");
    }

    // -- crack threshold is always <= break threshold -----------------------------

    @Test
    void crack_threshold_always_less_than_or_equal_break_threshold() {
        float[][] cases = {{0.3f, 0.3f}, {1.5f, 6f}, {5f, 10f}, {50f, 1200f}};
        for (float[] c : cases) {
            double crack = BlockHardnessProfile.crackThresholdJ(c[0], c[1]);
            double breakT = BlockHardnessProfile.breakThresholdJ(c[0], c[1]);
            assertTrue(crack <= breakT,
                    "crackThreshold must be <= breakThreshold for hardness=" + c[0] + " blastResist=" + c[1]);
        }
    }

    // -- crack threshold is always finite for non-bedrock -------------------------

    @Test
    void crack_threshold_is_finite_for_normal_blocks() {
        assertTrue(Double.isFinite(BlockHardnessProfile.crackThresholdJ(0f,  0f)));
        assertTrue(Double.isFinite(BlockHardnessProfile.crackThresholdJ(0.3f, 0.3f)));
        assertTrue(Double.isFinite(BlockHardnessProfile.crackThresholdJ(50f, 1200f)));
    }
}
