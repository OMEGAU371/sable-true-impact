package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlockDamageAccumulator.
 * Phase 2C: accumulates effectiveDamageJ (capped), not raw kImpact.
 * No Minecraft runtime required.
 */
class BlockDamageAccumulatorTest {

    @BeforeEach
    void reset() {
        BlockDamageAccumulator.clear();
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
        // STONE threshold=50, cap=100. kImpact=60 < 100 -> effective=60
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 60.0, 1L));

        assertEquals(1, BlockDamageAccumulator.entryCount());
        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(60.0, snap.accumulatedEffectiveDamageJ(), 0.001);
        assertEquals(60.0, snap.lastRawImpactJ(), 0.001);
        assertEquals(60.0, snap.lastEffectiveDamageJ(), 0.001);
        assertEquals(1, snap.hitCount());
        assertEquals(1L, snap.lastUpdatedTick());
        assertEquals(MaterialThresholdProfile.MaterialClass.STONE, snap.materialClass());
    }

    @Test
    void repeated_hits_on_same_block_accumulate_effective_damage() {
        // All hits below STONE cap (100). Effective = raw for all.
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 60.0, 1L));
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 80.0, 2L));
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 50.0, 3L));

        assertEquals(1, BlockDamageAccumulator.entryCount(), "same block/pos = same entry");
        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(190.0, snap.accumulatedEffectiveDamageJ(), 0.001, "60+80+50");
        assertEquals(50.0, snap.lastRawImpactJ(), 0.001, "last hit");
        assertEquals(50.0, snap.lastEffectiveDamageJ(), 0.001);
        assertEquals(3, snap.hitCount());
        assertEquals(3L, snap.lastUpdatedTick());
    }

    @Test
    void accumulator_stores_effective_damage_not_raw_when_capped() {
        // SOFT_SOIL threshold=5, cap=4*5=20. kImpact=1000 -> effective=20
        DeferredDamageEvent ev = event("minecraft:overworld", "minecraft:grass_block",
                MaterialThresholdProfile.MaterialClass.SOFT_SOIL, 5, 63, 5, 1000.0, 1L);
        BlockDamageAccumulator.accumulate(ev);

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 5, 63, 5, "minecraft:grass_block");
        assertNotNull(snap);
        assertEquals(20.0, snap.accumulatedEffectiveDamageJ(), 0.001,
                "accumulator must store effectiveDamageJ (20), not raw (1000)");
        assertEquals(1000.0, snap.lastRawImpactJ(), 0.001, "raw kImpact preserved in lastRawImpactJ");
        assertEquals(20.0, snap.lastEffectiveDamageJ(), 0.001);
        assertTrue(snap.lastEffectiveDamageJ() < snap.lastRawImpactJ(),
                "effective must be less than raw when capped");
    }

    @Test
    void raw_and_eff_last_are_both_tracked_independently() {
        // STONE cap=100. Hit with 300 -> raw=300, eff=100.
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 300.0, 1L));

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(300.0, snap.lastRawImpactJ(), 0.001);
        assertEquals(100.0, snap.lastEffectiveDamageJ(), 0.001);
        assertEquals(100.0, snap.accumulatedEffectiveDamageJ(), 0.001);
    }

    @Test
    void different_block_ids_at_same_pos_do_not_share_damage() {
        // stone 100 -> cap=100, effective=100; grass 20 -> cap=20, effective=20
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 100.0, 1L));
        BlockDamageAccumulator.accumulate(grassAt(10, 64, 10, 20.0, 2L));

        assertEquals(2, BlockDamageAccumulator.entryCount(),
                "different victimBlock = separate entry even at same pos");
        BlockDamageAccumulator.Snapshot stone =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        BlockDamageAccumulator.Snapshot grass =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:grass_block");
        assertNotNull(stone);
        assertNotNull(grass);
        assertEquals(100.0, stone.accumulatedEffectiveDamageJ(), 0.001);
        assertEquals(20.0, grass.accumulatedEffectiveDamageJ(), 0.001);
    }

    @Test
    void different_dimensions_do_not_share_damage() {
        DeferredDamageEvent ow = event("minecraft:overworld", "minecraft:stone",
                MaterialThresholdProfile.MaterialClass.STONE, 10, 64, 10, 60.0, 1L);
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
        assertEquals(60.0, owSnap.accumulatedEffectiveDamageJ(), 0.001);
        assertEquals(80.0, neSnap.accumulatedEffectiveDamageJ(), 0.001);
    }

    @Test
    void levelKey_from_event_is_preserved_in_snapshot() {
        DeferredDamageEvent netherEvent = event("minecraft:the_nether", "minecraft:stone",
                MaterialThresholdProfile.MaterialClass.STONE, 10, 64, 10, 60.0, 1L);
        BlockDamageAccumulator.accumulate(netherEvent);

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:the_nether", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals("minecraft:the_nether", snap.key().levelKey(),
                "levelKey must be preserved exactly from the DeferredDamageEvent");
    }

    @Test
    void stale_event_for_different_block_at_same_pos_does_not_corrupt_existing_entry() {
        // stone 200 -> cap=100, effective=100
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 200.0, 1L));
        double stoneDamage = BlockDamageAccumulator
                .getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone")
                .accumulatedEffectiveDamageJ();

        // Stale event for grass_block at same pos; creates separate entry.
        BlockDamageAccumulator.accumulate(grassAt(10, 64, 10, 15.0, 2L));

        BlockDamageAccumulator.Snapshot stone =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertEquals(stoneDamage, stone.accumulatedEffectiveDamageJ(), 0.001,
                "stone entry must not be contaminated by stale grass_block event");
        assertEquals(2, BlockDamageAccumulator.entryCount(),
                "stale event creates its own separate entry");
    }

    @Test
    void soft_soil_accumulates_effective_damage() {
        // SOFT_SOIL threshold=5, cap=20. kImpact=100 -> effective=20
        DeferredDamageEvent ev = event("minecraft:overworld", "minecraft:grass_block",
                MaterialThresholdProfile.MaterialClass.SOFT_SOIL, 5, 63, 5, 100.0, 1L);
        BlockDamageAccumulator.accumulate(ev);

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 5, 63, 5, "minecraft:grass_block");
        assertNotNull(snap, "SOFT_SOIL events must be accumulated");
        assertEquals(20.0, snap.accumulatedEffectiveDamageJ(), 0.001,
                "SOFT_SOIL: cap=4*threshold=20; effective=20 not raw=100");
        assertEquals(MaterialThresholdProfile.MaterialClass.SOFT_SOIL, snap.materialClass());
    }

    @Test
    void stone_accumulates_effective_damage_but_does_not_mutate_in_phase_2c() {
        // STONE: cap=2*50=100. kImpact=300 -> effective=100.
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 300.0, 1L));

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap, "STONE class must accumulate in Phase 2C");
        assertEquals(100.0, snap.accumulatedEffectiveDamageJ(), 0.001, "capped at 2x threshold");
        assertEquals(300.0, snap.lastRawImpactJ(), 0.001, "raw preserved");
        assertTrue(Double.isFinite(snap.ratio()) && snap.ratio() > 0,
                "ratio must be positive (100/50 = 2.0)");
        assertEquals(MaterialThresholdProfile.MaterialClass.STONE, snap.materialClass());
    }

    @Test
    void debug_flags_off_still_accumulates() {
        assertFalse(io.github.omegau371.trueimpact.observation.DiagnosticConfig.ENABLED,
                "ENABLED must be false by default");
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 60.0, 1L));
        assertEquals(1, BlockDamageAccumulator.entryCount(),
                "accumulate must work regardless of DiagnosticConfig.ENABLED");
    }

    // -- damage state -------------------------------------------------------------

    @Test
    void damage_state_classified_correctly_at_boundaries() {
        // STONE threshold=50. Hit with 12 effective -> ratio=12/50=0.24 -> INTACT
        // Need effective < 12.5 (50*0.25) for INTACT. Use kImpact=12, below cap.
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
        // ratio >= 1.0 -> CRITICAL. effective=50, ratio=1.0. STONE cap=100; kImpact=50.
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 50.0, 1L));
        assertEquals(DamageState.CRITICAL,
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone").damageState(),
                "50/50=1.0 -> CRITICAL");
    }

    @Test
    void damage_state_advances_with_accumulated_hits() {
        // Each hit: kImpact=10, STONE cap=100, effective=10. threshold=50.
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
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 60.0, 1L));
        BlockDamageAccumulator.clear();

        assertEquals(0, BlockDamageAccumulator.entryCount());
        assertNull(BlockDamageAccumulator.lastUpdatedSnapshot());
        assertNull(BlockDamageAccumulator.getSnapshot(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone"));
    }

    // -- ratio and snapshot -------------------------------------------------------

    @Test
    void ratio_uses_effective_damage_over_threshold() {
        // STONE threshold=50, cap=100. kImpact=75 (below cap) -> effective=75. ratio=75/50=1.5
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 75.0, 1L));
        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(50.0, snap.thresholdJ(), 0.001);
        assertEquals(75.0, snap.accumulatedEffectiveDamageJ(), 0.001);
        assertEquals(1.5, snap.ratio(), 0.001, "75/50=1.5");
    }

    @Test
    void lastUpdatedSnapshot_tracks_most_recent_accumulate() {
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 60.0, 1L));
        BlockDamageAccumulator.accumulate(grassAt(20, 64, 20, 15.0, 2L));

        BlockDamageAccumulator.Snapshot last = BlockDamageAccumulator.lastUpdatedSnapshot();
        assertNotNull(last);
        assertEquals("minecraft:grass_block", last.key().victimBlock(),
                "lastUpdatedSnapshot must reflect the most recent accumulate call");
        assertEquals(20, last.key().posX());
    }

    // -- regression tests (Phase 2D in-game observed mismatch) --------------------

    @Test
    void regression_stone_raw_55_965_below_cap_all_fields_correct() {
        // Regression: andesite, raw=55.965J, STONE threshold=50J, cap=100J.
        // raw < cap -> effective must equal raw.
        // effLast must equal rawLast. ratio=55.965/50=1.1193. state=CRITICAL.
        DeferredDamageEvent ev = event("minecraft:overworld", "minecraft:andesite",
                MaterialThresholdProfile.MaterialClass.STONE, 10, 64, 10, 55.965, 1L);
        BlockDamageAccumulator.accumulate(ev);

        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.getSnapshot(
                "minecraft:overworld", 10, 64, 10, "minecraft:andesite");
        assertNotNull(snap);
        assertEquals(55.965, snap.lastRawImpactJ(), 0.001,
                "rawLast must match input kImpact");
        assertEquals(55.965, snap.lastEffectiveDamageJ(), 0.001,
                "effLast must equal raw when raw(55.965) < cap(100)");
        assertEquals(55.965, snap.accumulatedEffectiveDamageJ(), 0.001,
                "accumEff must equal effective -- must NOT show cap value (100)");
        assertEquals(50.0, snap.thresholdJ(), 0.001);
        assertEquals(55.965 / 50.0, snap.ratio(), 0.001,
                "ratio = accumEff/threshold = 55.965/50 = 1.1193");
        assertEquals(1, snap.hitCount());
        assertEquals(DamageState.CRITICAL, snap.damageState(),
                "1.1193 >= 1.0 -> CRITICAL");
    }

    @Test
    void regression_stone_raw_150_above_cap_accumulates_capped_value() {
        // Regression: raw=150J, STONE threshold=50J, cap=100J.
        // raw > cap -> effective=100. accumEff=100. ratio=2.0. rawLast=150 (preserved).
        DeferredDamageEvent ev = event("minecraft:overworld", "minecraft:stone",
                MaterialThresholdProfile.MaterialClass.STONE, 10, 64, 10, 150.0, 1L);
        BlockDamageAccumulator.accumulate(ev);

        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.getSnapshot(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(150.0, snap.lastRawImpactJ(), 0.001,
                "rawLast must be raw kImpact even when capped");
        assertEquals(100.0, snap.lastEffectiveDamageJ(), 0.001,
                "effLast must be cap (100) when raw(150) > cap(100)");
        assertEquals(100.0, snap.accumulatedEffectiveDamageJ(), 0.001,
                "accumEff must equal capped effective");
        assertEquals(2.0, snap.ratio(), 0.001,
                "ratio = 100/50 = 2.0");
        assertEquals(1, snap.hitCount());
        assertEquals(DamageState.CRITICAL, snap.damageState(), "2.0 >= 1.0 -> CRITICAL");
    }
}
