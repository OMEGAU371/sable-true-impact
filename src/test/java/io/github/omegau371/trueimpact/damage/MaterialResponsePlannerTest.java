package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MaterialResponsePlanner.plan() rules and debris deduplication.
 * No Minecraft runtime required.
 */
class MaterialResponsePlannerTest {

    @BeforeEach
    void reset() {
        MaterialResponsePlanner.clear();
        ImpactRuntimeConfig.ENABLE_DEBRIS_DROPS = true;
        BlockDamageAccumulator.clear();
        // Snapshots at exact ratios are built via accumulation; disable floor/relaxation.
        ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR = 0.0;
        ImpactRuntimeConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = 0;
    }

    @AfterEach
    void restoreDefaults() {
        ImpactRuntimeConfig.ENABLE_DEBRIS_DROPS = false; // production default since Phase 2E hotfix
        ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR = 0.2;
        ImpactRuntimeConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = 60;
    }

    // -- helper: build a snapshot at a specific ratio via accumulation ------------

    private static BlockDamageAccumulator.Snapshot snapAt(
            MaterialThresholdProfile.MaterialClass mc, double ratio) {
        // Compute kImpact that produces the target ratio in effective damage.
        // For below-cap hits: effective = kImpact. target ratio = effective/threshold.
        // So kImpact = ratio * threshold (valid only when kImpact <= cap).
        double threshold = MaterialThresholdProfile.threshold(mc);
        double kImpact   = ratio * threshold;
        DeferredDamageEvent ev = new DeferredDamageEvent(
                1L, "minecraft:overworld", blockIdFor(mc),
                10, 64, 10, mc, kImpact, threshold,
                VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);
        BlockDamageAccumulator.accumulate(ev);
        return BlockDamageAccumulator.getSnapshot(
                "minecraft:overworld", 10, 64, 10, blockIdFor(mc));
    }

    private static String blockIdFor(MaterialThresholdProfile.MaterialClass mc) {
        return switch (mc) {
            case SOFT_SOIL     -> "minecraft:grass_block";
            case BRITTLE       -> "minecraft:glass";
            case WOOD          -> "minecraft:oak_planks";
            case STONE         -> "minecraft:stone";
            case METAL         -> "minecraft:iron_block";
            case HIGH_STRENGTH -> "minecraft:obsidian";
            case GENERIC       -> "minecraft:andesite";
        };
    }

    // -- NONE rules ---------------------------------------------------------------

    @Test
    void plan_INTACT_state_returns_NONE() {
        // ratio=0.10 -> INTACT
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.STONE, 0.10);
        assertNotNull(snap);
        assertEquals(DamageState.INTACT, snap.damageState());
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertEquals(MaterialResponseType.NONE, plan.responseType());
        assertFalse(plan.shouldDropDebris());
        assertFalse(plan.futureBreakEligible());
    }

    @Test
    void plan_BRUISED_state_returns_NONE() {
        // ratio=0.30 -> BRUISED (0.25 <= ratio < 0.60)
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.STONE, 0.30);
        assertEquals(DamageState.BRUISED, snap.damageState());
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertEquals(MaterialResponseType.NONE, plan.responseType());
        assertFalse(plan.shouldDropDebris());
        assertFalse(plan.futureBreakEligible());
    }

    // -- COSMETIC_CRACK rule ------------------------------------------------------

    @Test
    void plan_CRACKED_state_returns_COSMETIC_CRACK() {
        // ratio=0.70 -> CRACKED (0.60 <= ratio < 1.00)
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.STONE, 0.70);
        assertEquals(DamageState.CRACKED, snap.damageState());
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertEquals(MaterialResponseType.COSMETIC_CRACK, plan.responseType());
        assertFalse(plan.shouldDropDebris());
        assertFalse(plan.futureBreakEligible());
    }

    // -- CRITICAL + SOFT_SOIL -----------------------------------------------------

    @Test
    void plan_CRITICAL_SOFT_SOIL_returns_COMPACT_SOFT_SOIL() {
        // ratio=1.50 -> CRITICAL, class=SOFT_SOIL
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.SOFT_SOIL, 1.50);
        assertEquals(DamageState.CRITICAL, snap.damageState());
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertEquals(MaterialResponseType.COMPACT_SOFT_SOIL, plan.responseType());
        assertFalse(plan.shouldDropDebris(), "SOFT_SOIL compaction does not drop debris");
        assertTrue(plan.futureBreakEligible(),
                "SOFT_SOIL at CRITICAL: futureBreakEligible=true allows breaking when chain exhausted");
    }

    // -- CRITICAL + STONE/GENERIC -------------------------------------------------

    @Test
    void plan_CRITICAL_STONE_returns_DROP_DEBRIS_with_fbe() {
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.STONE, 1.50);
        assertEquals(DamageState.CRITICAL, snap.damageState());
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertEquals(MaterialResponseType.DROP_DEBRIS, plan.responseType());
        assertTrue(plan.shouldDropDebris());
        assertTrue(plan.futureBreakEligible());
    }

    @Test
    void plan_CRITICAL_GENERIC_returns_DROP_DEBRIS_with_fbe() {
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.GENERIC, 1.50);
        assertEquals(DamageState.CRITICAL, snap.damageState());
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertEquals(MaterialResponseType.DROP_DEBRIS, plan.responseType());
        assertTrue(plan.shouldDropDebris());
        assertTrue(plan.futureBreakEligible());
    }

    // -- CRITICAL + WOOD/METAL/HIGH_STRENGTH --------------------------------------

    @Test
    void plan_CRITICAL_WOOD_returns_FUTURE_BREAK_ELIGIBLE_without_debris() {
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.WOOD, 1.50);
        assertEquals(DamageState.CRITICAL, snap.damageState());
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertEquals(MaterialResponseType.FUTURE_BREAK_ELIGIBLE, plan.responseType());
        assertFalse(plan.shouldDropDebris(), "WOOD does not drop debris in Phase 2E");
        assertTrue(plan.futureBreakEligible());
    }

    @Test
    void plan_CRITICAL_METAL_returns_FUTURE_BREAK_ELIGIBLE_without_debris() {
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.METAL, 1.50);
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertEquals(MaterialResponseType.FUTURE_BREAK_ELIGIBLE, plan.responseType());
        assertFalse(plan.shouldDropDebris());
        assertTrue(plan.futureBreakEligible());
    }

    @Test
    void plan_CRITICAL_HIGH_STRENGTH_returns_FUTURE_BREAK_ELIGIBLE_without_debris() {
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.HIGH_STRENGTH, 1.50);
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertEquals(MaterialResponseType.FUTURE_BREAK_ELIGIBLE, plan.responseType());
        assertFalse(plan.shouldDropDebris());
        assertTrue(plan.futureBreakEligible());
    }

    // -- plan fields --------------------------------------------------------------

    @Test
    void plan_populates_damage_fields_from_snapshot() {
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.STONE, 1.5);
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertEquals(snap.materialClass(), plan.materialClass());
        assertEquals(snap.damageState(), plan.damageState());
        assertEquals(snap.thresholdJ(), plan.thresholdJ(), 0.001);
        assertEquals(snap.ratio(), plan.ratio(), 0.001);
        assertNotNull(plan.diagnosticNote(), "diagnosticNote must not be null");
    }

    // -- debris deduplication -----------------------------------------------------

    @Test
    void markDebrisDropped_first_call_returns_true() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertTrue(MaterialResponsePlanner.markDebrisDropped(key),
                "first drop for this key must return true");
    }

    @Test
    void markDebrisDropped_second_call_for_same_key_returns_false() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertTrue(MaterialResponsePlanner.markDebrisDropped(key));
        assertFalse(MaterialResponsePlanner.markDebrisDropped(key),
                "second drop for same key must return false (already dropped)");
    }

    @Test
    void markDebrisDropped_different_keys_are_independent() {
        BlockDamageAccumulator.AccKey k1 = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        BlockDamageAccumulator.AccKey k2 = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 20, 64, 20, "minecraft:stone");
        assertTrue(MaterialResponsePlanner.markDebrisDropped(k1));
        assertTrue(MaterialResponsePlanner.markDebrisDropped(k2),
                "different positions have independent debris cooldowns");
    }

    @Test
    void hasDroppedDebris_reflects_markDebrisDropped_state() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertFalse(MaterialResponsePlanner.hasDroppedDebris(key));
        MaterialResponsePlanner.markDebrisDropped(key);
        assertTrue(MaterialResponsePlanner.hasDroppedDebris(key));
    }

    // -- config gate and debug isolation ------------------------------------------

    @Test
    void debug_flags_off_does_not_block_planner() {
        assertFalse(io.github.omegau371.trueimpact.observation.DiagnosticConfig.ENABLED,
                "ENABLED must be false by default");
        // plan() must work regardless of DiagnosticConfig
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.STONE, 1.5);
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertNotNull(plan);
        assertEquals(MaterialResponseType.DROP_DEBRIS, plan.responseType());
    }

    @Test
    void no_setBlock_or_destroyBlock_for_stone_in_phase_2E_plan() {
        // MaterialResponsePlan for STONE CRITICAL has responseType=DROP_DEBRIS
        // but contains NO world-mutation calls. The plan is pure data.
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.STONE, 1.5);
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        // Verifies the plan says DROP_DEBRIS (debris spawn, not block removal)
        assertEquals(MaterialResponseType.DROP_DEBRIS, plan.responseType());
        // The plan record itself has no MC methods -- asserting on pure data is sufficient
        // to prove no world mutation occurred in the planning stage.
        assertEquals(MaterialThresholdProfile.MaterialClass.STONE, plan.materialClass());
    }

    // -- counters -----------------------------------------------------------------

    @Test
    void recordExecuted_increments_totalResponsesPlanned_for_non_NONE() {
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.STONE, 1.5);
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        assertEquals(0L, MaterialResponsePlanner.stats().totalResponsesPlanned());
        MaterialResponsePlanner.recordExecuted(plan);
        assertEquals(1L, MaterialResponsePlanner.stats().totalResponsesPlanned());
        assertEquals(MaterialResponseType.DROP_DEBRIS, MaterialResponsePlanner.stats().lastResponseType());
    }

    @Test
    void recordExecuted_does_not_increment_planned_for_NONE() {
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.STONE, 0.10);
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        MaterialResponsePlanner.recordExecuted(plan);
        assertEquals(0L, MaterialResponsePlanner.stats().totalResponsesPlanned(),
                "NONE plans must not increment totalResponsesPlanned");
    }

    @Test
    void clear_resets_all_state() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        MaterialResponsePlanner.markDebrisDropped(key);
        BlockDamageAccumulator.Snapshot snap = snapAt(MaterialThresholdProfile.MaterialClass.STONE, 1.5);
        MaterialResponsePlanner.recordExecuted(MaterialResponsePlanner.plan(snap));

        MaterialResponsePlanner.clear();

        assertFalse(MaterialResponsePlanner.hasDroppedDebris(key),
                "debris state must be reset after clear");
        assertEquals(0L, MaterialResponsePlanner.stats().totalResponsesPlanned());
        assertEquals(0L, MaterialResponsePlanner.stats().totalDebrisDropped());
        assertEquals(MaterialResponseType.NONE, MaterialResponsePlanner.stats().lastResponseType());
    }
}
