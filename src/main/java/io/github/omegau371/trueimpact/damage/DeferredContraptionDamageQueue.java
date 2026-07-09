package io.github.omegau371.trueimpact.damage;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe queue for DeferredContraptionDamageEvent.
 * Produced on the Rapier physics thread (via SableImpactCapture), drained on the server tick thread.
 */
public final class DeferredContraptionDamageQueue {

    private DeferredContraptionDamageQueue() {}

    private static final Queue<DeferredContraptionDamageEvent> QUEUE = new ConcurrentLinkedQueue<>();

    public static void enqueue(DeferredContraptionDamageEvent event) {
        QUEUE.add(event);
    }

    public static List<DeferredContraptionDamageEvent> drainAll() {
        List<DeferredContraptionDamageEvent> result = new ArrayList<>();
        DeferredContraptionDamageEvent e;
        while ((e = QUEUE.poll()) != null) result.add(e);
        return result;
    }

    public static void clear() {
        QUEUE.clear();
    }
}
