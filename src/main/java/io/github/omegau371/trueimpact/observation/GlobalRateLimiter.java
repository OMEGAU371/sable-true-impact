package io.github.omegau371.trueimpact.observation;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global hard limits on diagnostic log output.
 * [C9-codex] All limits apply unconditionally — high-energy events are NOT exempt.
 * [C9-codex] Dropped-count summary itself also goes through tryLog().
 *
 * All callers MUST use ExperimentLog.info() which calls tryLog() internally.
 * Direct access to ExperimentLog's underlying Logger is forbidden outside ExperimentLog.
 */
public final class GlobalRateLimiter {

    public static final int MAX_LOGS_PER_TICK   = 20;
    public static final int MAX_LOGS_PER_SECOND = 100;

    private final AtomicInteger logsThisTick    = new AtomicInteger(0);
    private final AtomicInteger logsThisSecond  = new AtomicInteger(0);
    private final AtomicInteger droppedThisTick = new AtomicInteger(0);

    /** @return true if the caller may log; false if the hard limit has been reached. */
    public boolean tryLog() {
        int newTick = logsThisTick.incrementAndGet();
        if (newTick > MAX_LOGS_PER_TICK) {
            logsThisTick.decrementAndGet();
            droppedThisTick.incrementAndGet();
            return false;
        }
        int newSec = logsThisSecond.incrementAndGet();
        if (newSec > MAX_LOGS_PER_SECOND) {
            logsThisSecond.decrementAndGet();
            logsThisTick.decrementAndGet();
            droppedThisTick.incrementAndGet();
            return false;
        }
        return true;
    }

    /**
     * Try to log a dropped-count summary.
     * [C9-codex] Summary itself is also subject to the hard limit.
     * @return number dropped this tick, or 0 if nothing to report or limit hit
     */
    public int tryLogDroppedSummary() {
        int dropped = droppedThisTick.get();
        if (dropped == 0) return 0;
        if (!tryLog()) return 0; // summary itself dropped — caller must not log
        return dropped;
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
