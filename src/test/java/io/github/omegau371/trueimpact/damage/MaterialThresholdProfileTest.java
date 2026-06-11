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
    @Test void wood_threshold_is_20()            { assertEquals(20.0,  threshold(WOOD),           1e-9); }
    @Test void stone_threshold_is_50()           { assertEquals(50.0,  threshold(STONE),          1e-9); }
    @Test void metal_threshold_is_120()          { assertEquals(120.0, threshold(METAL),          1e-9); }
    @Test void high_strength_threshold_is_300()  { assertEquals(300.0, threshold(HIGH_STRENGTH),  1e-9); }
    @Test void generic_threshold_is_50()         { assertEquals(50.0,  threshold(GENERIC),        1e-9); }

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
        assertEquals(GENERIC, classify("minecraft:diamond_block"));
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
    void wouldExceed_generic_uses_stone_threshold() {
        // GENERIC threshold == STONE threshold (both 50.0)
        assertEquals(threshold(STONE), threshold(GENERIC), 1e-9,
                "GENERIC and STONE share the same placeholder threshold");
        assertTrue(wouldExceed(51.0, GENERIC), "51.0 > 50.0 -> true");
        assertFalse(wouldExceed(49.0, GENERIC), "49.0 < 50.0 -> false");
    }
}
