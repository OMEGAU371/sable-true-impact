package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.Test;

import static io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.MaterialClass.*;
import static io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MaterialThresholdProfile -- T-9 diagnostic material threshold profiles.
 *
 * All thresholds are Phase 1C temporary calibration values; not final material thresholds.
 * wouldExceed() uses kineticImpactEnergyJ (velocity-derived); contactCount/rawSum forbidden.
 */
class MaterialThresholdProfileTest {

    // -- threshold values -------------------------------------------------------

    @Test void soft_soil_threshold_is_5()        { assertEquals(5.0,   threshold(SOFT_SOIL),     1e-9); }
    @Test void brittle_threshold_is_15()         { assertEquals(15.0,  threshold(BRITTLE),        1e-9); }
    @Test void wood_threshold_is_20()            { assertEquals(20.0,  threshold(WOOD),           1e-9); }
    @Test void stone_threshold_is_50()           { assertEquals(50.0,  threshold(STONE),          1e-9); }
    @Test void metal_threshold_is_120()          { assertEquals(120.0, threshold(METAL),          1e-9); }
    @Test void high_strength_threshold_is_300()  { assertEquals(300.0, threshold(HIGH_STRENGTH),  1e-9); }
    @Test void generic_threshold_is_40()         { assertEquals(40.0,  threshold(GENERIC),        1e-9); }

    // -- block classification: required spec cases ----------------------------

    @Test
    void dirt_is_soft_soil() {
        assertEquals(SOFT_SOIL, classify("minecraft:dirt"));
    }

    @Test
    void grass_block_is_soft_soil() {
        assertEquals(SOFT_SOIL, classify("minecraft:grass_block"));
    }

    @Test
    void oak_planks_is_wood() {
        assertEquals(WOOD, classify("minecraft:oak_planks"));
    }

    @Test
    void stone_is_stone_class() {
        assertEquals(STONE, classify("minecraft:stone"));
    }

    @Test
    void cobblestone_is_stone_class() {
        assertEquals(STONE, classify("minecraft:cobblestone"));
    }

    @Test
    void iron_block_is_metal() {
        assertEquals(METAL, classify("minecraft:iron_block"));
    }

    @Test
    void obsidian_is_high_strength() {
        assertEquals(HIGH_STRENGTH, classify("minecraft:obsidian"));
    }

    @Test
    void unknown_block_is_generic() {
        assertEquals(GENERIC, classify("minecraft:sponge"));
    }

    @Test
    void null_block_id_is_generic() {
        assertEquals(GENERIC, classify(null));
    }

    // -- namespace stripping ---------------------------------------------------

    @Test
    void namespace_prefix_is_stripped_before_lookup() {
        // "minecraft:stone" and "stone" must resolve to the same class
        assertEquals(classify("stone"), classify("minecraft:stone"));
    }

    @Test
    void non_minecraft_namespace_not_stripped() {
        // Custom mod namespaces are not stripped; unknown -> GENERIC
        assertEquals(GENERIC, classify("mymod:stone"));
    }

    // -- additional block coverage checks -------------------------------------

    @Test void gravel_is_soft_soil()              { assertEquals(SOFT_SOIL,     classify("gravel")); }
    @Test void sand_is_soft_soil()                { assertEquals(SOFT_SOIL,     classify("sand")); }
    @Test void spruce_planks_is_wood()            { assertEquals(WOOD,          classify("spruce_planks")); }
    @Test void deepslate_is_stone_class()         { assertEquals(STONE,         classify("deepslate")); }
    @Test void gold_block_is_metal()              { assertEquals(METAL,         classify("gold_block")); }
    @Test void ancient_debris_is_high_strength()  { assertEquals(HIGH_STRENGTH, classify("ancient_debris")); }
    @Test void netherite_block_is_high_strength() { assertEquals(HIGH_STRENGTH, classify("netherite_block")); }

    // -- wouldExceed -----------------------------------------------------------

    @Test
    void wouldExceed_true_when_kImpact_above_threshold() {
        assertTrue(wouldExceed(6.0, SOFT_SOIL),   "6.0 > threshold 5.0 -> true");
        assertTrue(wouldExceed(25.0, WOOD),        "25.0 > threshold 20.0 -> true");
        assertTrue(wouldExceed(60.0, STONE),       "60.0 > threshold 50.0 -> true");
        assertTrue(wouldExceed(130.0, METAL),      "130.0 > threshold 120.0 -> true");
        assertTrue(wouldExceed(350.0, HIGH_STRENGTH), "350.0 > threshold 300.0 -> true");
    }

    @Test
    void wouldExceed_false_when_kImpact_below_threshold() {
        assertFalse(wouldExceed(4.0, SOFT_SOIL),   "4.0 < threshold 5.0 -> false");
        assertFalse(wouldExceed(19.0, WOOD),        "19.0 < threshold 20.0 -> false");
        assertFalse(wouldExceed(49.0, STONE),       "49.0 < threshold 50.0 -> false");
        assertFalse(wouldExceed(119.0, METAL),      "119.0 < threshold 120.0 -> false");
        assertFalse(wouldExceed(299.0, HIGH_STRENGTH), "299.0 < threshold 300.0 -> false");
    }

    @Test
    void wouldExceed_false_when_kImpact_equals_threshold() {
        // Strictly greater than (not >=)
        assertFalse(wouldExceed(5.0, SOFT_SOIL),   "5.0 == threshold 5.0 -> false (strictly >)");
        assertFalse(wouldExceed(50.0, STONE),       "50.0 == threshold 50.0 -> false");
    }

    @Test
    void wouldExceed_false_when_kImpact_is_NaN() {
        // NaN occurs when velocity data is unavailable; must not produce false positives.
        assertFalse(wouldExceed(Double.NaN, SOFT_SOIL),     "NaN -> false");
        assertFalse(wouldExceed(Double.NaN, STONE),         "NaN -> false");
        assertFalse(wouldExceed(Double.NaN, HIGH_STRENGTH), "NaN -> false");
        assertFalse(wouldExceed(Double.NaN, GENERIC),       "NaN -> false");
    }

    @Test
    void wouldExceed_false_when_kImpact_is_infinite() {
        assertFalse(wouldExceed(Double.POSITIVE_INFINITY, SOFT_SOIL),
                "Positive infinity is non-finite -> false");
    }

    @Test
    void wouldExceed_generic_threshold_is_40() {
        assertEquals(40.0, threshold(GENERIC), 1e-9);
        assertTrue(wouldExceed(41.0, GENERIC),  "41.0 > 40.0 -> true");
        assertFalse(wouldExceed(39.0, GENERIC), "39.0 < 40.0 -> false");
    }

    @Test
    void glass_is_brittle() {
        assertEquals(BRITTLE, classify("minecraft:glass"));
    }

    @Test
    void ice_is_brittle() {
        assertEquals(BRITTLE, classify("minecraft:ice"));
    }

    // -- breakMultiplier values ------------------------------------------------

    @Test void brittle_multiplier_is_3()       { assertEquals(3.0,  breakMultiplier(BRITTLE),       1e-9); }
    @Test void wood_multiplier_is_5()          { assertEquals(5.0,  breakMultiplier(WOOD),          1e-9); }
    @Test void stone_multiplier_is_10()        { assertEquals(10.0, breakMultiplier(STONE),         1e-9); }
    @Test void metal_multiplier_is_15()        { assertEquals(15.0, breakMultiplier(METAL),         1e-9); }
    @Test void highStrength_multiplier_is_25() { assertEquals(25.0, breakMultiplier(HIGH_STRENGTH), 1e-9); }

    // -- combined break thresholds (Phase 3A: the value that governs destruction) --

    @Test void brittleBreakThreshold_is45J()       { assertEquals(45.0,   breakThreshold(BRITTLE),       1e-9); }
    @Test void woodBreakThreshold_is100J()         { assertEquals(100.0,  breakThreshold(WOOD),          1e-9); }
    @Test void stoneBreakThreshold_is500J()        { assertEquals(500.0,  breakThreshold(STONE),         1e-9); }
    @Test void metalBreakThreshold_is1800J()       { assertEquals(1800.0, breakThreshold(METAL),         1e-9); }
    @Test void highStrengthBreakThreshold_is7500J(){ assertEquals(7500.0, breakThreshold(HIGH_STRENGTH), 1e-9); }

    @Test
    void breakThresholds_strictlyOrdered() {
        assertTrue(breakThreshold(BRITTLE) < breakThreshold(WOOD));
        assertTrue(breakThreshold(WOOD)    < breakThreshold(STONE));
        assertTrue(breakThreshold(STONE)   < breakThreshold(METAL));
        assertTrue(breakThreshold(METAL)   < breakThreshold(HIGH_STRENGTH));
    }

    // -- additional block coverage (Phase 3A material lookup) -----------------

    @Test void glass_pane_is_brittle()        { assertEquals(BRITTLE,       classify("glass_pane")); }
    @Test void packed_ice_is_brittle()        { assertEquals(BRITTLE,       classify("packed_ice")); }
    @Test void terracotta_is_brittle()        { assertEquals(BRITTLE,       classify("terracotta")); }
    @Test void stone_bricks_is_stone()        { assertEquals(STONE,         classify("stone_bricks")); }
    @Test void white_concrete_is_stone()      { assertEquals(STONE,         classify("white_concrete")); }
    @Test void diamond_block_is_metal()       { assertEquals(METAL,         classify("diamond_block")); }
    @Test void crying_obsidian_is_high()      { assertEquals(HIGH_STRENGTH, classify("crying_obsidian")); }

    // -- helper ---------------------------------------------------------------

    private static double breakThreshold(MaterialThresholdProfile.MaterialClass mc) {
        return threshold(mc) * breakMultiplier(mc);
    }
}
