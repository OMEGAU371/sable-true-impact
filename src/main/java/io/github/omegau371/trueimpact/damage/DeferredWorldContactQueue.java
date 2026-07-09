package io.github.omegau371.trueimpact.damage;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe queue for DeferredWorldContactEvent.
 * Produced on the Rapier physics thread (via SableImpactCapture), drained on the server tick thread.
 */
public final class DeferredWorldContactQueue {

    private DeferredWorldContactQueue() {}

    private static final Queue<DeferredWorldContactEvent> QUEUE = new ConcurrentLinkedQueue<>();

    public static void enqueue(DeferredWorldContactEvent event) {
        QUEUE.add(event);
    }

    public static List<DeferredWorldContactEvent> drainAll() {
        List<DeferredWorldContactEvent> result = new ArrayList<>();
        DeferredWorldContactEvent e;
        while ((e = QUEUE.poll()) != null) result.add(e);
        return result;
    }

    public static void clear() {
        QUEUE.clear();
    }
}
