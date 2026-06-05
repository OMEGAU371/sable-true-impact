package io.github.omegau371.trueimpact.observation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobalRateLimiterTest {

    @Test
    void hard_limit_per_tick_is_enforced() {
        GlobalRateLimiter limiter = new GlobalRateLimiter();
        int allowed = 0;
        // Try well above the limit
        for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK + 50; i++) {
            if (limiter.tryLog()) allowed++;
        }
        assertEquals(GlobalRateLimiter.MAX_LOGS_PER_TICK, allowed,
                "Exactly MAX_LOGS_PER_TICK must be allowed, no more");
    }

    @Test
    void hard_limit_per_second_is_enforced_across_ticks() {
        GlobalRateLimiter limiter = new GlobalRateLimiter();
        int totalAllowed = 0;
        // Simulate 10 ticks (each <= MAX_LOGS_PER_TICK), enough to exceed per-second limit
        for (int tick = 0; tick < 10; tick++) {
            limiter.resetTick();
            for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK; i++) {
                if (limiter.tryLog()) totalAllowed++;
            }
        }
        assertTrue(totalAllowed <= GlobalRateLimiter.MAX_LOGS_PER_SECOND,
                "Total allowed across 10 ticks must not exceed MAX_LOGS_PER_SECOND=" +
                GlobalRateLimiter.MAX_LOGS_PER_SECOND + " (got " + totalAllowed + ")");
    }

    @Test
    void reset_tick_allows_fresh_logs_next_tick() {
        GlobalRateLimiter limiter = new GlobalRateLimiter();
        // Exhaust tick budget
        for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK + 10; i++) {
            limiter.tryLog();
        }
        // Reset
        limiter.resetTick();
        // Should be able to log again (up to per-tick limit)
        assertTrue(limiter.tryLog(), "After resetTick, must be able to log again");
    }

    @Test
    void high_energy_events_are_not_exempt_from_limit() {
        // [C9-codex] All events — including "high energy" — are subject to the same limit
        GlobalRateLimiter limiter = new GlobalRateLimiter();
        int allowed = 0;
        for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK + 100; i++) {
            // Even if the caller considers this a "high energy" event, limit still applies
            if (limiter.tryLog()) allowed++;
        }
        assertEquals(GlobalRateLimiter.MAX_LOGS_PER_TICK, allowed,
                "High-energy events are NOT exempt from per-tick hard limit");
    }

    @Test
    void dropped_counter_tracks_missed_logs() {
        GlobalRateLimiter limiter = new GlobalRateLimiter();
        for (int i = 0; i < GlobalRateLimiter.MAX_LOGS_PER_TICK + 5; i++) {
            limiter.tryLog();
        }
        assertEquals(5, limiter.droppedThisTick(),
                "droppedThisTick should reflect exactly the number of rejected calls");
    }
}
