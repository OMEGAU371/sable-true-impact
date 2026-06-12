package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlockDamageAccumulator.
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
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 100.0, 1L));

        assertEquals(1, BlockDamageAccumulator.entryCount());
        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(100.0, snap.accumulatedDamageJ(), 0.001);
        assertEquals(100.0, snap.lastImpactJ(), 0.001);
        assertEquals(1, snap.hitCount());
        assertEquals(1L, snap.lastUpdatedTick());
        assertEquals(MaterialThresholdProfile.MaterialClass.STONE, snap.materialClass());
    }

    @Test
    void repeated_hits_on_same_block_accumulate_damage() {
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 60.0, 1L));
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 80.0, 2L));
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 50.0, 3L));

        assertEquals(1, BlockDamageAccumulator.entryCount(), "same block/pos = same entry");
        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(190.0, snap.accumulatedDamageJ(), 0.001, "60+80+50");
        assertEquals(50.0, snap.lastImpactJ(), 0.001, "last hit wins");
        assertEquals(3, snap.hitCount());
        assertEquals(3L, snap.lastUpdatedTick());
    }

    @Test
    void different_block_ids_at_same_pos_do_not_share_damage() {
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
        assertEquals(100.0, stone.accumulatedDamageJ(), 0.001, "stone entry unchanged");
        assertEquals(20.0, grass.accumulatedDamageJ(), 0.001, "grass entry independent");
    }

    @Test
    void different_dimensions_do_not_share_damage() {
        DeferredDamageEvent ow = event("minecraft:overworld", "minecraft:stone",
                MaterialThresholdProfile.MaterialClass.STONE, 10, 64, 10, 100.0, 1L);
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
        assertEquals(100.0, owSnap.accumulatedDamageJ(), 0.001);
        assertEquals(80.0, neSnap.accumulatedDamageJ(), 0.001);
    }

    @Test
    void stale_event_for_different_block_at_same_pos_does_not_corrupt_existing_entry() {
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 200.0, 1L));
        double stoneDamage = BlockDamageAccumulator
                .getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone").accumulatedDamageJ();

        // Stale event: grass_block was at this pos at enqueue time, now stone occupies it.
        // victimBlock differs, so this creates a separate entry -- no contamination.
        BlockDamageAccumulator.accumulate(grassAt(10, 64, 10, 15.0, 2L));

        BlockDamageAccumulator.Snapshot stone =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertEquals(stoneDamage, stone.accumulatedDamageJ(), 0.001,
                "stone entry must not be contaminated by stale grass_block event");
        assertEquals(2, BlockDamageAccumulator.entryCount(),
                "stale event creates its own separate entry, not corrupting stone entry");
    }

    @Test
    void soft_soil_accumulates_damage() {
        DeferredDamageEvent ev = event("minecraft:overworld", "minecraft:grass_block",
                MaterialThresholdProfile.MaterialClass.SOFT_SOIL, 5, 63, 5, 100.0, 1L);
        BlockDamageAccumulator.accumulate(ev);

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 5, 63, 5, "minecraft:grass_block");
        assertNotNull(snap, "SOFT_SOIL events must be accumulated");
        assertEquals(100.0, snap.accumulatedDamageJ(), 0.001);
        assertEquals(MaterialThresholdProfile.MaterialClass.SOFT_SOIL, snap.materialClass());
    }

    @Test
    void stone_accumulates_but_does_not_mutate_in_phase_2b() {
        // Phase 2B: STONE accumulates damage but ImpactBlockApplicator skips it (SKIP_MATERIAL_CLASS).
        // The accumulator entry exists and ratio is computable.
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 300.0, 1L));

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap, "STONE class must accumulate in Phase 2B");
        assertEquals(300.0, snap.accumulatedDamageJ(), 0.001);
        assertTrue(Double.isFinite(snap.ratio()) && snap.ratio() > 0,
                "ratio must be positive (300J / 50J threshold)");
        assertEquals(MaterialThresholdProfile.MaterialClass.STONE, snap.materialClass());
    }

    @Test
    void debug_flags_off_still_accumulates() {
        // DiagnosticConfig.ENABLED defaults to false; accumulate() must run unconditionally.
        assertFalse(io.github.omegau371.trueimpact.observation.DiagnosticConfig.ENABLED,
                "ENABLED must be false by default");
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 100.0, 1L));
        assertEquals(1, BlockDamageAccumulator.entryCount(),
                "accumulate must work regardless of DiagnosticConfig.ENABLED");
    }

    // -- clear --------------------------------------------------------------------

    @Test
    void clear_resets_all_state() {
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 100.0, 1L));
        BlockDamageAccumulator.clear();

        assertEquals(0, BlockDamageAccumulator.entryCount());
        assertNull(BlockDamageAccumulator.lastUpdatedSnapshot());
        assertNull(BlockDamageAccumulator.getSnapshot(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone"));
    }

    // -- ratio and snapshot -------------------------------------------------------

    @Test
    void ratio_calculation_reflects_threshold() {
        // STONE threshold = 50.0; accumulate 75.0 -> ratio = 1.5
        BlockDamageAccumulator.accumulate(stoneAt(10, 64, 10, 75.0, 1L));
        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        assertEquals(50.0, snap.thresholdJ(), 0.001);
        assertEquals(75.0, snap.accumulatedDamageJ(), 0.001);
        assertEquals(1.5, snap.ratio(), 0.001, "75/50 = 1.5");
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
}
