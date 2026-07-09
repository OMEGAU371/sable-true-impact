package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.Test;

import static io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.MaterialClass.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MaterialPropertiesProfile.
 * Validates ordering invariants and physical reasonableness — not magic numbers.
 *
 * Reference inputs (vanilla hardness / blastResist / class):
 *   Glass    : 0.3  /   0.3 / BRITTLE
 *   Dirt     : 0.5  /   0.5 / SOFT_SOIL
 *   Wood     : 2.0  /   3.0 / WOOD
 *   Stone    : 1.5  /   6.0 / STONE
 *   Iron     : 5.0  /  10.0 / METAL
 *   Obsidian : 50.0 / 1200  / HIGH_STRENGTH
 *   Bedrock  : -1.0 / 3.6M  / HIGH_STRENGTH
 */
class MaterialPropertiesProfileTest {

    // -- helpers ------------------------------------------------------------------

    private static MaterialProperties glass() {
        return MaterialPropertiesProfile.of(0.3f, 0.3f, BRITTLE);
    }
    private static MaterialProperties dirt() {
        return MaterialPropertiesProfile.of(0.5f, 0.5f, SOFT_SOIL);
    }
    private static MaterialProperties stone() {
        return MaterialPropertiesProfile.of(1.5f, 6.0f, STONE);
    }
    private static MaterialProperties iron() {
        return MaterialPropertiesProfile.of(5.0f, 10.0f, METAL);
    }
    private static MaterialProperties obsidian() {
        return MaterialPropertiesProfile.of(50.0f, 1200f, HIGH_STRENGTH);
    }
    private static MaterialProperties bedrock() {
        return MaterialPropertiesProfile.of(-1.0f, 3_600_000f, HIGH_STRENGTH);
    }

    // -- all ratio fields are in [0, 1] -------------------------------------------

    @Test
    void all_ratio_fields_in_range_for_glass() {
        assertRatiosInRange(glass());
    }

    @Test
    void all_ratio_fields_in_range_for_stone() {
        assertRatiosInRange(stone());
    }

    @Test
    void all_ratio_fields_in_range_for_metal() {
        assertRatiosInRange(iron());
    }

    private static void assertRatiosInRange(MaterialProperties p) {
        assertTrue(p.toughness()   >= 0 && p.toughness()   <= 1, "toughness");
        assertTrue(p.ductility()   >= 0 && p.ductility()   <= 1, "ductility");
        assertTrue(p.brittleness() >= 0 && p.brittleness() <= 1, "brittleness");
        assertTrue(p.elasticity()  >= 0 && p.elasticity()  <= 1, "elasticity");
        assertTrue(p.friction()    >= 0 && p.friction()    <= 1, "friction");
    }

    // -- density is always positive -----------------------------------------------

    @Test
    void density_is_positive_for_all_classes() {
        assertTrue(glass().densityKgM3()    > 0);
        assertTrue(dirt().densityKgM3()     > 0);
        assertTrue(stone().densityKgM3()    > 0);
        assertTrue(iron().densityKgM3()     > 0);
        assertTrue(obsidian().densityKgM3() > 0);
    }

    // -- ordering: toughness (metal > stone > glass) ------------------------------

    @Test
    void toughness_ordering() {
        assertTrue(iron().toughness()   > stone().toughness(),  "metal > stone");
        assertTrue(stone().toughness()  > glass().toughness(),  "stone > glass");
    }

    @Test
    void high_strength_toughest() {
        assertTrue(obsidian().toughness() > iron().toughness(),
                "HIGH_STRENGTH should be toughest class");
    }

    // -- ordering: brittleness (glass > stone > metal) ----------------------------

    @Test
    void brittleness_ordering() {
        assertTrue(glass().brittleness()  > stone().brittleness(), "glass > stone");
        assertTrue(stone().brittleness()  > iron().brittleness(),  "stone > metal");
    }

    // -- ordering: density (metal >> stone ≈ glass > soil > wood) ----------------

    @Test
    void density_ordering() {
        assertTrue(iron().densityKgM3()   > stone().densityKgM3(),  "metal >> stone");
        assertTrue(stone().densityKgM3()  > dirt().densityKgM3(),   "stone > soil");
        assertTrue(dirt().densityKgM3()   > MaterialPropertiesProfile
                        .of(2.0f, 3.0f, WOOD).densityKgM3(),        "soil > wood");
    }

    // -- SOFT_SOIL compaction threshold -------------------------------------------

    @Test
    void soft_soil_compaction_threshold_is_finite_and_below_crack_threshold() {
        double crackJ = BlockHardnessProfile.crackThresholdJ(0.5f, 0.5f);
        double compactJ = dirt().compactionThresholdJ();
        assertTrue(Double.isFinite(compactJ), "compaction threshold must be finite");
        assertTrue(compactJ < crackJ,
                "compaction should trigger before cracking: " + compactJ + " < " + crackJ);
    }

    @Test
    void non_soft_soil_compaction_threshold_is_max_value() {
        assertEquals(Double.MAX_VALUE, glass().compactionThresholdJ());
        assertEquals(Double.MAX_VALUE, stone().compactionThresholdJ());
        assertEquals(Double.MAX_VALUE, iron().compactionThresholdJ());
    }

    // -- yield threshold ----------------------------------------------------------

    @Test
    void metal_yield_threshold_is_below_crack_threshold() {
        double crackJ = BlockHardnessProfile.crackThresholdJ(5.0f, 10.0f);
        double yieldJ = iron().yieldThresholdJ();
        assertTrue(yieldJ < crackJ,
                "metal yields before cracking: yieldJ=" + yieldJ + " crackJ=" + crackJ);
    }

    @Test
    void brittle_yield_threshold_is_close_to_crack_threshold() {
        double crackJ = BlockHardnessProfile.crackThresholdJ(0.3f, 0.3f);
        double yieldJ = glass().yieldThresholdJ();
        // brittle fraction = 0.95 → yield ≈ 95% of crack threshold
        assertTrue(yieldJ >= crackJ * 0.90,
                "glass barely yields before fracture: yieldJ=" + yieldJ + " crackJ=" + crackJ);
    }

    @Test
    void metal_yields_at_lower_fraction_than_stone() {
        double metalCrack = BlockHardnessProfile.crackThresholdJ(5.0f, 10.0f);
        double stoneCrack = BlockHardnessProfile.crackThresholdJ(1.5f, 6.0f);
        double metalFraction = iron().yieldThresholdJ()  / metalCrack;
        double stoneFraction = stone().yieldThresholdJ() / stoneCrack;
        assertTrue(metalFraction < stoneFraction,
                "metal has lower yield/crack ratio than stone (metal yields earlier)");
    }

    // -- indestructible (bedrock) -------------------------------------------------

    @Test
    void bedrock_yield_threshold_is_max_value() {
        assertEquals(Double.MAX_VALUE, bedrock().yieldThresholdJ());
    }

    @Test
    void bedrock_compaction_threshold_is_max_value() {
        assertEquals(Double.MAX_VALUE, bedrock().compactionThresholdJ());
    }

    @Test
    void bedrock_ratio_fields_still_valid() {
        // Bedrock: HIGH_STRENGTH class → well-defined ratios even if thresholds are MAX
        MaterialProperties b = bedrock();
        assertTrue(b.toughness()   > 0.5, "bedrock should be tough");
        assertTrue(b.brittleness() < 0.2, "bedrock should not be brittle");
    }

    // -- ductility ----------------------------------------------------------------

    @Test
    void soft_soil_more_ductile_than_stone() {
        assertTrue(dirt().ductility() > stone().ductility());
    }

    @Test
    void metal_most_ductile_of_common_materials() {
        assertTrue(iron().ductility() > stone().ductility());
        assertTrue(iron().ductility() > MaterialPropertiesProfile
                .of(2.0f, 3.0f, WOOD).ductility());
    }

    // -- friction -----------------------------------------------------------------

    @Test
    void stone_friction_higher_than_glass() {
        // Rough stone vs smooth glass surface
        assertTrue(stone().friction() > glass().friction());
    }

    @Test
    void metal_friction_lower_than_stone() {
        // Machined metal surface vs rough stone
        assertTrue(iron().friction() < stone().friction());
    }

    // -- elasticity ---------------------------------------------------------------

    @Test
    void metal_more_elastic_than_stone() {
        assertTrue(iron().elasticity() > stone().elasticity());
    }

    @Test
    void soft_soil_least_elastic() {
        assertTrue(dirt().elasticity() < stone().elasticity());
        assertTrue(dirt().elasticity() < glass().elasticity());
    }
}
