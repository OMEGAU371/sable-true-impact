package io.github.omegau371.trueimpact.damage;

import java.util.ArrayDeque;
import java.util.HashSet;

/**
 * Deferred damage event queue.
 *
 * Events are enqueued during physics tick processing (clearCollisions, server thread)
 * and applied on ServerTickEvent.Post (safe world-access window, same thread).
 *
 * Phase 2A: ImpactBlockApplicator.tryApply() applies SOFT_SOIL compaction effects.
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

    // Phase 2A apply/skip counters (updated by recordApplyResult per flushed event).
    private static long totalApplied;
    private static long totalSkipped;
    private static ApplyRecord lastApplyRecord;

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

    // ---- flush (Phase 1E compat) --------------------------------------------------

    /**
     * Count-only flush: removes all pending events, increments totalFlushed, no world mutation.
     * Used in unit tests to verify the queue without a real ServerLevel.
     *
     * TrueImpactMod uses drainAll() + ImpactBlockApplicator for Phase 2A production behavior.
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

    // ---- Phase 2A: drain + apply -------------------------------------------------

    /**
     * Drains all pending events and returns them as a list.
     * Increments totalFlushed and updates lastFlushed for each drained event.
     *
     * Called by TrueImpactMod.onServerTickPost() which then calls
     * ImpactBlockApplicator.tryApply() per event and recordApplyResult() for each outcome.
     *
     * @return list of drained events (empty if none pending)
     */
    public static java.util.List<DeferredDamageEvent> drainAll() {
        if (pending.isEmpty()) return java.util.List.of();
        java.util.List<DeferredDamageEvent> result = new java.util.ArrayList<>(pending);
        for (DeferredDamageEvent e : result) {
            lastFlushed = e;
            totalFlushed++;
        }
        pending.clear();
        return result;
    }

    /**
     * Records the outcome of applying a flushed event.
     * Updates totalApplied or totalSkipped based on ApplyOutcome.wasApplied().
     *
     * Called by TrueImpactMod once per event after ImpactBlockApplicator.tryApply().
     */
    public static void recordApplyResult(DeferredDamageEvent event, ApplyOutcome outcome) {
        lastApplyRecord = new ApplyRecord(event, outcome);
        if (outcome.wasApplied()) {
            totalApplied++;
        } else {
            totalSkipped++;
        }
    }

    // ---- stats/control -----------------------------------------------------------

    /** Point-in-time snapshot of queue statistics for status display. */
    public static QueueStats stats() {
        return new QueueStats(pending.size(), totalEnqueued, totalFlushed,
                totalApplied, totalSkipped, lastFlushed, lastApplyRecord);
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
        totalApplied    = 0L;
        totalSkipped    = 0L;
        lastApplyRecord = null;
    }

    /** Outcome record pairing an event with its apply result. */
    public record ApplyRecord(DeferredDamageEvent event, ApplyOutcome outcome) {}

    /** Point-in-time snapshot of queue state. */
    public record QueueStats(
            int                 pending,
            long                totalEnqueued,
            long                totalFlushed,
            long                totalApplied,   // Phase 2A: events that mutated a block
            long                totalSkipped,   // Phase 2A: events skipped (gate or mismatch)
            DeferredDamageEvent lastFlushed,    // null if no event flushed since startup
            ApplyRecord         lastApplyRecord // null if no result recorded since startup
    ) {}
}
