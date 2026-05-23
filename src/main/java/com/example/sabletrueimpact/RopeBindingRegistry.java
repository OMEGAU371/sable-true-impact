/*
 *  dev.ryanhcode.sable.api.physics.object.rope.RopeHandle
 *  dev.ryanhcode.sable.sublevel.ServerSubLevel
 */
package com.example.sabletrueimpact;

import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;

// 1.0.5-fork_1: rope-connected-structure DETECTOR.
// 1.0.5-fork_29: also a rope CUTTER.
//
// RapierRopeHandleMixin mirrors every rope attachment Sable establishes (START and END — the
// far END's ServerSubLevel is a plain Java arg there) into this registry, so isRopeSubLevel()
// is a reliable object-identity test for "is this sub-level currently a rope endpoint".
//
// fork_29 — why the cutter exists: the narrow_phase crash's worst trigger is Sable SPLITTING a
// rope-CONSTRAINED structure (True Impact destroys enough of its blocks → Sable's heatmap
// splitter runs → the new fragments inherit a rope joint pointing at a rebaked/removed body →
// the joint solver explodes → extreme-Y fling → Sable removes the sub-level mid-step → stale
// narrow-phase contact → uncatchable panic). Splitting a FREE (unconstrained) structure is the
// normal, non-crashing case. So: the first fracture-grade hit on a rope structure CUTS its
// ropes (cleanly, via Sable's own RopeHandle.remove()); from then on it is a free structure and
// shatters into flying fragments normally — ropes visibly snapping first, which only adds to
// the effect. The cut is deferred through ImpactBreakQueue so it never runs mid-physics-step.
public final class RopeBindingRegistry {

    private record Key(long handle, boolean isEnd) {}

    private record Binding(int sceneId, long handle, boolean isEnd, RopeHandle rope,
                           ServerSubLevel subLevel, double x, double y, double z) {}

    private static final Map<Key, Binding> BINDINGS = new ConcurrentHashMap<>();

    // beta.5 — block IDs that act as rope ANCHORS. Per the user's empirical observation
    // (2026-05-23): "only smashing the rope-binder block itself crashes; everything else is
    // fine". The fix is to protect these specific block types from ANY destruction. Currently
    // hard-coded to Create Simulated's rope_connector; if other mods add rope anchors, this
    // could be promoted to a config list later.
    private static final Set<ResourceLocation> ROPE_ANCHOR_BLOCK_IDS = Set.of(
        ResourceLocation.fromNamespaceAndPath("simulated", "rope_connector")
    );

    // True if the block is one of the rope-anchor types we must never destroy.
    public static boolean isRopeAnchorBlockType(BlockState state) {
        if (state == null) {
            return false;
        }
        try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return ROPE_ANCHOR_BLOCK_IDS.contains(id);
        } catch (Throwable t) {
            return false;
        }
    }

    private RopeBindingRegistry() {
    }

    // True if sl is currently an attachment endpoint of any tracked rope (object identity).
    public static boolean isRopeSubLevel(ServerSubLevel sl) {
        if (sl == null || BINDINGS.isEmpty()) {
            return false;
        }
        for (Binding b : BINDINGS.values()) {
            if (b.subLevel() == sl) {
                return true;
            }
        }
        return false;
    }

    // Object overload for callsites that hold the sub-level as a plain Object (reflection paths).
    public static boolean isRopeSubLevel(Object subLevel) {
        return subLevel instanceof ServerSubLevel ssl && RopeBindingRegistry.isRopeSubLevel(ssl);
    }

    // Mirror of RapierRopeHandle.setAttachment. subLevel == null means Sable detached that
    // end → drop the binding.
    public static void record(int sceneId, long handle, boolean isEnd, RopeHandle rope,
                              ServerSubLevel subLevel, double x, double y, double z) {
        Key key = new Key(handle, isEnd);
        if (subLevel == null) {
            BINDINGS.remove(key);
            return;
        }
        BINDINGS.put(key, new Binding(sceneId, handle, isEnd, rope, subLevel, x, y, z));
        if (RopeBindingRegistry.debug()) {
            LogManager.getLogger().warn(
                "[TrueImpact] RopeBind: recorded handle={} end={} sub={} (total {})",
                handle, isEnd, System.identityHashCode(subLevel), BINDINGS.size());
        }
    }

    // RapierRopeHandle.remove() → the native rope is gone; stop tracking it.
    public static void forget(long handle) {
        BINDINGS.remove(new Key(handle, false));
        BINDINGS.remove(new Key(handle, true));
    }

    // fork_29's scheduleRopeCut was REMOVED in fork_30: calling RopeHandle.remove() to cut a
    // rope crashed natively (rapier/rope.rs:244 "Option::unwrap() on None" — many fracture
    // calls per tick scheduled the same rope's removal more than once → native double-free,
    // and it also desyncs Create Simulated's rope_connector). Cutting ropes is not a viable
    // downstream operation. The RopeHandle is still stored in each Binding for any future
    // approach that can use it read-only.

    private static boolean debug() {
        try {
            return ((Boolean) TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()).booleanValue();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
