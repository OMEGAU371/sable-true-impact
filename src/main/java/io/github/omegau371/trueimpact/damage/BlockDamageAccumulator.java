package io.github.omegau371.trueimpact.damage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-block cumulative damage accumulator for deferred impact events.
 *
 * Phase 2C: accumulates bounded effective damage (EffectiveDamageModel) rather than raw
 * kImpact. Each entry is keyed on (levelKey, posX, posY, posZ, victimBlock) so damage
 * from different block types at the same position stays in separate entries.
 *
 * Effective damage = min(rawKImpact, threshold * capMultiplier).
 * Raw kImpact is preserved per-hit for diagnostic display.
 * DamageState classifies the ratio (accumulatedEffective / threshold) for status coloring.
 *
 * Phase 2C scope: accumulation + diagnostics only. No block mutation for STONE/WOOD/etc.
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
            AccKey     key,
            MaterialThresholdProfile.MaterialClass materialClass,
            double     accumulatedEffectiveDamageJ,
            double     thresholdJ,
            double     lastRawImpactJ,
            double     lastEffectiveDamageJ,
            long       lastUpdatedTick,
            int        hitCount,
            DamageState damageState
    ) {
        /** Ratio of accumulated effective damage to threshold. NaN when threshold is zero. */
        public double ratio() {
            return thresholdJ > 0.0 ? accumulatedEffectiveDamageJ / thresholdJ : Double.NaN;
        }
    }

    private static final class Entry {
        final MaterialThresholdProfile.MaterialClass materialClass;
        final double thresholdJ;
        double     accumulatedEffectiveDamageJ;
        double     lastRawImpactJ;
        double     lastEffectiveDamageJ;
        long       lastUpdatedTick;
        int        hitCount;
        DamageState damageState;

        Entry(MaterialThresholdProfile.MaterialClass mc, double threshold,
              double rawImpact, double effectiveDamage, long tick) {
            this.materialClass              = mc;
            this.thresholdJ                 = threshold;
            this.accumulatedEffectiveDamageJ = effectiveDamage;
            this.lastRawImpactJ             = rawImpact;
            this.lastEffectiveDamageJ       = effectiveDamage;
            this.lastUpdatedTick            = tick;
            this.hitCount                   = 1;
            this.damageState                = DamageState.of(effectiveDamage / threshold);
        }

        void addImpact(double rawImpact, double effectiveDamage, long tick) {
            accumulatedEffectiveDamageJ += effectiveDamage;
            lastRawImpactJ               = rawImpact;
            lastEffectiveDamageJ         = effectiveDamage;
            lastUpdatedTick              = tick;
            hitCount++;
            damageState = DamageState.of(accumulatedEffectiveDamageJ / thresholdJ);
        }

        Snapshot toSnapshot(AccKey key) {
            return new Snapshot(key, materialClass, accumulatedEffectiveDamageJ, thresholdJ,
                    lastRawImpactJ, lastEffectiveDamageJ, lastUpdatedTick, hitCount, damageState);
        }
    }

    private static final Map<AccKey, Entry> entries = new LinkedHashMap<>();
    private static Snapshot lastUpdatedSnapshot;

    /**
     * Accumulates bounded effective damage from a drained DeferredDamageEvent.
     *
     * Calls EffectiveDamageModel.compute() to cap raw kImpact per material class.
     * Creates a new entry if none exists for this (levelKey, pos, victimBlock) triplet;
     * otherwise adds effectiveDamageJ to the running total.
     *
     * Called from TrueImpactMod.onServerTickPost() before ImpactBlockApplicator.tryApply().
     * DiagnosticConfig state does not affect whether accumulation occurs.
     */
    public static void accumulate(DeferredDamageEvent event) {
        EffectiveDamageModel.Result eff = EffectiveDamageModel.compute(
                event.kImpact(), event.materialClass(), event.threshold());
        AccKey key = new AccKey(event.levelKey(),
                event.posX(), event.posY(), event.posZ(), event.victimBlock());
        Entry entry = entries.get(key);
        if (entry == null) {
            entry = new Entry(event.materialClass(), event.threshold(),
                    eff.rawImpactJ(), eff.effectiveDamageJ(), event.serverTick());
            entries.put(key, entry);
        } else {
            entry.addImpact(eff.rawImpactJ(), eff.effectiveDamageJ(), event.serverTick());
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
