package io.github.omegau371.trueimpact.damage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Deferred sublevel-block damage queue (Phase 3A: Newton's 3rd law).
 *
 * Events are enqueued during physics tick processing and applied on ServerTickEvent.Post
 * in the same safe world-access window as DeferredDamageQueue.
 *
 * Energy-conservation dedup: per (runtimeId) per server tick, only the highest-kImpact
 * event is kept. A single sublevel–world collision transfers kinetic energy once; allowing
 * multiple contact-face events to each apply the full kImpact would multiply the energy
 * across as many blocks as there are contact points. The highest-energy contact is the
 * primary contact (maximum Δv for the body) and is the one that should drive block damage.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class DeferredSublevelDamageQueue {

    private DeferredSublevelDamageQueue() {}

    static final int MAX_PENDING = 32;

    // Per-runtimeId: highest-kImpact event seen for each sublevel this tick.
    // LinkedHashMap preserves insertion order for deterministic drain output.
    private static final LinkedHashMap<Integer, DeferredSublevelDamageEvent> pendingByRuntimeId =
            new LinkedHashMap<>();
    private static long lastEnqueueTick = -1L;
    private static long totalEnqueued;
    private static long totalFlushed;

    /**
     * Attempts to enqueue a sublevel damage event.
     *
     * Returns true if a new slot was consumed (first event for this runtimeId this tick).
     * Returns false if: kImpact is non-finite; queue is full; or this runtimeId already has
     * an event this tick — in the latter case the existing event is replaced only when the
     * new kImpact is strictly higher (energy-conservation: keep the primary contact).
     */
    public static boolean enqueue(DeferredSublevelDamageEvent event) {
        if (!Double.isFinite(event.kImpact())) return false;

        if (event.serverTick() != lastEnqueueTick) {
            pendingByRuntimeId.clear();
            lastEnqueueTick = event.serverTick();
        }

        int id = event.sublevelRuntimeId();
        DeferredSublevelDamageEvent existing = pendingByRuntimeId.get(id);
        if (existing != null) {
            if (event.kImpact() > existing.kImpact()) {
                pendingByRuntimeId.put(id, event);
            }
            return false; // no new slot consumed
        }

        if (pendingByRuntimeId.size() >= MAX_PENDING) return false;

        pendingByRuntimeId.put(id, event);
        totalEnqueued++;
        return true;
    }

    /** Drains all pending events; updates totalFlushed. */
    public static List<DeferredSublevelDamageEvent> drainAll() {
        if (pendingByRuntimeId.isEmpty()) return List.of();
        List<DeferredSublevelDamageEvent> result = new ArrayList<>(pendingByRuntimeId.values());
        totalFlushed += result.size();
        pendingByRuntimeId.clear();
        return result;
    }

    /** Clears all state including lifetime counters. */
    public static void clear() {
        pendingByRuntimeId.clear();
        lastEnqueueTick = -1L;
        totalEnqueued   = 0L;
        totalFlushed    = 0L;
    }

    public static long totalEnqueued() { return totalEnqueued; }
    public static long totalFlushed()  { return totalFlushed; }
    public static int  pendingCount()  { return pendingByRuntimeId.size(); }
}
