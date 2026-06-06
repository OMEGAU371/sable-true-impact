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
    void dropped_summary_returns_zero_when_budget_exhausted() {
        // [P1-1] When per-tick budget is gone, tryLogDroppedSummary() must return 0
        // so ExperimentLog never bypasses the hard limit via a direct LOG.info call.
        GlobalRateLimiter lim = new GlobalRateLimiter();
        // Exhaust the budget and generate 10 drops
        for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK + 10; i++) lim.tryLog();
        assertTrue(lim.droppedThisTick() > 0, "precondition: drops must have been recorded");
        int result = lim.tryLogDroppedSummary();
        assertEquals(0, result,
                "[P1-1] summary must return 0 when budget is exhausted — caller must not log");
    }

    @Test
    void dropped_summary_returns_zero_when_no_drops() {
        GlobalRateLimiter lim = new GlobalRateLimiter();
        // Log once — no drops
        lim.tryLog();
        assertEquals(0, lim.tryLogDroppedSummary(),
                "summary must return 0 when droppedThisTick == 0");
    }
}
