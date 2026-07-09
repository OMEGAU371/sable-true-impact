package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlockDamageAccumulator.
 *
 * Unified cap formula: effectiveJ = min(rawKImpact, event.threshold()).
 * event.threshold() is the break threshold supplied by onServerTickPost
 * (BlockHardnessProfile × confinement factor). In these tests the threshold
 * comes from MaterialThresholdProfile.threshold(mc) (the crack threshold) as a
 * convenient numeric value -- the accumulator treats it as an opaque cap.
 *
 * STONE crack threshold = 50J.  SOFT_SOIL crack threshold = 5J.
 * Below threshold → effective = raw.  At/above threshold → effective = threshold.
 *
 * No Minecraft runtime required.
 */
class BlockDamageAccumulatorTest {

    @BeforeEach
    void reset() {
        BlockDamageAccumulator.clear();
        // These tests verify the raw accumulation math; disable the elastic floor and
        // stress relaxation so exact-value assertions stay valid. Dedicated tests below
        // re-enable them explicitly.
        ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR = 0.0;
        ImpactRuntimeConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = 0;
    }

    @AfterEach
    void restoreConfig() {
        ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR = 0.2;
        ImpactRuntimeConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = 60;
    }

    // -- helpers ------------------------------------------------------------------

    private static DeferredDamageEvent event(String levelKey, String blockId,
            MaterialThresholdProfile.MaterialClass mc,
            int x, int y, int z, double kImpact, long tick) {
        return new DeferredDamageEvent(tick, levelKey, blockId, x, y, z,
                mc, kImpact, MaterialThresholdProfile.threshold(mc),
                VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);
    }

    private static DeferredDamageEvent stoneAt(int x, int y, int z, double kImpact, long tick) {
        return event("minecraft:overworld", "minecraft:stone",
                MaterialThresholdProfile.MaterialClass.STONE, x, y, z, kImpact, tick);
    }

    private static DeferredDamageEvent grassAt(int x, int y, int z, double kImpact, long tick) {
        return event("minecraft:overworld", "minecraft:grass_block",
                MaterialThresholdProfile.MaterialClass.SOFT_SOIL, x, y, z, kImpact, tick);
    }

    // -- basic accumulation -------------------------------------------------------

    @Test
    void one_hit_creates_accumulator_entry() {
        // STONE threshold=50J. kImpact=30 < threshold -> effective=30J (no capping).
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 30.0, 1L));

        assertEquals(1, BlockDamageAccumulator.entryCount());
        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(30.0, snap.accumulatedEffectiveDamageJ(), 0.001);
        assertEquals(30.0, snap.lastRawImpactJ(), 0.001);
        assertEquals(30.0, snap.lastEffectiveDamageJ(), 0.001);
        assertEquals(1, snap.hitCount());
        assertEquals(1L, snap.lastUpdatedTick());
        assertEquals(MaterialThresholdProfile.MaterialClass.STONE, snap.materialClass());
    }

    @Test
    void repeated_hits_on_same_block_accumulate_effective_damage() {
        // k=30: below threshold(50) → eff=30
        // k=60: above threshold    → eff=50
        // k=50: equal threshold    → eff=50
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 30.0, 1L));
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 60.0, 2L));
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 50.0, 3L));

        assertEquals(1, BlockDamageAccumulator.entryCount(), "same block/pos = same entry");
        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(130.0, snap.accumulatedEffectiveDamageJ(), 0.001, "30+50+50");
        assertEquals(50.0, snap.lastRawImpactJ(), 0.001, "last hit raw");
        assertEquals(50.0, snap.lastEffectiveDamageJ(), 0.001, "last hit effective (50=threshold)");
        assertEquals(3, snap.hitCount());
        assertEquals(3L, snap.lastUpdatedTick());
    }

    @Test
    void accumulator_stores_effective_damage_not_raw_when_capped() {
        // SOFT_SOIL threshold=5J. kImpact=1000 >> threshold -> effective=5J.
        DeferredDamageEvent ev = event("minecraft:overworld", "minecraft:grass_block",
                MaterialThresholdProfile.MaterialClass.SOFT_SOIL, 5, 63, 5, 1000.0, 1L);
        BlockDamageAccumulator.accumulate(ev);

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 5, 63, 5, "minecraft:grass_block");
        assertNotNull(snap);
        assertEquals(5.0, snap.accumulatedEffectiveDamageJ(), 0.001,
                "accumulator must store effectiveDamageJ (5=threshold), not raw (1000)");
        assertEquals(1000.0, snap.lastRawImpactJ(), 0.001, "raw kImpact preserved in lastRawImpactJ");
        assertEquals(5.0, snap.lastEffectiveDamageJ(), 0.001);
        assertTrue(snap.lastEffectiveDamageJ() < snap.lastRawImpactJ(),
                "effective must be less than raw when capped");
    }

    @Test
    void raw_and_eff_last_are_both_tracked_independently() {
        // STONE threshold=50J. kImpact=300 >> threshold -> raw=300, eff=50.
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 300.0, 1L));

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(300.0, snap.lastRawImpactJ(), 0.001, "raw kImpact always preserved");
        assertEquals(50.0, snap.lastEffectiveDamageJ(), 0.001, "effective = min(300, threshold=50)");
        assertEquals(50.0, snap.accumulatedEffectiveDamageJ(), 0.001);
        assertNotEquals(snap.lastRawImpactJ(), snap.lastEffectiveDamageJ(),
                "raw and effective must differ when capped");
    }

    @Test
    void different_block_ids_at_same_pos_do_not_share_damage() {
        // stone k=30 < threshold(50) → eff=30; grass k=2 < SOFT_SOIL threshold(5) → eff=2
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 30.0, 1L));
        BlockDamageAccumulator.accumulate(grassAt(10, 64, 10, 2.0, 2L));

        assertEquals(2, BlockDamageAccumulator.entryCount(),
                "different victimBlock = separate entry even at same pos");
        BlockDamageAccumulator.Snapshot stone =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        BlockDamageAccumulator.Snapshot grass =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:grass_block");
        assertNotNull(stone);
        assertNotNull(grass);
        assertEquals(30.0, stone.accumulatedEffectiveDamageJ(), 0.001, "stone below threshold → raw");
        assertEquals(2.0,  grass.accumulatedEffectiveDamageJ(), 0.001, "grass below threshold → raw");
    }

    @Test
    void different_dimensions_do_not_share_damage() {
        // ow: k=30 < threshold(50) → eff=30
        // ne: k=80 > threshold(50) → eff=50
        DeferredDamageEvent ow = event("minecraft:overworld", "minecraft:stone",
                MaterialThresholdProfile.MaterialClass.STONE, 10, 64, 10, 30.0, 1L);
        DeferredDamageEvent ne = event("minecraft:the_nether", "minecraft:stone",
                MaterialThresholdProfile.MaterialClass.STONE, 10, 64, 10, 80.0, 1L);

        BlockDamageAccumulator.accumulate(ow);
        BlockDamageAccumulator.accumulate(ne);

        assertEquals(2, BlockDamageAccumulator.entryCount(), "different dimensions = different entries");
        BlockDamageAccumulator.Snapshot owSnap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        BlockDamageAccumulator.Snapshot neSnap =
                BlockDamageAccumulator.getSnapshot("minecraft:the_nether", 10, 64, 10, "minecraft:stone");
        assertNotNull(owSnap);
        assertNotNull(neSnap);
        assertEquals(30.0, owSnap.accumulatedEffectiveDamageJ(), 0.001, "ow: k=30 < threshold → raw");
        assertEquals(50.0, neSnap.accumulatedEffectiveDamageJ(), 0.001, "ne: k=80 > threshold → capped to 50");
    }

    @Test
    void levelKey_from_event_is_preserved_in_snapshot() {
        DeferredDamageEvent netherEvent = event("minecraft:the_nether", "minecraft:stone",
                MaterialThresholdProfile.MaterialClass.STONE, 10, 64, 10, 30.0, 1L);
        BlockDamageAccumulator.accumulate(netherEvent);

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:the_nether", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals("minecraft:the_nether", snap.key().levelKey(),
                "levelKey must be preserved exactly from the DeferredDamageEvent");
    }

    @Test
    void stale_event_for_different_block_at_same_pos_does_not_corrupt_existing_entry() {
        // stone k=80 > threshold(50) → eff=50
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 80.0, 1L));
        double stoneDamage = BlockDamageAccumulator
                .getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone")
                .accumulatedEffectiveDamageJ();

        // Stale event for grass_block at same pos; creates separate entry.
        BlockDamageAccumulator.accumulate(grassAt(10, 64, 10, 3.0, 2L));

        BlockDamageAccumulator.Snapshot stone =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertEquals(stoneDamage, stone.accumulatedEffectiveDamageJ(), 0.001,
                "stone entry must not be contaminated by stale grass_block event");
        assertEquals(2, BlockDamageAccumulator.entryCount(),
                "stale event creates its own separate entry");
    }

    @Test
    void soft_soil_accumulates_effective_damage() {
        // SOFT_SOIL threshold=5J. kImpact=100 >> threshold → effective=5J.
        DeferredDamageEvent ev = event("minecraft:overworld", "minecraft:grass_block",
                MaterialThresholdProfile.MaterialClass.SOFT_SOIL, 5, 63, 5, 100.0, 1L);
        BlockDamageAccumulator.accumulate(ev);

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 5, 63, 5, "minecraft:grass_block");
        assertNotNull(snap, "SOFT_SOIL events must be accumulated");
        assertEquals(5.0, snap.accumulatedEffectiveDamageJ(), 0.001,
                "SOFT_SOIL: cap=threshold=5; effective=5 not raw=100");
        assertEquals(MaterialThresholdProfile.MaterialClass.SOFT_SOIL, snap.materialClass());
    }

    @Test
    void stone_accumulates_effective_damage_but_does_not_mutate_in_phase_2c() {
        // STONE threshold=50J. kImpact=300 → effective=50. ratio=1.0 → CRITICAL.
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 300.0, 1L));

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap, "STONE class must accumulate");
        assertEquals(50.0, snap.accumulatedEffectiveDamageJ(), 0.001, "capped to threshold(50)");
        assertEquals(300.0, snap.lastRawImpactJ(), 0.001, "raw preserved");
        assertTrue(Double.isFinite(snap.ratio()) && snap.ratio() > 0,
                "ratio must be positive (50/50 = 1.0)");
        assertEquals(MaterialThresholdProfile.MaterialClass.STONE, snap.materialClass());
    }

    @Test
    void debug_flags_off_still_accumulates() {
        assertFalse(io.github.omegau371.trueimpact.observation.DiagnosticConfig.ENABLED,
                "ENABLED must be false by default");
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 30.0, 1L));
        assertEquals(1, BlockDamageAccumulator.entryCount(),
                "accumulate must work regardless of DiagnosticConfig.ENABLED");
    }

    // -- damage state -------------------------------------------------------------

    @Test
    void damage_state_classified_correctly_at_boundaries() {
        // STONE threshold=50. Hit with 12 effective -> ratio=12/50=0.24 -> INTACT
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 12.0, 1L));
        assertEquals(DamageState.INTACT,
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone").damageState(),
                "12/50=0.24 -> INTACT");

        BlockDamageAccumulator.clear();
        // ratio = 0.25 -> BRUISED. effective=12.5, threshold=50, kImpact=12.5
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 12.5, 1L));
        assertEquals(DamageState.BRUISED,
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone").damageState(),
                "12.5/50=0.25 -> BRUISED");

        BlockDamageAccumulator.clear();
        // ratio >= 1.0 -> CRITICAL. kImpact=50=threshold -> effective=50, ratio=1.0.
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 50.0, 1L));
        assertEquals(DamageState.CRITICAL,
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone").damageState(),
                "50/50=1.0 -> CRITICAL");
    }

    @Test
    void damage_state_advances_with_accumulated_hits() {
        // Each hit: kImpact=10 < threshold(50), effective=10. threshold=50.
        // After 3 hits: effective=30, ratio=0.60 -> CRACKED
        // After 5 hits: effective=50, ratio=1.00 -> CRITICAL
        for (int i = 1; i <= 3; i++) {
            BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 10.0, (long) i));
        }
        BlockDamageAccumulator.Snapshot after3 =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertEquals(DamageState.CRACKED, after3.damageState(), "30/50=0.60 -> CRACKED");

        for (int i = 4; i <= 5; i++) {
            BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 10.0, (long) i));
        }
        BlockDamageAccumulator.Snapshot after5 =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertEquals(DamageState.CRITICAL, after5.damageState(), "50/50=1.0 -> CRITICAL");
    }

    // -- clear --------------------------------------------------------------------

    @Test
    void clear_resets_all_state() {
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 30.0, 1L));
        BlockDamageAccumulator.clear();

        assertEquals(0, BlockDamageAccumulator.entryCount());
        assertNull(BlockDamageAccumulator.lastUpdatedSnapshot());
        assertNull(BlockDamageAccumulator.getSnapshot(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone"));
    }

    // -- ratio and snapshot -------------------------------------------------------

    @Test
    void ratio_uses_effective_damage_over_threshold() {
        // STONE threshold=50. kImpact=25 (sub-threshold) → effective=25. ratio=25/50=0.5.
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 25.0, 1L));
        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(50.0, snap.thresholdJ(), 0.001);
        assertEquals(25.0, snap.accumulatedEffectiveDamageJ(), 0.001);
        assertEquals(0.5, snap.ratio(), 0.001, "25/50=0.5");
    }

    @Test
    void lastUpdatedSnapshot_tracks_most_recent_accumulate() {
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 30.0, 1L));
        BlockDamageAccumulator.accumulate(grassAt(20, 64, 20, 3.0, 2L));

        BlockDamageAccumulator.Snapshot last = BlockDamageAccumulator.lastUpdatedSnapshot();
        assertNotNull(last);
        assertEquals("minecraft:grass_block", last.key().victimBlock(),
                "lastUpdatedSnapshot must reflect the most recent accumulate call");
        assertEquals(20, last.key().posX());
    }

    // -- regression tests ---------------------------------------------------------

    @Test
    void regression_stone_raw_below_threshold_not_capped() {
        // k=30J < STONE threshold(50J) → effective=raw=30J. No capping.
        DeferredDamageEvent ev = event("minecraft:overworld", "minecraft:andesite",
                MaterialThresholdProfile.MaterialClass.STONE, 10, 64, 10, 30.0, 1L);
        BlockDamageAccumulator.accumulate(ev);

        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.getSnapshot(
                "minecraft:overworld", 10, 64, 10, "minecraft:andesite");
        assertNotNull(snap);
        assertEquals(30.0, snap.lastRawImpactJ(), 0.001);
        assertEquals(30.0, snap.lastEffectiveDamageJ(), 0.001, "sub-threshold: effective=raw");
        assertEquals(30.0, snap.accumulatedEffectiveDamageJ(), 0.001);
        assertEquals(50.0, snap.thresholdJ(), 0.001);
        assertEquals(30.0 / 50.0, snap.ratio(), 0.001, "ratio=0.6 → CRACKED");
        assertEquals(DamageState.CRACKED, snap.damageState());
    }

    @Test
    void regression_stone_raw_above_threshold_capped_to_threshold() {
        // k=150J > STONE threshold(50J) → effective=threshold=50J. ratio=1.0. rawLast preserved.
        DeferredDamageEvent ev = event("minecraft:overworld", "minecraft:stone",
                MaterialThresholdProfile.MaterialClass.STONE, 10, 64, 10, 150.0, 1L);
        BlockDamageAccumulator.accumulate(ev);

        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.getSnapshot(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(150.0, snap.lastRawImpactJ(), 0.001, "raw preserved even when capped");
        assertEquals(50.0, snap.lastEffectiveDamageJ(), 0.001, "effective=min(150,threshold=50)");
        assertEquals(50.0, snap.accumulatedEffectiveDamageJ(), 0.001);
        assertEquals(1.0, snap.ratio(), 0.001, "ratio=50/50=1.0");
        assertEquals(DamageState.CRITICAL, snap.damageState(), "ratio=1.0 → CRITICAL");
    }

    // -- elastic floor + stress relaxation (2026-07-08 anti-grind fix) -------------

    @Test
    void hits_below_elastic_floor_are_purely_elastic_no_entry() {
        ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR = 0.2;
        // STONE threshold=50J, floor=10J. A 9.9 J hit is elastic: no entry, no accumulation,
        // no matter how many times it repeats (the observed resting-contact grind).
        for (long t = 1; t <= 100; t++) {
            BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 9.9, t));
        }
        assertEquals(0, BlockDamageAccumulator.entryCount(),
                "sub-floor hits must never accumulate");
        // At the floor: accumulates normally.
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 10.0, 101L));
        assertEquals(1, BlockDamageAccumulator.entryCount());
    }

    @Test
    void accumulated_damage_decays_between_hits() {
        ImpactRuntimeConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = 60;
        // Two 30 J hits 60 ticks apart: the first has halved to 15 by the second hit.
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 30.0, 1L));
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 30.0, 61L));
        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.getSnapshot(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(45.0, snap.accumulatedEffectiveDamageJ(), 0.001, "30/2 + 30");
        assertEquals(2, snap.hitCount());
    }
}
