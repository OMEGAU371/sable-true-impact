package io.github.omegau371.trueimpact.sable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-substep registry of world block contacts from BlockSubLevelCollisionCallback.onCollision.
 *
 * DiagnosticCallbackWrapperMixin writes into this registry for EVERY block that is hit
 * by a physics body (not just FRAGILE blocks). SableImpactCapture reads it after
 * clearCollisions() to distribute the total impact energy across all contacted blocks.
 *
 * Lifecycle (per substep):
 *   SableEventBridge.onPreStep (substep 0) -> clear()
 *   Rapier3D.step() callbacks              -> record(...)
 *   SableImpactCapture.process()           -> snapshotAndClear()
 *
 * Threading: all calls happen on the physics/server thread. No synchronization needed.
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class TrueImpactBlockCallbackRegistry {

    private TrueImpactBlockCallbackRegistry() {}

    public record BlockContact(int posX, int posY, int posZ, String blockId, double speed) {}

    // Keyed by (posX, posY, posZ) to deduplicate multiple Rapier contacts on the same block.
    // When a block appears multiple times (e.g. two contact points on the same face),
    // we keep the entry with the highest speed.
    private static final Map<Long, BlockContact> contactsByPos = new LinkedHashMap<>();

    /**
     * Records a block contact from sable$onCollision.
     * Embedded-level coords (~4e7 offset) are filtered out.
     * Duplicate contacts on the same block retain the highest speed.
     */
    public static void record(int x, int y, int z, String blockId, double speed) {
        // Embedded-level coords live at ~4e7; world blocks stay within ~1e6.
        if (Math.abs(x) > 1_000_000 || Math.abs(z) > 1_000_000) return;
        long key = posKey(x, y, z);
        BlockContact existing = contactsByPos.get(key);
        if (existing == null || speed > existing.speed()) {
            contactsByPos.put(key, new BlockContact(x, y, z, blockId, speed));
        }
    }

    /**
     * Returns a snapshot of all registered contacts and clears the registry.
     * Called once per substep by SableImpactCapture after consuming the data.
     */
    public static List<BlockContact> snapshotAndClear() {
        List<BlockContact> copy = new ArrayList<>(contactsByPos.values());
        contactsByPos.clear();
        return copy;
    }

    /** True if at least one contact has been recorded since last clear. */
    public static boolean hasContacts() {
        return !contactsByPos.isEmpty();
    }

    /** Clears all contacts without returning them. Called from onPreStep at substep 0. */
    public static void clear() {
        contactsByPos.clear();
    }

    private static long posKey(int x, int y, int z) {
        // Pack (x+512k, y+512, z+512k) into a 64-bit key.
        // Ranges: x/z within ±1M (already filtered), y within ±512 (world bounds).
        return ((long)(x & 0x3FFFFF) << 41)
             | ((long)(y & 0x7FF)    << 22)
             | ((long)(z & 0x3FFFFF));
    }
}
