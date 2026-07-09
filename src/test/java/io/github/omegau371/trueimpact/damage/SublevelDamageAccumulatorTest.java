package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SublevelDamageAccumulator (Phase 3A physics-structure block damage).
 *
 * effectiveJ = min(kImpact, breakThresholdJ). No Minecraft runtime required.
 */
class SublevelDamageAccumulatorTest {

    private static final double STONE_BREAK_J = 490.0;

    @BeforeEach
    void reset() {
        SublevelDamageAccumulator.clear();
        ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION = true;
        ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR = 0.0;
        ImpactRuntimeConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = 0;
    }

    @AfterEach
    void restoreConfig() {
        ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION = true;
        ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR = 0.2;
        ImpactRuntimeConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = 60;
    }

    // -- basic accumulation (mirrors BlockDamageAccumulatorTest) -------------------

    @Test
    void repeated_hits_accumulate_and_reach_critical() {
        SublevelDamageAccumulator.accumulate(1, 0, 0, 0,
                MaterialThresholdProfile.MaterialClass.STONE, 200.0, STONE_BREAK_J, 1L);
        SublevelDamageAccumulator.accumulate(1, 0, 0, 0,
                MaterialThresholdProfile.MaterialClass.STONE, 200.0, STONE_BREAK_J, 2L);
        SublevelDamageAccumulator.Snapshot snap = SublevelDamageAccumulator.accumulate(1, 0, 0, 0,
                MaterialThresholdProfile.MaterialClass.STONE, 200.0, STONE_BREAK_J, 3L);

        assertEquals(DamageState.CRITICAL, snap.damageState(), "200*3=600 > 490J threshold");
        assertEquals(3, snap.hitCount());
    }

    @Test
    void single_hit_below_elastic_floor_causes_zero_damage() {
        ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR = 0.2;
        // floor = 0.2*490 = 98J. A 50J hit is purely elastic -- no entry created.
        SublevelDamageAccumulator.Snapshot snap = SublevelDamageAccumulator.accumulate(1, 0, 0, 0,
                MaterialThresholdProfile.MaterialClass.STONE, 50.0, STONE_BREAK_J, 1L);

        assertEquals(DamageState.INTACT, snap.damageState());
        assertEquals(0, snap.hitCount());
        assertNull(SublevelDamageAccumulator.getSnapshot(1, 0, 0, 0,
                MaterialThresholdProfile.MaterialClass.STONE));
    }

    // -- single-hit mode (ENABLE_DAMAGE_ACCUMULATION = false) -----------------------

    @org.junit.jupiter.api.Nested
    class SingleHitMode {
        @BeforeEach
        void disableAccumulation() {
            ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION = false;
        }

        @Test
        void single_hit_at_or_above_threshold_reaches_critical_immediately() {
            SublevelDamageAccumulator.Snapshot snap = SublevelDamageAccumulator.accumulate(1, 0, 0, 0,
                    MaterialThresholdProfile.MaterialClass.STONE, STONE_BREAK_J, STONE_BREAK_J, 1L);

            assertEquals(DamageState.CRITICAL, snap.damageState());
            assertEquals(1.0, snap.ratio(), 0.001);
            assertEquals(1, snap.hitCount());
            assertNull(SublevelDamageAccumulator.getSnapshot(1, 0, 0, 0,
                            MaterialThresholdProfile.MaterialClass.STONE),
                    "single-hit mode must never persist an entry");
        }

        @Test
        void repeated_sub_threshold_hits_never_accumulate() {
            // Ten 300J hits on a 490J-threshold block: 10*300=3000J would blow past the
            // threshold if accumulating. It must not -- each hit is judged alone.
            SublevelDamageAccumulator.Snapshot snap = null;
            for (long t = 1; t <= 10; t++) {
                snap = SublevelDamageAccumulator.accumulate(1, 0, 0, 0,
                        MaterialThresholdProfile.MaterialClass.STONE, 300.0, STONE_BREAK_J, t);
            }
            assertNotNull(snap);
            assertNotEquals(DamageState.CRITICAL, snap.damageState(),
                    "single 300J hit alone (ratio~0.61) must never reach CRITICAL");
            assertEquals(1, snap.hitCount(), "no carry-over between hits");
        }

        @Test
        void stale_entry_from_accumulation_mode_is_cleared_on_switch() {
            ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION = true;
            SublevelDamageAccumulator.accumulate(1, 0, 0, 0,
                    MaterialThresholdProfile.MaterialClass.STONE, 400.0, STONE_BREAK_J, 1L);
            assertNotNull(SublevelDamageAccumulator.getSnapshot(1, 0, 0, 0,
                    MaterialThresholdProfile.MaterialClass.STONE));

            ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION = false;
            SublevelDamageAccumulator.Snapshot snap = SublevelDamageAccumulator.accumulate(1, 0, 0, 0,
                    MaterialThresholdProfile.MaterialClass.STONE, 100.0, STONE_BREAK_J, 2L);

            assertNotEquals(DamageState.CRITICAL, snap.damageState(), "100J alone, no leftover 400J from before");
            assertNull(SublevelDamageAccumulator.getSnapshot(1, 0, 0, 0,
                    MaterialThresholdProfile.MaterialClass.STONE), "stale entry must be cleared");
        }
    }
}
