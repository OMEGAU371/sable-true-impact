package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DamageFeedbackTracker.shouldEmit() decision logic.
 * No Minecraft runtime required.
 */
class DamageFeedbackTrackerTest {

    @BeforeEach
    void reset() {
        DamageFeedbackTracker.clear();
        ImpactRuntimeConfig.ENABLE_DAMAGE_FEEDBACK = true;
    }

    @AfterEach
    void restoreDefaults() {
        ImpactRuntimeConfig.ENABLE_DAMAGE_FEEDBACK = true;
    }

    // -- state filter -------------------------------------------------------------

    @Test
    void INTACT_does_not_trigger_feedback() {
        assertFalse(DamageFeedbackTracker.shouldEmit(0, 64, 0, DamageState.INTACT, 1L),
                "INTACT blocks must not emit feedback");
    }

    @Test
    void BRUISED_does_not_trigger_feedback() {
        assertFalse(DamageFeedbackTracker.shouldEmit(0, 64, 0, DamageState.BRUISED, 1L),
                "BRUISED blocks must not emit feedback");
    }

    @Test
    void CRACKED_triggers_feedback() {
        assertTrue(DamageFeedbackTracker.shouldEmit(0, 64, 0, DamageState.CRACKED, 1L),
                "CRACKED blocks must emit feedback");
    }

    @Test
    void CRITICAL_triggers_feedback() {
        assertTrue(DamageFeedbackTracker.shouldEmit(0, 64, 0, DamageState.CRITICAL, 1L),
                "CRITICAL blocks must emit feedback");
    }

    // -- per-block cooldown -------------------------------------------------------

    @Test
    void same_pos_within_cooldown_is_suppressed() {
        assertTrue(DamageFeedbackTracker.shouldEmit(10, 64, 10, DamageState.CRITICAL, 1L));
        // Same pos, tick 2: within cooldown (< 10 ticks)
        assertFalse(DamageFeedbackTracker.shouldEmit(10, 64, 10, DamageState.CRITICAL, 2L),
                "second hit at same pos within cooldown must be suppressed");
    }

    @Test
    void same_pos_after_cooldown_triggers_again() {
        assertTrue(DamageFeedbackTracker.shouldEmit(10, 64, 10, DamageState.CRITICAL, 1L));
        // Tick 1 + 10 = tick 11: cooldown elapsed
        assertTrue(DamageFeedbackTracker.shouldEmit(10, 64, 10, DamageState.CRITICAL, 11L),
                "same pos after full cooldown must trigger again");
    }

    @Test
    void different_positions_do_not_share_cooldown() {
        assertTrue(DamageFeedbackTracker.shouldEmit(10, 64, 10, DamageState.CRITICAL, 1L));
        // Different pos at same tick: cooldown is per-position
        assertTrue(DamageFeedbackTracker.shouldEmit(20, 64, 10, DamageState.CRITICAL, 1L),
                "different position must not be affected by the first position's cooldown");
    }

    // -- global per-tick budget ---------------------------------------------------

    @Test
    void global_budget_exhausted_suppresses_further_feedback() {
        // Fill the budget with distinct positions in the same tick
        int admitted = 0;
        for (int i = 0; i < ImpactRuntimeConfig.FEEDBACK_BUDGET_PER_TICK + 5; i++) {
            if (DamageFeedbackTracker.shouldEmit(i, 64, 0, DamageState.CRITICAL, 100L)) {
                admitted++;
            }
        }
        assertEquals(ImpactRuntimeConfig.FEEDBACK_BUDGET_PER_TICK, admitted,
                "exactly GLOBAL_BUDGET_PER_TICK events must be admitted per tick");
    }

    @Test
    void budget_resets_on_new_tick() {
        // Exhaust budget on tick 100
        for (int i = 0; i < ImpactRuntimeConfig.FEEDBACK_BUDGET_PER_TICK; i++) {
            DamageFeedbackTracker.shouldEmit(i, 64, 0, DamageState.CRITICAL, 100L);
        }
        // New tick: budget resets, different position (beyond cooldown doesn't matter here
        // since new positions have no cooldown)
        assertTrue(DamageFeedbackTracker.shouldEmit(999, 64, 0, DamageState.CRITICAL, 101L),
                "budget must reset at the start of a new tick");
    }

    // -- config gate --------------------------------------------------------------

    @Test
    void feedback_disabled_config_prevents_all_feedback() {
        ImpactRuntimeConfig.ENABLE_DAMAGE_FEEDBACK = false;
        assertFalse(DamageFeedbackTracker.shouldEmit(0, 64, 0, DamageState.CRITICAL, 1L),
                "shouldEmit must return false when ENABLE_DAMAGE_FEEDBACK=false");
        assertFalse(DamageFeedbackTracker.shouldEmit(0, 64, 0, DamageState.CRACKED, 1L));
    }

    @Test
    void feedback_disabled_does_not_consume_budget_slot() {
        ImpactRuntimeConfig.ENABLE_DAMAGE_FEEDBACK = false;
        for (int i = 0; i < ImpactRuntimeConfig.FEEDBACK_BUDGET_PER_TICK + 5; i++) {
            DamageFeedbackTracker.shouldEmit(i, 64, 0, DamageState.CRITICAL, 1L);
        }
        // Re-enable: budget should still be full (no slots consumed while disabled)
        ImpactRuntimeConfig.ENABLE_DAMAGE_FEEDBACK = true;
        assertTrue(DamageFeedbackTracker.shouldEmit(0, 64, 0, DamageState.CRITICAL, 1L),
                "budget must be untouched when feedback was disabled");
    }

    // -- clear and counters -------------------------------------------------------

    @Test
    void clear_resets_all_rate_limit_state() {
        DamageFeedbackTracker.shouldEmit(10, 64, 10, DamageState.CRITICAL, 1L);
        DamageFeedbackTracker.clear();
        // After clear, same pos at same tick should be admitted again
        assertTrue(DamageFeedbackTracker.shouldEmit(10, 64, 10, DamageState.CRITICAL, 1L),
                "after clear, cooldown state must be reset");
    }

    @Test
    void feedback_does_not_call_setBlock_or_mutate_world() {
        // DamageFeedbackTracker is purely a boolean gate -- it does not call any MC APIs.
        // Calling shouldEmit() with STONE CRITICAL must have no side effects beyond
        // updating the rate-limit counters.
        boolean result = DamageFeedbackTracker.shouldEmit(10, 64, 10, DamageState.CRITICAL, 1L);
        assertTrue(result, "STONE CRITICAL state should get feedback");
        assertEquals(1, DamageFeedbackTracker.currentTickFeedbackCount(),
                "one budget slot consumed -- no other side effect");
    }
}
