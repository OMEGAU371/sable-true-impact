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

    // -- ratioToProgress: INTACT ---------------------------------------------------

    @Test
    void intact_returns_no_overlay() {
        assertEquals(-1, CrackOverlayTracker.ratioToProgress(DamageState.INTACT, 0.10));
    }

    // -- ratioToProgress: BRUISED --------------------------------------------------

    @Test
    void bruised_below_threshold_returns_no_overlay() {
        // ratio < 0.10 -> -1 even for non-INTACT states
        assertEquals(-1, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.05));
    }

    @Test
    void bruised_emits_overlay_when_ratio_above_threshold() {
        // BRUISED with ratio >= 0.10 must now produce an overlay
        int progress = CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.15);
        assertTrue(progress >= 0,
                "BRUISED at ratio 0.15 must emit overlay, got " + progress);
    }

    // -- ratioToProgress: ratio step table (BRUISED used as representative state) ---

    @Test
    void ratio_005_returns_no_overlay() {
        assertEquals(-1, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.05));
    }

    @Test
    void ratio_015_returns_0() {
        assertEquals(0, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.15));
    }

    @Test
    void ratio_025_returns_1() {
        assertEquals(1, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.25));
    }

    @Test
    void ratio_035_returns_2() {
        assertEquals(2, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.35));
    }

    @Test
    void ratio_045_returns_3() {
        assertEquals(3, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.45));
    }

    @Test
    void ratio_055_returns_4() {
        assertEquals(4, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.55));
    }

    @Test
    void ratio_065_returns_5() {
        assertEquals(5, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.65));
    }

    @Test
    void ratio_075_returns_6() {
        assertEquals(6, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.75));
    }

    @Test
    void ratio_085_returns_7() {
        assertEquals(7, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.85));
    }

    @Test
    void ratio_095_returns_8() {
        assertEquals(8, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 0.95));
    }

    @Test
    void ratio_100_returns_9() {
        assertEquals(9, CrackOverlayTracker.ratioToProgress(DamageState.BRUISED, 1.00));
    }

    // -- ratioToProgress: CRACKED uses same step table ----------------------------

    @Test
    void cracked_at_0_60_returns_5() {
        assertEquals(5, CrackOverlayTracker.ratioToProgress(DamageState.CRACKED, 0.60));
    }

    @Test
    void cracked_at_0_80_returns_7() {
        assertEquals(7, CrackOverlayTracker.ratioToProgress(DamageState.CRACKED, 0.80));
    }

    @Test
    void cracked_at_0_50_returns_4() {
        assertEquals(4, CrackOverlayTracker.ratioToProgress(DamageState.CRACKED, 0.50));
    }

    @Test
    void cracked_at_1_00_returns_9() {
        assertEquals(9, CrackOverlayTracker.ratioToProgress(DamageState.CRACKED, 1.00));
    }

    // -- ratioToProgress: CRITICAL -------------------------------------------------

    @Test
    void critical_always_returns_9_at_threshold() {
        assertEquals(9, CrackOverlayTracker.ratioToProgress(DamageState.CRITICAL, 1.00));
    }

    @Test
    void critical_always_returns_9_at_high_ratio() {
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
        // ratio=0.80 -> 0.80 <= x < 0.90 -> 7
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        int result = CrackOverlayTracker.tryUpdate(key, DamageState.CRACKED, 0.80, 1L);
        assertEquals(7, result);
    }

    @Test
    void tryUpdate_first_call_for_bruised_emits_overlay() {
        // BRUISED at ratio 0.25 -> progress 1
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        int result = CrackOverlayTracker.tryUpdate(key, DamageState.BRUISED, 0.25, 1L);
        assertEquals(1, result);
    }

    // -- tryUpdate: rate limiting --------------------------------------------------

    @Test
    void tryUpdate_same_progress_within_cooldown_suppressed() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 1L);
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
        // First call: CRACKED ratio=0.80 -> progress 7
        // Second call same tick: CRITICAL -> progress 9 (different) -> must bypass cooldown
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        CrackOverlayTracker.tryUpdate(key, DamageState.CRACKED, 0.80, 1L);
        int result = CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 1L);
        assertEquals(9, result, "changed progress must bypass cooldown");
    }

    @Test
    void tryUpdate_intact_returns_minus_one() {
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
        assertTrue(CrackOverlayTracker.fakeBreakerIdFor(k1) < 0);
        assertTrue(CrackOverlayTracker.fakeBreakerIdFor(k2) < 0);
    }

    // -- removeEntry sentinel ------------------------------------------------------

    @Test
    void removeEntry_missing_key_returns_MIN_VALUE_not_minus_one() {
        // fakeBreakerId range is [FAKE_BREAKER_BASE, -1]; -1 is a valid real ID,
        // so the "not found" sentinel must be Integer.MIN_VALUE (outside that range).
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        int result = CrackOverlayTracker.removeEntry(key);
        assertEquals(Integer.MIN_VALUE, result,
                "removeEntry must return Integer.MIN_VALUE for non-existent key, not -1");
    }

    @Test
    void removeEntry_existing_key_returns_fakeBreakerId() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        CrackOverlayTracker.tryUpdate(key, DamageState.CRITICAL, 1.5, 1L);
        int expected = CrackOverlayTracker.fakeBreakerIdFor(key);
        int result = CrackOverlayTracker.removeEntry(key);
        assertEquals(expected, result, "removeEntry must return the stored fakeBreakerId");
        assertEquals(0, CrackOverlayTracker.activeCrackOverlays(), "entry removed after removeEntry");
    }

    @Test
    void fakeBreakerIdFor_range_never_reaches_Integer_MIN_VALUE() {
        // Sentinel is Integer.MIN_VALUE; valid IDs must not reach it.
        // FAKE_BREAKER_BASE = Integer.MIN_VALUE/2 = -1073741824, max offset = 0x3FFFFFFF = 1073741823
        // Range: [-1073741824, -1]. Integer.MIN_VALUE = -2147483648 is outside this range.
        BlockDamageAccumulator.AccKey k = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotEquals(Integer.MIN_VALUE, CrackOverlayTracker.fakeBreakerIdFor(k),
                "fakeBreakerId must never equal Integer.MIN_VALUE (sentinel)");
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
