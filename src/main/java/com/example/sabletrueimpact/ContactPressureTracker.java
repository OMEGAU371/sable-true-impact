package com.example.sabletrueimpact;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 1.1.2 — per-sub-level contact-count tracker for HardnessFragileCallback's
 * pressure normalization.
 *
 * <p>Problem: a tracked vehicle (large flat contact area) generates one
 * {@code HardnessFragileCallback.sable$onCollision} per tread cell touching
 * the ground. Each callback gets the full sub-level mass and velocity,
 * computes the full kinetic energy, and applies that energy to its terrain
 * block. With 20+ treads on grass, the same kinetic-energy budget gets
 * spent 20× per tick, easily exceeding the HEAVY_BREAK threshold on every
 * cell. Result: tanks dig trenches just by driving.
 *
 * <p>Fix: track contacts per sub-level per tick. When a callback fires for a
 * sub-level we've seen before, divide its kineticEnergy by
 * {@code max(1, smoothedContactCount^exponent)}. This is the same
 * pressure = force / area normalization the {@link ClippingDamageScanner}
 * already does — wide flat contacts get a soft per-cell load, sharp single
 * contacts get the full punch.
 *
 * <p>Uses an EMA (50% blend) of the previous tick's count, because we can't
 * know the current tick's final count when the FIRST callback fires. The
 * tradeoff: the first contact on a brand-new sub-level still gets full
 * energy (no history to divide by), but as soon as a tank settles into a
 * steady N contacts/tick, the smoothing converges.
 */
public final class ContactPressureTracker {
    /** Per-sub-level contact stats. Keyed by {@code ServerSubLevel.getRuntimeId()}. */
    private static final Map<Integer, Stats> SUBLEVEL_STATS = new ConcurrentHashMap<>();

    private ContactPressureTracker() {}

    /**
     * Records one contact for this sub-level on this game tick. Returns the
     * energy divisor to apply: {@code max(1, smoothedCount^exponent)}.
     *
     * <p>Callers should multiply kineticEnergy by {@code 1 / divisor} (or
     * equivalently divide) before threshold checks.
     */
    public static double recordAndGetDivisor(int sublevelRuntimeId, long gameTime, double exponent) {
        Stats s = SUBLEVEL_STATS.computeIfAbsent(sublevelRuntimeId, k -> new Stats());
        synchronized (s) {
            if (s.lastTick != gameTime) {
                // Tick boundary — fold last tick's count into the smoothed estimate.
                if (s.lastTick >= 0L) {
                    s.smoothedCount = 0.5 * s.smoothedCount + 0.5 * (double) s.currentTickCount;
                }
                s.currentTickCount = 0;
                s.lastTick = gameTime;
            }
            s.currentTickCount++;
            double basis = Math.max(s.smoothedCount, 1.0);
            return Math.max(1.0, Math.pow(basis, exponent));
        }
    }

    /** Drop stale entries. Called periodically to keep the map from growing forever. */
    public static void prune(long currentGameTime, long maxStaleTicks) {
        SUBLEVEL_STATS.entrySet().removeIf(e -> currentGameTime - e.getValue().lastTick > maxStaleTicks);
    }

    /** Visible for diagnostics. */
    public static int size() {
        return SUBLEVEL_STATS.size();
    }

    private static final class Stats {
        long lastTick = -1L;
        int currentTickCount = 0;
        double smoothedCount = 1.0;
    }
}
