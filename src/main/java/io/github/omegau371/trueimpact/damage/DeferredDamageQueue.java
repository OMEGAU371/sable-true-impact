package io.github.omegau371.trueimpact.damage;

import java.util.ArrayDeque;
import java.util.HashSet;

/**
 * Phase 1E: deferred damage event queue.
 *
 * Events are enqueued during physics tick processing (clearCollisions, server thread)
 * and flushed on ServerTickEvent.Post (safe world-access window, same thread).
 *
 * CONTRACT (Phase 1E -- diagnostic only):
 *   - flush() counts and stores the last event; MUST NOT call destroyBlock, setBlock, or crack.
 *   - DamageResolver still returns NONE; this queue does NOT feed the resolver yet.
 *   - Phase 2A will promote flush() to call DamageResolver.resolve() and apply real effects.
 *
 * Threading: all operations are called on the server thread. No synchronization needed.
 *
 * Dedup: same (posX, posY, posZ, victimBlock) within the same serverTick is enqueued at most once.
 * Cap: queue is capped at MAX_PENDING to prevent unbounded growth on rapid impacts.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class DeferredDamageQueue {

    private DeferredDamageQueue() {}

    static final int MAX_PENDING = 64;

    private static final ArrayDeque<DeferredDamageEvent> pending = new ArrayDeque<>();
    private static long totalEnqueued;
    private static long totalFlushed;
    private static DeferredDamageEvent lastFlushed;

    // Dedup state for the current tick; cleared when serverTick changes.
    private static final HashSet<String> seenThisTick = new HashSet<>();
    private static long lastEnqueueTick = -1L;

    // ---- enqueue -----------------------------------------------------------------

    /**
     * Attempts to enqueue a candidate damage event.
     *
     * The caller (SableImpactCapture) is responsible for pre-conditions:
     *   - event.kImpact() is finite and > event.threshold()
     *   - The victim kind is WORLD_BLOCK
     *
     * Returns true if enqueued; false if deduped, NaN guard triggered, or queue full.
     */
    public static boolean enqueue(DeferredDamageEvent event) {
        // Guard: caller should have checked, but defend against NaN reaching the queue
        if (!Double.isFinite(event.kImpact())) return false;

        // Clear dedup when tick changes
        if (event.serverTick() != lastEnqueueTick) {
            seenThisTick.clear();
            lastEnqueueTick = event.serverTick();
        }

        // Dedup: same pos+block in same tick is a duplicate
        String key = event.posX() + "," + event.posY() + "," + event.posZ()
                + "," + event.victimBlock();
        if (!seenThisTick.add(key)) {
            return false;
        }

        if (pending.size() >= MAX_PENDING) {
            return false;
        }

        pending.add(event);
        totalEnqueued++;
        return true;
    }

    // ---- flush -------------------------------------------------------------------

    /**
     * Flushes all pending events.
     *
     * Phase 1E: counts events and retains last for status display.
     * MUST NOT modify game world state in this phase.
     * Phase 2A: will call DamageResolver.resolve() per event and apply crack/break.
     *
     * Called from TrueImpactMod.onServerTickPost() on every server tick.
     *
     * @return count of events flushed
     */
    public static int flush() {
        int count = pending.size();
        if (count == 0) return 0;
        while (!pending.isEmpty()) {
            lastFlushed = pending.poll();
            totalFlushed++;
        }
        return count;
    }

    // ---- stats/control -----------------------------------------------------------

    /** Point-in-time snapshot of queue statistics for status display. */
    public static QueueStats stats() {
        return new QueueStats(pending.size(), totalEnqueued, totalFlushed, lastFlushed);
    }

    /**
     * Clears all queue state including lifetime counters.
     * Registered as a DiagnosticStateManager flush hook (fires on /trueimpact debug all off).
     * Resets counters so that status shows a clean baseline after all-off.
     */
    public static void clear() {
        pending.clear();
        seenThisTick.clear();
        lastEnqueueTick = -1L;
        totalEnqueued   = 0L;
        totalFlushed    = 0L;
        lastFlushed     = null;
    }

    /** Point-in-time snapshot of queue state. */
    public record QueueStats(
            int                 pending,
            long                totalEnqueued,
            long                totalFlushed,
            DeferredDamageEvent lastFlushed    // null if no event flushed since startup
    ) {}
}
