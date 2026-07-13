package io.github.omegau371.trueimpact.damage;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 2D: rate-limiting gate for cosmetic damage feedback (particles/sounds).
 *
 * Decides whether a feedback event should be emitted for a block position.
 * Two independent limits apply:
 *
 *   Per-block cooldown:    same position must wait PER_BLOCK_COOLDOWN_TICKS before
 *                          receiving feedback again.
 *   Global per-tick cap:   at most GLOBAL_BUDGET_PER_TICK feedback events per server tick,
 *                          preventing spam during chain impacts.
 *
 * Feedback is only emitted for CRACKED or CRITICAL damage states.
 * INTACT and BRUISED are silent.
 *
 * All MC-dependent work (particle spawning, sound playback) is done by the caller
 * (TrueImpactMod). This class is pure decision logic.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class DamageFeedbackTracker {

    private DamageFeedbackTracker() {}

    // Cooldown/budget now live in ImpactRuntimeConfig.FEEDBACK_COOLDOWN_TICKS /
    // FEEDBACK_BUDGET_PER_TICK (configurable from [advanced.calibration]); defaults
    // below match the original hardcoded 10/16.

    private static final Map<Long, Long> lastFeedbackTick = new HashMap<>();
    private static int  tickBudgetUsed;
    private static int  lastTickFeedbackCount;
    private static long currentBudgetTick = -1L;

    /**
     * Returns true and consumes a budget slot if feedback should be emitted for this position.
     *
     * All of the following must hold:
     *   1. ImpactRuntimeConfig.ENABLE_DAMAGE_FEEDBACK == true
     *   2. state is CRACKED or CRITICAL
     *   3. global per-tick budget not exhausted
     *   4. per-block cooldown has elapsed since last feedback at this position
     *
     * @param posX        block X coordinate
     * @param posY        block Y coordinate
     * @param posZ        block Z coordinate
     * @param state       accumulated DamageState for the block
     * @param serverTick  current server tick (level.getTickCount())
     */
    public static boolean shouldEmit(int posX, int posY, int posZ,
                                     DamageState state, long serverTick) {
        if (!ImpactRuntimeConfig.ENABLE_DAMAGE_FEEDBACK) return false;
        if (state != DamageState.CRACKED && state != DamageState.CRITICAL) return false;

        if (serverTick != currentBudgetTick) {
            lastTickFeedbackCount = tickBudgetUsed;
            tickBudgetUsed        = 0;
            currentBudgetTick     = serverTick;
        }
        if (tickBudgetUsed >= ImpactRuntimeConfig.FEEDBACK_BUDGET_PER_TICK) return false;

        long key  = packPos(posX, posY, posZ);
        Long last = lastFeedbackTick.get(key);
        if (last != null && serverTick - last < ImpactRuntimeConfig.FEEDBACK_COOLDOWN_TICKS) return false;

        lastFeedbackTick.put(key, serverTick);
        tickBudgetUsed++;
        return true;
    }

    /** Feedback events emitted in the most recently completed server tick. */
    public static int lastTickFeedbackCount() { return lastTickFeedbackCount; }

    /** Feedback events emitted so far in the current tick. */
    public static int currentTickFeedbackCount() { return tickBudgetUsed; }

    /**
     * Clears all rate-limit state.
     * Registered as a DiagnosticStateManager flush hook -- fires on server stop
     * and on /trueimpact debug all off.
     */
    public static void clear() {
        lastFeedbackTick.clear();
        tickBudgetUsed        = 0;
        lastTickFeedbackCount = 0;
        currentBudgetTick     = -1L;
    }

    // Packs block coordinates into a 64-bit key (same bit layout as BlockPos.asLong()).
    // X and Z use 26 bits (range -33M..+33M), Y uses 12 bits (range -2048..+2047).
    static long packPos(int x, int y, int z) {
        return ((long)x & 0x3FFFFFFL)
             | (((long)z & 0x3FFFFFFL) << 26)
             | (((long)y & 0xFFFL)     << 52);
    }
}
