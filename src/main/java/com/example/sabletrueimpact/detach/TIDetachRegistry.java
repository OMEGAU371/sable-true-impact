/*
 *  dev.ryanhcode.sable.api.physics.PhysicsPipeline
 *  dev.ryanhcode.sable.sublevel.ServerSubLevel
 *  dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.Level
 */
package com.example.sabletrueimpact.detach;

import com.example.sabletrueimpact.PhysicsStepGate;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// 1.2.0 SD-port — registry of debris sub-levels True Impact itself creates via the detach
// mechanism (carve a cluster + SubLevelAssemblyHelper.assembleBlocks, see SubLevelDetacher).
//
// Why this exists: True Impact's prior fracture path destroyed blocks of a sub-level and let
// Sable's heatmap split the structure if it became disconnected. That works for free
// structures but crashes for rope-CONSTRAINED ones — the resulting fragments inherit a rope
// joint pointing at a rebaked body, the joint explodes, the body is flung to an extreme Y,
// Sable removes it mid-step, and a stale narrow-phase contact pair panics (`narrow_phase.rs
// :1115`, upstream sable#950). The SD-style approach sidesteps that entirely: TI itself
// assembles a fresh small debris sub-level from a cluster of blocks — Sable's heatmap-split
// is never triggered. This registry then tracks every such debris body so we can:
//
//   - cap the simultaneous count (a hard ceiling prevents runaway debris accumulation when
//     the player smashes structures in a chain)
//   - evict the oldest when full
//   - auto-despawn each after a lifetime expires (free physics resources)
//   - clean up everything on server stop / level unload
//
// The Sable: Destructive (MIT) DetachRegistry follows the same shape and is the architectural
// reference here. The code is our own; the model is the proven one.
//
// Mid-step safety: pipeline.remove() is the exact native primitive that, when called while
// Rapier's narrow-phase still holds a contact pair, panics. tick() therefore re-checks
// PhysicsStepGate.isMidStep() and bails entirely if a step is somehow still in progress —
// defense in depth on top of being driven by ServerTickEvent.Post (which is normally clear).
public final class TIDetachRegistry {

    private static final Logger LOG = LogManager.getLogger("TIDetach");
    private static final Deque<Entry> ACTIVE = new ArrayDeque<>();
    private static long tickCounter = 0L;
    private static final int MIN_LIFETIME_TICKS = 20;
    private static final int MIN_CAP = 8;

    private TIDetachRegistry() {
    }

    // Called immediately after a fresh debris sub-level has been assembled. Enforces the cap
    // by despawning the oldest entry if we are already at the limit. Pass the level so we can
    // resolve the physics pipeline at despawn time without needing it stored elsewhere.
    public static void register(ServerLevel level, ServerSubLevel debris, int maxActive) {
        if (level == null || debris == null) {
            return;
        }
        int cap = Math.max(MIN_CAP, maxActive);
        int evicted = 0;
        while (ACTIVE.size() >= cap) {
            Entry old = ACTIVE.pollFirst();
            if (old == null) break;
            ++evicted;
            despawn(old, "cap-evict");
        }
        ACTIVE.addLast(new Entry(
            new WeakReference<>(debris),
            new WeakReference<>(level),
            tickCounter));
        LOG.info("[beta] registered debris (active={}/{}, evicted={}, debris-id={})",
            ACTIVE.size(), cap, evicted, System.identityHashCode(debris));
    }

    // Driven once per server tick from TrueImpactMod. Walks entries: drops anything whose weak
    // ref is gone, despawns anything older than `maxLifetimeTicks`. Skipped entirely if a
    // physics step is in progress — pipeline.remove() must never run mid-step.
    public static void tick(int maxLifetimeTicks) {
        if (PhysicsStepGate.isMidStep()) {
            return;
        }
        long lifetime = Math.max((long) MIN_LIFETIME_TICKS, (long) maxLifetimeTicks);
        long cutoff = ++tickCounter - lifetime;
        Iterator<Entry> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            ServerSubLevel sub = e.ref.get();
            if (sub == null) {
                // GC ate it; just drop the bookkeeping.
                it.remove();
                continue;
            }
            if (e.spawnedAtTick >= cutoff) {
                continue;
            }
            it.remove();
            despawn(e, "lifetime");
        }
    }

    // Resolve the level's physics system + pipeline at despawn time (no cached references that
    // could outlive the level). pipeline.remove() does the real work — the same primitive
    // Sable itself calls for clean sub-level disposal.
    private static void despawn(Entry e, String reason) {
        try {
            ServerSubLevel sub = e.ref.get();
            ServerLevel lvl = e.levelRef.get();
            if (sub == null || lvl == null || sub.isRemoved()) {
                LOG.info("[beta] despawn skipped (reason={}, sub-gone={}, removed={})",
                    reason, sub == null, sub != null && sub.isRemoved());
                return;
            }
            SubLevelPhysicsSystem sys = SubLevelPhysicsSystem.get((Level) lvl);
            if (sys == null) return;
            PhysicsPipeline pipe = sys.getPipeline();
            if (pipe == null) return;
            long age = tickCounter - e.spawnedAtTick;
            LOG.info("[beta] despawn → pipe.remove (reason={}, age={}t, debris-id={})",
                reason, age, System.identityHashCode(sub));
            pipe.remove(sub);
            LOG.info("[beta] despawn done");
        } catch (Throwable t) {
            LOG.warn("[beta] despawn threw", t);
        }
    }

    public static int size() {
        return ACTIVE.size();
    }

    // Called by lifecycle hooks (server start/stop). Drops bookkeeping wholesale; Sable handles
    // sub-level teardown on world unload itself.
    public static void clear() {
        ACTIVE.clear();
        tickCounter = 0L;
    }

    // Level unload: drop entries belonging to that level. Other levels' entries stay tracked.
    public static void clearForLevel(ServerLevel target) {
        if (target == null) {
            return;
        }
        Iterator<Entry> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            ServerLevel ref = e.levelRef.get();
            // Drop if (a) the ref is gone, or (b) this entry belongs to the unloading level.
            if (ref == null || ref == target) {
                it.remove();
            }
        }
    }

    private record Entry(WeakReference<ServerSubLevel> ref,
                         WeakReference<ServerLevel> levelRef,
                         long spawnedAtTick) {
    }
}
