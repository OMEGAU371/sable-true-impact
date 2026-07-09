package io.github.omegau371.trueimpact.command;

import io.github.omegau371.trueimpact.damage.BlockDamageAccumulator;
import io.github.omegau371.trueimpact.damage.DamageState;
import io.github.omegau371.trueimpact.damage.DeferredDamageEvent;
import io.github.omegau371.trueimpact.damage.DeferredDamageQueue;
import io.github.omegau371.trueimpact.damage.MaterialResponsePlan;
import io.github.omegau371.trueimpact.damage.MaterialResponseType;
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
        io.github.omegau371.trueimpact.damage.CrackOverlayTracker.clear();
    }

    @AfterEach
    void restoreDefaults() {
        io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.ENABLE_DAMAGE_FEEDBACK = true;
        io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION = true;
        io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.ENABLE_DEBRIS_DROPS = false;
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

        // All fields required by the command spec must be present.
        // kImpact=55.965 > threshold=50.0 → effective=50.0 (capped). rawLast preserved.
        assertTrue(formatted.contains("block=minecraft:andesite"), "block id");
        assertTrue(formatted.contains("pos=(5,63,-3)"), "position");
        assertTrue(formatted.contains("class=STONE"), "material class");
        assertTrue(formatted.contains("threshold=50.000J"), "threshold");
        assertTrue(formatted.contains("rawLast=55.965J"), "raw last impact preserved even when capped");
        assertTrue(formatted.contains("effLast=50.000J"), "effLast=min(55.965,threshold=50)=50");
        assertTrue(formatted.contains("accumEff=50.000J"), "accumEff=50 (capped to threshold)");
        assertTrue(formatted.contains("state=CRITICAL"), "damage state (ratio=50/50=1.0 >= 1.0)");
        assertTrue(formatted.contains("hits=1"), "hit count");
    }

    // -- DamageInspectFormatter.formatPlan ----------------------------------------

    @Test
    void formatPlan_DROP_DEBRIS_with_debris_already_dropped() {
        MaterialResponsePlan plan = new MaterialResponsePlan(
                MaterialResponseType.DROP_DEBRIS,
                MaterialThresholdProfile.MaterialClass.STONE,
                DamageState.CRITICAL,
                100.0, 100.0, 50.0, 2.0,
                true, true,
                "debris eligible; block preserved in Phase 2E");
        String result = DamageInspectFormatter.formatPlan(plan, true);
        assertTrue(result.contains("[TI damage plan]"));
        assertTrue(result.contains("response=DROP_DEBRIS"));
        assertTrue(result.contains("fbe=true"));
        assertTrue(result.contains("debris=true"));
        assertTrue(result.contains("crack=9"), "CRITICAL must show crack progress 9");
        assertTrue(result.contains("note="));
    }

    @Test
    void formatPlan_NONE_shows_false_flags_and_no_overlay() {
        MaterialResponsePlan plan = new MaterialResponsePlan(
                MaterialResponseType.NONE,
                MaterialThresholdProfile.MaterialClass.STONE,
                DamageState.INTACT,
                20.0, 20.0, 50.0, 0.4,
                false, false,
                "below crack threshold (ratio=0.400)");
        String result = DamageInspectFormatter.formatPlan(plan, false);
        assertTrue(result.contains("response=NONE"));
        assertTrue(result.contains("fbe=false"));
        assertTrue(result.contains("debris=false"));
        assertTrue(result.contains("crack=-1"), "INTACT must show crack=-1 (no overlay)");
    }

    @Test
    void formatPlan_FUTURE_BREAK_ELIGIBLE_debris_not_yet_dropped() {
        MaterialResponsePlan plan = new MaterialResponsePlan(
                MaterialResponseType.FUTURE_BREAK_ELIGIBLE,
                MaterialThresholdProfile.MaterialClass.WOOD,
                DamageState.CRITICAL,
                60.0, 60.0, 20.0, 3.0,
                false, true,
                "future break eligible (WOOD); no action in Phase 2E");
        String result = DamageInspectFormatter.formatPlan(plan, false);
        assertTrue(result.contains("response=FUTURE_BREAK_ELIGIBLE"));
        assertTrue(result.contains("fbe=true"));
        assertTrue(result.contains("debris=false"),
                "debris=false when markDebrisDropped has not been called for this block");
        assertTrue(result.contains("crack=9"), "CRITICAL WOOD must show crack=9");
    }

    @Test
    void formatPlan_CRACKED_shows_mid_crack_stage() {
        MaterialResponsePlan plan = new MaterialResponsePlan(
                MaterialResponseType.COSMETIC_CRACK,
                MaterialThresholdProfile.MaterialClass.STONE,
                DamageState.CRACKED,
                40.0, 40.0, 50.0, 0.80,
                false, false,
                "cosmetic crack (ratio=0.800)");
        String result = DamageInspectFormatter.formatPlan(plan, false);
        // ratio=0.80 -> 0.80..0.90 band -> progress=7
        assertTrue(result.contains("crack=7"), "CRACKED at ratio=0.80 must show crack=7");
    }

    @Test
    void debris_disabled_by_default_and_planner_still_returns_drop_debris_for_stone() {
        // Phase 2E hotfix: ENABLE_DEBRIS_DROPS=false disables spawn; plan is unchanged.
        io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.ENABLE_DEBRIS_DROPS = false;
        io.github.omegau371.trueimpact.damage.DeferredDamageEvent ev =
                new io.github.omegau371.trueimpact.damage.DeferredDamageEvent(
                        1L, "minecraft:overworld", "minecraft:stone", 10, 64, 10,
                        MaterialThresholdProfile.MaterialClass.STONE, 100.0, 50.0,
                        VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);
        BlockDamageAccumulator.accumulate(ev);
        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.lastUpdatedSnapshot();
        assertNotNull(snap);
        io.github.omegau371.trueimpact.damage.MaterialResponsePlan plan =
                io.github.omegau371.trueimpact.damage.MaterialResponsePlanner.plan(snap);
        assertEquals(MaterialResponseType.DROP_DEBRIS, plan.responseType(),
                "planner always plans DROP_DEBRIS for STONE CRITICAL regardless of flag");
        assertTrue(plan.shouldDropDebris(), "plan.shouldDropDebris=true even when flag is false");
        assertFalse(io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.ENABLE_DEBRIS_DROPS,
                "ENABLE_DEBRIS_DROPS=false: TrueImpactMod suppresses the ItemEntity spawn");
    }

    @Test
    void soft_soil_plan_never_sets_shouldDropDebris() {
        // SOFT_SOIL at CRITICAL -> COMPACT_SOFT_SOIL; shouldDropDebris must always be false.
        io.github.omegau371.trueimpact.damage.DeferredDamageEvent ev =
                new io.github.omegau371.trueimpact.damage.DeferredDamageEvent(
                        1L, "minecraft:overworld", "minecraft:grass_block", 10, 64, 10,
                        MaterialThresholdProfile.MaterialClass.SOFT_SOIL, 30.0, 5.0,
                        VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);
        BlockDamageAccumulator.accumulate(ev);
        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.lastUpdatedSnapshot();
        assertNotNull(snap);
        io.github.omegau371.trueimpact.damage.MaterialResponsePlan plan =
                io.github.omegau371.trueimpact.damage.MaterialResponsePlanner.plan(snap);
        assertEquals(MaterialResponseType.COMPACT_SOFT_SOIL, plan.responseType());
        assertFalse(plan.shouldDropDebris(), "SOFT_SOIL must never set shouldDropDebris");
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
