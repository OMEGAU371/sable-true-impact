package io.github.omegau371.trueimpact.damage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks per-block vanilla crack overlay state (progress 0..9).
 *
 * Converts accumulated damage ratio + DamageState to a vanilla block-breaking
 * progress stage (0-9) for use with ServerLevel.destroyBlockProgress().
 *
 * INTACT               -> -1 (no overlay)
 * CRITICAL             -> 9  (always max, regardless of ratio)
 * BRUISED / CRACKED    -> ratio-driven step table (0.10 per step):
 *   ratio < 0.10 -> -1 | 0.10..0.20 -> 0 | 0.20..0.30 -> 1 | ...
 *   0.90..1.00 -> 8    | >= 1.00     -> 9
 *
 * Rate-limited: suppresses re-sends of the same progress for the same block
 * within PER_BLOCK_UPDATE_COOLDOWN_TICKS to avoid packet spam.
 *
 * Fake breaker IDs are stable and always negative to avoid collision with
 * MC entity IDs (which are small positive integers).
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class CrackOverlayTracker {

    private CrackOverlayTracker() {}

    // Cooldown before re-sending the same progress for the same block (ticks).
    static final int PER_BLOCK_UPDATE_COOLDOWN_TICKS = 10;

    // Negative base for fake breaker IDs.
    // Range: [Integer.MIN_VALUE/2, Integer.MIN_VALUE/2 + 0x3FFFFFFF].
    // Always negative, never overlaps with MC entity IDs (small positive ints).
    private static final int FAKE_BREAKER_BASE = Integer.MIN_VALUE / 2;

    private static final Map<BlockDamageAccumulator.AccKey, CrackEntry> states = new HashMap<>();

    private static long totalCrackOverlayUpdates;
    private static int  lastCrackProgress = -1;

    private static final class CrackEntry {
        int  lastProgress;
        long lastTick;
        final int fakeBreakerId;

        CrackEntry(int progress, long tick, int fakeBreakerId) {
            this.lastProgress  = progress;
            this.lastTick      = tick;
            this.fakeBreakerId = fakeBreakerId;
        }
    }

    /**
     * Maps accumulated damage ratio + DamageState to a vanilla crack progress (0-9), or -1.
     *
     *   INTACT              -> -1 always
     *   CRITICAL            -> 9 always
     *   BRUISED / CRACKED   -> ratio step table (each 0.10 band = one step):
     *     < 0.10  -> -1
     *     0.10..  ->  0, 0.20.. -> 1, 0.30.. -> 2, 0.40.. -> 3, 0.50.. -> 4
     *     0.60..  ->  5, 0.70.. -> 6, 0.80.. -> 7, 0.90.. -> 8, >= 1.00 -> 9
     */
    public static int ratioToProgress(DamageState state, double ratio) {
        if (state == DamageState.INTACT)   return -1;
        if (state == DamageState.CRITICAL) return 9;
        // BRUISED and CRACKED: pure ratio-driven step table
        if (ratio < 0.10) return -1;
        if (ratio < 0.20) return 0;
        if (ratio < 0.30) return 1;
        if (ratio < 0.40) return 2;
        if (ratio < 0.50) return 3;
        if (ratio < 0.60) return 4;
        if (ratio < 0.70) return 5;
        if (ratio < 0.80) return 6;
        if (ratio < 0.90) return 7;
        if (ratio < 1.00) return 8;
        return 9;
    }

    /**
     * Returns a stable, always-negative fake breaker ID for this AccKey.
     * Negative ensures no collision with MC entity IDs (small positive ints).
     * Deterministic for the same (levelKey, pos, victimBlock) tuple.
     */
    public static int fakeBreakerIdFor(BlockDamageAccumulator.AccKey key) {
        return FAKE_BREAKER_BASE + (key.hashCode() & 0x3FFFFFFF);
    }

    /**
     * Decides whether to send a crack overlay update for this block.
     *
     * Returns the crack progress (0-9) to send, or -1 if no update is needed.
     * Updates internal tracking state when it returns >= 0.
     *
     * Suppressed when:
     * - ImpactRuntimeConfig.ENABLE_VANILLA_CRACK_OVERLAY is false
     * - The state maps to -1 (INTACT/BRUISED)
     * - Same progress already sent within the cooldown window
     */
    public static int tryUpdate(BlockDamageAccumulator.AccKey key, DamageState state,
                                double ratio, long serverTick) {
        if (!ImpactRuntimeConfig.ENABLE_VANILLA_CRACK_OVERLAY) return -1;
        int newProgress = ratioToProgress(state, ratio);
        if (newProgress < 0) return -1;

        CrackEntry entry = states.get(key);
        if (entry != null) {
            if (entry.lastProgress == newProgress
                    && serverTick - entry.lastTick < PER_BLOCK_UPDATE_COOLDOWN_TICKS) {
                return -1;
            }
            entry.lastProgress = newProgress;
            entry.lastTick     = serverTick;
        } else {
            states.put(key, new CrackEntry(newProgress, serverTick, fakeBreakerIdFor(key)));
        }

        totalCrackOverlayUpdates++;
        lastCrackProgress = newProgress;
        return newProgress;
    }

    /** Number of distinct blocks currently tracked with an active crack overlay. */
    public static int activeCrackOverlays() {
        return states.size();
    }

    public static long totalCrackOverlayUpdates() {
        return totalCrackOverlayUpdates;
    }

    public static int lastCrackProgress() {
        return lastCrackProgress;
    }

    /**
     * Returns all active crack overlay entries as WorldClearAction records, then clears state.
     *
     * Use from the damage-clear command so the caller can send
     * destroyBlockProgress(fakeBreakerId, pos, -1) to remove visual overlays from the world.
     */
    public static List<WorldClearAction> drainForClear() {
        List<WorldClearAction> actions = new ArrayList<>(states.size());
        for (Map.Entry<BlockDamageAccumulator.AccKey, CrackEntry> e : states.entrySet()) {
            actions.add(new WorldClearAction(e.getKey(), e.getValue().fakeBreakerId));
        }
        clear();
        return actions;
    }

    /**
     * Clears internal tracking state without producing world-clear actions.
     * Visual overlays already sent to clients are NOT removed by this call.
     * Registered as a DiagnosticStateManager flush hook.
     */
    public static void clear() {
        states.clear();
        totalCrackOverlayUpdates = 0L;
        lastCrackProgress        = -1;
    }

    /** Pair of AccKey + fakeBreakerId needed to clear a crack overlay from the world. */
    public record WorldClearAction(BlockDamageAccumulator.AccKey key, int fakeBreakerId) {}
}
