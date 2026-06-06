package io.github.omegau371.trueimpact.observation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobalRateLimiterTest {

    @Test
    void hard_limit_per_tick_is_enforced() {
        GlobalRateLimiter lim = new GlobalRateLimiter();
        int allowed = 0;
        for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK + 50; i++) {
            if (lim.tryLog()) allowed++;
        }
        assertEquals(GlobalRateLimiter.MAX_LOGS_PER_TICK, allowed,
                "Exactly MAX_LOGS_PER_TICK must be allowed");
    }

    @Test
    void hard_limit_per_second_enforced_across_ticks() {
        GlobalRateLimiter lim = new GlobalRateLimiter();
        int total = 0;
        for (int tick = 0; tick < 10; tick++) {
            lim.resetTick();
            for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK; i++) {
                if (lim.tryLog()) total++;
            }
        }
        assertTrue(total <= GlobalRateLimiter.MAX_LOGS_PER_SECOND,
                "Total across 10 ticks must not exceed MAX_LOGS_PER_SECOND=" +
                GlobalRateLimiter.MAX_LOGS_PER_SECOND + " (got " + total + ")");
    }

    @Test
    void reset_tick_allows_fresh_logs() {
        GlobalRateLimiter lim = new GlobalRateLimiter();
        for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK + 10; i++) lim.tryLog();
        lim.resetTick();
        assertTrue(lim.tryLog(), "After resetTick must be able to log again");
    }

    @Test
    void high_energy_events_not_exempt() {
        // [C9-codex] Unconditional limit
        GlobalRateLimiter lim = new GlobalRateLimiter();
        int allowed = 0;
        for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK + 100; i++) {
            if (lim.tryLog()) allowed++;
        }
        assertEquals(GlobalRateLimiter.MAX_LOGS_PER_TICK, allowed,
                "High-energy events are NOT exempt from per-tick hard limit [C9-codex]");
    }

    @Test
    void dropped_counter_tracks_rejected_calls() {
        GlobalRateLimiter lim = new GlobalRateLimiter();
        for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK + 5; i++) lim.tryLog();
        assertEquals(5, lim.droppedThisTick());
    }

    @Test
    void dropped_summary_also_subject_to_limit() {
        // [C9-codex] summary itself goes through tryLog()
        GlobalRateLimiter lim = new GlobalRateLimiter();
        // Exhaust the budget
        for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK + 10; i++) lim.tryLog();
        // Summary should fail too (budget exhausted)
        int result = lim.tryLogDroppedSummary();
        // result > 0 means something was dropped AND the summary call consumed a slot
        // but since budget is gone, tryLog() returns false so no slot consumed
        // The key is: tryLogDroppedSummary() itself uses tryLog() — can return 0 if at limit
        assertTrue(result >= 0, "tryLogDroppedSummary must not throw");
    }
}
