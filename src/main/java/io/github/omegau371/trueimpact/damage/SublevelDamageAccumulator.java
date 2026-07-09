package io.github.omegau371.trueimpact.damage;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-sublevel-block cumulative damage accumulator (Phase 3A crack overlays).
 *
 * Keyed by (runtimeId, localX, localY, localZ) -- body-local block coordinates.
 * effectiveJ per hit = min(kImpact, breakThresholdJ), so a single hit at or above
 * breakThresholdJ saturates the accumulator immediately (ratio = 1.0 → CRITICAL),
 * while repeated sub-threshold hits accumulate proportionally.
 *
 * Fake breaker IDs use range [Integer.MIN_VALUE, Integer.MIN_VALUE + 0x1FFFFFFF],
 * which does not overlap with CrackOverlayTracker's range (Integer.MIN_VALUE/2 .. -1).
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class SublevelDamageAccumulator {

    private SublevelDamageAccumulator() {}

    /**
     * Key includes materialClass so that runtimeId reuse across tests (or successive sublevels
     * with different block types) never blends damage from different materials together.
     */
    public record AccKey(int runtimeId, int localX, int localY, int localZ,
                         MaterialThresholdProfile.MaterialClass materialClass) {}

    public record Snapshot(
            AccKey key,
            MaterialThresholdProfile.MaterialClass materialClass,
            double accumulatedJ,
            double breakThresholdJ,
            int hitCount,
            DamageState damageState
    ) {
        public double ratio() {
            return breakThresholdJ > 0.0 ? accumulatedJ / breakThresholdJ : Double.NaN;
        }
    }

    private static final int SUBLEVEL_BREAKER_BASE = Integer.MIN_VALUE;

    private static final class Entry {
        final MaterialThresholdProfile.MaterialClass materialClass;
        final double breakThresholdJ;
        double accumulatedJ;
        int hitCount;
        DamageState damageState;
        long lastHitTick;
        int maxProgressShown = -1;

        Entry(MaterialThresholdProfile.MaterialClass mc, double breakThresholdJ, double firstEffective) {
            this.materialClass   = mc;
            this.breakThresholdJ = breakThresholdJ;
            this.accumulatedJ    = firstEffective;
            this.hitCount        = 1;
            this.damageState     = DamageState.of(firstEffective / breakThresholdJ);
            this.lastHitTick     = Long.MIN_VALUE;
        }

        void addHit(double effectiveJ) {
            accumulatedJ += effectiveJ;
            hitCount++;
            damageState = DamageState.of(accumulatedJ / breakThresholdJ);
        }

        /**
         * Stress relaxation: accumulated damage decays exponentially with the configured
         * half-life. A real crash delivers its energy within a few ticks (negligible decay);
         * scattered sub-critical hits spread over seconds fade away instead of stacking forever.
         */
        void decayTo(long nowTick, int halfLifeTicks) {
            if (halfLifeTicks <= 0 || lastHitTick == Long.MIN_VALUE || nowTick <= lastHitTick) return;
            accumulatedJ *= Math.pow(0.5, (nowTick - lastHitTick) / (double) halfLifeTicks);
            damageState = DamageState.of(accumulatedJ / breakThresholdJ);
        }

        Snapshot toSnapshot(AccKey key) {
            return new Snapshot(key, materialClass, accumulatedJ, breakThresholdJ, hitCount, damageState);
        }
    }

    private static final Map<AccKey, Entry> entries = new HashMap<>();

    /**
     * Monotonic crack-overlay progress: accumulated damage decays between hits (stress
     * relaxation), so a later hit can compute a LOWER overlay progress — sending it makes
     * the crack visually heal mid-bombardment ("crack reversal"). Returns the highest
     * progress reached for this key and records the given one if higher; entry removal
     * (block destroyed) resets naturally.
     */
    public static int monotonicProgress(AccKey key, int progress) {
        Entry e = entries.get(key);
        if (e == null) return progress;
        if (progress > e.maxProgressShown) e.maxProgressShown = progress;
        return e.maxProgressShown;
    }

    /**
     * Accumulates damage for the given sublevel block and returns the updated snapshot.
     *
     * effectiveJ = min(kImpact, breakThresholdJ): a single hit at or above breakThresholdJ
     * immediately saturates (ratio ≥ 1.0 → CRITICAL); sub-threshold hits accumulate.
     * breakThresholdJ is pre-computed by the caller and may include ConfinementFactor scaling.
     * Creates a new entry on first hit; adds effectiveJ to existing entry on subsequent hits.
     */
    public static Snapshot accumulate(
            int runtimeId, int localX, int localY, int localZ,
            MaterialThresholdProfile.MaterialClass mc,
            double kImpact, double breakThresholdJ) {
        double effectiveJ = Math.min(kImpact, breakThresholdJ);
        AccKey key = new AccKey(runtimeId, localX, localY, localZ, mc);
        Entry e = entries.get(key);
        if (e == null) {
            e = new Entry(mc, breakThresholdJ, effectiveJ);
            entries.put(key, e);
        } else {
            e.addHit(effectiveJ);
        }
        return e.toSnapshot(key);
    }

    /**
     * Tick-aware accumulate with elastic floor and stress relaxation (the production path).
     *
     * When {@code ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION} is false: single-hit mode
     * -- no persistent state. This hit alone is judged against breakThresholdJ; no carry-over,
     * no crack overlay (the caller skips overlay dispatch entirely in this mode).
     *
     * Elastic floor (fatigue limit): a single hit below
     * {@code ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR × breakThresholdJ} is a purely
     * elastic contact — it causes ZERO damage and does not create an entry. Real materials
     * do not accumulate plastic damage from stresses below the yield point; without this,
     * gentle repeated contacts (waving a structure) grind any block to CRITICAL over time.
     *
     * Stress relaxation: existing accumulated damage decays exponentially
     * (half-life {@code SUBLEVEL_DAMAGE_HALF_LIFE_TICKS}) before the new hit is applied.
     * A genuine crash lands all its energy within a few ticks and is unaffected.
     */
    public static Snapshot accumulate(
            int runtimeId, int localX, int localY, int localZ,
            MaterialThresholdProfile.MaterialClass mc,
            double kImpact, double breakThresholdJ, long serverTick) {
        AccKey key = new AccKey(runtimeId, localX, localY, localZ, mc);

        if (!ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION) {
            entries.remove(key);
            double effectiveJ = Math.min(kImpact, breakThresholdJ);
            return new Snapshot(key, mc, effectiveJ, breakThresholdJ, 1,
                    DamageState.of(effectiveJ / breakThresholdJ));
        }

        Entry e = entries.get(key);
        if (e != null) {
            e.decayTo(serverTick, ImpactRuntimeConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS);
        }
        double elasticFloorJ = ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR * breakThresholdJ;
        if (kImpact < elasticFloorJ) {
            // Elastic contact: no damage. Report current (possibly decayed) state.
            return e != null ? e.toSnapshot(key)
                             : new Snapshot(key, mc, 0.0, breakThresholdJ, 0, DamageState.INTACT);
        }
        double effectiveJ = Math.min(kImpact, breakThresholdJ);
        if (e == null) {
            e = new Entry(mc, breakThresholdJ, effectiveJ);
            entries.put(key, e);
        } else {
            e.addHit(effectiveJ);
        }
        e.lastHitTick = serverTick;
        return e.toSnapshot(key);
    }

    /** Overload without explicit threshold — uses default (no confinement). */
    public static Snapshot accumulate(
            int runtimeId, int localX, int localY, int localZ,
            MaterialThresholdProfile.MaterialClass mc,
            double kImpact) {
        double breakThresholdJ =
                MaterialThresholdProfile.threshold(mc) * MaterialThresholdProfile.breakMultiplier(mc);
        return accumulate(runtimeId, localX, localY, localZ, mc, kImpact, breakThresholdJ);
    }

    /**
     * Returns a stable, always-negative fake breaker ID for this AccKey.
     * Range: [Integer.MIN_VALUE, Integer.MIN_VALUE + 0x1FFFFFFF].
     */
    public static int fakeBreakerIdFor(AccKey key) {
        return SUBLEVEL_BREAKER_BASE + (key.hashCode() & 0x1FFFFFFF);
    }

    /**
     * Returns the current snapshot for a specific sublevel block, or null if no entry exists.
     * Used by GameTests to assert crack state without breaking the block.
     */
    public static Snapshot getSnapshot(int runtimeId, int localX, int localY, int localZ,
                                        MaterialThresholdProfile.MaterialClass mc) {
        AccKey key = new AccKey(runtimeId, localX, localY, localZ, mc);
        Entry e = entries.get(key);
        return e != null ? e.toSnapshot(key) : null;
    }

    /** Removes the accumulator entry for a single block. Call after the block is destroyed. */
    public static void removeEntry(AccKey key) {
        entries.remove(key);
    }

    /** Removes all entries for a given runtimeId (call when sublevel is removed). */
    public static void clearForRuntimeId(int runtimeId) {
        entries.keySet().removeIf(k -> k.runtimeId() == runtimeId);
    }

    /** Clears all state. Registered as a DiagnosticStateManager flush hook. */
    public static void clear() {
        entries.clear();
    }
}
