/*
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.event.tick.ServerTickEvent
 */
package com.example.sabletrueimpact;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// fork_25: SD-pattern deferred destruction queue.
//
// The block-collision callback (HardnessFragileCallback) runs DURING Sable's physics step
// (invoked from native inside Rapier3D.step). Mutating the world there is unsafe. The pre-fork_25
// code deferred via MinecraftServer.execute(), but that task queue drains at loosely-defined
// points (including managedBlock waits mid-tick), not strictly at the tick boundary.
//
// Sable: Destructive (a same-ecosystem MIT add-on) survives the rope/balloon smash scenario that
// crashes True Impact. Studying it showed the difference is discipline, not a rope-specific fix:
// SD performs ALL world mutation strictly on ServerTickEvent.Post — well after the physics step.
// This queue gives True Impact the same property: the mid-step callback only enqueues here, and
// onServerTick (Post) drains it. Architecture mirrors a common pattern; the code is our own.
//
// Bounded (capacity) + per-tick drain budget so a burst of contacts can never flood it.
public final class ImpactBreakQueue {
    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger SIZE = new AtomicInteger();
    private static final int CAPACITY = 8192;
    private static final int DRAIN_BUDGET = 1024;

    private ImpactBreakQueue() {
    }

    // Called from the mid-physics-step collision callback. Defers `action` to the next
    // ServerTickEvent.Post. Drops silently if the queue is at capacity.
    public static void enqueue(Runnable action) {
        if (action == null) {
            return;
        }
        if (SIZE.get() >= CAPACITY) {
            return;
        }
        SIZE.incrementAndGet();
        QUEUE.offer(action);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Runnable r;
        int drained = 0;
        while (drained < DRAIN_BUDGET && (r = QUEUE.poll()) != null) {
            SIZE.decrementAndGet();
            ++drained;
            try {
                r.run();
            } catch (RuntimeException | LinkageError t) {
                // One bad action must never abort the rest of the drain.
            }
        }
    }

    public static void clear() {
        QUEUE.clear();
        SIZE.set(0);
    }
}
