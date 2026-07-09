package io.github.omegau371.trueimpact.damage;

import java.util.HashSet;
import java.util.Set;

/**
 * Phase 2E: converts a BlockDamageAccumulator.Snapshot into a MaterialResponsePlan.
 *
 * plan() is a pure function (no state is modified, no world access).
 * Side-effecting state (debris-dedup set, counters) is updated by the caller
 * (TrueImpactMod) via markDebrisDropped() and recordExecuted().
 *
 * Rules:
 *   INTACT / BRUISED                        -> NONE
 *   CRACKED                                 -> COSMETIC_CRACK
 *   CRITICAL + SOFT_SOIL                   -> COMPACT_SOFT_SOIL
 *   CRITICAL + STONE / GENERIC             -> DROP_DEBRIS + futureBreakEligible=true
 *   CRITICAL + WOOD / METAL / HIGH_STRENGTH -> FUTURE_BREAK_ELIGIBLE (no debris in Phase 2E)
 *
 * Debris deduplication: markDebrisDropped(key) returns true only on the FIRST call
 * for a given AccKey. Subsequent calls return false -- debris is never re-dropped for
 * the same (level, pos, victimBlock) combination within the server session.
 *
 * ImpactRuntimeConfig.ENABLE_DEBRIS_DROPS gates the actual drop in TrueImpactMod;
 * this class does not read it (the plan is produced regardless of that flag).
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class MaterialResponsePlanner {

    private MaterialResponsePlanner() {}

    // -- debris deduplication (one drop per AccKey per session) -------------------

    private static final Set<BlockDamageAccumulator.AccKey> debrisDroppedKeys  = new HashSet<>();

    // -- break deduplication (one destroy per AccKey per session) -----------------

    private static final Set<BlockDamageAccumulator.AccKey> breakScheduledKeys = new HashSet<>();

    /**
     * Marks that a block-break has been scheduled for this accumulator key.
     *
     * Returns true on the FIRST call for this key (the caller should destroy the block).
     * Returns false on subsequent calls (skip -- already broken/scheduled).
     * Increments totalBlocksBroken counter on first call.
     */
    public static boolean markBreakScheduled(BlockDamageAccumulator.AccKey key) {
        boolean first = breakScheduledKeys.add(key);
        if (first) totalBlocksBroken++;
        return first;
    }

    /** Returns true if a break has already been scheduled for this accumulator key. */
    public static boolean hasBreakScheduled(BlockDamageAccumulator.AccKey key) {
        return breakScheduledKeys.contains(key);
    }

    /**
     * Marks that debris has been dropped for this accumulator key.
     *
     * Returns true on the FIRST call for this key (the caller should drop debris).
     * Returns false on subsequent calls (skip -- already dropped).
     * Increments totalDebrisDropped counter on first call.
     */
    public static boolean markDebrisDropped(BlockDamageAccumulator.AccKey key) {
        boolean first = debrisDroppedKeys.add(key);
        if (first) totalDebrisDropped++;
        return first;
    }

    /** Returns true if debris has already been dropped for this accumulator key. */
    public static boolean hasDroppedDebris(BlockDamageAccumulator.AccKey key) {
        return debrisDroppedKeys.contains(key);
    }

    // -- execution counters -------------------------------------------------------

    private static long totalResponsesPlanned;    // non-NONE plan executions
    private static long totalDebrisDropped;        // first-time debris drops
    private static long totalFutureBreakEligible;  // plan executions with fbe=true
    private static long totalBlocksBroken;          // first-time block destructions (Phase 2F)
    private static MaterialResponseType lastResponseType = MaterialResponseType.NONE;

    /**
     * Records that a plan was acted upon.
     *
     * Called by TrueImpactMod once per event after executing the plan.
     * Updates totalResponsesPlanned, totalFutureBreakEligible, lastResponseType.
     */
    public static void recordExecuted(MaterialResponsePlan plan) {
        if (plan.responseType() != MaterialResponseType.NONE) {
            totalResponsesPlanned++;
        }
        lastResponseType = plan.responseType();
        if (plan.futureBreakEligible()) {
            totalFutureBreakEligible++;
        }
    }

    /** Point-in-time snapshot of planner statistics for status display. */
    public static PlannerStats stats() {
        return new PlannerStats(totalResponsesPlanned, totalDebrisDropped,
                totalFutureBreakEligible, totalBlocksBroken,
                lastResponseType, debrisDroppedKeys.size(), breakScheduledKeys.size());
    }

    /**
     * Clears all planner state (debris-drop history, break history, and counters).
     * Registered as a DiagnosticStateManager flush hook.
     */
    public static void clear() {
        debrisDroppedKeys.clear();
        breakScheduledKeys.clear();
        totalResponsesPlanned    = 0L;
        totalDebrisDropped       = 0L;
        totalFutureBreakEligible = 0L;
        totalBlocksBroken        = 0L;
        lastResponseType         = MaterialResponseType.NONE;
    }

    // -- planning (pure) ----------------------------------------------------------

    /**
     * Produces a MaterialResponsePlan from an accumulated block-damage snapshot.
     *
     * Pure function: no static state is read or modified.
     * The plan describes what SHOULD happen; execution is the caller's responsibility.
     */
    public static MaterialResponsePlan plan(BlockDamageAccumulator.Snapshot snap) {
        DamageState state = snap.damageState();
        MaterialThresholdProfile.MaterialClass mc = snap.materialClass();
        double ratio = snap.ratio();

        MaterialResponseType responseType;
        boolean shouldDropDebris    = false;
        boolean futureBreakEligible = false;
        String note;

        if (state == DamageState.INTACT || state == DamageState.BRUISED) {
            responseType = MaterialResponseType.NONE;
            note = "below crack threshold (ratio=" + fmt(ratio) + ")";
        } else if (state == DamageState.CRACKED) {
            responseType = MaterialResponseType.COSMETIC_CRACK;
            note = "cosmetic crack (ratio=" + fmt(ratio) + ")";
        } else {
            // CRITICAL
            switch (mc) {
                case SOFT_SOIL -> {
                    responseType        = MaterialResponseType.COMPACT_SOFT_SOIL;
                    futureBreakEligible = true;  // break when compaction chain is exhausted
                    note = "soil compaction or break at CRITICAL (SOFT_SOIL)";
                }
                case BRITTLE, STONE, GENERIC -> {
                    responseType        = MaterialResponseType.DROP_DEBRIS;
                    shouldDropDebris    = true;
                    futureBreakEligible = true;
                    note = "debris + break eligible (" + mc + ")";
                }
                case WOOD, METAL, HIGH_STRENGTH -> {
                    responseType        = MaterialResponseType.FUTURE_BREAK_ELIGIBLE;
                    futureBreakEligible = true;
                    note = "break eligible (" + mc + ")";
                }
                default -> {
                    responseType = MaterialResponseType.NONE;
                    note = "unhandled material class " + mc;
                }
            }
        }

        return new MaterialResponsePlan(responseType, mc, state,
                snap.lastRawImpactJ(), snap.lastEffectiveDamageJ(),
                snap.thresholdJ(), ratio, shouldDropDebris, futureBreakEligible, note);
    }

    // -- supporting types ---------------------------------------------------------

    /** Point-in-time statistics snapshot for status display. */
    public record PlannerStats(
            long totalResponsesPlanned,
            long totalDebrisDropped,
            long totalFutureBreakEligible,
            long totalBlocksBroken,
            MaterialResponseType lastResponseType,
            int debrisDroppedKeyCount,
            int breakScheduledKeyCount
    ) {}

    private static String fmt(double v) {
        if (!Double.isFinite(v)) return String.valueOf(v);
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
