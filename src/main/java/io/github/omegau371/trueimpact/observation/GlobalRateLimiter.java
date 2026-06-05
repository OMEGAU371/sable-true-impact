package io.github.omegau371.trueimpact.observation;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global hard limits on diagnostic log output.
 * [C9-codex] All limits apply unconditionally — high-energy events are NOT exempt.
 *
 * Usage: call tryLog() before any diagnostic LOGGER call.
 * Call resetTick() at the start of each server tick.
 * Call resetSecond() every 20 ticks (once per second at 20 TPS).
 */
public final class GlobalRateLimiter {

    public static final int MAX_LOGS_PER_TICK   = 20;
    public static final int MAX_LOGS_PER_SECOND = 100;

    private final AtomicInteger logsThisTick    = new AtomicInteger(0);
    private final AtomicInteger logsThisSecond  = new AtomicInteger(0);
    private final AtomicInteger droppedThisTick = new AtomicInteger(0);

    /** @return true if the caller may log; false if the hard limit has been reached. */
    public boolean tryLog() {
        if (logsThisTick.incrementAndGet() > MAX_LOGS_PER_TICK) {
            droppedThisTick.incrementAndGet();
            logsThisTick.decrementAndGet(); // keep counter at MAX so next call still bounces
            return false;
        }
        if (logsThisSecond.incrementAndGet() > MAX_LOGS_PER_SECOND) {
            droppedThisTick.incrementAndGet();
            logsThisSecond.decrementAndGet();
            logsThisTick.decrementAndGet();
            return false;
        }
        return true;
    }

    public int droppedThisTick() {
        return droppedThisTick.get();
    }

    /** Call at the start of every server tick. */
    public void resetTick() {
        logsThisTick.set(0);
        droppedThisTick.set(0);
    }

    /** Call every 20 ticks (1 second at 20 TPS). */
    public void resetSecond() {
        logsThisSecond.set(0);
    }
}
