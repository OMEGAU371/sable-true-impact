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
