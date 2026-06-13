package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EffectiveDamageModel.
 * No Minecraft runtime required.
 */
class EffectiveDamageModelTest {

    // -- cap behaviour ------------------------------------------------------------

    @Test
    void stone_below_cap_passes_through_unchanged() {
        // STONE threshold=50, cap=50*2=100. kImpact=60 < 100 -> effective=60
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                60.0, MaterialThresholdProfile.MaterialClass.STONE, 50.0);
        assertEquals(60.0, r.effectiveDamageJ(), 0.001);
        assertFalse(r.wasCapped());
    }

    @Test
    void stone_above_cap_is_capped_at_2x_threshold() {
        // STONE threshold=50, cap=100. kImpact=300 > 100 -> effective=100
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                300.0, MaterialThresholdProfile.MaterialClass.STONE, 50.0);
        assertEquals(100.0, r.effectiveDamageJ(), 0.001);
        assertTrue(r.wasCapped());
    }

    @Test
    void soft_soil_cap_is_4x_threshold() {
        // SOFT_SOIL threshold=5, cap=20. kImpact=1000 -> effective=20
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                1000.0, MaterialThresholdProfile.MaterialClass.SOFT_SOIL, 5.0);
        assertEquals(20.0, r.effectiveDamageJ(), 0.001);
        assertTrue(r.wasCapped());
    }

    @Test
    void soft_soil_below_cap_passes_through() {
        // SOFT_SOIL threshold=5, cap=20. kImpact=10 < 20 -> effective=10
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                10.0, MaterialThresholdProfile.MaterialClass.SOFT_SOIL, 5.0);
        assertEquals(10.0, r.effectiveDamageJ(), 0.001);
        assertFalse(r.wasCapped());
    }

    @Test
    void wood_cap_is_3x_threshold() {
        // WOOD threshold=20, cap=60. kImpact=300 -> effective=60
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                300.0, MaterialThresholdProfile.MaterialClass.WOOD, 20.0);
        assertEquals(60.0, r.effectiveDamageJ(), 0.001);
        assertTrue(r.wasCapped());
    }

    @Test
    void metal_cap_is_1_5x_threshold() {
        // METAL threshold=120, cap=180. kImpact=500 -> effective=180
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                500.0, MaterialThresholdProfile.MaterialClass.METAL, 120.0);
        assertEquals(180.0, r.effectiveDamageJ(), 0.001);
        assertTrue(r.wasCapped());
    }

    @Test
    void high_strength_cap_is_1x_threshold() {
        // HIGH_STRENGTH threshold=300, cap=300. kImpact=1000 -> effective=300
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                1000.0, MaterialThresholdProfile.MaterialClass.HIGH_STRENGTH, 300.0);
        assertEquals(300.0, r.effectiveDamageJ(), 0.001);
        assertTrue(r.wasCapped());
    }

    @Test
    void generic_cap_is_2x_threshold() {
        // GENERIC threshold=50, cap=100. kImpact=200 -> effective=100
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                200.0, MaterialThresholdProfile.MaterialClass.GENERIC, 50.0);
        assertEquals(100.0, r.effectiveDamageJ(), 0.001);
        assertTrue(r.wasCapped());
    }

    @Test
    void kImpact_exactly_at_cap_is_not_capped() {
        // STONE threshold=50, cap=100. kImpact=100 exactly -> effective=100, wasCapped=false
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                100.0, MaterialThresholdProfile.MaterialClass.STONE, 50.0);
        assertEquals(100.0, r.effectiveDamageJ(), 0.001);
        assertFalse(r.wasCapped(), "kImpact == cap should not set wasCapped");
    }

    @Test
    void raw_kImpact_preserved_in_result_regardless_of_cap() {
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                999.0, MaterialThresholdProfile.MaterialClass.STONE, 50.0);
        assertEquals(999.0, r.rawImpactJ(), 0.001, "rawImpactJ must always equal the input kImpact");
        assertEquals(100.0, r.effectiveDamageJ(), 0.001);
    }

    // -- regression tests (Phase 2D in-game observed mismatch) --------------------

    @Test
    void regression_stone_raw_55_965_below_cap_effective_equals_raw() {
        // Regression: raw=55.965J, STONE threshold=50J, cap=100J.
        // raw < cap -> effective must equal raw, NOT the cap value.
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                55.965, MaterialThresholdProfile.MaterialClass.STONE, 50.0);
        assertEquals(55.965, r.rawImpactJ(), 0.001,
                "rawImpactJ must always equal the input kImpact");
        assertEquals(55.965, r.effectiveDamageJ(), 0.001,
                "effective must equal raw when raw(55.965) < cap(100) -- must not return cap");
        assertFalse(r.wasCapped(),
                "wasCapped must be false when raw <= cap");
    }

    @Test
    void regression_stone_raw_150_above_cap_effective_is_capped_at_100() {
        // Regression: raw=150J, STONE threshold=50J, cap=100J.
        // raw > cap -> effective must be exactly the cap value.
        EffectiveDamageModel.Result r = EffectiveDamageModel.compute(
                150.0, MaterialThresholdProfile.MaterialClass.STONE, 50.0);
        assertEquals(150.0, r.rawImpactJ(), 0.001,
                "rawImpactJ must always equal the input kImpact");
        assertEquals(100.0, r.effectiveDamageJ(), 0.001,
                "effective must be capped at 2*threshold=100 when raw(150) > cap(100)");
        assertTrue(r.wasCapped());
    }
}
