package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeferredSublevelDamageQueue (Phase 3A).
 * No Minecraft runtime — pure Java.
 */
class DeferredSublevelDamageQueueTest {

    @BeforeEach
    void reset() {
        DeferredSublevelDamageQueue.clear();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static DeferredSublevelDamageEvent ev(long tick, int id,
                                                   double cpX, double cpY, double cpZ,
                                                   double k) {
        return new DeferredSublevelDamageEvent(tick, "minecraft:overworld", id, cpX, cpY, cpZ, k,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, 0.0,
                -1, Double.NaN, Double.NaN, Double.NaN);
    }

    // ── basic enqueue ─────────────────────────────────────────────────────────

    @Test
    void enqueue_returnsTrue_forValidEvent() {
        assertTrue(DeferredSublevelDamageQueue.enqueue(ev(1, 1, 0, 0, 0, 600.0)));
        assertEquals(1, DeferredSublevelDamageQueue.pendingCount());
    }

    @Test
    void enqueue_returnsFalse_forNaN_kImpact() {
        assertFalse(DeferredSublevelDamageQueue.enqueue(ev(1, 1, 0, 0, 0, Double.NaN)));
        assertEquals(0, DeferredSublevelDamageQueue.pendingCount());
    }

    @Test
    void enqueue_returnsFalse_forPositiveInfinity() {
        assertFalse(DeferredSublevelDamageQueue.enqueue(ev(1, 1, 0, 0, 0, Double.POSITIVE_INFINITY)));
    }

    @Test
    void enqueue_returnsFalse_forNegativeInfinity() {
        assertFalse(DeferredSublevelDamageQueue.enqueue(ev(1, 1, 0, 0, 0, Double.NEGATIVE_INFINITY)));
    }

    // ── dedup: per-runtimeId per tick ─────────────────────────────────────────

    @Test
    void dedup_sameSubLevel_sameBlock_sameTick_rejected() {
        assertTrue(DeferredSublevelDamageQueue.enqueue(ev(1, 5, 0.1, -0.4, 0.1, 600.0)));
        // same runtimeId → rejected regardless of block position
        assertFalse(DeferredSublevelDamageQueue.enqueue(ev(1, 5, -0.1, -0.3, -0.2, 800.0)));
    }

    @Test
    void dedup_sameSubLevel_differentBlock_sameTick_rejected() {
        // Energy conservation: one collision → one block damaged per sublevel per tick.
        // Different contact faces (different blocks) of the same sublevel are deduplicated.
        assertTrue(DeferredSublevelDamageQueue.enqueue(ev(1, 5, 0.0, 0.0, 0.0, 600.0)));
        assertFalse(DeferredSublevelDamageQueue.enqueue(ev(1, 5, 1.0, 0.0, 0.0, 600.0)));
        assertEquals(1, DeferredSublevelDamageQueue.pendingCount());
    }

    @Test
    void dedup_sameSubLevel_higherKImpact_replacesExisting() {
        assertTrue(DeferredSublevelDamageQueue.enqueue(ev(1, 5, 0.0, 0.0, 0.0, 600.0)));
        assertFalse(DeferredSublevelDamageQueue.enqueue(ev(1, 5, 1.0, 0.0, 0.0, 900.0)));  // higher k
        List<DeferredSublevelDamageEvent> drained = DeferredSublevelDamageQueue.drainAll();
        assertEquals(1, drained.size());
        assertEquals(900.0, drained.get(0).kImpact());
    }

    @Test
    void dedup_sameSubLevel_lowerKImpact_keepsOriginal() {
        assertTrue(DeferredSublevelDamageQueue.enqueue(ev(1, 5, 0.0, 0.0, 0.0, 600.0)));
        assertFalse(DeferredSublevelDamageQueue.enqueue(ev(1, 5, 1.0, 0.0, 0.0, 400.0)));  // lower k
        List<DeferredSublevelDamageEvent> drained = DeferredSublevelDamageQueue.drainAll();
        assertEquals(1, drained.size());
        assertEquals(600.0, drained.get(0).kImpact());
    }

    @Test
    void dedup_differentSubLevel_sameBlock_sameTick_accepted() {
        assertTrue(DeferredSublevelDamageQueue.enqueue(ev(1, 5, 0.0, 0.0, 0.0, 600.0)));
        assertTrue(DeferredSublevelDamageQueue.enqueue(ev(1, 7, 0.0, 0.0, 0.0, 600.0)));
    }

    @Test
    void dedup_clearsOnNewTick() {
        assertTrue(DeferredSublevelDamageQueue.enqueue(ev(1, 5, 0.0, 0.0, 0.0, 600.0)));
        // Same sublevel + same block but new tick — accepted
        assertTrue(DeferredSublevelDamageQueue.enqueue(ev(2, 5, 0.0, 0.0, 0.0, 600.0)));
    }

    // ── drain ─────────────────────────────────────────────────────────────────

    @Test
    void drainAll_returnsAllPending_andClearsQueue() {
        DeferredSublevelDamageQueue.enqueue(ev(1, 1, 0, 0, 0, 600.0));
        DeferredSublevelDamageQueue.enqueue(ev(1, 2, 0, 0, 0, 600.0));
        List<DeferredSublevelDamageEvent> drained = DeferredSublevelDamageQueue.drainAll();
        assertEquals(2, drained.size());
        assertEquals(0, DeferredSublevelDamageQueue.pendingCount());
    }

    @Test
    void drainAll_emptyQueue_returnsEmptyList() {
        assertTrue(DeferredSublevelDamageQueue.drainAll().isEmpty());
    }

    @Test
    void drainAll_updatesTotalFlushed() {
        DeferredSublevelDamageQueue.enqueue(ev(1, 1, 0, 0, 0, 600.0));
        DeferredSublevelDamageQueue.enqueue(ev(1, 2, 0, 0, 0, 600.0));
        DeferredSublevelDamageQueue.drainAll();
        assertEquals(2, DeferredSublevelDamageQueue.totalFlushed());
    }

    // ── capacity ──────────────────────────────────────────────────────────────

    @Test
    void maxPending_rejectsOverflow() {
        for (int i = 0; i < DeferredSublevelDamageQueue.MAX_PENDING; i++) {
            assertTrue(DeferredSublevelDamageQueue.enqueue(ev(1, i, i, 0, 0, 600.0)),
                    "slot " + i + " should be accepted");
        }
        assertFalse(DeferredSublevelDamageQueue.enqueue(ev(1, 999, 999, 0, 0, 600.0)),
                "overflow slot should be rejected");
        assertEquals(DeferredSublevelDamageQueue.MAX_PENDING,
                DeferredSublevelDamageQueue.pendingCount());
    }

    // ── counters ──────────────────────────────────────────────────────────────

    @Test
    void totalEnqueued_countsSuccessfulEnqueues() {
        DeferredSublevelDamageQueue.enqueue(ev(1, 1, 0, 0, 0, 600.0));
        DeferredSublevelDamageQueue.enqueue(ev(1, 1, 0, 0, 0, Double.NaN)); // rejected
        DeferredSublevelDamageQueue.enqueue(ev(1, 2, 0, 0, 0, 600.0));
        assertEquals(2, DeferredSublevelDamageQueue.totalEnqueued());
    }

    @Test
    void clear_resetsAllState() {
        DeferredSublevelDamageQueue.enqueue(ev(1, 1, 0, 0, 0, 600.0));
        DeferredSublevelDamageQueue.drainAll();
        DeferredSublevelDamageQueue.clear();
        assertEquals(0, DeferredSublevelDamageQueue.pendingCount());
        assertEquals(0, DeferredSublevelDamageQueue.totalEnqueued());
        assertEquals(0, DeferredSublevelDamageQueue.totalFlushed());
    }
}
