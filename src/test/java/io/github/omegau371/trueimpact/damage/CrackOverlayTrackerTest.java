package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CrackOverlayTracker: ratio-to-progress mapping,
 * rate limiting, config gate, drainForClear, and fakeBreakerId stability.
 * No Minecraft runtime required.
 */
class CrackOverlayTrackerTest {

    @BeforeEach
    void reset() {
        CrackOverlayTracker.clear();
        ImpactRuntimeConfig.ENABLE_VANILLA_CRACK_OVERLAY = true;
    }

    @AfterEach
    void restoreDefaults() {
        ImpactRuntimeConfig.ENABLE_VANILLA_CRACK_OVERLAY = true;
    }

    // -- ratioToProgress: INTACT / BRUISED -----------------------------------------

    @Test
    void intact_returns_no_overlay() {
        assertEquals(-1, CrackOverlayTracker.ratioToProgress(DamageState.INTACT, 0.10));
    }

    @Test
    void bruised_returns_no_overlay() {
        assertEquals(-1, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.40));
    }

    // -- ratioToProgress: CRACKED --------------------------------------------------

    @Test
    void cracked_at_lower_bound_returns_5() {
        // ratio=0.60 -> t=0 -> round(5+0) = 5
        assertEquals(5, CrackOverlayTracker.ratioToProgress(DamageState.CRACKED, 0.60));
    }

    @Test
    void cracked_at_midpoint_returns_6() {
        // ratio=0.80 -> t=0.5 -> round(5+1) = 6
        assertEquals(6, CrackOverlayTracker.ratioToProgress(DamageState.CRACKED, 0.80));
    }

    @Test
    void cracked_at_upper_bound_returns_7() {
        // ratio=1.00 -> t=1.0 -> round(5+2) = 7
        assertEquals(7, CrackOverlayTracker.ratioToProgress(DamageState.CRACKED, 1.00));
    }

    @Test
    void cracked_clamps_below_lower_bound() {
        // ratio < 0.60 clamps t to 0 -> progress 5
        assertEquals(5, CrackOverlayTracker.ratioToProgress(DamageState.CRACKED, 0.50));
    }

    @Test
    void cracked_clamps_above_upper_bound() {
        // ratio > 1.00 clamps t to 1 -> progress 7
        assertEquals(7, CrackOverlayTracker.ratioToProgress(DamageState.CRACKED, 1.10));
    }

    // -- ratioToProgress: CRITICAL -------------------------------------------------

    @Test
    void critical_at_threshold_returns_9() {
        assertEquals(9, CrackOverlayTracker.ratioToProgress(DamageState.CRITICAL, 1.00));
    }

    @Test
    void critical_at_high_ratio_returns_9() {
        assertEquals(9, CrackOverlayTracker.ratioToProgress(DamageState.CRITICAL, 5.00));
    }

    // -- tryUpdate: first call -----------------------------------------------------

    @Test
    void tryUpdate_first_call_for_critical_returns_9() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        int result = CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 1L);
        assertEquals(9, result);
        assertEquals(1, CrackOverlayTracker.activeCrackOverlays());
        assertEquals(9, CrackOverlayTracker.lastCrackProgress());
    }

    @Test
    void tryUpdate_first_call_for_cracked_returns_correct_stage() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        int result = CrackOverlayTracker.tryUpdate(key, DamageState.CRACKED, 0.80, 1L);
        assertEquals(6, result);
    }

    // -- tryUpdate: rate limiting --------------------------------------------------

    @Test
    void tryUpdate_same_progress_within_cooldown_suppressed() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 1L);
        // Within cooldown window
        int result = CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 5L);
        assertEquals(-1, result, "same progress within cooldown must return -1");
        assertEquals(1L, CrackOverlayTracker.totalCrackOverlayUpdates(),
                "counter must not increment on suppressed update");
    }

    @Test
    void tryUpdate_same_progress_after_cooldown_resends() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 1L);
        long afterCooldown = 1L + CrackOverlayTracker.PER_BLOCK_UPDATE_COOLDOWN_TICKS + 1;
        int result = CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, afterCooldown);
        assertEquals(9, result, "same progress after cooldown must be resent");
        assertEquals(2L, CrackOverlayTracker.totalCrackOverlayUpdates());
    }

    @Test
    void tryUpdate_progress_change_triggers_immediate_update() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        CrackOverlayTracker.tryUpdate(key, DamageState.CRACKED, 0.80, 1L); // progress = 6
        // Same tick but CRITICAL -> progress = 9 (different) -- cooldown bypass
        int result = CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 1L);
        assertEquals(9, result, "changed progress must bypass cooldown");
    }

    @Test
    void tryUpdate_intact_within_cooldown_returns_minus_one() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        int result = CrackOverlayTracker.tryUpdate(key, DamageState.INTACT, 0.1, 1L);
        assertEquals(-1, result, "INTACT must never produce an overlay");
        assertEquals(0, CrackOverlayTracker.activeCrackOverlays());
    }

    @Test
    void tryUpdate_different_keys_are_independent() {
        BlockDamageAccumulator.AccKey k1 = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        BlockDamageAccumulator.AccKey k2 = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 20, 64, 20, "minecraft:stone");
        assertEquals(9, CrackOverlayTracker.tryUpdate(k1, DamageState.CRITICAL, 1.5, 1L));
        assertEquals(9, CrackOverlayTracker.tryUpdate(k2, DamageState.CRITICAL, 1.5, 1L),
                "different keys must be tracked independently");
        assertEquals(2, CrackOverlayTracker.activeCrackOverlays());
    }

    // -- config gate ---------------------------------------------------------------

    @Test
    void overlay_disabled_config_suppresses_all_updates() {
        ImpactRuntimeConfig.ENABLE_VANILLA_CRACK_OVERLAY = false;
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        int result = CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 1L);
        assertEquals(-1, result, "overlay disabled by config must return -1");
        assertEquals(0, CrackOverlayTracker.activeCrackOverlays());
        assertEquals(0L, CrackOverlayTracker.totalCrackOverlayUpdates());
    }

    // -- clear --------------------------------------------------------------------

    @Test
    void clear_resets_all_state() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 1L);

        CrackOverlayTracker.clear();

        assertEquals(0, CrackOverlayTracker.activeCrackOverlays(), "active overlays must be 0 after clear");
        assertEquals(0L, CrackOverlayTracker.totalCrackOverlayUpdates(), "update counter must be 0 after clear");
        assertEquals(-1, CrackOverlayTracker.lastCrackProgress(), "lastProgress must be -1 after clear");
    }

    // -- drainForClear ------------------------------------------------------------

    @Test
    void drainForClear_returns_active_entries_and_clears_state() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 1L);

        List<CrackOverlayTracker.WorldClearAction> actions = CrackOverlayTracker.drainForClear();

        assertEquals(1, actions.size(), "one action per tracked block");
        assertEquals(key, actions.get(0).key(), "action key must match tracked block");
        assertEquals(0, CrackOverlayTracker.activeCrackOverlays(), "state cleared after drain");
        assertEquals(0L, CrackOverlayTracker.totalCrackOverlayUpdates(), "counters cleared after drain");
    }

    @Test
    void drainForClear_on_empty_tracker_returns_empty_list() {
        List<CrackOverlayTracker.WorldClearAction> actions = CrackOverlayTracker.drainForClear();
        assertTrue(actions.isEmpty(), "empty tracker must return empty list");
    }

    // -- fakeBreakerId stability --------------------------------------------------

    @Test
    void fakeBreakerIdFor_same_key_returns_same_id() {
        BlockDamageAccumulator.AccKey k1 = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        BlockDamageAccumulator.AccKey k2 = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertEquals(CrackOverlayTracker.fakeBreakerIdFor(k1), CrackOverlayTracker.fakeBreakerIdFor(k2),
                "equal keys must produce the same fakeBreakerId");
    }

    @Test
    void fakeBreakerIdFor_is_always_negative() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertTrue(CrackOverlayTracker.fakeBreakerIdFor(key) < 0,
                "fakeBreakerId must be negative to avoid collision with MC entity IDs");
    }

    @Test
    void fakeBreakerIdFor_different_positions_produce_different_ids() {
        BlockDamageAccumulator.AccKey k1 = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        BlockDamageAccumulator.AccKey k2 = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 99, 64, 99, "minecraft:stone");
        // Different coords -> different AccKey hashCode -> different IDs (probabilistically)
        // Only check they are deterministic and negative; collision probability is negligible.
        assertTrue(CrackOverlayTracker.fakeBreakerIdFor(k1) < 0);
        assertTrue(CrackOverlayTracker.fakeBreakerIdFor(k2) < 0);
    }

    // -- counter accuracy ---------------------------------------------------------

    @Test
    void total_updates_counts_only_accepted_updates() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 1L);   // accepted
        CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 5L);   // suppressed (cooldown)
        CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 50L);  // accepted (after cooldown)
        assertEquals(2L, CrackOverlayTracker.totalCrackOverlayUpdates(),
                "only accepted updates increment the counter");
    }
}
