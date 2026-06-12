package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeferredDamageQueue mechanics.
 * No Minecraft runtime required.
 */
class DeferredDamageQueueTest {

    @BeforeEach
    void reset() {
        DeferredDamageQueue.clear();
    }

    // -- basic enqueue and stats ---------------------------------------------------

    @Test
    void initial_stats_are_zero() {
        DeferredDamageQueue.QueueStats s = DeferredDamageQueue.stats();
        assertEquals(0, s.pending());
        assertEquals(0L, s.totalEnqueued());
        assertEquals(0L, s.totalFlushed());
        assertNull(s.lastFlushed());
    }

    @Test
    void enqueue_valid_event_returns_true_and_increments_totalEnqueued() {
        assertTrue(DeferredDamageQueue.enqueue(stoneEvent(1L, 10, 63, 10, 100.0)));
        assertEquals(1L, DeferredDamageQueue.stats().totalEnqueued());
        assertEquals(1, DeferredDamageQueue.stats().pending());
    }

    @Test
    void enqueue_NaN_kImpact_returns_false() {
        assertFalse(DeferredDamageQueue.enqueue(stoneEvent(1L, 10, 63, 10, Double.NaN)));
        assertEquals(0L, DeferredDamageQueue.stats().totalEnqueued());
    }

    @Test
    void enqueue_positive_infinity_returns_false() {
        assertFalse(DeferredDamageQueue.enqueue(stoneEvent(1L, 10, 63, 10,
                Double.POSITIVE_INFINITY)));
        assertEquals(0L, DeferredDamageQueue.stats().totalEnqueued());
    }

    // -- dedup -------------------------------------------------------------------

    @Test
    void same_tick_same_pos_same_block_is_deduped() {
        DeferredDamageQueue.enqueue(stoneEvent(5L, 10, 63, 10, 100.0));
        boolean second = DeferredDamageQueue.enqueue(stoneEvent(5L, 10, 63, 10, 200.0));

        assertFalse(second, "same tick/pos/block must be deduped");
        assertEquals(1L, DeferredDamageQueue.stats().totalEnqueued());
        assertEquals(1, DeferredDamageQueue.stats().pending());
    }

    @Test
    void different_pos_same_tick_is_not_deduped() {
        DeferredDamageQueue.enqueue(stoneEvent(5L, 10, 63, 10, 100.0));
        boolean second = DeferredDamageQueue.enqueue(stoneEvent(5L, 11, 63, 10, 100.0));

        assertTrue(second, "different pos should not be deduped");
        assertEquals(2L, DeferredDamageQueue.stats().totalEnqueued());
    }

    @Test
    void same_pos_different_tick_is_not_deduped() {
        DeferredDamageQueue.enqueue(stoneEvent(5L, 10, 63, 10, 100.0));
        boolean second = DeferredDamageQueue.enqueue(stoneEvent(6L, 10, 63, 10, 100.0));

        assertTrue(second, "different tick should clear dedup and allow re-enqueue");
        assertEquals(2L, DeferredDamageQueue.stats().totalEnqueued());
    }

    @Test
    void same_pos_different_block_same_tick_is_not_deduped() {
        DeferredDamageQueue.enqueue(stoneEvent(5L, 10, 63, 10, 100.0));
        boolean second = DeferredDamageQueue.enqueue(dirtEvent(5L, 10, 63, 10, 10.0));

        assertTrue(second, "different block at same pos/tick should not be deduped");
        assertEquals(2L, DeferredDamageQueue.stats().totalEnqueued());
    }

    // -- flush -------------------------------------------------------------------

    @Test
    void flush_empties_pending_and_increments_totalFlushed() {
        DeferredDamageQueue.enqueue(stoneEvent(1L, 10, 63, 10, 100.0));
        DeferredDamageQueue.enqueue(stoneEvent(1L, 11, 63, 10, 100.0));

        int flushed = DeferredDamageQueue.flush();

        assertEquals(2, flushed, "flush returns count of events processed");
        assertEquals(0, DeferredDamageQueue.stats().pending(), "pending must be 0 after flush");
        assertEquals(2L, DeferredDamageQueue.stats().totalFlushed());
        assertNotNull(DeferredDamageQueue.stats().lastFlushed(),
                "lastFlushed must be set after flush");
    }

    @Test
    void flush_on_empty_queue_returns_zero() {
        assertEquals(0, DeferredDamageQueue.flush());
    }

    @Test
    void lastFlushed_is_last_event_in_queue() {
        DeferredDamageQueue.enqueue(stoneEvent(1L, 10, 63, 10, 100.0));
        DeferredDamageQueue.enqueue(stoneEvent(1L, 11, 63, 10, 200.0));
        DeferredDamageQueue.flush();

        DeferredDamageEvent last = DeferredDamageQueue.stats().lastFlushed();
        assertNotNull(last);
        assertEquals(11, last.posX(), "lastFlushed should be the last event dequeued");
    }

    @Test
    void totalFlushed_persists_across_flush_calls() {
        DeferredDamageQueue.enqueue(stoneEvent(1L, 10, 63, 10, 100.0));
        DeferredDamageQueue.flush();
        DeferredDamageQueue.enqueue(stoneEvent(2L, 10, 63, 10, 100.0));
        DeferredDamageQueue.flush();

        assertEquals(2L, DeferredDamageQueue.stats().totalFlushed());
    }

    // -- clear -------------------------------------------------------------------

    @Test
    void clear_resets_all_state_including_counters() {
        DeferredDamageQueue.enqueue(stoneEvent(1L, 10, 63, 10, 100.0));
        DeferredDamageQueue.flush();
        DeferredDamageQueue.enqueue(stoneEvent(2L, 10, 63, 10, 100.0));

        DeferredDamageQueue.clear();

        assertEquals(0,   DeferredDamageQueue.stats().pending());
        assertEquals(0L,  DeferredDamageQueue.stats().totalEnqueued(), "clear resets counters");
        assertEquals(0L,  DeferredDamageQueue.stats().totalFlushed(),  "clear resets counters");
        assertNull(DeferredDamageQueue.stats().lastFlushed(),           "clear resets lastFlushed");
    }

    @Test
    void clear_resets_dedup_state_allowing_reenqueue() {
        DeferredDamageQueue.enqueue(stoneEvent(1L, 10, 63, 10, 100.0));
        DeferredDamageQueue.clear();
        boolean second = DeferredDamageQueue.enqueue(stoneEvent(1L, 10, 63, 10, 100.0));

        assertTrue(second, "after clear, same tick/pos/block must be accepted again");
    }

    // -- cap -------------------------------------------------------------------

    @Test
    void queue_cap_prevents_unbounded_growth() {
        for (int i = 0; i < DeferredDamageQueue.MAX_PENDING + 10; i++) {
            DeferredDamageQueue.enqueue(stoneEvent((long) i, 10, 63, 10, 100.0));
        }
        assertTrue(DeferredDamageQueue.stats().pending() <= DeferredDamageQueue.MAX_PENDING,
                "queue must not exceed MAX_PENDING");
    }

    // -- DamageResolver still NONE (integration sanity) --------------------------

    @Test
    void damage_resolver_still_returns_NONE_regardless_of_queue() {
        DeferredDamageQueue.enqueue(stoneEvent(1L, 10, 63, 10, 100.0));
        // Constructing a minimal ImpactRecord just to call the resolver
        io.github.omegau371.trueimpact.physics.ImpactRecord fakeRecord =
                new io.github.omegau371.trueimpact.physics.ImpactRecord(
                        1L, 1L, 1, 2, 5.0, 8.0, 3.077, 1, 10.0, 8.0, 0.025,
                        io.github.omegau371.trueimpact.physics.ContactType.ACTIVE_IMPACT);
        assertEquals(DamageResolver.DamageEvent.NONE, DamageResolver.resolve(fakeRecord),
                "DamageResolver must still return NONE in Phase 1E");
    }

    // -- helpers -----------------------------------------------------------------

    private static DeferredDamageEvent stoneEvent(long tick, int x, int y, int z, double kImpact) {
        return new DeferredDamageEvent(tick, "minecraft:stone", x, y, z,
                MaterialThresholdProfile.MaterialClass.STONE,
                kImpact,
                MaterialThresholdProfile.threshold(MaterialThresholdProfile.MaterialClass.STONE),
                VictimInfo.Source.CONTACT_POINT_SAMPLE,
                VictimInfo.Confidence.APPROX);
    }

    private static DeferredDamageEvent dirtEvent(long tick, int x, int y, int z, double kImpact) {
        return new DeferredDamageEvent(tick, "minecraft:dirt", x, y, z,
                MaterialThresholdProfile.MaterialClass.SOFT_SOIL,
                kImpact,
                MaterialThresholdProfile.threshold(MaterialThresholdProfile.MaterialClass.SOFT_SOIL),
                VictimInfo.Source.CONTACT_POINT_SAMPLE,
                VictimInfo.Confidence.APPROX);
    }
}
