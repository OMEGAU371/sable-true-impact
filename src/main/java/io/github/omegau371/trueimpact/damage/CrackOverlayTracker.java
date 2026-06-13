package io.github.omegau371.trueimpact.damage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 2E hotfix: tracks per-block vanilla crack overlay state (progress 0..9).
 *
 * Converts accumulated damage ratio + DamageState to a vanilla block-breaking
 * progress stage (0-9) for use with ServerLevel.destroyBlockProgress().
 *
 * CRACKED  -> 5..7 (linear interpolation over ratio 0.60..1.00)
 * CRITICAL -> 9
 * INTACT / BRUISED -> -1 (no overlay)
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
     * Maps a damage state and accumulated ratio to a vanilla crack progress value (0-9).
     * Returns -1 when no overlay should be shown (INTACT, BRUISED).
     *
     *   CRACKED  (ratio 0.60..1.00) -> 5..7  (linear, rounded)
     *   CRITICAL (ratio >= 1.00)    -> 9
     *   INTACT / BRUISED            -> -1
     */
    public static int ratioToProgress(DamageState state, double ratio) {
        return switch (state) {
            case INTACT, BRUISED -> -1;
            case CRACKED -> {
                // Linear interpolation: 0.60 -> 5, 1.00 -> 7.
                double t = (ratio - 0.60) / (1.00 - 0.60);
                t = Math.max(0.0, Math.min(1.0, t));
                yield (int) Math.round(5.0 + t * 2.0);
            }
            case CRITICAL -> 9;
        };
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
