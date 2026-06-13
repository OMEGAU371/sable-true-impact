package io.github.omegau371.trueimpact.command;

import io.github.omegau371.trueimpact.damage.BlockDamageAccumulator;
import io.github.omegau371.trueimpact.damage.DamageState;
import io.github.omegau371.trueimpact.damage.DeferredDamageEvent;
import io.github.omegau371.trueimpact.damage.DeferredDamageQueue;
import io.github.omegau371.trueimpact.damage.MaterialThresholdProfile;
import io.github.omegau371.trueimpact.damage.VictimInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DamageCommand formatter helpers and damage-clear behavior.
 * No Minecraft server runtime required.
 */
class DamageCommandTest {

    @BeforeEach
    void reset() {
        BlockDamageAccumulator.clear();
        DeferredDamageQueue.clear();
        io.github.omegau371.trueimpact.damage.DamageFeedbackTracker.clear();
    }

    @AfterEach
    void restoreDefaults() {
        io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.ENABLE_DAMAGE_FEEDBACK = true;
    }

    // -- DamageInspectFormatter.formatNoEntry -------------------------------------

    @Test
    void formatNoEntry_includes_level_pos_and_blockId() {
        String result = DamageInspectFormatter.formatNoEntry(
                "minecraft:overworld", 10, 64, -5, "minecraft:stone");
        assertTrue(result.contains("no damage recorded"), "must mention no damage");
        assertTrue(result.contains("level=minecraft:overworld"));
        assertTrue(result.contains("pos=(10,64,-5)"));
        assertTrue(result.contains("block=minecraft:stone"));
    }

    @Test
    void formatNoEntry_shows_clean_message_when_block_is_air() {
        String result = DamageInspectFormatter.formatNoEntry(
                "minecraft:the_nether", 0, 100, 0, "minecraft:air");
        assertTrue(result.contains("minecraft:air"));
        assertTrue(result.contains("minecraft:the_nether"));
    }

    // -- DamageInspectFormatter.formatEntry ---------------------------------------

    @Test
    void formatEntry_includes_all_required_fields() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, -5, "minecraft:stone");
        BlockDamageAccumulator.Snapshot snap = new BlockDamageAccumulator.Snapshot(
                key,
                MaterialThresholdProfile.MaterialClass.STONE,
                75.0,   // accumulatedEffectiveDamageJ
                50.0,   // thresholdJ
                90.0,   // lastRawImpactJ
                75.0,   // lastEffectiveDamageJ
                42L,    // lastUpdatedTick
                3,      // hitCount
                DamageState.CRACKED);

        String result = DamageInspectFormatter.formatEntry(snap);
        assertTrue(result.contains("[TI damage inspect]"));
        assertTrue(result.contains("block=minecraft:stone"));
        assertTrue(result.contains("pos=(10,64,-5)"));
        assertTrue(result.contains("class=STONE"));
        assertTrue(result.contains("threshold=50.000J"));
        assertTrue(result.contains("accumEff=75.000J"));
        assertTrue(result.contains("rawLast=90.000J"));
        assertTrue(result.contains("effLast=75.000J"));
        assertTrue(result.contains("ratio=1.500"));
        assertTrue(result.contains("state=CRACKED"));
        assertTrue(result.contains("hits=3"));
        assertTrue(result.contains("lastTick=42"));
    }

    // -- clear semantics ----------------------------------------------------------

    @Test
    void clear_clears_accumulator_without_resetting_queue_counters() {
        // Enqueue and drain so queue counters are non-zero
        DeferredDamageEvent ev = new DeferredDamageEvent(
                1L, "minecraft:overworld", "minecraft:stone", 10, 64, 10,
                MaterialThresholdProfile.MaterialClass.STONE, 80.0, 50.0,
                VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);
        DeferredDamageQueue.enqueue(ev);
        DeferredDamageQueue.drainAll();
        BlockDamageAccumulator.accumulate(ev);

        long enqueuedBefore = DeferredDamageQueue.stats().totalEnqueued();
        long flushedBefore  = DeferredDamageQueue.stats().totalFlushed();
        assertTrue(enqueuedBefore > 0);
        assertTrue(flushedBefore  > 0);
        assertEquals(1, BlockDamageAccumulator.entryCount());

        // /trueimpact damage clear calls BlockDamageAccumulator.clear() only
        BlockDamageAccumulator.clear();

        assertEquals(0, BlockDamageAccumulator.entryCount(),
                "accumulator must be empty after clear");
        assertNull(BlockDamageAccumulator.lastUpdatedSnapshot(),
                "lastUpdatedSnapshot must be null after clear");
        assertEquals(enqueuedBefore, DeferredDamageQueue.stats().totalEnqueued(),
                "queue totalEnqueued must NOT be reset by damage clear");
        assertEquals(flushedBefore, DeferredDamageQueue.stats().totalFlushed(),
                "queue totalFlushed must NOT be reset by damage clear");
    }

    // -- inspect last path --------------------------------------------------------

    @Test
    void inspect_last_with_empty_accumulator_returns_null_snapshot() {
        // No accumulations: lastUpdatedSnapshot() must be null.
        // The command would show "no damage recorded yet".
        assertNull(BlockDamageAccumulator.lastUpdatedSnapshot(),
                "lastUpdatedSnapshot must be null when accumulator is empty");
    }

    @Test
    void inspect_last_after_single_accumulate_returns_that_entry() {
        DeferredDamageEvent ev = new DeferredDamageEvent(
                1L, "minecraft:overworld", "minecraft:stone", 10, 64, 10,
                MaterialThresholdProfile.MaterialClass.STONE, 60.0, 50.0,
                VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);
        BlockDamageAccumulator.accumulate(ev);

        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.lastUpdatedSnapshot();
        assertNotNull(snap, "lastUpdatedSnapshot must be non-null after accumulate");

        String formatted = DamageInspectFormatter.formatEntry(snap);
        assertTrue(formatted.contains("[TI damage inspect]"), "must use inspect format prefix");
        assertTrue(formatted.contains("minecraft:stone"), "must contain block id");
        assertTrue(formatted.contains("hits=1"), "must show hit count");
        assertTrue(formatted.contains("class=STONE"), "must show material class");
    }

    @Test
    void inspect_last_after_multiple_accumulates_shows_most_recent() {
        DeferredDamageEvent ev1 = new DeferredDamageEvent(
                1L, "minecraft:overworld", "minecraft:stone", 10, 64, 10,
                MaterialThresholdProfile.MaterialClass.STONE, 60.0, 50.0,
                VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);
        DeferredDamageEvent ev2 = new DeferredDamageEvent(
                2L, "minecraft:overworld", "minecraft:grass_block", 20, 64, 20,
                MaterialThresholdProfile.MaterialClass.SOFT_SOIL, 10.0, 5.0,
                VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);

        BlockDamageAccumulator.accumulate(ev1);
        BlockDamageAccumulator.accumulate(ev2);

        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.lastUpdatedSnapshot();
        assertNotNull(snap);
        assertEquals("minecraft:grass_block", snap.key().victimBlock(),
                "lastUpdatedSnapshot must reflect the most recent accumulate call");
        assertEquals(20, snap.key().posX(),
                "position must be from the most recent event");

        String formatted = DamageInspectFormatter.formatEntry(snap);
        assertTrue(formatted.contains("minecraft:grass_block"));
        assertTrue(formatted.contains("pos=(20,64,20)"));
    }

    @Test
    void inspect_last_formatted_output_contains_all_key_fields() {
        DeferredDamageEvent ev = new DeferredDamageEvent(
                42L, "minecraft:overworld", "minecraft:andesite", 5, 63, -3,
                MaterialThresholdProfile.MaterialClass.STONE, 55.965, 50.0,
                VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);
        BlockDamageAccumulator.accumulate(ev);

        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.lastUpdatedSnapshot();
        assertNotNull(snap);
        String formatted = DamageInspectFormatter.formatEntry(snap);

        // All fields required by the command spec must be present
        assertTrue(formatted.contains("block=minecraft:andesite"), "block id");
        assertTrue(formatted.contains("pos=(5,63,-3)"), "position");
        assertTrue(formatted.contains("class=STONE"), "material class");
        assertTrue(formatted.contains("threshold=50.000J"), "threshold");
        assertTrue(formatted.contains("rawLast=55.965J"), "raw last impact");
        assertTrue(formatted.contains("effLast=55.965J"), "eff last (below cap -> equals raw)");
        assertTrue(formatted.contains("accumEff=55.965J"), "accumulated effective");
        assertTrue(formatted.contains("state=CRITICAL"), "damage state (ratio=1.12 >= 1.0)");
        assertTrue(formatted.contains("hits=1"), "hit count");
    }

    @Test
    void non_SOFT_SOIL_CRITICAL_returns_feedback_flag_with_no_block_mutation() {
        // Phase 2D: DamageFeedbackTracker is a boolean gate only. It has no setBlock.
        // Actual particle emission happens in TrueImpactMod.emitDamageFeedback(),
        // which also does not call setBlock. Full no-setBlock guarantee for STONE
        // materials is verified in ImpactBlockApplicatorTest.
        io.github.omegau371.trueimpact.damage.DamageFeedbackTracker.clear();
        boolean emit = io.github.omegau371.trueimpact.damage.DamageFeedbackTracker
                .shouldEmit(10, 64, 10, DamageState.CRITICAL, 1L);
        assertTrue(emit, "CRITICAL state must trigger feedback regardless of material class");
        assertEquals(1, io.github.omegau371.trueimpact.damage.DamageFeedbackTracker
                .currentTickFeedbackCount(), "exactly one budget slot consumed");
    }
}
