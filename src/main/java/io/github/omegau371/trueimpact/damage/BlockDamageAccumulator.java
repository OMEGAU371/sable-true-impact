package io.github.omegau371.trueimpact.damage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-block cumulative damage accumulator for deferred impact events.
 *
 * Accumulates kinetic impact energy (kImpact) per (levelKey, posX, posY, posZ, victimBlock)
 * key. Damage from different block types at the same position is tracked in separate entries,
 * preventing cross-contamination when a block is replaced or a stale event arrives.
 *
 * Phase 2B scope: accumulation and diagnostics only. No block mutation, no crack overlay.
 * Phase 2C+ will use ratio >= 1.0 to trigger further material effects.
 *
 * Threading: all operations called on the server thread. No synchronization needed.
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class BlockDamageAccumulator {

    private BlockDamageAccumulator() {}

    /** Uniquely identifies a (dimension, block position, block type) triplet. */
    public record AccKey(String levelKey, int posX, int posY, int posZ, String victimBlock) {}

    /** Immutable snapshot of accumulated damage state for a single block. */
    public record Snapshot(
            AccKey key,
            MaterialThresholdProfile.MaterialClass materialClass,
            double accumulatedDamageJ,
            double thresholdJ,
            double lastImpactJ,
            long   lastUpdatedTick,
            int    hitCount
    ) {
        /** Ratio of accumulated damage to threshold. NaN when threshold is zero. */
        public double ratio() {
            return thresholdJ > 0.0 ? accumulatedDamageJ / thresholdJ : Double.NaN;
        }
    }

    private static final class Entry {
        final MaterialThresholdProfile.MaterialClass materialClass;
        final double thresholdJ;
        double accumulatedDamageJ;
        double lastImpactJ;
        long   lastUpdatedTick;
        int    hitCount;

        Entry(MaterialThresholdProfile.MaterialClass mc, double threshold,
              double firstImpact, long tick) {
            this.materialClass      = mc;
            this.thresholdJ         = threshold;
            this.accumulatedDamageJ = firstImpact;
            this.lastImpactJ        = firstImpact;
            this.lastUpdatedTick    = tick;
            this.hitCount           = 1;
        }

        void addImpact(double kImpact, long tick) {
            accumulatedDamageJ += kImpact;
            lastImpactJ         = kImpact;
            lastUpdatedTick     = tick;
            hitCount++;
        }

        Snapshot toSnapshot(AccKey key) {
            return new Snapshot(key, materialClass, accumulatedDamageJ, thresholdJ,
                    lastImpactJ, lastUpdatedTick, hitCount);
        }
    }

    private static final Map<AccKey, Entry> entries = new LinkedHashMap<>();
    private static Snapshot lastUpdatedSnapshot;

    /**
     * Accumulates damage from a drained DeferredDamageEvent.
     *
     * Creates a new entry if none exists for this (levelKey, pos, victimBlock) triplet;
     * otherwise adds kImpact to the running total and updates hit counters.
     *
     * Called from TrueImpactMod.onServerTickPost() before ImpactBlockApplicator.tryApply().
     * DiagnosticConfig state does not affect whether accumulation occurs.
     */
    public static void accumulate(DeferredDamageEvent event) {
        AccKey key = new AccKey(event.levelKey(),
                event.posX(), event.posY(), event.posZ(), event.victimBlock());
        Entry entry = entries.get(key);
        if (entry == null) {
            entry = new Entry(event.materialClass(), event.threshold(),
                    event.kImpact(), event.serverTick());
            entries.put(key, entry);
        } else {
            entry.addImpact(event.kImpact(), event.serverTick());
        }
        lastUpdatedSnapshot = entry.toSnapshot(key);
    }

    /**
     * Returns an immutable snapshot for the given (levelKey, pos, victimBlock), or null.
     * Used by unit tests and targeted diagnostic lookups.
     */
    public static Snapshot getSnapshot(String levelKey, int x, int y, int z, String victimBlock) {
        AccKey key = new AccKey(levelKey, x, y, z, victimBlock);
        Entry entry = entries.get(key);
        return entry != null ? entry.toSnapshot(key) : null;
    }

    /** Returns the snapshot of the most recently updated entry, or null if none. */
    public static Snapshot lastUpdatedSnapshot() { return lastUpdatedSnapshot; }

    /** Returns the number of distinct (levelKey, pos, victimBlock) entries tracked. */
    public static int entryCount() { return entries.size(); }

    /**
     * Clears all accumulated damage state.
     * Registered as a DiagnosticStateManager flush hook -- fires on server stop
     * and on /trueimpact debug all off.
     */
    public static void clear() {
        entries.clear();
        lastUpdatedSnapshot = null;
    }
}
